package cn.jens.mybatis.session;

/**
 * SqlSessionFactory
 * @author YumJens
 * @date 2026-09-05 14:33
 */
public interface SqlSessionFactory {

    SqlSession openSession();

    SqlSession openSession(boolean autoCommit);
}
