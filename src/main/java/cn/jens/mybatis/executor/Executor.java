package cn.jens.mybatis.executor;

import cn.jens.mybatis.mapping.MappedStatement;

import java.util.List;

/**
 * SQL 执行器。
 *
 * @author YumJens
 */
public interface Executor {

    <T> List<T> query(MappedStatement mappedStatement, Object parameter);

    int update(MappedStatement mappedStatement, Object parameter);

    void commit();

    void rollback();

    void clearLocalCache();

    void close();
}
