package cn.jens.mybatis;

import cn.jens.demo.type.EmailAddress;
import cn.jens.demo.typehandler.EmailAddressTypeHandler;
import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandler;
import cn.jens.mybatis.type.TypeHandlerRegistry;
import cn.jens.mybatis.type.handler.NumberTypeHandler;
import cn.jens.mybatis.type.handler.StringTypeHandler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TypeHandlerRegistryTest {

    @Test
    void shouldSelectBuiltInAndAnnotatedTypeHandlers() {
        TypeHandlerRegistry registry = new TypeHandlerRegistry();

        assertInstanceOf(
                StringTypeHandler.class,
                registry.getTypeHandler(String.class, JdbcType.VARCHAR)
        );
        assertInstanceOf(
                NumberTypeHandler.class,
                registry.getTypeHandler(int.class, JdbcType.INTEGER)
        );

        registry.register(EmailAddressTypeHandler.class);
        TypeHandler<Object> varcharHandler = registry.getTypeHandler(
                EmailAddress.class,
                JdbcType.VARCHAR
        );

        assertInstanceOf(EmailAddressTypeHandler.class, varcharHandler);
        assertSame(
                varcharHandler,
                registry.getTypeHandler(EmailAddress.class, null)
        );
    }

    @Test
    void shouldInferHandledJavaTypeFromGenericBaseClass() {
        TypeHandlerRegistry registry = new TypeHandlerRegistry();

        registry.register(IdentifierTypeHandler.class);

        assertInstanceOf(
                IdentifierTypeHandler.class,
                registry.getTypeHandler(Identifier.class, JdbcType.VARCHAR)
        );
    }

    @Test
    void shouldUseTypedNullAndConcreteJdbcSetter() throws Exception {
        TypeHandlerRegistry registry = new TypeHandlerRegistry();
        AtomicReference<String> invokedMethod = new AtomicReference<>();
        AtomicInteger parameterIndex = new AtomicInteger();
        AtomicReference<Object> parameterValue = new AtomicReference<>();
        PreparedStatement statement = proxy(
                PreparedStatement.class,
                (method, arguments) -> {
                    if ("setNull".equals(method)) {
                        invokedMethod.set(method);
                        parameterIndex.set((int) arguments[0]);
                        parameterValue.set(arguments[1]);
                    } else if ("setString".equals(method)) {
                        invokedMethod.set(method);
                        parameterIndex.set((int) arguments[0]);
                        parameterValue.set(arguments[1]);
                    }
                    return null;
                }
        );
        TypeHandler<Object> handler = registry.getTypeHandler(
                String.class,
                JdbcType.VARCHAR
        );

        handler.setParameter(statement, 2, null, JdbcType.VARCHAR);

        assertEquals("setNull", invokedMethod.get());
        assertEquals(2, parameterIndex.get());
        assertEquals(java.sql.Types.VARCHAR, parameterValue.get());

        handler.setParameter(statement, 3, "Alice", null);

        assertEquals("setString", invokedMethod.get());
        assertEquals(3, parameterIndex.get());
        assertEquals("Alice", parameterValue.get());
    }

    @Test
    void shouldPreserveSqlNullWhenReadingPrimitiveJdbcGetter() throws Exception {
        ResultSet resultSet = proxy(
                ResultSet.class,
                (method, arguments) -> switch (method) {
                    case "getInt" -> 0;
                    case "wasNull" -> true;
                    default -> null;
                }
        );
        TypeHandler<Object> handler = new TypeHandlerRegistry().getTypeHandler(
                Integer.class,
                JdbcType.INTEGER
        );

        assertNull(handler.getResult(resultSet, 1));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> interfaceType, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                (proxy, method, arguments) -> {
                    Object value = invocation.invoke(method.getName(), arguments);
                    if (value != null || !method.getReturnType().isPrimitive()) {
                        return value;
                    }
                    return primitiveDefault(method.getReturnType());
                }
        );
    }

    private Object primitiveDefault(Class<?> type) {
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    private interface Invocation {

        Object invoke(String method, Object[] arguments) throws Throwable;
    }

    private record Identifier(String value) {
    }

    public static class IdentifierTypeHandler extends BaseTypeHandler<Identifier> {

        @Override
        protected void setNonNullParameter(
                PreparedStatement statement,
                int parameterIndex,
                Identifier parameter,
                JdbcType jdbcType) throws SQLException {
            statement.setString(parameterIndex, parameter.value());
        }

        @Override
        protected Identifier getNullableResult(ResultSet resultSet, String columnName)
                throws SQLException {
            return toIdentifier(resultSet.getString(columnName));
        }

        @Override
        protected Identifier getNullableResult(ResultSet resultSet, int columnIndex)
                throws SQLException {
            return toIdentifier(resultSet.getString(columnIndex));
        }

        private Identifier toIdentifier(String value) {
            return value == null ? null : new Identifier(value);
        }
    }
}
