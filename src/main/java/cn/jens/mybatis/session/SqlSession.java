package cn.jens.mybatis.session;

import cn.jens.mybatis.config.Configuration;

import java.util.List;

/**
 * 执行 SQL、获取 Mapper 代理的会话接口。
 *
 * @author YumJens
 */
public interface SqlSession extends AutoCloseable {

    <T> T selectOne(String statementId, Object parameter);

    <T> List<T> selectList(String statementId, Object parameter);

    int insert(String statementId, Object parameter);

    int update(String statementId, Object parameter);

    int delete(String statementId, Object parameter);

    <T> T getMapper(Class<T> mapperClass);

    Configuration getConfiguration();

    void commit();

    void rollback();

    void clearCache();

    @Override
    void close();
}
