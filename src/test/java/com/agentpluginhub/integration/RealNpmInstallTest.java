package com.agentpluginhub.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestDataSeeder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"app.storage.type=local", "app.registry.auth.enabled=false"})
class RealNpmInstallTest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestDataSeeder seeder;

    private Path tmp;

    @BeforeEach
    void seed() throws Exception {
        Assumptions.assumeTrue(commandExists("npm"), "npm 不可用,跳过真实 npm install 集成测试");
        tmp = Files.createTempDirectory("aph-it");
        Path packDir = tmp.resolve("pack");
        Files.createDirectories(packDir);
        int packCode = runProcess(new File("examples/hello-plugin"),
                "npm", "pack", "--pack-destination", packDir.toAbsolutePath().toString());
        assertThat(packCode).isEqualTo(0);
        byte[] tgz = Files.readAllBytes(packDir.resolve("demo-hello-plugin-1.0.0.tgz"));
        seeder.publish("@demo/hello-plugin", "hello-plugin", "1.0.0",
                "demo-hello-plugin-1.0.0.tgz", tgz);
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
        // 不继承 stdin:inheritIO() 会把 Surefire 的 stdin 传给子进程,
        // npm 退出时关闭 stdin 会损坏 Surefire 的通信通道导致 BUILD FAILURE。
        Process p = new ProcessBuilder(cmd).directory(dir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        return p.waitFor();
    }
}
