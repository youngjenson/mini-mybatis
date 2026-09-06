package cn.jens.mybatis.mapping;

import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.SqlSource;
import cn.jens.mybatis.scripting.StaticSqlSource;

import java.util.Objects;

/**
 * 一条已注册 SQL 语句的元数据。
 *
 * @author YumJens
 */
public record MappedStatement(
        String id,
        String sql,
        Class<?> resultType,
        SqlCommandType sqlCommandType,
        ResultMap resultMap,
        boolean flushCacheRequired,
        boolean useCache,
        SqlSource sqlSource) {

    public MappedStatement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(sqlCommandType, "sqlCommandType");
        Objects.requireNonNull(sqlSource, "sqlSource");
    }

    public MappedStatement(
            String id,
            String sql,
            Class<?> resultType,
            SqlCommandType sqlCommandType,
            ResultMap resultMap,
            boolean flushCacheRequired,
            boolean useCache) {
        this(
                id,
                sql,
                resultType,
                sqlCommandType,
                resultMap,
                flushCacheRequired,
                useCache,
                new StaticSqlSource(sql)
        );
    }

    public MappedStatement(String id, String sql, Class<?> resultType) {
        this(id, sql, resultType, SqlCommandType.SELECT, null, false, true);
    }

    public MappedStatement(
            String id,
            String sql,
            Class<?> resultType,
            SqlCommandType sqlCommandType) {
        this(
                id,
                sql,
                resultType,
                sqlCommandType,
                null,
                sqlCommandType != SqlCommandType.SELECT,
                sqlCommandType == SqlCommandType.SELECT
        );
    }

    public MappedStatement(
            String id,
            String sql,
            Class<?> resultType,
            SqlCommandType sqlCommandType,
            ResultMap resultMap) {
        this(
                id,
                sql,
                resultType,
                sqlCommandType,
                resultMap,
                sqlCommandType != SqlCommandType.SELECT,
                sqlCommandType == SqlCommandType.SELECT
        );
    }

    public String namespace() {
        int separatorIndex = id.lastIndexOf('.');
        return separatorIndex < 0 ? id : id.substring(0, separatorIndex);
    }

    public BoundSql getBoundSql(Object parameterObject) {
        return sqlSource.getBoundSql(parameterObject);
    }
}
