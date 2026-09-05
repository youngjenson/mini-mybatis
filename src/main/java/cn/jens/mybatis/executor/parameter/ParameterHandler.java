package cn.jens.mybatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/** 为 PreparedStatement 绑定参数。 */
public interface ParameterHandler {

    void setParameters(PreparedStatement statement) throws SQLException;
}
