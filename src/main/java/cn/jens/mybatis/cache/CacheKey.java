package cn.jens.mybatis.cache;

import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.scripting.BoundSql;
import cn.jens.mybatis.scripting.ParameterMapping;

import java.util.List;

/**
 * 一级缓存键。
 *
 * @param statementId 映射语句 ID
 * @param sql JDBC SQL
 * @param parameterValues 按占位符顺序排列的参数值
 */
public record CacheKey(String statementId, String sql, List<Object> parameterValues) {

    public CacheKey {
        parameterValues = List.copyOf(parameterValues);
    }

    public static CacheKey create(MappedStatement mappedStatement, BoundSql boundSql) {
        List<Object> parameterValues = boundSql.getParameterMappings().stream()
                .map(ParameterMapping::value)
                .toList();
        return new CacheKey(mappedStatement.id(), boundSql.getSql(), parameterValues);
    }
}
