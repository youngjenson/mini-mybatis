package cn.jens.mybatis.cache;

import java.util.ArrayList;
import java.util.Collections;
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
        parameterValues = Collections.unmodifiableList(new ArrayList<>(parameterValues));
    }
}
