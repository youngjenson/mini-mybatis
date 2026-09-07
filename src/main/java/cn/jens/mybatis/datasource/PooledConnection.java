package cn.jens.mybatis.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/** 单次借用对应的逻辑连接代理。 */
final class PooledConnection implements InvocationHandler {

    private final PooledDataSource owner;

    private final PoolEntry poolEntry;

    private final long checkoutStartedNanos;

    private final Connection proxyConnection;

    private volatile boolean valid = true;

    PooledConnection(PooledDataSource owner, PoolEntry poolEntry, long checkoutStartedNanos) {
        this.owner = owner;
        this.poolEntry = poolEntry;
        this.checkoutStartedNanos = checkoutStartedNanos;
        proxyConnection = (Connection) Proxy.newProxyInstance(
                PooledConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                this
        );
    }

    Connection getProxyConnection() {
        return proxyConnection;
    }

    PoolEntry getPoolEntry() {
        return poolEntry;
    }

    long getCheckoutStartedNanos() {
        return checkoutStartedNanos;
    }

    void invalidate() {
        valid = false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, arguments);
        }
        return switch (method.getName()) {
            case "close" -> {
                owner.returnConnection(this);
                yield null;
            }
            case "isClosed" -> !valid || poolEntry.getPhysicalConnection().isClosed();
            case "unwrap" -> unwrap(proxy, (Class<?>) arguments[0]);
            case "isWrapperFor" -> isWrapperFor(proxy, (Class<?>) arguments[0]);
            default -> invokePhysicalConnection(method, arguments);
        };
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "PooledConnection{" + poolEntry.getPhysicalConnection() + '}';
            default -> throw new IllegalStateException("Unsupported Object method: " + method);
        };
    }

    private Object invokePhysicalConnection(Method method, Object[] arguments) throws Throwable {
        assertValid();
        try {
            return method.invoke(poolEntry.getPhysicalConnection(), arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object unwrap(Object proxy, Class<?> interfaceType) throws SQLException {
        assertValid();
        if (interfaceType.isInstance(proxy)) {
            return interfaceType.cast(proxy);
        }
        Connection physicalConnection = poolEntry.getPhysicalConnection();
        if (interfaceType.isInstance(physicalConnection)) {
            return interfaceType.cast(physicalConnection);
        }
        return physicalConnection.unwrap(interfaceType);
    }

    private boolean isWrapperFor(Object proxy, Class<?> interfaceType) throws SQLException {
        assertValid();
        Connection physicalConnection = poolEntry.getPhysicalConnection();
        return interfaceType.isInstance(proxy)
                || interfaceType.isInstance(physicalConnection)
                || physicalConnection.isWrapperFor(interfaceType);
    }

    private void assertValid() throws SQLException {
        if (!valid) {
            throw new SQLException("The pooled connection has already been closed or reclaimed");
        }
    }
}
