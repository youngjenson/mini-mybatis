package cn.jens.mybatis.scripting;

/** 根据调用参数生成可执行的 BoundSql。 */
public interface SqlSource {

    BoundSql getBoundSql(Object parameterObject);
}
