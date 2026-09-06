package cn.jens.mybatis.plugin;

import java.util.Properties;

/** mini-MyBatis 插件扩展点。 */
public interface Interceptor {

    Object intercept(Invocation invocation) throws Throwable;

    /**
     * 拦截器对应的类型目标。
     * @param target 目标对象
     * @return 拦截器对应的类型目标
     */
    default Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    default void setProperties(Properties properties) {
        // 插件可按需覆盖。
    }
}
