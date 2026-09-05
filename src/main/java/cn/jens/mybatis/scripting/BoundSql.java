package cn.jens.mybatis.scripting;

import java.util.List;

/**
 * 已将 #{} 转换为 JDBC 占位符的 SQL。
 *
 * @author YumJens
 */
public final class BoundSql {

    private final String sql;

    private final List<ParameterMapping> parameterMappings;

    public BoundSql(String sql, List<ParameterMapping> parameterMappings) {
        this.sql = sql;
        this.parameterMappings = List.copyOf(parameterMappings);
    }

    public String getSql() {
        return sql;
    }

    public List<ParameterMapping> getParameterMappings() {
        return parameterMappings;
    }
}
