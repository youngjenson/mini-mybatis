package cn.jens.mybatis.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 在 Java 值与 JDBC 值之间进行双向转换。 */
public interface TypeHandler<T> {

    void setParameter(
            PreparedStatement statement,
            int parameterIndex,
            T parameter,
            JdbcType jdbcType) throws SQLException;

    T getResult(ResultSet resultSet, String columnName) throws SQLException;

    T getResult(ResultSet resultSet, int columnIndex) throws SQLException;
}
