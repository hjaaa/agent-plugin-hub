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
