package cn.jens.mybatis.plugin;

import cn.jens.mybatis.exception.PersistenceException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 根据插件签名创建 JDK 动态代理。 */
public final class Plugin implements InvocationHandler {

    private final Object target;

    private final Interceptor interceptor;

    private final Map<Class<?>, Set<Method>> signatureMap;

    private Plugin(
            Object target,
            Interceptor interceptor,
            Map<Class<?>, Set<Method>> signatureMap) {
        this.target = target;
        this.interceptor = interceptor;
        this.signatureMap = signatureMap;
    }

    /**
     * 创建一个代理对象。
     * @param target 目标对象
     * @param interceptor 拦截器
     * @return 代理对象
     */
    public static Object wrap(Object target, Interceptor interceptor) {
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        Class<?>[] interfaces = getMatchingInterfaces(target.getClass(), signatureMap);
        if (interfaces.length == 0) {
            return target;
        }
        return Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                interfaces,
                new Plugin(target, interceptor, signatureMap)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Set<Method> interceptedMethods = signatureMap.get(method.getDeclaringClass());
        if (interceptedMethods != null && interceptedMethods.contains(method)) {
            return interceptor.intercept(new Invocation(target, method, args));
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
        Intercepts intercepts = interceptor.getClass().getAnnotation(Intercepts.class);
        if (intercepts == null) {
            throw new PersistenceException(
                    "Missing @Intercepts on plugin: " + interceptor.getClass().getName()
            );
        }

        Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
        for (Signature signature : intercepts.value()) {
            if (!signature.type().isInterface()) {
                throw new PersistenceException(
                        "Plugin signature type must be an interface: "
                                + signature.type().getName()
                );
            }
            Method method = resolveMethod(interceptor, signature);
            signatureMap.computeIfAbsent(signature.type(), key -> new HashSet<>()).add(method);
        }
        return signatureMap;
    }

    private static Method resolveMethod(Interceptor interceptor, Signature signature) {
        try {
            return signature.type().getMethod(signature.method(), signature.args());
        } catch (NoSuchMethodException e) {
            throw new PersistenceException(
                    "Invalid plugin signature on " + interceptor.getClass().getName()
                            + ": " + signature.type().getName() + "." + signature.method(),
                    e
            );
        }
    }

    private static Class<?>[] getMatchingInterfaces(
            Class<?> targetType,
            Map<Class<?>, Set<Method>> signatureMap) {
        List<Class<?>> interfaces = new ArrayList<>();
        Class<?> currentType = targetType;
        while (currentType != null) {
            for (Class<?> interfaceType : currentType.getInterfaces()) {
                if (signatureMap.containsKey(interfaceType)
                        && !interfaces.contains(interfaceType)) {
                    interfaces.add(interfaceType);
                }
            }
            currentType = currentType.getSuperclass();
        }
        return interfaces.toArray(Class<?>[]::new);
    }
}
