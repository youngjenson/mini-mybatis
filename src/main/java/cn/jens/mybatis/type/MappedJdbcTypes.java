package cn.jens.mybatis.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 TypeHandler 对应的 JDBC 类型。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MappedJdbcTypes {

    JdbcType[] value();

    boolean includeNullJdbcType() default false;
}
