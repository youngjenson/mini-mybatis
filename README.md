# mini-MyBatis

这是一个用于理解 MyBatis 核心原理的教学项目，不依赖 MyBatis 本体。

## 当前已完成：连接池与连接复用

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

JdbcTransaction → PooledConnection（逻辑连接）→ PooledDataSource
                                                    ↓
                                      idle / active 物理连接
```

当前支持：

- 从 XML 创建 `UNPOOLED` 或 `POOLED` 类型的 `DataSource`
- `PooledDataSource` 管理 active/idle 物理连接并提供只读状态快照
- 每次借用生成独立的逻辑连接代理，`close()` 将物理连接归还池中
- 池满时等待空闲连接，到达 `poolTimeToWait` 后抛出 `SQLTimeoutException`
- 超过 `poolMaximumCheckoutTime` 的连接可被回收，原逻辑连接随即永久失效
- 归还前自动回滚未提交事务、恢复 `autoCommit` 并清理 JDBC warning
- 可配置 ping SQL；失效连接会被丢弃并按容错次数重新获取
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
- 一个 SqlSession 内复用同一个逻辑连接，多个 SqlSession 可复用同一个物理连接
- SqlSession 级一级缓存，缓存键包含语句 ID、JDBC SQL 与参数值
- `<setting name="localCacheScope" value="SESSION|STATEMENT">`
- DML、`commit()`、`rollback()`、`close()` 自动清空一级缓存
- `clearCache()` 手动清理，以及 XML `flushCache="true"`
- XML Mapper 使用 `<cache/>` 启用 namespace 级二级缓存
- 二级缓存支持 LRU / FIFO 淘汰策略与容量限制，默认 LRU、1024 条查询结果
- CacheBuilder 组装存储、淘汰、同步装饰器，可选 BlockingCache 协调同 key 的并发未命中
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

测试不会 Mock JDBC：业务集成测试会在真实 MySQL 中重建并准备 `user` 表，通过
mini-MyBatis 执行业务路径，再使用原生 JDBC 校验提交后的最终数据。测试还覆盖 SQL
参数绑定、防注入、执行失败后的整体回滚，以及一、二级缓存行为。连接池集成测试还会通过
MySQL `CONNECTION_ID()` 验证不同 SqlSession 确实复用了同一个物理连接。

二级缓存淘汰配置（放在 Mapper XML 的 `<mapper>` 内）：

```xml
<cache eviction="LRU" size="1024"/>
```

`eviction` 支持 `LRU`（最久未访问）和 `FIFO`（最早写入），`size` 必须为正整数。
省略属性时分别默认 `LRU` 和 `1024`，因此 `<cache/>` 也会限制容量。容量按缓存键对应的
查询结果条目计算，一条结果可以包含多行数据，并不是行数或字节数上限。

LRU 的读取命中和覆盖写入都会更新顺序；FIFO 的读取和覆盖写入保留原插入顺序。
新结果在事务提交后进入共享缓存，超出容量时触发淘汰；回滚会丢弃暂存写入，不触发淘汰。
读取共享缓存命中时会立即更新 LRU 顺序，即使随后回滚也不会恢复该顺序。
这些配置只作用于 namespace 二级缓存，一级缓存仍由 SqlSession 生命周期与
`localCacheScope` 控制。

缓存结构参考 MyBatis 官方的
[CacheBuilder](https://github.com/mybatis/mybatis-3/blob/master/src/main/java/org/apache/ibatis/mapping/CacheBuilder.java)
和缓存装饰器，调用顺序如下：

```text
TransactionalCache（每个 SqlSession 独立）
    → BlockingCache（可选，按 key 等待加载结果）
    → SynchronizedCache（ReentrantLock 统一保护读写）
    → LruCache / FifoCache（维护淘汰顺序）
    → PerpetualCache（保存查询结果）
```

`BoundedCache` 保留为兼容构造入口，内部也通过 `CacheBuilder` 组装。
同步锁仍使同一 namespace 的缓存读写互斥；BlockingCache 用于减少并发未命中时的重复
查库，不会让 LRU 命中读取变成无锁操作。

启用阻塞缓存：

```xml
<cache eviction="LRU" size="1024" blocking="true">
    <property name="timeout" value="3000"/>
</cache>
```

`blocking` 默认 `false`。`timeout` 是获取某个 key 加载锁的总等待上限，单位毫秒，
仅在 `blocking="true"` 时支持；必须为非负整数，默认 `0` 表示无限等待。
这不是 JDBC 查询超时。等待超时或线程中断会抛出 `PersistenceException`，中断标记会保留。

首次未命中会持有该 key 的加载锁，提交回填结果后唤醒其他会话。回滚、未提交关闭、
自动提交 SQL 失败以及 JDBC 提交失败都会释放对应锁。手动事务中的 SQL 失败后，调用方仍需
回滚或关闭会话；事务持续时间也会影响其他请求的等待时间。多 key 交叉等待应配置有限超时，
并及时结束事务。

事务层记录自己持有的未命中 key，同一事务再次查询时交给一级缓存或 JDBC，避免等待自己；
`flushCache` 清空暂存结果后仍保留解锁记录。未产生回填结果的 key 通过 `remove` 释放锁，
不向底层写入 null；空查询结果仍用空列表正常缓存。普通缓存的 `remove` 删除数据，
BlockingCache 的 `remove` 仅释放加载锁。直接使用 BlockingCache 时，调用方也必须遵守
每次未命中最终由 `put` 或 `remove` 结束的协议。

本项目只实现上述装饰器子集，保留 FIFO 覆盖写入不重复排队的行为；尚未实现官方的
序列化、定时清空、命中率日志装饰器。阻塞缓存集成测试使用真实 MySQL 常量 SELECT，
验证等待期间不重复查库及事务结束后的唤醒，无需修改数据表。

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

连接池配置示例：

```xml
<dataSource type="POOLED">
    <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
    <property name="url" value="jdbc:mysql://127.0.0.1:3306/test"/>
    <property name="username" value="root"/>
    <property name="password" value="root"/>
    <property name="poolMaximumActiveConnections" value="10"/>
    <property name="poolMaximumIdleConnections" value="5"/>
    <property name="poolMaximumCheckoutTime" value="20000"/>
    <property name="poolTimeToWait" value="20000"/>
    <property name="poolMaximumLocalBadConnectionTolerance" value="3"/>
    <property name="poolPingEnabled" value="true"/>
    <property name="poolPingQuery" value="SELECT 1"/>
    <property name="poolPingConnectionsNotUsedFor" value="30000"/>
</dataSource>
```

未填写 `type` 时保持兼容，默认使用 `UNPOOLED`。时间配置的单位都是毫秒；本项目将
`poolTimeToWait` 定义为一次获取连接的总等待上限。`PooledDataSource.close()` 会关闭全部
物理连接并使尚未归还的逻辑连接失效，应用关闭或测试结束时应调用它。

运行测试：

```bash
mvn test
```

运行 `MiniMybatisMain` 前，请先创建配置中的 `test` 数据库、执行
[`src/main/resources/schema.sql`](src/main/resources/schema.sql)，并按本机环境修改
`mini-mybatis-config.xml` 的 MySQL 账号与密码。

测试配置当前连接 `test` 数据库，并会重建其中的 `user` 表；请只对专用测试库运行。
JUnit 资源锁保证相关测试即使启用并行执行，也不会同时修改该表。

当前二级缓存是便于理解原理的进程内实现，直接保存对象引用，尚未加入序列化、过期时间和
分布式一致性；容量限制不约束事务提交前的暂存结果。不要把它直接用于生产环境。

当前连接池同样以解释原理为目标：它使用单锁管理连接，没有生产连接池常见的异步建连、
泄漏检测、指标导出和完整连接状态恢复能力。生产项目应使用成熟连接池。

## 后续里程碑

1. 分页插件与数据库方言

生产级能力不是本项目目标；每个里程碑会先以小型、可测试的实现解释原理。
