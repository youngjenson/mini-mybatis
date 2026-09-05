package cn.jens.mybatis.plugin;

import cn.jens.mybatis.executor.Executor;
import cn.jens.mybatis.executor.parameter.ParameterHandler;
import cn.jens.mybatis.executor.resultset.ResultSetHandler;
import cn.jens.mybatis.executor.statement.StatementHandler;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.mapping.ResultMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 记录四层扩展点调用次数的集成测试插件。 */
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class}
        ),
        @Signature(
                type = Executor.class,
                method = "update",
                args = {MappedStatement.class, Object.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "parameterize",
                args = {PreparedStatement.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "query",
                args = {PreparedStatement.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "update",
                args = {PreparedStatement.class}
        ),
        @Signature(
                type = ParameterHandler.class,
                method = "setParameters",
                args = {PreparedStatement.class}
        ),
        @Signature(
                type = ResultSetHandler.class,
                method = "handle",
                args = {ResultSet.class, Class.class, ResultMap.class}
        )
})
public class InvocationCountingInterceptor implements Interceptor {

    public static final String EXECUTOR_QUERY = "Executor.query";

    public static final String EXECUTOR_UPDATE = "Executor.update";

    public static final String STATEMENT_PREPARE = "StatementHandler.prepare";

    public static final String STATEMENT_PARAMETERIZE = "StatementHandler.parameterize";

    public static final String STATEMENT_QUERY = "StatementHandler.query";

    public static final String STATEMENT_UPDATE = "StatementHandler.update";

    public static final String PARAMETER_SET = "ParameterHandler.setParameters";

    public static final String RESULT_HANDLE = "ResultSetHandler.handle";

    private static final ConcurrentMap<String, AtomicInteger> COUNTS =
            new ConcurrentHashMap<>();

    private static volatile String configuredLabel;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String key = invocation.getMethod().getDeclaringClass().getSimpleName()
                + "." + invocation.getMethod().getName();
        COUNTS.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        return invocation.proceed();
    }

    @Override
    public void setProperties(Properties properties) {
        configuredLabel = properties.getProperty("label");
    }

    public static int count(String key) {
        AtomicInteger count = COUNTS.get(key);
        return count == null ? 0 : count.get();
    }

    public static String getConfiguredLabel() {
        return configuredLabel;
    }

    public static void reset() {
        COUNTS.clear();
        configuredLabel = null;
    }
}
