package com.agentpluginhub.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;

// 单例容器模式:容器在 static 块启动、整个 JVM 生命周期保活,由 Ryuk 在进程退出时清理。
// 不用 @Container/@Testcontainers——跨测试类复用 Spring ApplicationContext 缓存时,
// @Container 会在首个测试类结束后停掉容器,导致后续测试连到已停容器而超时。
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");
    protected static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

    static {
        MYSQL.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.s3.endpoint", MINIO::getS3URL);
        registry.add("app.s3.access-key", MINIO::getUserName);
        registry.add("app.s3.secret-key", MINIO::getPassword);
    }
}
