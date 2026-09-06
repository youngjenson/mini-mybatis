package cn.jens.mybatis;

import cn.jens.mybatis.executor.parameter.DefaultParameterHandler;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandlerRegistry;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultParameterHandlerTest {

    @Test
    void shouldUseConfiguredJdbcTypeForUntypedNull() throws Exception {
        AtomicInteger nullTypeCode = new AtomicInteger();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if ("setNull".equals(method.getName())) {
                        nullTypeCode.set((int) arguments[1]);
                    }
                    return method.getReturnType().isPrimitive() ? primitiveDefault(
                            method.getReturnType()
                    ) : null;
                }
        );
        BoundSql boundSql = new BoundSql(
                "insert into sample(value) values (?)",
                List.of(new ParameterMapping("value", null))
        );
        DefaultParameterHandler parameterHandler = new DefaultParameterHandler(
                boundSql,
                new TypeHandlerRegistry(),
                JdbcType.NULL
        );

        parameterHandler.setParameters(statement);

        assertEquals(java.sql.Types.NULL, nullTypeCode.get());
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
}
