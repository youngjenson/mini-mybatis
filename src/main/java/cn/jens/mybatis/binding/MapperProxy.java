package cn.jens.mybatis.binding;

import cn.jens.mybatis.annotation.Param;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.session.SqlSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Mapper 方法调用转换为 SqlSession 调用。
 *
 * @param <T> Mapper 接口类型
 * @author YumJens
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
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        String statementId = mapperInterface.getName() + "." + method.getName();
        Object parameter = buildParameterObject(method, args);
        MappedStatement mappedStatement = sqlSession
                .getConfiguration()
                .getMappedStatement(statementId);

        return switch (mappedStatement.sqlCommandType()) {
            case SELECT -> executeSelect(method, statementId, parameter);
            case INSERT -> adaptRowCount(method, sqlSession.insert(statementId, parameter));
            case UPDATE -> adaptRowCount(method, sqlSession.update(statementId, parameter));
            case DELETE -> adaptRowCount(method, sqlSession.delete(statementId, parameter));
        };
    }

    private Object executeSelect(Method method, String statementId, Object parameter) {
        if (List.class.isAssignableFrom(method.getReturnType())) {
            return sqlSession.selectList(statementId, parameter);
        }
        return sqlSession.selectOne(statementId, parameter);
    }

    private Object adaptRowCount(Method method, int affectedRows) {
        Class<?> returnType = method.getReturnType();
        if (void.class.equals(returnType)) {
            return null;
        }
        if (int.class.equals(returnType) || Integer.class.equals(returnType)) {
            return affectedRows;
        }
        if (long.class.equals(returnType) || Long.class.equals(returnType)) {
            return (long) affectedRows;
        }
        if (boolean.class.equals(returnType) || Boolean.class.equals(returnType)) {
            return affectedRows > 0;
        }
        throw new PersistenceException(
                "Unsupported DML return type for " + method + ": " + returnType.getName()
        );
    }

    private Object buildParameterObject(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        if (args.length == 1 && method.getParameters()[0].getAnnotation(Param.class) == null) {
            return args[0];
        }

        Map<String, Object> namedParameters = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < args.length; index++) {
            Param param = parameters[index].getAnnotation(Param.class);
            String name = param == null ? "param" + (index + 1) : param.value();
            namedParameters.put(name, args[index]);
        }
        return namedParameters;
    }
}
