package cn.jens.mybatis.scripting;

import cn.jens.mybatis.type.TypeAliasRegistry;
import cn.jens.mybatis.type.TypeHandlerRegistry;

import java.util.Collections;

/** 不包含动态 XML 节点的 SQL 来源。 */
public class StaticSqlSource implements SqlSource {

    private final String sql;

    private final TypeHandlerRegistry typeHandlerRegistry;

    private final TypeAliasRegistry typeAliasRegistry;

    public StaticSqlSource(String sql) {
        this(sql, new TypeHandlerRegistry(), new TypeAliasRegistry());
    }

    public StaticSqlSource(
            String sql,
            TypeHandlerRegistry typeHandlerRegistry,
            TypeAliasRegistry typeAliasRegistry) {
        this.sql = sql;
        this.typeHandlerRegistry = typeHandlerRegistry;
        this.typeAliasRegistry = typeAliasRegistry;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        return SqlParser.parse(
                sql,
                parameterObject,
                Collections.emptyMap(),
                typeHandlerRegistry,
                typeAliasRegistry
        );
    }
}
