# JPA 到 MyBatis Plus 迁移设计

日期:2026-06-30

## 背景

当前项目是 Spring Boot 3.5.10 / Java 17 / Maven 应用,使用 MySQL 作为主数据库,Flyway 负责 schema 迁移。现有持久层依赖 `spring-boot-starter-data-jpa`,实体位于 `com.agentpluginhub.domain`,仓储接口是 7 个 `JpaRepository`:

- `PluginRepository`
- `PluginVersionRepository`
- `DistTagRepository`
- `SubmissionRepository`
- `AppUserRepository`
- `UserRoleRepository`
- `RegistryTokenRepository`

schema 已由 Flyway 脚本托管,JPA 配置中 `ddl-auto=validate`,因此本次迁移不应引入 MyBatis Plus 自动建表或变更表结构。

## 已确认决策

1. 一次性完整替换 JPA 为 MyBatis Plus。
2. 不保留 Repository 适配层。
3. 服务层直接注入 `PluginMapper` / `SubmissionMapper` 等 MyBatis Plus Mapper。
4. 保留 Flyway 作为唯一 schema 管理机制。
5. 保留现有业务行为、事务边界、异常语义和测试覆盖目标。

## 当前 JPA 使用面

当前 JPA 使用范围较小:

- 7 个实体,均为扁平表模型。
- 没有 JPA 关系映射、级联、懒加载、JPQL、Specification、分页查询。
- 查询主要来自 Spring Data 派生方法。
- `Submission.lockVersion` 使用 JPA `@Version` 实现乐观锁。
- `ReviewService.approve` 使用 `saveAndFlush` 在复制对象存储前抢占 `plugin_version` 唯一约束。

迁移风险集中在行为语义,不是 SQL 复杂度。

## 依赖与配置设计

### Maven 依赖

移除:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

新增:

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.16</version>
</dependency>
```

保留:

- `mysql-connector-j`
- `flyway-core`
- `flyway-mysql`
- Testcontainers 相关依赖

不要同时引入原生 MyBatis starter,避免 starter 版本和自动配置冲突。

### Spring 配置

删除 `spring.jpa.*`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

新增 MyBatis Plus 配置:

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
```

配置类中注册:

- `@MapperScan("com.agentpluginhub.mapper")`
- `MybatisPlusInterceptor`
- `OptimisticLockerInnerInterceptor`

## 实体迁移设计

实体类仍保留在 `com.agentpluginhub.domain`,但移除 `jakarta.persistence.*` 注解,改用 MyBatis Plus 注解。

示例:

```java
@TableName("plugin")
public class Plugin {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("package_name")
    private String packageName;
}
```

迁移映射规则:

| JPA 注解 | MyBatis Plus 替代 |
| --- | --- |
| `@Entity` | 删除 |
| `@Table(name = "...")` | `@TableName("...")` |
| `@Id` + `@GeneratedValue(strategy = IDENTITY)` | `@TableId(type = IdType.AUTO)` |
| `@Column(name = "...")` | `@TableField("...")` |
| `@Version` | `com.baomidou.mybatisplus.annotation.Version` |
| `@Enumerated(EnumType.STRING)` | 通过枚举字符串转换或显式条件值处理 |

`Submission.lockVersion` 需要补充 getter/setter,以便 MyBatis Plus 乐观锁插件和测试更容易验证。

`SubmissionState` 保留为枚举字段,数据库值继续使用枚举名字符串,与当前 `@Enumerated(EnumType.STRING)` 行为一致。实现时优先通过 MyBatis Plus 枚举转换配置保证读写为 `SUBMITTED` / `UNDER_REVIEW` / `APPROVED` / `REJECTED`;所有查询条件也必须按同一字符串语义验证。

## Mapper 设计

新增包 `com.agentpluginhub.mapper`,每张表一个 Mapper:

```java
public interface PluginMapper extends BaseMapper<Plugin> {
}
```

完整列表:

- `PluginMapper`
- `PluginVersionMapper`
- `DistTagMapper`
- `SubmissionMapper`
- `AppUserMapper`
- `UserRoleMapper`
- `RegistryTokenMapper`

普通查询使用 `LambdaQueryWrapper` / `Wrappers.lambdaQuery()`。暂不新增 XML Mapper,除非后续出现复杂 SQL 或性能问题。

## 服务层迁移设计

### PublishingService

将依赖从:

- `SubmissionRepository`
- `PluginRepository`
- `PluginVersionRepository`

替换为:

- `SubmissionMapper`
- `PluginMapper`
- `PluginVersionMapper`

行为保持:

1. 校验 tarball。
2. 查询已发布版本,若已存在则抛 `DuplicatePublishException`。
3. 保存 pending blob。
4. 插入 `submission`。
5. 返回自增 ID。

插入后需要确认实体 ID 已回填;MyBatis Plus `insert` 应写回 `@TableId(type = IdType.AUTO)` 字段。

### ReviewService

这是迁移重点。

`require(id)`:

- 从 `submissions.findById(id)` 改为 `submissionMapper.selectById(id)`。
- 查询为空时抛 `SubmissionNotFoundException`。

`approve(id, reviewer, notes)`:

1. 查询 `Submission` 并校验状态。
2. 查询或插入 `Plugin`。
3. 再次查询已发布版本,保持并发下的不可变发布检查。
4. 先 `pluginVersionMapper.insert(pv)` 抢占 `uk_version_plugin_ver` 唯一约束。
5. 插入成功后才从 pending blob 复制到 canonical key。
6. 更新或插入 `latest` dist-tag。
7. 首发时插入 `stable` dist-tag。
8. 更新 `Submission` 为 `APPROVED`。

关键约束:

- 第 4 步必须早于对象存储写入,保持原 `saveAndFlush` 的 claim-before-copy 语义。
- 唯一约束冲突不能污染已发布对象。
- `Submission` 更新必须经过乐观锁插件。
- `updateById` 返回 0 时应视为乐观锁冲突,转换为 `OptimisticLockingFailureException`,保持现有测试和并发冲突语义。

`reject(id, reviewer, notes)`:

1. 查询 `Submission` 并校验状态。
2. 更新为 `REJECTED`。
3. 注册事务提交后的 pending blob 清理。
4. 清理前仍保留 `countByTarballRefAndStateIn` 检查,避免误删遗留共享 pending key。

事务后清理仍使用 `TransactionSynchronizationManager.registerSynchronization`。

### DistTagService

服务层直接使用:

- `PluginMapper`
- `PluginVersionMapper`
- `DistTagMapper`

保留当前先查再插入/更新的 upsert 逻辑,不引入 MySQL 方言 `ON DUPLICATE KEY UPDATE`,避免扩大迁移行为差异。

### PluginCatalog

保持当前三次查询形状:

1. 查询全部 plugin。
2. 批量查询 published versions。
3. 批量查询 dist-tags。

不要退化为 1+2N 查询。

### VersionQueryService

保持读语义:

- 无 plugin 抛 `PackageNotFoundException`。
- 有 plugin 但无 `PUBLISHED` 版本也视为不存在。
- `publishedAt` 降序排序可继续在 Java 内存中完成。

### LocalUserService

保持用户 upsert 和默认角色逻辑:

- subject 不存在则插入 `app_user`。
- email 变化则更新。
- 新用户默认 `AUTHOR`。
- bootstrap subject 命中时补 `ADMIN`。

### RegistryTokenService

保持现有 token 语义:

- 签发时只返回一次明文。
- 数据库存 sha256 hash。
- 校验只查未吊销 token。
- 吊销用 `updateById`。

## 查询替换清单

| 原 Repository 方法 | MyBatis Plus 写法 |
| --- | --- |
| `findAll()` | `selectList(null)` |
| `findById(id)` | `selectById(id)` |
| `save(entity)` 新增 | `insert(entity)` |
| `save(entity)` 更新 | `updateById(entity)` |
| `saveAndFlush(entity)` | `insert/updateById` 后依赖当前事务内立即执行 SQL |
| `findByPackageName(packageName)` | `eq(Plugin::getPackageName, packageName)` |
| `findByPluginId(pluginId)` | `eq(DistTag::getPluginId, pluginId)` |
| `findByPluginIdAndTag(pluginId, tag)` | `eq(DistTag::getPluginId, pluginId).eq(DistTag::getTag, tag)` |
| `findByPluginIdIn(ids)` | `in(DistTag::getPluginId, ids)` |
| `findByPluginIdAndStatus(pluginId, status)` | `eq(PluginVersion::getPluginId, pluginId).eq(PluginVersion::getStatus, status)` |
| `existsByPluginIdAndVersionAndStatus(...)` | `selectCount(...) > 0` 或 `exists(...)` |
| `findByState(state)` | `eq(Submission::getState, state)` |
| `countByTarballRefAndStateIn(...)` | `eq(Submission::getTarballRef, key).in(Submission::getState, states)` |
| `findBySubject(subject)` | `eq(AppUser::getSubject, subject)` |
| `existsByUserIdAndRole(userId, role)` | `selectCount(...) > 0` |
| `findByTokenHashAndRevokedFalse(hash)` | `eq(RegistryToken::getTokenHash, hash).eq(RegistryToken::isRevoked, false)` |

## 异常与事务设计

事务仍使用 Spring `@Transactional`,不迁移到手写事务。

需要重点保持的异常语义:

- 重复发布版本仍返回冲突。
- 重复唯一约束不能导致对象存储污染。
- 乐观锁失败仍表现为并发更新冲突。
- 事务提交后 pending blob 清理失败只记录告警,不得影响已提交的审批结果。

实现时应对唯一约束插入路径增加测试。若 MyBatis Plus 抛出的异常未被 Spring 翻译为现有断言类型,优先在服务层做窄范围转换,不要改变全局异常处理。

## 测试设计

### 第一批:编译与上下文

命令:

```bash
mvn -q -DskipTests compile
```

目标:

- JPA 依赖已移除。
- MyBatis Plus Mapper 扫描正常。
- 应用上下文可加载。

### 第二批:schema 与基础持久化

重点测试:

- `SchemaMigrationTest`
- `DomainRepositoryTest` 迁移后的等价测试

目标:

- Flyway 仍创建 7 张核心表。
- 自增 ID 回填正常。
- 唯一约束仍触发冲突。
- `Submission.lockVersion` 乐观锁仍生效。
- `dist_tag` 审计列正常持久化。

### 第三批:发布和审批关键链路

重点测试:

- `PublishingServiceIT`
- `ReviewServiceIT`
- `PublishApproveEndToEndIT`

目标:

- pending blob 保存、审批复制、提交后清理行为一致。
- 同版本二次审批不能覆盖已发布对象。
- legacy shared pending key 兜底逻辑仍有效。
- 首发同时设置 `latest` 和 `stable`。
- 后续审批只推进 `latest`,不推进 `stable`。

### 第四批:读路径和安全路径

重点测试:

- catalog 相关测试
- versions 相关测试
- registry token 相关测试
- local user 相关测试
- Web MVC 授权相关测试

目标:

- marketplace/registry 输出保持兼容。
- token 签发、校验、吊销行为一致。
- 本地用户和角色 upsert 行为一致。

### 第五批:全量回归

命令:

```bash
mvn test
mvn verify
```

`mvn test` 跑单元测试和 surefire 测试;`mvn verify` 跑单元测试和 failsafe 集成测试。

## 回滚策略

本次迁移不改数据库表结构,回滚边界清晰:

1. 恢复 `spring-boot-starter-data-jpa` 依赖。
2. 删除 MyBatis Plus starter 和配置。
3. 恢复实体 JPA 注解。
4. 恢复 Repository 接口。
5. 恢复服务层 Repository 注入和调用。

由于 Flyway 脚本不变,数据库无需回滚。

## 实施顺序建议

1. 依赖和配置切换。
2. 新增 Mapper 和 MyBatis Plus 配置。
3. 实体注解替换。
4. 迁移 `PublishingService` 和 `ReviewService`。
5. 迁移读路径服务。
6. 迁移安全和用户相关服务。
7. 删除 Repository 接口。
8. 更新测试断言和测试类命名。
9. 执行分批验证和全量回归。

## 非目标

- 不改变数据库 schema。
- 不引入 XML Mapper 作为默认方案。
- 不重构业务流程。
- 不改变 API 响应结构。
- 不改变对象存储 key 规则。
- 不改变 Flyway 迁移策略。
- 不新增缓存、分页、批处理框架或其他持久层抽象。

## 风险清单

1. `SubmissionState` 枚举字符串映射不一致。
2. `Submission.lockVersion` 乐观锁未正确触发。
3. `saveAndFlush` 迁移后唯一约束抢占时机变化。
4. Mapper 插入后自增 ID 未按预期回填。
5. Spring 异常翻译类型与现有测试断言不完全一致。
6. `updateById` 返回 0 未被处理,导致乐观锁冲突被静默吞掉。
7. 批量 `in` 查询在空集合时需要短路,避免生成非法 SQL 或无意义查询。

这些风险都需要通过对应测试覆盖,不应靠人工约定保证。
