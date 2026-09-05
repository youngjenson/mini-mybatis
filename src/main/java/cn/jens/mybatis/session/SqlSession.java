package cn.jens.session;

/**
 * SqlSession接口
 * @author YumJens
 * @date 2026-09-04 23:50
 */
public interface SqlSession {

    <T> T selectOne(String statementId, Object parameter);

    <T> T getMapper(Class<T> mapperClass);
}
