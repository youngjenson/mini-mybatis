package cn.jens.mybatis.scripting;

/**
 * 一个 SQL 参数占位符及其运行时值。
 *
 * @param property 参数属性名
 * @param value 参数值
 * @author YumJens
 */
public record ParameterMapping(String property, Object value) {
}
