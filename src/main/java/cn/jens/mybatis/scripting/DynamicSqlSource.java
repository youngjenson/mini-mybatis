package cn.jens.mybatis.scripting;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.scripting.xmltags.DynamicContext;
import cn.jens.mybatis.scripting.xmltags.SqlNode;

/** 每次执行时渲染 SqlNode 树的动态 SQL 来源。 */
public class DynamicSqlSource implements SqlSource {

    private final SqlNode rootSqlNode;

    public DynamicSqlSource(SqlNode rootSqlNode) {
        this.rootSqlNode = rootSqlNode;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        DynamicContext context = new DynamicContext(parameterObject);
        rootSqlNode.apply(context);
        String sql = context.getSql();
        if (sql.isBlank()) {
            throw new PersistenceException("Dynamic SQL produced an empty statement");
        }
        return SqlParser.parse(sql, parameterObject, context.getBindings());
    }
}
