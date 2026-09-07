package cn.jens.mybatis.datasource;

import cn.jens.mybatis.exception.PersistenceException;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 一个用于讲解连接复用、等待和超时回收原理的小型 JDBC 连接池。
 *
 * @author YumJens
 */
public class PooledDataSource implements DataSource, AutoCloseable {

    private final DataSource delegate;

    private final PoolOptions options;

    private final Object lock = new Object();

    private final Deque<PoolEntry> idleConnections = new ArrayDeque<>();

    private final Set<PooledConnection> activeConnections = new LinkedHashSet<>();

    private boolean closed;

    private long requestCount;

    private long waitCount;

    private long accumulatedWaitTimeNanos;

    private long badConnectionCount;

    private long reclaimedConnectionCount;

    public PooledDataSource(DataSource delegate, PoolOptions options) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        this.delegate = delegate;
        this.options = options;
    }

    @Override
    public Connection getConnection() throws SQLException {
        synchronized (lock) {
            assertOpen();
            requestCount++;
            return borrowConnection();
        }
    }

    /** 使用其他凭据创建的连接不进入当前池，关闭时会直接关闭物理连接。 */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        synchronized (lock) {
            assertOpen();
        }
        return delegate.getConnection(username, password);
    }

    public PoolOptions getOptions() {
        return options;
    }

    public PoolStateSnapshot getPoolState() {
        synchronized (lock) {
            return new PoolStateSnapshot(
                    activeConnections.size(),
                    idleConnections.size(),
                    requestCount,
                    waitCount,
                    TimeUnit.NANOSECONDS.toMillis(accumulatedWaitTimeNanos),
                    badConnectionCount,
                    reclaimedConnectionCount
            );
        }
    }

    @Override
    public void close() {
        SQLException failure = null;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            for (PooledConnection activeConnection : activeConnections) {
                activeConnection.invalidate();
                failure = closePhysicalConnection(
                        activeConnection.getPoolEntry().getPhysicalConnection(),
                        failure
                );
            }
            activeConnections.clear();
            for (PoolEntry idleConnection : idleConnections) {
                failure = closePhysicalConnection(
                        idleConnection.getPhysicalConnection(),
                        failure
                );
            }
            idleConnections.clear();
            lock.notifyAll();
        }
        if (failure != null) {
            throw new PersistenceException("Failed to close pooled JDBC connections", failure);
        }
    }

    void returnConnection(PooledConnection pooledConnection) throws SQLException {
        synchronized (lock) {
            if (!activeConnections.remove(pooledConnection)) {
                pooledConnection.invalidate();
                return;
            }

            pooledConnection.invalidate();
            Connection physicalConnection = pooledConnection
                    .getPoolEntry()
                    .getPhysicalConnection();
            SQLException failure = null;
            try {
                resetConnection(physicalConnection);
            } catch (SQLException e) {
                failure = e;
                badConnectionCount++;
            }

            if (failure == null && !closed
                    && idleConnections.size() < options.maximumIdleConnections()) {
                PoolEntry poolEntry = pooledConnection.getPoolEntry();
                poolEntry.markUsed(System.nanoTime());
                idleConnections.addLast(poolEntry);
            } else {
                failure = closePhysicalConnection(physicalConnection, failure);
            }
            lock.notifyAll();
            if (failure != null) {
                throw failure;
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> interfaceType) throws SQLException {
        if (interfaceType.isInstance(this)) {
            return interfaceType.cast(this);
        }
        if (interfaceType.isInstance(delegate)) {
            return interfaceType.cast(delegate);
        }
        return delegate.unwrap(interfaceType);
    }

    @Override
    public boolean isWrapperFor(Class<?> interfaceType) throws SQLException {
        return interfaceType.isInstance(this)
                || interfaceType.isInstance(delegate)
                || delegate.isWrapperFor(interfaceType);
    }

    private Connection borrowConnection() throws SQLException {
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(options.timeToWaitMillis());
        int localBadConnectionCount = 0;
        while (true) {
            assertOpen();
            PoolEntry poolEntry = pollIdleConnection();
            if (poolEntry == null && activeConnections.size()
                    < options.maximumActiveConnections()) {
                poolEntry = new PoolEntry(delegate.getConnection(), System.nanoTime());
            }
            if (poolEntry == null) {
                poolEntry = reclaimOverdueConnection();
            }
            if (poolEntry != null) {
                try {
                    validateConnection(poolEntry);
                    return activate(poolEntry);
                } catch (SQLException e) {
                    badConnectionCount++;
                    localBadConnectionCount++;
                    closePhysicalConnection(poolEntry.getPhysicalConnection(), e);
                    int maximumLocalBadConnectionCount =
                            options.maximumIdleConnections()
                                    + options.maximumLocalBadConnectionTolerance();
                    if (localBadConnectionCount > maximumLocalBadConnectionCount) {
                        throw new SQLException(
                                "Could not acquire a valid pooled JDBC connection",
                                e
                        );
                    }
                    continue;
                }
            }
            waitForConnection(deadlineNanos);
        }
    }

    private PoolEntry pollIdleConnection() {
        return idleConnections.pollFirst();
    }

    private PoolEntry reclaimOverdueConnection() throws SQLException {
        if (activeConnections.isEmpty()) {
            return null;
        }
        PooledConnection oldestConnection = activeConnections.iterator().next();
        long checkoutNanos = System.nanoTime()
                - oldestConnection.getCheckoutStartedNanos();
        long maximumCheckoutNanos = TimeUnit.MILLISECONDS.toNanos(
                options.maximumCheckoutTimeMillis()
        );
        if (checkoutNanos < maximumCheckoutNanos) {
            return null;
        }

        activeConnections.remove(oldestConnection);
        oldestConnection.invalidate();
        reclaimedConnectionCount++;
        PoolEntry poolEntry = oldestConnection.getPoolEntry();
        try {
            resetConnection(poolEntry.getPhysicalConnection());
        } catch (SQLException e) {
            badConnectionCount++;
            closePhysicalConnection(poolEntry.getPhysicalConnection(), e);
            throw e;
        }
        poolEntry.markUsed(System.nanoTime());
        return poolEntry;
    }

    private Connection activate(PoolEntry poolEntry) {
        PooledConnection pooledConnection = new PooledConnection(
                this,
                poolEntry,
                System.nanoTime()
        );
        activeConnections.add(pooledConnection);
        return pooledConnection.getProxyConnection();
    }

    private void validateConnection(PoolEntry poolEntry) throws SQLException {
        Connection connection = poolEntry.getPhysicalConnection();
        if (connection.isClosed()) {
            throw new SQLException("The physical JDBC connection is closed");
        }
        if (!shouldPing(poolEntry)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(options.pingQuery());
        }
        if (!connection.getAutoCommit()) {
            connection.rollback();
        }
    }

    private boolean shouldPing(PoolEntry poolEntry) {
        if (!options.pingEnabled()) {
            return false;
        }
        long idleNanos = System.nanoTime() - poolEntry.getLastUsedNanos();
        return idleNanos >= TimeUnit.MILLISECONDS.toNanos(
                options.pingConnectionsNotUsedForMillis()
        );
    }

    private void waitForConnection(long deadlineNanos) throws SQLException {
        long nowNanos = System.nanoTime();
        long remainingNanos = deadlineNanos - nowNanos;
        if (remainingNanos <= 0) {
            throw new SQLTimeoutException(
                    "Timed out after " + options.timeToWaitMillis()
                            + " ms waiting for a pooled JDBC connection"
            );
        }

        remainingNanos = Math.min(remainingNanos, nanosUntilOldestCanBeReclaimed(nowNanos));
        waitCount++;
        long waitStartedNanos = System.nanoTime();
        try {
            TimeUnit.NANOSECONDS.timedWait(lock, Math.max(1L, remainingNanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a pooled JDBC connection", e);
        } finally {
            accumulatedWaitTimeNanos += System.nanoTime() - waitStartedNanos;
        }
    }

    private long nanosUntilOldestCanBeReclaimed(long nowNanos) {
        if (activeConnections.isEmpty()) {
            return Long.MAX_VALUE;
        }
        PooledConnection oldestConnection = activeConnections.iterator().next();
        long maximumCheckoutNanos = TimeUnit.MILLISECONDS.toNanos(
                options.maximumCheckoutTimeMillis()
        );
        long elapsedNanos = nowNanos - oldestConnection.getCheckoutStartedNanos();
        return Math.max(1L, maximumCheckoutNanos - elapsedNanos);
    }

    private void resetConnection(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        connection.clearWarnings();
    }

    private SQLException closePhysicalConnection(
            Connection connection,
            SQLException previousFailure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            if (previousFailure == null) {
                return closeFailure;
            }
            previousFailure.addSuppressed(closeFailure);
        }
        return previousFailure;
    }

    private void assertOpen() throws SQLException {
        if (closed) {
            throw new SQLException("The pooled DataSource has been closed");
        }
    }
}
