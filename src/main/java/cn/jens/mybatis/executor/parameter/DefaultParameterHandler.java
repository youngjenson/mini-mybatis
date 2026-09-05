package cn.jens.mybatis.executor.parameter;

import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/** 按 BoundSql 中的占位符顺序绑定参数。 */
public class DefaultParameterHandler implements ParameterHandler {

    private final BoundSql boundSql;

    public DefaultParameterHandler(BoundSql boundSql) {
        this.boundSql = boundSql;
    }

    @Override
    public void setParameters(PreparedStatement statement) throws SQLException {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        for (int index = 0; index < mappings.size(); index++) {
            statement.setObject(index + 1, mappings.get(index).value());
        }
    }
}
