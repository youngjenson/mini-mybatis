package cn.jens.mybatis.mapping;

import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandler;

/**
 * ResultSet 列与 Java 属性之间的一条显式映射。
 *
 * @param property Java 属性名
 * @param column 数据库列名或列别名
 * @param idFlag 是否为主键映射
 * @param javaType 属性对应的 Java 类型
 * @param jdbcType 显式声明的 JDBC 类型
 * @param typeHandler 映射级覆盖的类型处理器
 */
public record ResultMapping(
        String property,
        String column,
        boolean idFlag,
        Class<?> javaType,
        JdbcType jdbcType,
        TypeHandler<Object> typeHandler) {

    public ResultMapping(String property, String column, boolean idFlag) {
        this(property, column, idFlag, null, null, null);
    }
}
