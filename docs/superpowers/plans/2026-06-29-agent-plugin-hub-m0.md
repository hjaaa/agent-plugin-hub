# agent-plugin-hub 里程碑 0 实现计划(打通最小闭环)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一个最小 Spring Boot 后端,把手工塞入本地存储的一个 `npm pack` 插件 tarball,通过「自实现 npm 只读 registry + 动态 marketplace.json」让真实 Claude Code 用 `/plugin marketplace add` + `/plugin install` 装上,从而验证整套「source:npm + 自建 registry」协议假设。

**Architecture:** 单 Maven 模块、单进程 Spring Boot Web 服务。无前端、无审核、无登录、无鉴权、无数据库。插件元数据来自手工维护的 `artifacts/index.json`,产物 tarball 存本地文件系统。对外仅两类 HTTP 端点:`GET /marketplace.json`(⑦ 适配器)和 `GET /registry/**`(⑥ npm packument + tarball)。所有绝对 URL 由请求推导(`ServletUriComponentsBuilder`),不写死,以同时适配测试随机端口与真机主机。

**Tech Stack:** Java 17(构建需 JDK 21)、Maven、Spring Boot 3.5.10(spring-boot-starter-web)、Jackson(随 web 引入)、JUnit5 + AssertJ + Mockito(随 spring-boot-starter-test)、本机 npm(真实集成测试与真机冒烟用,本机 npm 11.6.2 已具备)。

## Global Constraints

> 每个任务的要求都隐含包含本节。值均逐字来自 spec 与现有团队规范。

- 构建工具 **Maven**;Spring Boot 父 POM 版本 **3.5.10**;`java.version=17`;**构建需 JDK 21**(运行最低 JDK 17)。本机默认 `java` 为 JDK 11,执行任何 `mvn` 前必须设置工具链(JDK 与 Maven 都在 `~/devsoft`):
  ```bash
  export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home
  alias mvn=/Users/richardhuang/devsoft/apache-maven-3.9.11/bin/mvn   # 或用绝对路径调用
  ```
  本计划中所有 `mvn ...` 命令均指 `~/devsoft/apache-maven-3.9.11/bin/mvn`,且已 `export JAVA_HOME` 到上面的 JDK 21。
- 测试框架 **JUnit5 + AssertJ + Mockito**;命名 `should_xxx_when_yyy` 或 `given_yyy_when_xxx_then_zzz`;外部依赖(文件系统/进程/HTTP)按需 mock 或用真实临时资源;覆盖正常路径 + 边界 + 异常路径。
- 提交信息用 **Conventional Commits**(`feat:`/`fix:`/`refactor:`/`test:`/`docs:`/`chore:`),描述用**简体中文**;代码标识符用**英文**;注释用**中文**;面向终端用户的文案用中文,日志用英文。
- **M0 范围铁律(spec §10)**:无前端、无审核、无登录、无鉴权(仅靠网络层),手工塞 tarball,本地文件存储;唯一成功标准 = 真实 CC 装上能用。版本范围解析、不可变发布、dist-tag 多渠道、DB —— **不在 M0**(留 M1/M2)。
- **命名 / 保留名(spec 附录)**:marketplace 的 `name` = `agent-plugin-hub`;必须避开保留名 `claude-code-marketplace`、`anthropic-*`、`claude-plugins-*` 及其仿冒变体。
- **npm registry 协议事实(spec §3.1 / §9)**:
  - packument 端点 `GET <registry>/<package>`;tarball 端点 `GET <registry>/<package>/-/<file>.tgz`。
  - 完整性 `dist.integrity = "sha512-" + Base64(sha512(tarball 字节))`;同时附 `dist.shasum = hex(sha1(tarball 字节))` 兼容老 npm。
  - **scoped 包名(`@org/foo`)在 npm 的 packument 请求路径里被编码为 `@org%2Ffoo`**,服务端必须放行编码斜杠(Tomcat `encodedSolidusHandling=decode`)。
  - `dist.tarball` 是不透明绝对 URL,由服务端生成、npm 原样 GET,可用字面斜杠(与官方 registry 一致)。
- **manifest 永远吐合法 JSON(spec §7)**:坏插件**跳过 + 记日志**,绝不让单个插件拖垮整份 `marketplace.json`。
- **manifest 鉴权(spec §8)**:M0 先实测路线 (i) 动态 HTTP 端点(无应用层鉴权,靠内网/本机);若 CC 对远程 URL marketplace 鉴权走不通,**兜底路线 (ii)**:把渲染好的 `marketplace.json` push 进内网私有 git 仓,用 CC 原生 git 凭据 add(见 Task 10)。

## 里程碑 0 完成定义(DoD)

1. `mvn test` 全绿(含真实 `npm install` 集成测试,本机有 npm 时实际运行)。
2. `./scripts/seed-artifacts.sh` 能把样例插件 `npm pack` 进 `./artifacts` 并生成 `index.json`。
3. 启动服务后,真实 Claude Code 里 `/plugin marketplace add http://<host>/marketplace.json` 成功、`/plugin install hello-plugin@agent-plugin-hub` 成功、插件命令可运行。
4. Task 10 的冒烟结果(go / no-go,及如走兜底则记录路线 (ii))写入 `docs/m0-smoke-runbook.md`。

## 执行顺序与依赖

T1 骨架 → T2 哈希 → T3 存储 → T4 目录 → T5 packument → T6 registry 端点 → T7 marketplace 端点 → T8 样例插件与种子 → T9 真实 npm install 集成测试 → T10 CC 真机冒烟(go/no-go gate)。
T1–T7 用 `src/test/resources/fixtures/artifacts` 下的**合成 fixture**(任意字节的假 tgz + index.json)即可测;T8 才产出**真实** npm 包,供 T9/T10 使用。

## 文件结构

```
agent-plugin-hub/
├── pom.xml                                                  # Spring Boot 3.5.10, Java 17, web + test
├── .gitignore                                              # target/ artifacts/ node_modules/ *.tgz
├── scripts/seed-artifacts.sh                               # npm pack 样例 → ./artifacts + index.json  (T8)
├── examples/hello-plugin/                                  # 样例 CC 插件源(npm + .claude-plugin)      (T8)
│   ├── package.json
│   ├── .claude-plugin/plugin.json
│   └── commands/hello.md
├── docs/m0-smoke-runbook.md                                # 真机冒烟手册 + go/no-go 记录              (T10)
└── src/
    ├── main/
    │   ├── java/com/agentpluginhub/
    │   │   ├── AgentPluginHubApplication.java              # @SpringBootApplication 入口             (T1)
    │   │   ├── web/HealthController.java                   # GET /healthz                            (T1)
    │   │   ├── config/AppProperties.java                   # app.artifacts-dir                       (T3)
    │   │   ├── config/TomcatConfig.java                    # 放行编码斜杠 %2F                         (T6)
    │   │   ├── common/IntegrityUtil.java                   # sha512 SRI + sha1 hex                    (T2)
    │   │   ├── common/ArtifactNotFoundException.java       # 404 产物                                 (T3)
    │   │   ├── common/PackageNotFoundException.java        # 404 包                                   (T4)
    │   │   ├── common/ApiExceptionHandler.java             # 异常 → 404 JSON                          (T6)
    │   │   ├── storage/ArtifactStore.java                  # 接口 load(filename):byte[]              (T3)
    │   │   ├── storage/LocalArtifactStore.java             # 本地文件实现                             (T3)
    │   │   ├── catalog/PluginCatalog.java                  # 读 index.json,all/find/require          (T4)
    │   │   ├── catalog/model/CatalogIndex.java             # record                                   (T4)
    │   │   ├── catalog/model/PluginEntry.java              # record                                   (T4)
    │   │   ├── catalog/model/VersionEntry.java             # record                                   (T4)
    │   │   ├── registry/PackumentService.java              # build(package, baseUrl):Packument        (T5)
    │   │   ├── registry/RegistryController.java            # GET /registry/{*path}                    (T6)
    │   │   ├── registry/model/Packument.java               # record                                   (T5)
    │   │   ├── registry/model/PackumentVersion.java        # record                                   (T5)
    │   │   ├── registry/model/Dist.java                    # record                                   (T5)
    │   │   ├── marketplace/MarketplaceService.java         # render(baseUrl):Marketplace              (T7)
    │   │   ├── marketplace/MarketplaceController.java       # GET /marketplace.json                    (T7)
    │   │   ├── marketplace/model/Marketplace.java          # record                                   (T7)
    │   │   ├── marketplace/model/PluginRef.java            # record                                   (T7)
    │   │   └── marketplace/model/NpmSource.java            # record                                   (T7)
    │   └── resources/application.yml                       # server.port + app.artifacts-dir          (T1)
    └── test/
        ├── java/com/agentpluginhub/...                     # 各任务单元/Web/集成测试
        └── resources/fixtures/artifacts/
            ├── index.json                                  # 合成目录 fixture                         (T4)
            └── demo-hello-plugin-1.0.0.tgz                 # 合成假 tarball fixture                   (T3)
```

---

## Task 1: 项目骨架与健康检查

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/com/agentpluginhub/AgentPluginHubApplication.java`
- Create: `src/main/java/com/agentpluginhub/web/HealthController.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/agentpluginhub/AgentPluginHubApplicationTests.java`
- Test: `src/test/java/com/agentpluginhub/web/HealthControllerTest.java`

**Interfaces:**
- Consumes: 无(首个任务)。
- Produces: 入口类 `com.agentpluginhub.AgentPluginHubApplication`(已标 `@ConfigurationPropertiesScan`,供 T3 的 `AppProperties` 被扫描);Web 端点 `GET /healthz` 返回 `{"status":"ok"}`。Maven 坐标 `com.agentpluginhub:agent-plugin-hub:0.0.1-SNAPSHOT`。

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.10</version>
        <relativePath/>
    </parent>

    <groupId>com.agentpluginhub</groupId>
    <artifactId>agent-plugin-hub</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>agent-plugin-hub</name>
    <description>面向 AI 编码 agent 的内部插件市场平台(里程碑 0)</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写 .gitignore**

```gitignore
target/
artifacts/
node_modules/
*.tgz
.idea/
*.iml
.DS_Store
.superpowers/
```

- [ ] **Step 3: 写 application.yml**

```yaml
server:
  port: 8080
app:
  # M0 本地文件存储目录,放 index.json 与 *.tgz;由 ./scripts/seed-artifacts.sh 填充
  artifacts-dir: ./artifacts
```

- [ ] **Step 4: 写入口类**

`src/main/java/com/agentpluginhub/AgentPluginHubApplication.java`:

```java
package com.agentpluginhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentPluginHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentPluginHubApplication.class, args);
    }
}
```

- [ ] **Step 5: 写健康检查 controller**

`src/main/java/com/agentpluginhub/web/HealthController.java`:

```java
package com.agentpluginhub.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // 探活端点:确认进程能起、能服务 HTTP
    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

- [ ] **Step 6: 写失败测试(上下文加载 + 健康检查)**

`src/test/java/com/agentpluginhub/AgentPluginHubApplicationTests.java`:

```java
package com.agentpluginhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentPluginHubApplicationTests {

    @Test
    void should_load_context_when_app_starts() {
        // 仅验证 Spring 上下文能加载;无断言即代表加载成功
    }
}
```

`src/test/java/com/agentpluginhub/web/HealthControllerTest.java`:

```java
package com.agentpluginhub.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void should_return_ok_when_get_healthz() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
```

- [ ] **Step 7: 运行测试,确认通过**

Run: `export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home && ~/devsoft/apache-maven-3.9.11/bin/mvn test`
Expected: `BUILD SUCCESS`,`Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 8: 提交**

```bash
git add pom.xml .gitignore src/main src/test
git commit -m "feat: 初始化 Spring Boot 骨架与健康检查端点"
```

---

## Task 2: 完整性哈希工具 IntegrityUtil

**Files:**
- Create: `src/main/java/com/agentpluginhub/common/IntegrityUtil.java`
- Test: `src/test/java/com/agentpluginhub/common/IntegrityUtilTest.java`

**Interfaces:**
- Consumes: 无。
- Produces: `IntegrityUtil.sriSha512(byte[]) : String`(返回 `"sha512-" + Base64(sha512)`,即 npm SRI 格式);`IntegrityUtil.hexSha1(byte[]) : String`(返回 40 位十六进制 sha1)。供 T5 `PackumentService` 计算 `dist.integrity` / `dist.shasum`。

- [ ] **Step 1: 写失败测试(已知向量)**

`src/test/java/com/agentpluginhub/common/IntegrityUtilTest.java`:

```java
package com.agentpluginhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IntegrityUtilTest {

    // 空输入的 sha512 SRI 是业界公认固定值,可作 golden 向量
    @Test
    void should_compute_sha512_sri_when_input_empty() {
        assertThat(IntegrityUtil.sriSha512(new byte[0]))
                .isEqualTo("sha512-z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg/SpIdNs6c5H0NE8XYXysP+DGNKHfuwvY7kxvUdBeoGlODJ6+SfaPg==");
    }

    // "abc" 的 sha1 是经典测试向量
    @Test
    void should_compute_sha1_hex_when_input_abc() {
        assertThat(IntegrityUtil.hexSha1("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -Dtest=IntegrityUtilTest test`
Expected: 编译失败 `cannot find symbol: class IntegrityUtil`(类未创建)。

- [ ] **Step 3: 写实现**

`src/main/java/com/agentpluginhub/common/IntegrityUtil.java`:

```java
package com.agentpluginhub.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// npm 完整性哈希工具:packument 的 dist.integrity / dist.shasum 由此生成
public final class IntegrityUtil {

    private IntegrityUtil() {
    }

    // npm SRI 格式:sha512-<base64(原始 sha512 摘要)>
    public static String sriSha512(byte[] data) {
        return "sha512-" + Base64.getEncoder().encodeToString(digest("SHA-512", data));
    }

    // 老 npm 仍读 shasum:40 位十六进制 sha1
    public static String hexSha1(byte[] data) {
        byte[] d = digest("SHA-1", data);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] digest(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 / SHA-512 是 JDK 标配,理论上不可能走到这里
            throw new IllegalStateException("missing digest algorithm: " + algorithm, e);
        }
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `mvn -Dtest=IntegrityUtilTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/agentpluginhub/common/IntegrityUtil.java src/test/java/com/agentpluginhub/common/IntegrityUtilTest.java
git commit -m "feat: 新增 npm 完整性哈希工具(sha512 SRI + sha1)"
```

---

## Task 3: 配置属性 + 本地制品存储 LocalArtifactStore

**Files:**
- Create: `src/main/java/com/agentpluginhub/config/AppProperties.java`
- Create: `src/main/java/com/agentpluginhub/common/ArtifactNotFoundException.java`
- Create: `src/main/java/com/agentpluginhub/storage/ArtifactStore.java`
- Create: `src/main/java/com/agentpluginhub/storage/LocalArtifactStore.java`
- Create: `src/test/resources/fixtures/artifacts/demo-hello-plugin-1.0.0.tgz`(合成假 tarball,任意字节)
- Test: `src/test/java/com/agentpluginhub/storage/LocalArtifactStoreTest.java`

**Interfaces:**
- Consumes: 无(纯基础设施)。
- Produces:
  - `AppProperties`(`@ConfigurationProperties(prefix="app")`):`getArtifactsDir() : String`(默认 `./artifacts`)、`setArtifactsDir(String)`。
  - `ArtifactNotFoundException extends RuntimeException`。
  - `ArtifactStore`(接口):`byte[] load(String filename)`,文件不存在/非法名 → 抛 `ArtifactNotFoundException`。
  - `LocalArtifactStore implements ArtifactStore`(`@Component`,构造注入 `AppProperties`):从 `artifactsDir` 直接子文件读字节,拒绝含 `/`、`\`、`..` 的文件名(防路径穿越)。

- [ ] **Step 1: 建合成 fixture 假 tarball**

Run:
```bash
mkdir -p src/test/resources/fixtures/artifacts
printf 'M0-fake-tarball-content\n' > src/test/resources/fixtures/artifacts/demo-hello-plugin-1.0.0.tgz
```
(T1–T7 的测试不经过 npm,此文件只需是「一段确定字节」;真实可被 npm 解析的包在 T8 产出。)

- [ ] **Step 2: 写失败测试**

`src/test/java/com/agentpluginhub/storage/LocalArtifactStoreTest.java`:

```java
package com.agentpluginhub.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.common.ArtifactNotFoundException;
import com.agentpluginhub.config.AppProperties;
import org.junit.jupiter.api.Test;

class LocalArtifactStoreTest {

    private LocalArtifactStore newStore() {
        AppProperties props = new AppProperties();
        props.setArtifactsDir("src/test/resources/fixtures/artifacts");
        return new LocalArtifactStore(props);
    }

    @Test
    void should_read_bytes_when_file_exists() {
        byte[] bytes = newStore().load("demo-hello-plugin-1.0.0.tgz");
        assertThat(bytes).isNotEmpty();
    }

    @Test
    void should_throw_when_file_missing() {
        assertThatThrownBy(() -> newStore().load("does-not-exist.tgz"))
                .isInstanceOf(ArtifactNotFoundException.class);
    }

    @Test
    void should_reject_when_filename_has_path_traversal() {
        assertThatThrownBy(() -> newStore().load("../application.yml"))
                .isInstanceOf(ArtifactNotFoundException.class);
    }
}
```

- [ ] **Step 3: 运行测试,确认失败**

Run: `mvn -Dtest=LocalArtifactStoreTest test`
Expected: 编译失败(`AppProperties` / `LocalArtifactStore` / `ArtifactNotFoundException` 未创建)。

- [ ] **Step 4: 写 AppProperties**

`src/main/java/com/agentpluginhub/config/AppProperties.java`:

```java
package com.agentpluginhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    // 本地制品目录;存放 index.json 与各版本 *.tgz
    private String artifactsDir = "./artifacts";

    public String getArtifactsDir() {
        return artifactsDir;
    }

    public void setArtifactsDir(String artifactsDir) {
        this.artifactsDir = artifactsDir;
    }
}
```

- [ ] **Step 5: 写 ArtifactNotFoundException**

`src/main/java/com/agentpluginhub/common/ArtifactNotFoundException.java`:

```java
package com.agentpluginhub.common;

// 产物(tarball)不存在或文件名非法
public class ArtifactNotFoundException extends RuntimeException {

    public ArtifactNotFoundException(String filename) {
        super("artifact not found: " + filename);
    }
}
```

- [ ] **Step 6: 写 ArtifactStore 接口**

`src/main/java/com/agentpluginhub/storage/ArtifactStore.java`:

```java
package com.agentpluginhub.storage;

// 制品存储抽象;M0 用本地文件实现,后续可换 MinIO/S3 而不动上层
public interface ArtifactStore {

    // 按文件名读取 tarball 字节;不存在或非法名抛 ArtifactNotFoundException
    byte[] load(String filename);
}
```

- [ ] **Step 7: 写 LocalArtifactStore**

`src/main/java/com/agentpluginhub/storage/LocalArtifactStore.java`:

```java
package com.agentpluginhub.storage;

import com.agentpluginhub.common.ArtifactNotFoundException;
import com.agentpluginhub.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class LocalArtifactStore implements ArtifactStore {

    private final AppProperties props;

    public LocalArtifactStore(AppProperties props) {
        this.props = props;
    }

    @Override
    public byte[] load(String filename) {
        // 防路径穿越:只允许 artifactsDir 下的直接子文件
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ArtifactNotFoundException(String.valueOf(filename));
        }
        Path file = Path.of(props.getArtifactsDir(), filename);
        if (!Files.isRegularFile(file)) {
            throw new ArtifactNotFoundException(filename);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ArtifactNotFoundException(filename);
        }
    }
}
```

- [ ] **Step 8: 运行测试,确认通过**

Run: `mvn -Dtest=LocalArtifactStoreTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/agentpluginhub/config/AppProperties.java \
        src/main/java/com/agentpluginhub/common/ArtifactNotFoundException.java \
        src/main/java/com/agentpluginhub/storage \
        src/test/java/com/agentpluginhub/storage/LocalArtifactStoreTest.java \
        src/test/resources/fixtures/artifacts/demo-hello-plugin-1.0.0.tgz
git commit -m "feat: 新增配置属性与本地制品存储(防路径穿越)"
```

---

## Task 4: 制品目录 PluginCatalog(读 index.json)

**Files:**
- Create: `src/main/java/com/agentpluginhub/catalog/model/VersionEntry.java`
- Create: `src/main/java/com/agentpluginhub/catalog/model/PluginEntry.java`
- Create: `src/main/java/com/agentpluginhub/catalog/model/CatalogIndex.java`
- Create: `src/main/java/com/agentpluginhub/common/PackageNotFoundException.java`
- Create: `src/main/java/com/agentpluginhub/catalog/PluginCatalog.java`
- Create: `src/test/resources/fixtures/artifacts/index.json`(合成目录 fixture)
- Test: `src/test/java/com/agentpluginhub/catalog/PluginCatalogTest.java`

**Interfaces:**
- Consumes: `AppProperties.getArtifactsDir()`(T3)。
- Produces:
  - `record VersionEntry(String version, String tarball)`,访问器 `version()` / `tarball()`。
  - `record PluginEntry(@JsonProperty("package") String packageName, String pluginName, String description, Map<String,String> distTags, List<VersionEntry> versions)`,访问器 `packageName()` / `pluginName()` / `description()` / `distTags()` / `versions()`。
  - `record CatalogIndex(List<PluginEntry> plugins)`。
  - `PackageNotFoundException extends RuntimeException`。
  - `PluginCatalog`(`@Component`,构造注入 `AppProperties`、`ObjectMapper`):`@PostConstruct void load()`(`index.json` 缺失时不报错、目录为空);`List<PluginEntry> all()`;`Optional<PluginEntry> find(String packageName)`;`PluginEntry require(String packageName)`(缺失抛 `PackageNotFoundException`)。

- [ ] **Step 1: 建合成 fixture index.json**

`src/test/resources/fixtures/artifacts/index.json`:

```json
{
  "plugins": [
    {
      "package": "@demo/hello-plugin",
      "pluginName": "hello-plugin",
      "description": "Demo plugin for agent-plugin-hub M0 smoke test",
      "distTags": { "latest": "1.0.0" },
      "versions": [
        { "version": "1.0.0", "tarball": "demo-hello-plugin-1.0.0.tgz" }
      ]
    }
  ]
}
```

- [ ] **Step 2: 写失败测试**

`src/test/java/com/agentpluginhub/catalog/PluginCatalogTest.java`:

```java
package com.agentpluginhub.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PluginCatalogTest {

    private PluginCatalog catalogOn(String dir) throws Exception {
        AppProperties props = new AppProperties();
        props.setArtifactsDir(dir);
        PluginCatalog catalog = new PluginCatalog(props, new ObjectMapper());
        catalog.load();
        return catalog;
    }

    @Test
    void should_load_plugins_when_index_present() throws Exception {
        PluginCatalog catalog = catalogOn("src/test/resources/fixtures/artifacts");
        assertThat(catalog.all()).hasSize(1);

        PluginEntry e = catalog.require("@demo/hello-plugin");
        assertThat(e.pluginName()).isEqualTo("hello-plugin");
        assertThat(e.distTags()).containsEntry("latest", "1.0.0");
        assertThat(e.versions()).extracting(VersionEntry::version).containsExactly("1.0.0");
        assertThat(e.versions()).extracting(VersionEntry::tarball)
                .containsExactly("demo-hello-plugin-1.0.0.tgz");
    }

    @Test
    void should_throw_when_package_unknown() throws Exception {
        PluginCatalog catalog = catalogOn("src/test/resources/fixtures/artifacts");
        assertThatThrownBy(() -> catalog.require("@x/none"))
                .isInstanceOf(PackageNotFoundException.class);
    }

    @Test
    void should_be_empty_when_index_missing() throws Exception {
        PluginCatalog catalog = catalogOn("src/test/resources/fixtures/no-such-dir");
        assertThat(catalog.all()).isEmpty();
    }
}
```

- [ ] **Step 3: 运行测试,确认失败**

Run: `mvn -Dtest=PluginCatalogTest test`
Expected: 编译失败(目录模型与 `PluginCatalog` 未创建)。

- [ ] **Step 4: 写目录模型(records)**

`src/main/java/com/agentpluginhub/catalog/model/VersionEntry.java`:

```java
package com.agentpluginhub.catalog.model;

// 单个已发布版本:版本号 + 对应 tarball 文件名(artifactsDir 下)
public record VersionEntry(String version, String tarball) {
}
```

`src/main/java/com/agentpluginhub/catalog/model/PluginEntry.java`:

```java
package com.agentpluginhub.catalog.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

// 一个插件的跨版本元信息;package 是 npm 包名(Java 关键字,故字段名用 packageName)
public record PluginEntry(
        @JsonProperty("package") String packageName,
        String pluginName,
        String description,
        Map<String, String> distTags,
        List<VersionEntry> versions) {
}
```

`src/main/java/com/agentpluginhub/catalog/model/CatalogIndex.java`:

```java
package com.agentpluginhub.catalog.model;

import java.util.List;

// index.json 根结构
public record CatalogIndex(List<PluginEntry> plugins) {
}
```

- [ ] **Step 5: 写 PackageNotFoundException**

`src/main/java/com/agentpluginhub/common/PackageNotFoundException.java`:

```java
package com.agentpluginhub.common;

// 目录中找不到该 npm 包
public class PackageNotFoundException extends RuntimeException {

    public PackageNotFoundException(String packageName) {
        super("package not found: " + packageName);
    }
}
```

- [ ] **Step 6: 写 PluginCatalog**

`src/main/java/com/agentpluginhub/catalog/PluginCatalog.java`:

```java
package com.agentpluginhub.catalog;

import com.agentpluginhub.catalog.model.CatalogIndex;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PluginCatalog {

    private static final Logger log = LoggerFactory.getLogger(PluginCatalog.class);

    private final Map<String, PluginEntry> byPackage = new LinkedHashMap<>();
    private final AppProperties props;
    private final ObjectMapper mapper;

    public PluginCatalog(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    // 启动时一次性加载 index.json;M0 手工维护,缺失则目录为空(允许空仓启动)
    @PostConstruct
    public void load() throws IOException {
        byPackage.clear();
        Path index = Path.of(props.getArtifactsDir(), "index.json");
        if (!Files.exists(index)) {
            log.warn("artifacts index.json not found at {}, catalog is empty", index.toAbsolutePath());
            return;
        }
        CatalogIndex idx = mapper.readValue(Files.readAllBytes(index), CatalogIndex.class);
        if (idx.plugins() != null) {
            for (PluginEntry e : idx.plugins()) {
                byPackage.put(e.packageName(), e);
            }
        }
        log.info("loaded {} plugin(s) from {}", byPackage.size(), index.toAbsolutePath());
    }

    public List<PluginEntry> all() {
        return new ArrayList<>(byPackage.values());
    }

    public Optional<PluginEntry> find(String packageName) {
        return Optional.ofNullable(byPackage.get(packageName));
    }

    public PluginEntry require(String packageName) {
        PluginEntry e = byPackage.get(packageName);
        if (e == null) {
            throw new PackageNotFoundException(packageName);
        }
        return e;
    }
}
```

- [ ] **Step 7: 运行测试,确认通过**

Run: `mvn -Dtest=PluginCatalogTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/agentpluginhub/catalog \
        src/main/java/com/agentpluginhub/common/PackageNotFoundException.java \
        src/test/java/com/agentpluginhub/catalog/PluginCatalogTest.java \
        src/test/resources/fixtures/artifacts/index.json
git commit -m "feat: 新增制品目录,从 index.json 加载插件元数据"
```

---

## Task 5: Packument 构建 PackumentService

**Files:**
- Create: `src/main/java/com/agentpluginhub/registry/model/Dist.java`
- Create: `src/main/java/com/agentpluginhub/registry/model/PackumentVersion.java`
- Create: `src/main/java/com/agentpluginhub/registry/model/Packument.java`
- Create: `src/main/java/com/agentpluginhub/registry/PackumentService.java`
- Test: `src/test/java/com/agentpluginhub/registry/PackumentServiceTest.java`

**Interfaces:**
- Consumes: `PluginCatalog.require(String)`(T4)、`ArtifactStore.load(String)`(T3)、`IntegrityUtil.sriSha512/hexSha1`(T2)、`PluginEntry`/`VersionEntry`(T4)、`PackageNotFoundException`(T4)。
- Produces:
  - `record Dist(String tarball, String integrity, String shasum)`。
  - `record PackumentVersion(String name, String version, Dist dist)`。
  - `record Packument(@JsonProperty("_id") String id, String name, @JsonProperty("dist-tags") Map<String,String> distTags, Map<String,PackumentVersion> versions)`。
  - `PackumentService`(`@Service`,构造注入 `PluginCatalog`、`ArtifactStore`):`Packument build(String packageName, String baseUrl)`。`baseUrl` 形如 `http://host:port`(无尾斜杠);tarball URL 拼为 `baseUrl + "/registry/" + packageName + "/-/" + tarballFilename`(用字面斜杠,与官方 registry 一致)。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/agentpluginhub/registry/PackumentServiceTest.java`:

```java
package com.agentpluginhub.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.registry.model.Packument;
import com.agentpluginhub.registry.model.PackumentVersion;
import com.agentpluginhub.storage.ArtifactStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackumentServiceTest {

    private static final byte[] TARBALL = "hello-tarball".getBytes(StandardCharsets.UTF_8);

    private PackumentService serviceWithOnePackage() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        PluginEntry entry = new PluginEntry(
                "@demo/hello-plugin", "hello-plugin", "desc",
                Map.of("latest", "1.0.0"),
                List.of(new VersionEntry("1.0.0", "demo-hello-plugin-1.0.0.tgz")));
        when(catalog.require("@demo/hello-plugin")).thenReturn(entry);
        when(store.load("demo-hello-plugin-1.0.0.tgz")).thenReturn(TARBALL);
        return new PackumentService(catalog, store);
    }

    @Test
    void should_build_packument_with_dist_when_package_exists() {
        Packument doc = serviceWithOnePackage().build("@demo/hello-plugin", "http://localhost:8080");

        assertThat(doc.name()).isEqualTo("@demo/hello-plugin");
        assertThat(doc.id()).isEqualTo("@demo/hello-plugin");
        assertThat(doc.distTags()).containsEntry("latest", "1.0.0");

        PackumentVersion v = doc.versions().get("1.0.0");
        assertThat(v.name()).isEqualTo("@demo/hello-plugin");
        assertThat(v.version()).isEqualTo("1.0.0");
        assertThat(v.dist().tarball())
                .isEqualTo("http://localhost:8080/registry/@demo/hello-plugin/-/demo-hello-plugin-1.0.0.tgz");
        // 完整性必须与同一份字节自洽(npm 下载后会校验)
        assertThat(v.dist().integrity()).isEqualTo(IntegrityUtil.sriSha512(TARBALL));
        assertThat(v.dist().shasum()).isEqualTo(IntegrityUtil.hexSha1(TARBALL));
    }

    @Test
    void should_throw_when_package_unknown() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        ArtifactStore store = mock(ArtifactStore.class);
        when(catalog.require("@x/none")).thenThrow(new PackageNotFoundException("@x/none"));
        PackumentService svc = new PackumentService(catalog, store);

        assertThatThrownBy(() -> svc.build("@x/none", "http://localhost:8080"))
                .isInstanceOf(PackageNotFoundException.class);
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -Dtest=PackumentServiceTest test`
Expected: 编译失败(`Packument` 等模型与 `PackumentService` 未创建)。

- [ ] **Step 3: 写 packument 模型(records)**

`src/main/java/com/agentpluginhub/registry/model/Dist.java`:

```java
package com.agentpluginhub.registry.model;

// npm packument 的 dist 块:下载地址 + 完整性校验
public record Dist(String tarball, String integrity, String shasum) {
}
```

`src/main/java/com/agentpluginhub/registry/model/PackumentVersion.java`:

```java
package com.agentpluginhub.registry.model;

// packument.versions 里的单版本对象;M0 只放 npm 解析必需字段
public record PackumentVersion(String name, String version, Dist dist) {
}
```

`src/main/java/com/agentpluginhub/registry/model/Packument.java`:

```java
package com.agentpluginhub.registry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

// npm 包文档(packument):GET <registry>/<package> 的响应体
public record Packument(
        @JsonProperty("_id") String id,
        String name,
        @JsonProperty("dist-tags") Map<String, String> distTags,
        Map<String, PackumentVersion> versions) {
}
```

- [ ] **Step 4: 写 PackumentService**

`src/main/java/com/agentpluginhub/registry/PackumentService.java`:

```java
package com.agentpluginhub.registry;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.common.IntegrityUtil;
import com.agentpluginhub.registry.model.Dist;
import com.agentpluginhub.registry.model.Packument;
import com.agentpluginhub.registry.model.PackumentVersion;
import com.agentpluginhub.storage.ArtifactStore;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PackumentService {

    private final PluginCatalog catalog;
    private final ArtifactStore store;

    public PackumentService(PluginCatalog catalog, ArtifactStore store) {
        this.catalog = catalog;
        this.store = store;
    }

    // 为某 npm 包构建 packument;baseUrl 形如 http://host:port(无尾斜杠)
    public Packument build(String packageName, String baseUrl) {
        PluginEntry entry = catalog.require(packageName); // 未知包 → PackageNotFoundException
        Map<String, PackumentVersion> versions = new LinkedHashMap<>();
        for (VersionEntry v : entry.versions()) {
            byte[] bytes = store.load(v.tarball());
            Dist dist = new Dist(
                    baseUrl + "/registry/" + packageName + "/-/" + v.tarball(),
                    IntegrityUtil.sriSha512(bytes),
                    IntegrityUtil.hexSha1(bytes));
            versions.put(v.version(), new PackumentVersion(packageName, v.version(), dist));
        }
        return new Packument(packageName, packageName, entry.distTags(), versions);
    }
}
```

- [ ] **Step 5: 运行测试,确认通过**

Run: `mvn -Dtest=PackumentServiceTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/agentpluginhub/registry/model \
        src/main/java/com/agentpluginhub/registry/PackumentService.java \
        src/test/java/com/agentpluginhub/registry/PackumentServiceTest.java
git commit -m "feat: 新增 packument 构建服务(npm 只读元数据)"
```

---

## Task 6: Registry 端点 RegistryController(packument + tarball + %2F 放行)

**Files:**
- Create: `src/main/java/com/agentpluginhub/config/TomcatConfig.java`
- Create: `src/main/java/com/agentpluginhub/common/ApiExceptionHandler.java`
- Create: `src/main/java/com/agentpluginhub/registry/RegistryController.java`
- Test: `src/test/java/com/agentpluginhub/registry/RegistryControllerTest.java`

**Interfaces:**
- Consumes: `PackumentService.build(String,String)`(T5)、`ArtifactStore.load(String)`(T3)、`PackageNotFoundException`(T4)、`ArtifactNotFoundException`(T3)。
- Produces:
  - `TomcatConfig`:`WebServerFactoryCustomizer<TomcatServletWebServerFactory>` Bean,设 `connector.setEncodedSolidusHandling("decode")` —— 让 `%2F` 被解码为 `/`,使 scoped 包名请求能进 controller。
  - `ApiExceptionHandler`(`@RestControllerAdvice`):`PackageNotFoundException` / `ArtifactNotFoundException` → HTTP 404,body `{"error":"Not found"}`。
  - `RegistryController`(`@RestController`):`GET /registry/{*path}`。`path` 含前导 `/`;以 `/-/` 且后缀 `.tgz` 区分 tarball 请求(取 `/-/` 之后为文件名 → `store.load`),否则按 packument 请求(剩余即包名 → `packumentService.build`)。`baseUrl` 由 `ServletUriComponentsBuilder.fromCurrentContextPath()` 推导。

- [ ] **Step 1: 写失败测试(真实端口 + JDK HttpClient,覆盖字面/编码两种 scope 路径)**

`src/test/java/com/agentpluginhub/registry/RegistryControllerTest.java`:

```java
package com.agentpluginhub.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.artifacts-dir=src/test/resources/fixtures/artifacts")
class RegistryControllerTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getString(String rawPath) throws Exception {
        URI uri = URI.create("http://localhost:" + port + rawPath);
        return http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_return_packument_when_literal_scoped_path() throws Exception {
        HttpResponse<String> res = getString("/registry/@demo/hello-plugin");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"dist-tags\"").contains("\"latest\":\"1.0.0\"");
    }

    // 关键:npm 拉 packument 时把 scope 斜杠编码成 %2F,服务端必须放行
    @Test
    void should_return_packument_when_percent_encoded_scope() throws Exception {
        HttpResponse<String> res = getString("/registry/@demo%2Fhello-plugin");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"latest\":\"1.0.0\"");
    }

    @Test
    void should_return_tarball_bytes_when_tarball_path() throws Exception {
        URI uri = URI.create("http://localhost:" + port
                + "/registry/@demo/hello-plugin/-/demo-hello-plugin-1.0.0.tgz");
        HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isNotEmpty();
    }

    @Test
    void should_return_404_when_package_unknown() throws Exception {
        HttpResponse<String> res = getString("/registry/@demo/missing");
        assertThat(res.statusCode()).isEqualTo(404);
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -Dtest=RegistryControllerTest test`
Expected: 失败 —— 端点未实现,`should_return_packument_*` 返回 404 而非 200(`@demo%2Fhello-plugin` 一例在未配置放行时可能返回 400)。

- [ ] **Step 3: 写 TomcatConfig(放行编码斜杠)**

`src/main/java/com/agentpluginhub/config/TomcatConfig.java`:

```java
package com.agentpluginhub.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    // npm 把 scoped 包名里的 "/" 编码为 "%2F";Tomcat 默认拒绝路径中的编码斜杠。
    // 设为 decode,使 %2F 被解码为 "/",请求能进入 RegistryController。
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashCustomizer() {
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setEncodedSolidusHandling("decode"));
    }
}
```

> 说明:若某 Tomcat 版本不接受字符串 `"decode"`,等效兜底是在启动时加 JVM 参数 `-Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true`。本任务的 `should_return_packument_when_percent_encoded_scope` 是该配置是否生效的判定测试。

- [ ] **Step 4: 写 ApiExceptionHandler**

`src/main/java/com/agentpluginhub/common/ApiExceptionHandler.java`:

```java
package com.agentpluginhub.common;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    // 未知包/产物 → 404,沿用 npm 习惯的 JSON 错误体
    @ExceptionHandler({PackageNotFoundException.class, ArtifactNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
    }
}
```

- [ ] **Step 5: 写 RegistryController**

`src/main/java/com/agentpluginhub/registry/RegistryController.java`:

```java
package com.agentpluginhub.registry;

import com.agentpluginhub.common.PackageNotFoundException;
import com.agentpluginhub.registry.model.Packument;
import com.agentpluginhub.storage.ArtifactStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class RegistryController {

    private final PackumentService packumentService;
    private final ArtifactStore store;

    public RegistryController(PackumentService packumentService, ArtifactStore store) {
        this.packumentService = packumentService;
        this.store = store;
    }

    // npm 只读 registry 单入口:
    //   GET /registry/<package>                      → packument(元数据)
    //   GET /registry/<package>/-/<file>.tgz         → tarball(字节)
    // {*path} 捕获 /registry/ 之后的全部(含已被 Tomcat 解码的 "/"),带前导斜杠。
    @GetMapping("/registry/{*path}")
    public ResponseEntity<?> handle(@PathVariable("path") String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.isEmpty()) {
            throw new PackageNotFoundException("");
        }
        int sep = p.indexOf("/-/");
        if (sep >= 0 && p.endsWith(".tgz")) {
            String filename = p.substring(sep + 3);
            byte[] bytes = store.load(filename); // 不存在 → ArtifactNotFoundException → 404
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes);
        }
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        Packument doc = packumentService.build(p, baseUrl); // 未知包 → 404
        return ResponseEntity.ok(doc);
    }
}
```

- [ ] **Step 6: 运行测试,确认通过**

Run: `mvn -Dtest=RegistryControllerTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0`。**若 `should_return_packument_when_percent_encoded_scope` 仍失败**:确认 `TomcatConfig` Bean 已生效,或改用 Step 3 说明里的 JVM 参数兜底。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/agentpluginhub/config/TomcatConfig.java \
        src/main/java/com/agentpluginhub/common/ApiExceptionHandler.java \
        src/main/java/com/agentpluginhub/registry/RegistryController.java \
        src/test/java/com/agentpluginhub/registry/RegistryControllerTest.java
git commit -m "feat: 新增 npm registry 只读端点(packument + tarball,放行编码斜杠)"
```

---

## Task 7: Marketplace 渲染与端点(坏插件跳过)

**Files:**
- Create: `src/main/java/com/agentpluginhub/marketplace/model/NpmSource.java`
- Create: `src/main/java/com/agentpluginhub/marketplace/model/PluginRef.java`
- Create: `src/main/java/com/agentpluginhub/marketplace/model/Marketplace.java`
- Create: `src/main/java/com/agentpluginhub/marketplace/MarketplaceService.java`
- Create: `src/main/java/com/agentpluginhub/marketplace/MarketplaceController.java`
- Test: `src/test/java/com/agentpluginhub/marketplace/MarketplaceServiceTest.java`
- Test: `src/test/java/com/agentpluginhub/marketplace/MarketplaceControllerTest.java`

**Interfaces:**
- Consumes: `PluginCatalog.all()`(T4)、`PluginEntry`(T4)。
- Produces:
  - `record NpmSource(String source, @JsonProperty("package") String packageName, String version, String registry)`。
  - `record PluginRef(String name, String description, NpmSource source)`。
  - `record Marketplace(String name, Map<String,Object> owner, List<PluginRef> plugins)`。
  - `MarketplaceService`(`@Service`,构造注入 `PluginCatalog`):`Marketplace render(String baseUrl)`。每个插件取 `distTags.get("latest")` 作版本(M0 用精确版本,不用区间);`registry = baseUrl + "/registry"`;`source = "npm"`。元数据不全(无 latest / 无 package / 无 pluginName)或渲染抛异常的插件**跳过 + warn**,绝不让整份 JSON 失败。
  - `MarketplaceController`(`@RestController`):`GET /marketplace.json`(`produces=application/json`),`baseUrl` 由 `ServletUriComponentsBuilder` 推导。

- [ ] **Step 1: 写失败测试(service 单元 + controller Web)**

`src/test/java/com/agentpluginhub/marketplace/MarketplaceServiceTest.java`:

```java
package com.agentpluginhub.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.catalog.model.VersionEntry;
import com.agentpluginhub.marketplace.model.Marketplace;
import com.agentpluginhub.marketplace.model.NpmSource;
import com.agentpluginhub.marketplace.model.PluginRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketplaceServiceTest {

    @Test
    void should_render_npm_source_and_skip_incomplete_plugin() {
        PluginCatalog catalog = mock(PluginCatalog.class);
        PluginEntry good = new PluginEntry("@demo/good", "good", "g",
                Map.of("latest", "1.0.0"), List.of(new VersionEntry("1.0.0", "good-1.0.0.tgz")));
        // 坏插件:没有 latest dist-tag,必须被跳过
        PluginEntry bad = new PluginEntry("@demo/bad", "bad", "b", Map.of(), List.of());
        when(catalog.all()).thenReturn(List.of(good, bad));

        Marketplace m = new MarketplaceService(catalog).render("http://localhost:8080");

        assertThat(m.name()).isEqualTo("agent-plugin-hub");
        assertThat(m.plugins()).extracting(PluginRef::name).containsExactly("good");

        NpmSource src = m.plugins().get(0).source();
        assertThat(src.source()).isEqualTo("npm");
        assertThat(src.packageName()).isEqualTo("@demo/good");
        assertThat(src.version()).isEqualTo("1.0.0");
        assertThat(src.registry()).isEqualTo("http://localhost:8080/registry");
    }
}
```

`src/test/java/com/agentpluginhub/marketplace/MarketplaceControllerTest.java`:

```java
package com.agentpluginhub.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.artifacts-dir=src/test/resources/fixtures/artifacts")
class MarketplaceControllerTest {

    @LocalServerPort
    int port;

    @Test
    void should_serve_marketplace_json() throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/marketplace.json");
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
                .contains("\"name\":\"agent-plugin-hub\"")
                .contains("\"source\":\"npm\"")
                .contains("\"package\":\"@demo/hello-plugin\"")
                .contains("\"version\":\"1.0.0\"")
                .contains("/registry");
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -Dtest=MarketplaceServiceTest,MarketplaceControllerTest test`
Expected: 编译失败(模型、service、controller 未创建)。

- [ ] **Step 3: 写 marketplace 模型(records)**

`src/main/java/com/agentpluginhub/marketplace/model/NpmSource.java`:

```java
package com.agentpluginhub.marketplace.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// CC marketplace 条目的 source 对象(npm 类型,指向自建 registry)
public record NpmSource(
        String source,
        @JsonProperty("package") String packageName,
        String version,
        String registry) {
}
```

`src/main/java/com/agentpluginhub/marketplace/model/PluginRef.java`:

```java
package com.agentpluginhub.marketplace.model;

// marketplace.json 的 plugins[] 单条
public record PluginRef(String name, String description, NpmSource source) {
}
```

`src/main/java/com/agentpluginhub/marketplace/model/Marketplace.java`:

```java
package com.agentpluginhub.marketplace.model;

import java.util.List;
import java.util.Map;

// CC 远程市场清单:GET /marketplace.json 的响应体
public record Marketplace(String name, Map<String, Object> owner, List<PluginRef> plugins) {
}
```

- [ ] **Step 4: 写 MarketplaceService**

`src/main/java/com/agentpluginhub/marketplace/MarketplaceService.java`:

```java
package com.agentpluginhub.marketplace;

import com.agentpluginhub.catalog.PluginCatalog;
import com.agentpluginhub.catalog.model.PluginEntry;
import com.agentpluginhub.marketplace.model.Marketplace;
import com.agentpluginhub.marketplace.model.NpmSource;
import com.agentpluginhub.marketplace.model.PluginRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final PluginCatalog catalog;

    public MarketplaceService(PluginCatalog catalog) {
        this.catalog = catalog;
    }

    // 把目录里的插件渲染成 CC 认的 marketplace.json。
    // 铁律(spec §7):坏插件跳过 + 记日志,永远吐合法 JSON。
    public Marketplace render(String baseUrl) {
        List<PluginRef> refs = new ArrayList<>();
        for (PluginEntry e : catalog.all()) {
            try {
                String latest = e.distTags() == null ? null : e.distTags().get("latest");
                if (latest == null || e.packageName() == null || e.pluginName() == null) {
                    log.warn("skip plugin with incomplete metadata: {}", e.packageName());
                    continue;
                }
                NpmSource src = new NpmSource("npm", e.packageName(), latest, baseUrl + "/registry");
                refs.add(new PluginRef(e.pluginName(), e.description(), src));
            } catch (RuntimeException ex) {
                log.warn("skip bad plugin {}", e.packageName(), ex);
            }
        }
        return new Marketplace("agent-plugin-hub", Map.of("name", "agent-plugin-hub"), refs);
    }
}
```

- [ ] **Step 5: 写 MarketplaceController**

`src/main/java/com/agentpluginhub/marketplace/MarketplaceController.java`:

```java
package com.agentpluginhub.marketplace;

import com.agentpluginhub.marketplace.model.Marketplace;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class MarketplaceController {

    private final MarketplaceService service;

    public MarketplaceController(MarketplaceService service) {
        this.service = service;
    }

    // CC 远程市场入口:/plugin marketplace add http://<host>/marketplace.json
    @GetMapping(value = "/marketplace.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Marketplace marketplace() {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return service.render(baseUrl);
    }
}
```

- [ ] **Step 6: 运行测试,确认通过**

Run: `mvn -Dtest=MarketplaceServiceTest,MarketplaceControllerTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 7: 全量回归**

Run: `mvn test`
Expected: `BUILD SUCCESS`,所有任务的测试全绿。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/agentpluginhub/marketplace \
        src/test/java/com/agentpluginhub/marketplace
git commit -m "feat: 新增 marketplace.json 渲染与端点(坏插件跳过)"
```

---

## Task 8: 样例插件与种子制品(npm pack 真包入库)

**Files:**
- Create: `examples/hello-plugin/package.json`
- Create: `examples/hello-plugin/.claude-plugin/plugin.json`
- Create: `examples/hello-plugin/commands/hello.md`
- Create: `scripts/seed-artifacts.sh`

**Interfaces:**
- Consumes: T1 的 `.gitignore`(已忽略 `artifacts/`、`*.tgz`、`node_modules/`)。
- Produces:
  - 一个可被 `npm pack` 的样例 CC 插件(同时含 `package.json` 与 `.claude-plugin/plugin.json`),npm 包名 `@demo/hello-plugin@1.0.0`,CC 插件名 `hello-plugin`,`npm pack` 产出文件名 `demo-hello-plugin-1.0.0.tgz`。
  - `scripts/seed-artifacts.sh`:把样例 `npm pack` 进 `./artifacts` 并写出 `./artifacts/index.json`(与 T4 fixture 同结构)。供 T9 集成测试与 T10 真机冒烟。

- [ ] **Step 1: 写样例插件 package.json**

`examples/hello-plugin/package.json`:

```json
{
  "name": "@demo/hello-plugin",
  "version": "1.0.0",
  "description": "Demo plugin for agent-plugin-hub M0 smoke test",
  "files": [
    ".claude-plugin",
    "commands"
  ]
}
```

- [ ] **Step 2: 写 CC 插件清单 plugin.json**

`examples/hello-plugin/.claude-plugin/plugin.json`:

```json
{
  "name": "hello-plugin",
  "description": "Demo plugin for agent-plugin-hub M0 smoke test",
  "version": "1.0.0",
  "author": { "name": "agent-plugin-hub" }
}
```

- [ ] **Step 3: 写一个可观测的命令**

`examples/hello-plugin/commands/hello.md`:

```markdown
---
description: M0 冒烟命令,打印一句话证明插件已装上
---

请直接输出这一行(不要做别的):

hello from agent-plugin-hub M0 plugin ✅
```

- [ ] **Step 4: 写种子脚本**

`scripts/seed-artifacts.sh`:

```bash
#!/usr/bin/env bash
# 把样例插件 npm pack 进 ./artifacts 并生成 index.json(M0 手工塞 tarball 的自动化版)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ART="$ROOT/artifacts"
mkdir -p "$ART"

# npm 7+ 支持 --pack-destination;本机 npm 11 已具备
( cd "$ROOT/examples/hello-plugin" && npm pack --pack-destination "$ART" )

cat > "$ART/index.json" <<'JSON'
{
  "plugins": [
    {
      "package": "@demo/hello-plugin",
      "pluginName": "hello-plugin",
      "description": "Demo plugin for agent-plugin-hub M0 smoke test",
      "distTags": { "latest": "1.0.0" },
      "versions": [
        { "version": "1.0.0", "tarball": "demo-hello-plugin-1.0.0.tgz" }
      ]
    }
  ]
}
JSON

echo "seeded -> $ART"
ls -1 "$ART"
```

- [ ] **Step 5: 赋可执行权限**

Run: `chmod +x scripts/seed-artifacts.sh`

- [ ] **Step 6: 验证 npm pack 内容正确(含 .claude-plugin/plugin.json 与命令)**

Run: `cd examples/hello-plugin && npm pack --dry-run`
Expected: 输出的文件清单包含 `.claude-plugin/plugin.json`、`commands/hello.md`、`package.json`;末尾打印 `filename: demo-hello-plugin-1.0.0.tgz`。

- [ ] **Step 7: 验证种子脚本可生成制品**

Run: `cd <repo-root> && ./scripts/seed-artifacts.sh`
Expected: `./artifacts/` 下出现 `demo-hello-plugin-1.0.0.tgz` 与 `index.json`(`artifacts/` 已被 .gitignore 忽略,不会进版本库)。

- [ ] **Step 8: 提交(只提交源与脚本,不提交生成的 tarball)**

```bash
git add examples scripts/seed-artifacts.sh
git commit -m "feat: 新增样例插件与制品种子脚本"
```

---

## Task 9: 真实 npm install 集成测试(协议自动化证明)

**Files:**
- Test: `src/test/java/com/agentpluginhub/integration/RealNpmInstallTest.java`

**Interfaces:**
- Consumes: `AgentPluginHubApplication`(T1)、registry 端点(T6)、`examples/hello-plugin`(T8)。
- Produces: 一个端到端测试:程序化启动应用(随机端口、`app.artifacts-dir` 指向临时目录)→ 把样例 `npm pack` 进该目录并写 `index.json` → 用**真实 `npm install --registry=<本测试实例>`** 验证 npm 能解析 packument、下载 tarball、校验 integrity 并装上。本机无 `npm` 时 `Assumptions.assumeTrue` 优雅跳过。

> 说明:此测试名以 `Test` 结尾,故 `mvn test` 会运行(本机 npm 可用时实际执行,CI 无 npm 时自动跳过)。它是「source:npm + 自建 registry」协议的**自动化**证明,但真机 CC 冒烟(T10)才是最终判定。

- [ ] **Step 1: 写集成测试**

`src/test/java/com/agentpluginhub/integration/RealNpmInstallTest.java`:

```java
package com.agentpluginhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.AgentPluginHubApplication;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class RealNpmInstallTest {

    private static ConfigurableApplicationContext ctx;
    private static int port;
    private static Path tmp;

    @BeforeAll
    static void boot() throws Exception {
        Assumptions.assumeTrue(commandExists("npm"), "npm 不可用,跳过真实 npm install 集成测试");

        tmp = Files.createTempDirectory("aph-it");
        Path artifacts = tmp.resolve("artifacts");
        Files.createDirectories(artifacts);

        // 把样例插件打成真实 tarball 放进临时 artifacts 目录
        int packCode = runProcess(new File("examples/hello-plugin"),
                "npm", "pack", "--pack-destination", artifacts.toAbsolutePath().toString());
        assertThat(packCode).isEqualTo(0);

        Files.writeString(artifacts.resolve("index.json"), """
                {
                  "plugins": [
                    {
                      "package": "@demo/hello-plugin",
                      "pluginName": "hello-plugin",
                      "description": "demo",
                      "distTags": { "latest": "1.0.0" },
                      "versions": [
                        { "version": "1.0.0", "tarball": "demo-hello-plugin-1.0.0.tgz" }
                      ]
                    }
                  ]
                }
                """);

        // 用命令行参数(高优先级)覆盖 application.yml 的 server.port/artifacts-dir;
        // 注意:不能用 .properties(...),那是最低优先级的默认属性,会被 application.yml 覆盖。
        ctx = new SpringApplicationBuilder(AgentPluginHubApplication.class)
                .run("--server.port=0",
                        "--app.artifacts-dir=" + artifacts.toAbsolutePath());
        port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
    }

    @AfterAll
    static void shutdown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void should_install_plugin_via_real_npm() throws Exception {
        Path project = tmp.resolve("consumer");
        Files.createDirectories(project);
        Files.writeString(project.resolve("package.json"), "{\"name\":\"c\",\"version\":\"1.0.0\"}");

        int code = runProcess(project.toFile(),
                "npm", "install", "@demo/hello-plugin@1.0.0",
                "--registry=http://localhost:" + port + "/registry",
                "--no-audit", "--no-fund", "--no-save",
                "--prefix", project.toAbsolutePath().toString());

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(project.resolve("node_modules/@demo/hello-plugin/package.json")))
                .as("npm 应已把插件装进 node_modules")
                .isTrue();
    }

    private static boolean commandExists(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int runProcess(File dir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir).inheritIO().start();
        return p.waitFor();
    }
}
```

- [ ] **Step 2: 运行集成测试**

Run: `mvn -Dtest=RealNpmInstallTest test`
Expected(本机有 npm):`Tests run: 1, Failures: 0, Errors: 0`,且日志里能看到 npm 从 `http://localhost:<port>/registry` 拉取并装上。**若 integrity 不匹配则会失败** —— 说明 packument 的哈希与 tarball 字节不一致,需回到 T5 排查。

- [ ] **Step 3: 全量回归**

Run: `mvn test`
Expected: `BUILD SUCCESS`,全部测试通过。

- [ ] **Step 4: 提交**

```bash
git add src/test/java/com/agentpluginhub/integration/RealNpmInstallTest.java
git commit -m "test: 新增真实 npm install 集成测试验证 registry 协议"
```

---

## Task 10: CC 真机冒烟手册 + go/no-go gate(里程碑 0 验收)

**Files:**
- Create: `docs/m0-smoke-runbook.md`

**Interfaces:**
- Consumes: 全部前序任务产出的服务 + `scripts/seed-artifacts.sh`(T8)。
- Produces: 真机冒烟手册与验收记录。这是里程碑 0 的**唯一真凭据**(spec §9:CC 真机冒烟是验证协议假设的唯一证明),无法自动化,需人工在真实 Claude Code 里执行并记录 go/no-go。

> 关键判定:CC 对「远程 URL 形式 marketplace.json + source:npm 自建 registry」是否真的兼容。**走通 → 里程碑 0 达成**;**走不通 → 启用兜底路线 (ii)**(把 marketplace.json push 进内网私有 git 仓,CC 用原生 git 凭据 add;registry 仍走动态 npm 端点),并据此判断是否需调整整体方案。

- [ ] **Step 1: 写冒烟手册**

`docs/m0-smoke-runbook.md`:

````markdown
# 里程碑 0 真机冒烟手册(Claude Code)

目的:在**真实 Claude Code** 里验证「自建 npm registry + 动态 marketplace.json」能被原生 `marketplace add` + `install` 装上。这是 M0 的唯一验收证据。

## 前置

- 已构建通过:`export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home && mvn test` 全绿。
- 已种子化制品:在仓库根执行 `./scripts/seed-artifacts.sh`,确认 `./artifacts/` 下有 `demo-hello-plugin-1.0.0.tgz` 与 `index.json`。

## 启动服务

```bash
export JAVA_HOME=/Users/richardhuang/devsoft/jdk-21.0.10.jdk/Contents/Home
mvn spring-boot:run
# 另开终端自检(应返回 marketplace JSON,registry 字段为 http://localhost:8080/registry):
curl -s http://localhost:8080/marketplace.json
# 自检 packument(scoped 包名用 %2F):
curl -s http://localhost:8080/registry/@demo%2Fhello-plugin
```

## 路线 (i):动态 HTTP marketplace(默认先试)

在真实 Claude Code 会话里:

```
/plugin marketplace add http://localhost:8080/marketplace.json
/plugin install hello-plugin@agent-plugin-hub
```

验证:

```
/hello-plugin:hello
```

预期:输出 `hello from agent-plugin-hub M0 plugin ✅`。

- 若全部成功 → 里程碑 0 **GO**(路线 i)。在下方「验收记录」勾选并记录 CC 版本。
- 若 `marketplace add` 因鉴权/协议被拒,或 `install` 无法命中 registry → 记录报错原文,转路线 (ii)。

## 路线 (ii):私有 git 仓 marketplace(兜底,spec §8)

当路线 (i) 不通时:

1. 建一个内网私有 git 仓(如 GitLab),仓内放 `.claude-plugin/marketplace.json`,内容等于 `curl http://localhost:8080/marketplace.json` 的输出(`registry` 指向本服务的 `/registry`,需为 CC 所在机可达的地址而非 localhost)。
2. 配置 CC 的 git 凭据(`GITLAB_TOKEN` 等)。
3. 在 CC 里:
   ```
   /plugin marketplace add <私有仓 git url>
   /plugin install hello-plugin@agent-plugin-hub
   /hello-plugin:hello
   ```
4. 若成功 → 里程碑 0 **GO**(路线 ii)。registry 仍由本服务动态提供,仅 manifest 改为 git 分发。

## 验收记录(执行后填写)

- [ ] 路线 (i) 通过 —— CC 版本:____,日期:____
- [ ] 路线 (ii) 通过 —— CC 版本:____,日期:____,转用原因:____
- [ ] 均不通过 —— 报错原文与结论:____(触发对整体方案的重新评估)
````

- [ ] **Step 2: 按手册执行真机冒烟并回填验收记录**

人工操作:依手册启动服务、在真实 Claude Code 里执行 `marketplace add` / `install` / `/hello`,把结果(GO/NO-GO、走的是路线 i 还是 ii、CC 版本)回填进 `docs/m0-smoke-runbook.md` 的「验收记录」。

- [ ] **Step 3: 提交**

```bash
git add docs/m0-smoke-runbook.md
git commit -m "docs: 新增 M0 真机冒烟手册与验收记录"
```

---

## 自检结果(写计划者已核对)

- **Spec 覆盖**:M0(spec §10)所需 ⑤本地存储=T3、⑥registry 两端点=T5+T6、⑦marketplace.json=T7、手工塞 tarball=T8、CC 真机冒烟=T10、真实 npm install 验证(§9)=T9、坏插件跳过(§7)=T7、整洁 404(§7)=T6、保留名/marketplace 命名(附录)=T7 固定为 `agent-plugin-hub`、manifest 鉴权双路兜底(§8)=T10。**不在 M0 的**(审核/OIDC/DB/dist-tag 多渠道/不可变发布/前端)按里程碑划分**已显式延后**,符合范围裁剪。
- **占位符扫描**:无 TBD/TODO;每个改代码的步骤均给出完整代码与确切命令、预期输出。
- **类型一致性**:`ArtifactStore.load(String):byte[]`、`PluginCatalog.require/all/find`、`PackumentService.build(String,String):Packument`、`MarketplaceService.render(String):Marketplace`、records(`PluginEntry.packageName()` 等)在定义任务与消费任务间签名一致;tarball 文件名 `demo-hello-plugin-1.0.0.tgz` 在 fixture(T3/T4)、packument(T5)、registry 测试(T6)、种子脚本与样例(T8)、集成测试(T9)间一致。
- **已知协议不确定性(M0 的存在意义)**:① CC 对远程 URL marketplace + source:npm 的真实兼容性 → T10 真机判定 + 路线 (ii) 兜底;② `%2F` 编码斜杠放行 → T6 有专门判定测试 + JVM 参数兜底。两者均已在对应任务内显式标注处置方式。
