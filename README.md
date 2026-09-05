# mini-MyBatis

这是一个用于理解 MyBatis 核心原理的教学项目，不依赖 MyBatis 本体。

## 当前已完成：Mapper namespace 二级缓存闭环

```text
mini-mybatis-config.xml + UserXmlMapper.xml
        ↓ XmlConfigBuilder / XmlMapperBuilder
Configuration + MappedStatement + ResultMap + Namespace Cache
        ↓ SqlSessionFactory
SqlSession → MapperProxy → CachingExecutor → SimpleExecutor → JdbcTransaction
        ↓                     ↓              ↓      ↓          ↓
   方法与参数解析       二级事务缓存      一级缓存  JDBC   Session Connection
```

当前支持：

- 从 XML 创建非池化 `DataSource`
- `<mapper class="...">` 注解 Mapper 与 `<mapper resource="...">` XML Mapper
- XML `<mapper namespace>` 和 `<select>/<insert>/<update>/<delete>`
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
- 基础类型、JavaBean、下划线转驼峰结果映射
- JDBC 资源自动关闭与统一持久化异常
- 本地 MySQL 端到端测试

测试不会 Mock JDBC：每个集成测试都会在真实 MySQL 中重建并准备 `user` 表，通过
mini-MyBatis 执行业务路径，再使用原生 JDBC 校验提交后的最终数据。测试还覆盖 SQL
参数绑定、防注入、执行失败后的整体回滚，以及一、二级缓存行为。

运行测试：

```bash
mvn test
```

运行 `Main` 前，请先创建配置中的 `test` 数据库、执行
[`src/main/resources/schema.sql`](src/main/resources/schema.sql)，并按本机环境修改
`mini-mybatis-config.xml` 的 MySQL 账号与密码。

测试配置当前连接 `test` 数据库，并会重建其中的 `user` 表；请只对专用测试库运行。
JUnit 资源锁保证相关测试即使启用并行执行，也不会同时修改该表。

当前二级缓存是便于理解原理的进程内实现，直接保存对象引用，尚未加入序列化、淘汰策略、
容量限制和分布式一致性；不要把它直接用于生产环境。

## 后续里程碑

1. Executor / StatementHandler / ParameterHandler / ResultSetHandler 插件链
2. 动态 SQL、类型处理器、连接池和分页

生产级能力不是本项目目标；每个里程碑会先以小型、可测试的实现解释原理。
