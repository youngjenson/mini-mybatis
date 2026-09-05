package cn.jens.session;

import cn.jens.config.Configuration;

/**
 * 默认的SqlSessionFactory实现
 *
 * @author YumJens
 * @date 2026-09-05 14:34
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

    private final Configuration configuration;

    public DefaultSqlSessionFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public SqlSession openSession() {
        return new DefaultSqlSession(configuration);
    }
}
