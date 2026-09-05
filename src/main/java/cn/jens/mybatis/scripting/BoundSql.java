package cn.jens.scripting;

import java.util.List;

/**
 * 绑定SQL
 * @author YumJens
 * @date 2026-09-05 00:02
 */
public class BoundSql {

    private String sql;

    private List<Object> parameters;

    public BoundSql(
            String sql,
            List<Object> parameters) {

        this.sql = sql;
        this.parameters = parameters;
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParameters() {
        return parameters;
    }
}
