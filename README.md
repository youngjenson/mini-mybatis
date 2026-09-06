# mini-MyBatis

这是一个用于理解 MyBatis 核心原理的教学项目，不依赖 MyBatis 本体。

## 当前已完成：类型处理器与 JDBC 类型转换

```text
mini-mybatis-config.xml + UserXmlMapper.xml
        ↓ XmlConfigBuilder / XmlMapperBuilder
Configuration + MappedStatement + SqlSource + ResultMap + TypeHandlerRegistry
        ↓ SqlSessionFactory
SqlSession → MapperProxy → Plugin(Executor) → CachingExecutor → SimpleExecutor
                              ↓                    ↓
                    DynamicSqlSource → SqlNode Tree → BoundSql
                                                          ↓
                                              Plugin(StatementHandler)
                                                   ↙             ↘
                                    Plugin(ParameterHandler)  Plugin(ResultSetHandler)
                                                   ↘             ↙
                                             TypeHandler / JdbcType
                                                          ↓
                                            PreparedStatement / JDBC
```

当前支持：

- 从 XML 创建非池化 `DataSource`
- `<mapper class="...">` 注解 Mapper 与 `<mapper resource="...">` XML Mapper
- XML `<mapper namespace>` 和 `<select>/<insert>/<update>/<delete>`
- 动态 SQL 节点树：`SqlSource`、`DynamicSqlSource`、`SqlNode`、`DynamicContext`
- XML 动态标签：`<if>`、`<where>`、`<foreach>`，并支持嵌套组合
- `<if test="...">` 支持空值、布尔值、数字、字符串、比较运算、`and/or/not`
  以及括号组成的常用表达式子集
- `<where>` 仅在存在有效条件时生成 `WHERE`，并移除开头的 `AND` 或 `OR`
- `<foreach>` 支持 `Iterable`、数组和 `Map`，以及 `item`、`index`、`open`、
  `close`、`separator`、`nullable` 属性
- `<foreach>` 为每次迭代生成唯一参数名，集合值仍通过 `#{}` 和
  `PreparedStatement` 安全绑定
- `TypeHandler<T>` 与 `BaseTypeHandler<T>` 双向转换 Java 值和 JDBC 值
- `TypeHandlerRegistry` 按 Java 类型、`JdbcType` 和映射级覆盖选择处理器
- 内置字符串、数值、布尔、字符、枚举、`LocalDate`、`LocalDateTime` 处理器
- XML `<typeHandlers>` 注册自定义处理器，支持 `@MappedTypes`、
  `@MappedJdbcTypes` 和泛型类型推断
- `#{property, javaType=..., jdbcType=..., typeHandler=...}` 参数元数据
- `<id>/<result>` 的 `javaType`、`jdbcType`、`typeHandler` 映射属性
- `<setting name="jdbcTypeForNull" value="OTHER"/>` 空参数 JDBC 类型策略
- `EmailAddressTypeHandler` 自定义值对象转换示例
- `resultType` 与 `<resultMap>`、`<id>`、`<result>` 显式字段映射
- `@Select`、`@Insert`、`@Update`、`@Delete` 与 `@Param`
- `#{property}` 参数解析和 `PreparedStatement` 参数绑定
- Mapper JDK 动态代理
- `selectOne` / `selectList`
- 增删改受影响行数，以及 `int`、`long`、`boolean`、`void` 返回值适配
- 默认手动提交、`commit()`、`rollback()`、关闭会话自动回滚
- `openSession(true)` 自动提交
- 一个 SqlSession 内复用同一个 JDBC Connection
- SqlSession 级一级缓存，缓存键包含语句 ID、JDBC SQL 与参数值
- `<setting name="localCacheScope" value="SESSION|STATEMENT">`
- DML、`commit()`、`rollback()`、`close()` 自动清空一级缓存
- `clearCache()` 手动清理，以及 XML `flushCache="true"`
- XML Mapper 使用 `<cache/>` 启用 namespace 级二级缓存
- `<setting name="cacheEnabled" value="true|false"/>` 全局开关
- 查询默认 `useCache="true"`，可按语句关闭二级缓存
- 二级缓存写入和清理由事务协调：提交后生效，回滚或未提交关闭时丢弃
- 自动提交会话在每条 SQL 成功后同步提交二级缓存变更
- `StatementHandler` 负责创建、参数化和执行 JDBC Statement
- `ParameterHandler` 负责按占位符顺序绑定参数
- `ResultSetHandler` 负责基础类型、JavaBean 与 `resultMap` 映射
- `Interceptor`、`Invocation`、`Plugin`、`InterceptorChain` 插件体系
- `@Intercepts` 与 `@Signature` 精确声明被拦截的接口方法
- XML `<plugins>` 注册插件并通过 `<property>` 注入配置
- 内置 `SlowSqlInterceptor` 慢 SQL 教学示例
- 基础类型、JavaBean、下划线转驼峰结果映射
- JDBC 资源自动关闭与统一持久化异常
- 本地 MySQL 端到端测试

测试不会 Mock JDBC：每个集成测试都会在真实 MySQL 中重建并准备 `user` 表，通过
mini-MyBatis 执行业务路径，再使用原生 JDBC 校验提交后的最终数据。测试还覆盖 SQL
参数绑定、防注入、执行失败后的整体回滚，以及一、二级缓存行为。

动态 SQL 示例：

```xml
<select id="selectByIds" resultMap="userResultMap">
    select id, name, age, email from user
    <where>
        <if test="ids == null or ids.size == 0">1 = 0</if>
        <foreach collection="ids" item="id" nullable="true"
                 open="id in (" separator="," close=")">
            #{id}
        </foreach>
    </where>
    order by id
</select>
```

表达式求值器用于展示动态 SQL 原理，当前不是完整 OGNL 实现。它支持嵌套属性，以及集合的
`size`、`isEmpty`、数组/字符串的 `length`；尚不支持方法调用、算术运算和 OGNL 的全部语法。

类型处理器配置与映射示例：

```xml
<typeHandlers>
    <typeHandler handler="cn.jens.demo.typehandler.EmailAddressTypeHandler"/>
</typeHandlers>

<result property="email" column="user_email"
        javaType="cn.jens.demo.type.EmailAddress" jdbcType="VARCHAR"/>

<!-- null 值也能依靠显式 javaType 找到处理器，并通过 setNull(VARCHAR) 绑定 -->
#{email, javaType=cn.jens.demo.type.EmailAddress, jdbcType=VARCHAR}
```

当前 `<typeHandlers>` 支持逐个注册处理器；包扫描尚未实现。未显式声明 `javaType` 时，参数侧
从非空运行时值推断，结果侧从目标字段推断。映射级 `typeHandler` 的优先级高于全局注册表。

插件配置示例：

```xml
<plugins>
    <plugin interceptor="cn.jens.mybatis.plugin.SlowSqlInterceptor">
        <property name="thresholdMillis" value="200"/>
    </plugin>
</plugins>
```

插件按照配置顺序依次包装目标对象，因此最后配置的插件最先收到调用。当前使用 JDK
动态代理，只能拦截 `Executor`、`StatementHandler`、`ParameterHandler` 和
`ResultSetHandler` 接口中通过 `@Signature` 声明的方法。

运行测试：

```bash
mvn test
```

运行 `MiniMybatisMain` 前，请先创建配置中的 `test` 数据库、执行
[`src/main/resources/schema.sql`](src/main/resources/schema.sql)，并按本机环境修改
`mini-mybatis-config.xml` 的 MySQL 账号与密码。

测试配置当前连接 `test` 数据库，并会重建其中的 `user` 表；请只对专用测试库运行。
JUnit 资源锁保证相关测试即使启用并行执行，也不会同时修改该表。

当前二级缓存是便于理解原理的进程内实现，直接保存对象引用，尚未加入序列化、淘汰策略、
容量限制和分布式一致性；不要把它直接用于生产环境。

## 后续里程碑

1. 连接池与连接复用
2. 分页插件与数据库方言

生产级能力不是本项目目标；每个里程碑会先以小型、可测试的实现解释原理。
