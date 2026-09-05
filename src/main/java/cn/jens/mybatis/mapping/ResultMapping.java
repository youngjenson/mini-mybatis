package cn.jens.mybatis.mapping;

/**
 * ResultSet 列与 Java 属性之间的一条显式映射。
 *
 * @param property Java 属性名
 * @param column 数据库列名或列别名
 * @param idFlag 是否为主键映射
 */
public record ResultMapping(String property, String column, boolean idFlag) {
}
