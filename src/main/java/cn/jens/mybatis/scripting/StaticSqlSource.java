package cn.jens.mybatis.scripting;

/** 不包含动态 XML 节点的 SQL 来源。 */
public class StaticSqlSource implements SqlSource {

    private final String sql;

    public StaticSqlSource(String sql) {
        this.sql = sql;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        return SqlParser.parse(sql, parameterObject);
    }
}
