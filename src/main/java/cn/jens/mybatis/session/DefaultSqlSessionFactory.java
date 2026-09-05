package cn.jens.mybatis.session;

import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.executor.Executor;
import cn.jens.mybatis.executor.SimpleExecutor;
import cn.jens.mybatis.transaction.JdbcTransaction;
import cn.jens.mybatis.transaction.Transaction;

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
        return openSession(false);
    }

    @Override
    public SqlSession openSession(boolean autoCommit) {
        Transaction transaction = new JdbcTransaction(configuration.getDataSource(), autoCommit);
        Executor executor = new SimpleExecutor(transaction, configuration.getLocalCacheScope());
        return new DefaultSqlSession(configuration, executor, autoCommit);
    }
}
