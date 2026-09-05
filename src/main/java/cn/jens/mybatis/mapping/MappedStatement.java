package cn.jens.mybatis.mapping;

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
        boolean flushCacheRequired) {

    public MappedStatement(String id, String sql, Class<?> resultType) {
        this(id, sql, resultType, SqlCommandType.SELECT, null, false);
    }

    public MappedStatement(
            String id,
            String sql,
            Class<?> resultType,
            SqlCommandType sqlCommandType) {
        this(id, sql, resultType, sqlCommandType, null, sqlCommandType != SqlCommandType.SELECT);
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
                sqlCommandType != SqlCommandType.SELECT
        );
    }
}
