package com.agentpluginhub.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

// M1 集成测试基类:提供一次性启动的 MySQL 容器,并注入 Spring datasource。
// Task 3 会在子类或本类补充 MinIO(对象存储)注入。
// 注意:不用 @Container 管理生命周期——Testcontainers 会在每个测试类结束后停止容器,
// 而多个子类可能共享同一个 Spring ApplicationContext 缓存(datasource URL 已固定),
// 导致后续测试拿到的连接指向已停止的容器。改用 static initializer 让容器在
// 整个 JVM 生命周期内保持运行,由 Ryuk 在进程退出时统一清理。
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
