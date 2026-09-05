package cn.jens.mybatis.plugin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 精确描述被拦截接口、方法名和参数类型。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Signature {

    Class<?> type();

    String method();

    Class<?>[] args();
}
