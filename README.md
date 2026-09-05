# mini-MyBatis

这是一个用于理解 MyBatis 核心原理的教学项目，不依赖 MyBatis 本体。

## 当前已完成：SqlSession 一级缓存闭环

```text
mini-mybatis-config.xml + UserXmlMapper.xml
        ↓ XmlConfigBuilder / XmlMapperBuilder
Configuration + MappedStatement + ResultMap
        ↓ SqlSessionFactory
SqlSession → MapperProxy → SimpleExecutor → JdbcTransaction
        ↓                    ↓       ↓          ↓
   方法与参数解析       LocalCache  JDBC    Session Connection
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
- 基础类型、JavaBean、下划线转驼峰结果映射
- JDBC 资源自动关闭与统一持久化异常
- 本地 MySQL 端到端测试

运行测试：

```bash
mvn test
```

运行 `Main` 前，请先创建配置中的 `test` 数据库、执行
[`src/main/resources/schema.sql`](src/main/resources/schema.sql)，并按本机环境修改
`mini-mybatis-config.xml` 的 MySQL 账号与密码。

测试配置当前连接 `test` 数据库，并会重建其中的 `user` 表；请只对专用测试库运行。

## 后续里程碑

1. Mapper namespace 二级缓存
2. Executor / StatementHandler / ParameterHandler / ResultSetHandler 插件链
3. 动态 SQL、类型处理器、连接池和分页

生产级能力不是本项目目标；每个里程碑会先以小型、可测试的实现解释原理。
