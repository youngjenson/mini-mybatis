package cn.jens.mybatis.plugin;

import java.util.ArrayList;
import java.util.List;

/** 按配置顺序为目标对象应用全部插件。 */
public class InterceptorChain {

    private final List<Interceptor> interceptors = new ArrayList<>();

    public void addInterceptor(Interceptor interceptor) {
        interceptors.add(interceptor);
    }

    public Object pluginAll(Object target) {
        Object wrappedTarget = target;
        for (Interceptor interceptor : interceptors) {
            wrappedTarget = interceptor.plugin(wrappedTarget);
        }
        return wrappedTarget;
    }

    public List<Interceptor> getInterceptors() {
        return List.copyOf(interceptors);
    }
}
