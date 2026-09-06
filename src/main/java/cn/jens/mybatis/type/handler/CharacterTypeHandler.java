package cn.jens.mybatis.type.handler;

import cn.jens.mybatis.type.BaseTypeHandler;
import cn.jens.mybatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Character 与单字符 JDBC 列之间的转换。 */
public class CharacterTypeHandler extends BaseTypeHandler<Character> {

    @Override
    protected void setNonNullParameter(
            PreparedStatement statement,
            int parameterIndex,
            Character parameter,
            JdbcType jdbcType) throws SQLException {
        statement.setString(parameterIndex, parameter.toString());
    }

    @Override
    protected Character getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return firstCharacter(resultSet.getString(columnName));
    }

    @Override
    protected Character getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return firstCharacter(resultSet.getString(columnIndex));
    }

    private Character firstCharacter(String value) {
        return value == null || value.isEmpty() ? null : value.charAt(0);
    }
}
