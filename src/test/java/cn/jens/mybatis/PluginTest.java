package cn.jens.mybatis;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.plugin.Interceptor;
import cn.jens.mybatis.plugin.InterceptorChain;
import cn.jens.mybatis.plugin.Intercepts;
import cn.jens.mybatis.plugin.Invocation;
import cn.jens.mybatis.plugin.Signature;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginTest {

    @Test
    void shouldInvokeLastConfiguredPluginFirst() throws Exception {
        List<String> calls = new ArrayList<>();
        InterceptorChain chain = new InterceptorChain();
        chain.addInterceptor(new OrderedInterceptor("first", calls));
        chain.addInterceptor(new OrderedInterceptor("second", calls));
        Callable<?> service = (Callable<?>) chain.pluginAll(
                (Callable<String>) () -> {
                    calls.add("target");
                    return "Hello, Alice";
                }
        );

        assertEquals("Hello, Alice", service.call());
        assertEquals(
                List.of(
                        "second-before",
                        "first-before",
                        "target",
                        "first-after",
                        "second-after"
                ),
                calls
        );
    }

    @Test
    void shouldRejectPluginWithoutInterceptsAnnotation() {
        Interceptor interceptor = Invocation::proceed;

        assertThrows(
                PersistenceException.class,
                () -> interceptor.plugin((Callable<String>) () -> "Hello")
        );
    }

    @Test
    void shouldRejectUnknownSignatureMethod() {
        assertThrows(
                PersistenceException.class,
                () -> new InvalidSignatureInterceptor().plugin(
                        (Callable<String>) () -> "Hello"
                )
        );
    }

    @Intercepts(@Signature(
            type = Callable.class,
            method = "call",
            args = {}
    ))
    private static class OrderedInterceptor implements Interceptor {

        private final String name;

        private final List<String> calls;

        private OrderedInterceptor(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            calls.add(name + "-before");
            try {
                return invocation.proceed();
            } finally {
                calls.add(name + "-after");
            }
        }
    }

    @Intercepts(@Signature(
            type = Callable.class,
            method = "missing",
            args = {}
    ))
    private static class InvalidSignatureInterceptor implements Interceptor {

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            return invocation.proceed();
        }
    }
}
