package cn.jens.mybatis.desgin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * TestProxy class
 * @author YumJens
 * @date 2026-09-05 15:32
 */
public class TestProxy implements InvocationHandler {


    public <T> T getProxy(Class<T> clazz) {
        Object proxy = Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class<?>[]{clazz},
                this
        );
        return clazz.cast(proxy);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return null;
    }
}
