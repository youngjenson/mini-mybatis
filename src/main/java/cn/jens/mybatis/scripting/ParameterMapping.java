package cn.jens.mybatis.scripting;

import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandler;

/**
 * 一个 SQL 参数占位符及其运行时值。
 *
 * @param property 参数属性名
 * @param value 参数值
 * @param javaType 参数声明或推断出的 Java 类型
 * @param jdbcType 显式声明的 JDBC 类型
 * @param typeHandler 映射级覆盖的类型处理器
 * @author YumJens
 */
public record ParameterMapping(
        String property,
        Object value,
        Class<?> javaType,
        JdbcType jdbcType,
        TypeHandler<Object> typeHandler) {

    public ParameterMapping(String property, Object value) {
        this(
                property,
                value,
                value == null ? Object.class : value.getClass(),
                null,
                null
        );
    }
}
