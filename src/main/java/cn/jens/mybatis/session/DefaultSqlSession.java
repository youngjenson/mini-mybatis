package cn.jens.session;

import cn.jens.binding.MapperProxy;
import cn.jens.config.Configuration;
import cn.jens.excutor.Executor;
import cn.jens.excutor.SimpleExecutor;
import cn.jens.mapping.MappedStatement;

import java.lang.reflect.Proxy;

/**
 * 默认的SqlSession实现
 * @author YumJens
 * @date 2026-09-04 23:51
 */
public class DefaultSqlSession implements SqlSession{

    private final Configuration configuration;

    public DefaultSqlSession(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * 根据statementId和参数执行查询，返回结果
     * @param statementId 语句的id
     * @param parameter 参数
     * @param <T> 返回结果的类型
     * @return 查询结果
     */
    @Override
    public <T> T selectOne(String statementId, Object parameter) {
        MappedStatement mappedStatement = configuration.getMappedStatement(statementId);
        Executor executor = new SimpleExecutor(configuration);
        return executor.query(mappedStatement, parameter);
    }

    /**
     * 根据mapper接口返回mapper代理对象
     * @param mapperClass mapper接口类
     * @param <T> mapper接口类型
     * @return mapper代理对象
     */
    @Override
    public <T> T getMapper(Class<T> mapperClass) {

        MapperProxy<T> mapperProxy = new MapperProxy<>(this, mapperClass);
        Object proxy = Proxy.newProxyInstance(mapperClass.getClassLoader(), new Class[]{mapperClass}, mapperProxy);
        return mapperClass.cast(proxy);
    }
}
