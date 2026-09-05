package cn.jens.mybatis.config;

import cn.jens.mybatis.annotation.Delete;
import cn.jens.mybatis.annotation.Insert;
import cn.jens.mybatis.annotation.Select;
import cn.jens.mybatis.annotation.Update;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.mapping.ResultMap;
import cn.jens.mybatis.mapping.SqlCommandType;
import cn.jens.mybatis.session.LocalCacheScope;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mini-MyBatis 的运行时配置中心。
 *
 * @author YumJens
 */
public class Configuration {

    private DataSource dataSource;

    private LocalCacheScope localCacheScope = LocalCacheScope.SESSION;

    private final Map<String, MappedStatement> mappedStatements = new HashMap<>();

    private final Map<String, ResultMap> resultMaps = new HashMap<>();

    public DataSource getDataSource() {
        if (dataSource == null) {
            throw new PersistenceException("DataSource has not been configured");
        }
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public LocalCacheScope getLocalCacheScope() {
        return localCacheScope;
    }

    public void setLocalCacheScope(LocalCacheScope localCacheScope) {
        this.localCacheScope = localCacheScope;
    }

    public void addMappedStatement(String statementId, MappedStatement mappedStatement) {
        MappedStatement previous = mappedStatements.putIfAbsent(statementId, mappedStatement);
        if (previous != null) {
            throw new PersistenceException("Duplicate mapped statement: " + statementId);
        }
    }

    public MappedStatement getMappedStatement(String statementId) {
        MappedStatement mappedStatement = mappedStatements.get(statementId);
        if (mappedStatement == null) {
            throw new PersistenceException("Mapped statement not found: " + statementId);
        }
        return mappedStatement;
    }

    public void addResultMap(String resultMapId, ResultMap resultMap) {
        ResultMap previous = resultMaps.putIfAbsent(resultMapId, resultMap);
        if (previous != null) {
            throw new PersistenceException("Duplicate result map: " + resultMapId);
        }
    }

    public ResultMap getResultMap(String resultMapId) {
        ResultMap resultMap = resultMaps.get(resultMapId);
        if (resultMap == null) {
            throw new PersistenceException("Result map not found: " + resultMapId);
        }
        return resultMap;
    }

    /**
     * 解析 Mapper 方法上的 SQL 注解并注册为 MappedStatement。
     *
     * @param mapperType Mapper 接口
     */
    public void addMapper(Class<?> mapperType) {
        if (!mapperType.isInterface()) {
            throw new PersistenceException("Mapper type must be an interface: " + mapperType.getName());
        }

        for (Method method : mapperType.getDeclaredMethods()) {
            SqlDefinition definition = resolveSqlDefinition(method);
            if (definition == null) {
                continue;
            }

            String statementId = mapperType.getName() + "." + method.getName();
            addMappedStatement(
                    statementId,
                    new MappedStatement(
                            statementId,
                            definition.sql(),
                            resolveResultType(method, definition.commandType()),
                            definition.commandType()
                    )
            );
        }
    }

    private SqlDefinition resolveSqlDefinition(Method method) {
        List<SqlDefinition> definitions = new ArrayList<>();
        Select select = method.getAnnotation(Select.class);
        Insert insert = method.getAnnotation(Insert.class);
        Update update = method.getAnnotation(Update.class);
        Delete delete = method.getAnnotation(Delete.class);
        if (select != null) {
            definitions.add(new SqlDefinition(select.value(), SqlCommandType.SELECT));
        }
        if (insert != null) {
            definitions.add(new SqlDefinition(insert.value(), SqlCommandType.INSERT));
        }
        if (update != null) {
            definitions.add(new SqlDefinition(update.value(), SqlCommandType.UPDATE));
        }
        if (delete != null) {
            definitions.add(new SqlDefinition(delete.value(), SqlCommandType.DELETE));
        }
        if (definitions.size() > 1) {
            throw new PersistenceException("Mapper method has multiple SQL annotations: " + method);
        }
        return definitions.isEmpty() ? null : definitions.getFirst();
    }

    private Class<?> resolveResultType(Method method, SqlCommandType commandType) {
        if (commandType != SqlCommandType.SELECT) {
            return void.class;
        }
        if (!List.class.isAssignableFrom(method.getReturnType())) {
            return method.getReturnType();
        }

        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterizedType) {
            Type elementType = parameterizedType.getActualTypeArguments()[0];
            if (elementType instanceof Class<?> elementClass) {
                return elementClass;
            }
        }
        throw new PersistenceException(
                "Mapper collection return type must declare an element type: " + method
        );
    }

    private record SqlDefinition(String sql, SqlCommandType commandType) {
    }
}
