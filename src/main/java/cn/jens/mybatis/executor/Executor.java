package cn.jens.mybatis.executor;

import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.scripting.BoundSql;

import java.util.List;

/**
 * SQL 执行器。
 *
 * @author YumJens
 */
public interface Executor {

    <T> List<T> query(MappedStatement mappedStatement, Object parameter);

    <T> List<T> query(
            MappedStatement mappedStatement,
            Object parameter,
            BoundSql boundSql);

    int update(MappedStatement mappedStatement, Object parameter);

    void commit();

    void rollback();

    void clearLocalCache();

    void close();
}
