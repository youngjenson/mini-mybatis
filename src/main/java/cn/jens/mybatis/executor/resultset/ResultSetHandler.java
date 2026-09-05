package cn.jens.mybatis.executor.resultset;

import cn.jens.mybatis.mapping.ResultMap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** 把 JDBC ResultSet 映射为 Java 结果集合。 */
public interface ResultSetHandler {

    <T> List<T> handle(
            ResultSet resultSet,
            Class<?> resultType,
            ResultMap resultMap) throws SQLException;
}
