package cn.jens.mybatis.datasource;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PooledDataSourceTest {

    @Test
    void shouldReturnLogicalConnectionAndReusePhysicalConnection() throws Exception {
        TrackingDataSource delegate = new TrackingDataSource();
        PooledDataSource dataSource = new PooledDataSource(
                delegate,
                options(2, 1, 10_000L, 100L, false)
        );

        Connection first = dataSource.getConnection();
        PhysicalConnection firstPhysical = first.unwrap(PhysicalConnection.class);
        first.setAutoCommit(false);
        first.close();

        assertTrue(first.isClosed());
        assertThrows(SQLException.class, first::getAutoCommit);
        assertEquals(1, delegate.rollbackCount);
        assertEquals(1, dataSource.getPoolState().idleConnectionCount());

        Connection second = dataSource.getConnection();
        PhysicalConnection secondPhysical = second.unwrap(PhysicalConnection.class);

        assertNotSame(first, second);
        assertSame(firstPhysical, secondPhysical);
        assertTrue(second.getAutoCommit());
        assertEquals(1, delegate.createdConnectionCount());
        assertEquals(2, dataSource.getPoolState().requestCount());

        second.close();
        dataSource.close();
        assertEquals(1, delegate.closedConnectionCount);
    }

    @Test
    void shouldTimeOutWhenPoolIsExhausted() throws Exception {
        TrackingDataSource delegate = new TrackingDataSource();
        try (PooledDataSource dataSource = new PooledDataSource(
                delegate,
                options(1, 1, 10_000L, 30L, false)
        )) {
            Connection connection = dataSource.getConnection();

            assertThrows(SQLTimeoutException.class, dataSource::getConnection);
            assertEquals(1, dataSource.getPoolState().activeConnectionCount());
            assertTrue(dataSource.getPoolState().waitCount() >= 1);

            connection.close();
        }
    }

    @Test
    void shouldReclaimConnectionCheckedOutForTooLong() throws Exception {
        TrackingDataSource delegate = new TrackingDataSource();
        try (PooledDataSource dataSource = new PooledDataSource(
                delegate,
                options(1, 1, 0L, 100L, false)
        )) {
            Connection first = dataSource.getConnection();
            PhysicalConnection firstPhysical = first.unwrap(PhysicalConnection.class);

            Connection second = dataSource.getConnection();

            assertTrue(first.isClosed());
            assertThrows(SQLException.class, first::createStatement);
            assertSame(
                    firstPhysical,
                    second.unwrap(PhysicalConnection.class)
            );
            assertEquals(1, delegate.createdConnectionCount());
            assertEquals(1, dataSource.getPoolState().reclaimedConnectionCount());

            first.close();
            second.close();
        }
    }

    @Test
    void shouldDiscardClosedIdleConnection() throws Exception {
        TrackingDataSource delegate = new TrackingDataSource();
        try (PooledDataSource dataSource = new PooledDataSource(
                delegate,
                options(1, 1, 10_000L, 100L, false)
        )) {
            Connection first = dataSource.getConnection();
            PhysicalConnection firstPhysical = first.unwrap(PhysicalConnection.class);
            first.close();
            delegate.breakLastConnection();

            Connection second = dataSource.getConnection();

            assertNotSame(firstPhysical, second.unwrap(PhysicalConnection.class));
            assertEquals(2, delegate.createdConnectionCount());
            assertEquals(1, dataSource.getPoolState().badConnectionCount());
            second.close();
        }
    }

    @Test
    void shouldCloseSurplusConnectionWhenIdlePoolIsFull() throws Exception {
        TrackingDataSource delegate = new TrackingDataSource();
        PooledDataSource dataSource = new PooledDataSource(
                delegate,
                options(2, 1, 10_000L, 100L, false)
        );
        Connection first = dataSource.getConnection();
        Connection second = dataSource.getConnection();

        first.close();
        second.close();

        assertEquals(1, dataSource.getPoolState().idleConnectionCount());
        assertEquals(1, delegate.closedConnectionCount);
        dataSource.close();
        assertEquals(2, delegate.closedConnectionCount);
    }

    @Test
    void shouldPingConnectionsAndInvalidateLeasesWhenPoolCloses() throws Exception {
        TrackingDataSource delegate = new TrackingDataSource();
        PooledDataSource dataSource = new PooledDataSource(
                delegate,
                options(1, 1, 10_000L, 100L, true)
        );
        Connection first = dataSource.getConnection();
        first.close();
        Connection second = dataSource.getConnection();

        assertEquals(2, delegate.pingCount);

        dataSource.close();
        assertTrue(second.isClosed());
        assertThrows(SQLException.class, second::getAutoCommit);
        assertThrows(SQLException.class, dataSource::getConnection);
        assertEquals(1, delegate.closedConnectionCount);
    }

    private PoolOptions options(
            int maximumActiveConnections,
            int maximumIdleConnections,
            long maximumCheckoutTimeMillis,
            long timeToWaitMillis,
            boolean pingEnabled) {
        return new PoolOptions(
                maximumActiveConnections,
                maximumIdleConnections,
                maximumCheckoutTimeMillis,
                timeToWaitMillis,
                3,
                pingEnabled,
                "SELECT 1",
                0L
        );
    }

    private interface PhysicalConnection {
    }

    private static final class TrackingDataSource implements DataSource {

        private final List<TrackingConnection> connections = new ArrayList<>();

        private int closedConnectionCount;

        private int rollbackCount;

        private int pingCount;

        @Override
        public Connection getConnection() {
            TrackingConnection trackingConnection = new TrackingConnection(this);
            connections.add(trackingConnection);
            return trackingConnection.createProxy();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        int createdConnectionCount() {
            return connections.size();
        }

        void breakLastConnection() {
            connections.getLast().closed = true;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> interfaceType) throws SQLException {
            if (interfaceType.isInstance(this)) {
                return interfaceType.cast(this);
            }
            throw new SQLException("Not a wrapper for " + interfaceType.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> interfaceType) {
            return interfaceType.isInstance(this);
        }
    }

    private static final class TrackingConnection implements InvocationHandler {

        private final TrackingDataSource owner;

        private boolean closed;

        private boolean autoCommit = true;

        private TrackingConnection(TrackingDataSource owner) {
            this.owner = owner;
        }

        Connection createProxy() {
            return (Connection) Proxy.newProxyInstance(
                    PooledDataSourceTest.class.getClassLoader(),
                    new Class<?>[]{Connection.class, PhysicalConnection.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws SQLException {
            return switch (method.getName()) {
                case "close" -> close();
                case "isClosed" -> closed;
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> setAutoCommit((boolean) arguments[0]);
                case "rollback" -> rollback();
                case "clearWarnings" -> null;
                case "createStatement" -> createStatementProxy();
                case "toString" -> "PhysicalConnection@" + System.identityHashCode(proxy);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object close() {
            if (!closed) {
                closed = true;
                owner.closedConnectionCount++;
            }
            return null;
        }

        private Object setAutoCommit(boolean newAutoCommit) {
            autoCommit = newAutoCommit;
            return null;
        }

        private Object rollback() {
            owner.rollbackCount++;
            return null;
        }

        private Statement createStatementProxy() {
            return (Statement) Proxy.newProxyInstance(
                    PooledDataSourceTest.class.getClassLoader(),
                    new Class<?>[]{Statement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "execute" -> {
                            owner.pingCount++;
                            yield false;
                        }
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private static Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return 0;
        }
    }
}
