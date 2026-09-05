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
        boolean flushCacheRequired,
        boolean useCache) {

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
}
