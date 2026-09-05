package cn.jens.mybatis.binding;

import cn.jens.mybatis.session.SqlSession;

import java.lang.reflect.Proxy;

/**
 * 创建 Mapper 接口的 JDK 动态代理。
 *
 * @param <T> Mapper 接口类型
 * @author YumJens
 */
public class MapperProxyFactory<T> {

    private final Class<T> mapperInterface;

    public MapperProxyFactory(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public T newInstance(SqlSession sqlSession) {
        MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface);
        Object proxy = Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class<?>[]{mapperInterface},
                mapperProxy
        );
        return mapperInterface.cast(proxy);
    }
}
