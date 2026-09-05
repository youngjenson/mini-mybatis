package cn.jens.binding;

import cn.jens.session.SqlSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Mapper代理类
 *
 * @author YumJens
 * @date 2026-09-04 23:46
 */
public class MapperProxy<T> implements InvocationHandler {

    private final SqlSession sqlSession;

    private final Class<T> mapperInterface;

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // Object 方法特殊处理
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }

        String statementId =
                mapperInterface.getName()
                        + "."
                        + method.getName();

        Object parameter =
                args == null || args.length == 0
                        ? null
                        : args[0];

        return sqlSession.selectOne(
                statementId,
                parameter
        );

    }
}
