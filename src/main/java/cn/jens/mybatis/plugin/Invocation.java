package cn.jens.mybatis.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** 一次可继续执行的插件方法调用。 */
public final class Invocation {

    private final Object target;

    private final Method method;

    private final Object[] args;

    public Invocation(Object target, Method method, Object[] args) {
        this.target = target;
        this.method = method;
        this.args = args;
    }

    public Object getTarget() {
        return target;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public Object proceed() throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
