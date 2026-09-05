package cn.jens.mybatis.plugin;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.executor.Executor;
import cn.jens.mybatis.mapping.MappedStatement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** 超过指定耗时阈值时记录 mapped statement ID。 */
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
        )
})
public class SlowSqlInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(SlowSqlInterceptor.class);

    private long thresholdMillis = 500;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startNanos
            );
            if (elapsedMillis >= thresholdMillis) {
                MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
                LOG.warn(
                        "slow_sql statementId={} elapsedMs={} thresholdMs={}",
                        mappedStatement.id(),
                        elapsedMillis,
                        thresholdMillis
                );
            }
        }
    }

    @Override
    public void setProperties(Properties properties) {
        String value = properties.getProperty("thresholdMillis");
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            thresholdMillis = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new PersistenceException("Invalid slow SQL thresholdMillis: " + value, e);
        }
        if (thresholdMillis < 0) {
            throw new PersistenceException("slow SQL thresholdMillis must not be negative");
        }
    }
}
