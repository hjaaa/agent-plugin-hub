package com.agentpluginhub.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentpluginhub.registry.TarballManifestReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    private final Validator validator = new Validator(
            new TarballManifestReader(new ObjectMapper()), new DependencyInspector());

    // 构造一个含多个 entry 的 .tgz(key=entry 名,value=文件内容)
    private static byte[] tgz(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(bos))) {
            for (Map.Entry<String, String> en : entries.entrySet()) {
                byte[] c = en.getValue().getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry e = new TarArchiveEntry(en.getKey());
                e.setSize(c.length);
                tar.putArchiveEntry(e);
                tar.write(c);
                tar.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }

    @Test
    void should_accept_valid_self_contained_plugin() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/hello-plugin\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"hello-plugin\",\"description\":\"hi\",\"version\":\"1.0.0\"}"));
        ValidationResult r = validator.validate(t);
        assertThat(r.packageName()).isEqualTo("@demo/hello-plugin");
        assertThat(r.version()).isEqualTo("1.0.0");
        assertThat(r.pluginName()).isEqualTo("hello-plugin");
        assertThat(r.description()).isEqualTo("hi");
    }

    @Test
    void should_reject_when_package_json_missing() throws Exception {
        byte[] t = tgz(Map.of("package/.claude-plugin/plugin.json", "{\"name\":\"x\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("PACKAGE_JSON_MISSING"));
    }

    @Test
    void should_reject_when_plugin_json_missing() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/x\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("PLUGIN_JSON_MISSING"));
    }

    @Test
    void should_reject_reserved_plugin_name() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/x\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"anthropic-foo\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("RESERVED_NAME"));
    }

    @Test
    void should_reject_plugin_with_external_dependency() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json",
                "{\"name\":\"@demo/x\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1\"}}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"x\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("EXTERNAL_DEPENDENCY"));
    }

    @Test
    void should_reject_when_bundled_dependency_absent_from_tarball() throws Exception {
        // 声明 bundleDependencies 却未实际打包 node_modules → 仍判外部依赖(codex P2)
        byte[] t = tgz(Map.of(
                "package/package.json",
                "{\"name\":\"@demo/x\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1\"},"
                        + "\"bundleDependencies\":[\"left-pad\"]}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"x\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("EXTERNAL_DEPENDENCY"));
    }

    @Test
    void should_reject_invalid_package_name() throws Exception {
        // 含多斜杠/-/ 的包名会破坏 registry 路由,发布期即拒(codex P2)
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/e/-/vil\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"x\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("PACKAGE_JSON_INVALID"));
    }

    @Test
    void should_reject_invalid_version() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/x\",\"version\":\"1.0/0\"}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"x\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("PACKAGE_JSON_INVALID"));
    }

    @Test
    void should_accept_when_bundled_dependency_present_in_tarball() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json",
                "{\"name\":\"@demo/x\",\"version\":\"1.0.0\",\"dependencies\":{\"left-pad\":\"^1\"},"
                        + "\"bundleDependencies\":[\"left-pad\"]}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"x\",\"version\":\"1.0.0\"}",
                "package/node_modules/left-pad/package.json", "{\"name\":\"left-pad\",\"version\":\"1.0.0\"}"));
        ValidationResult r = validator.validate(t);
        assertThat(r.packageName()).isEqualTo("@demo/x");
        assertThat(r.pluginName()).isEqualTo("x");
    }

    @Test
    void should_reject_claude_plugins_prefix() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/x\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"claude-plugins-foo\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("RESERVED_NAME"));
    }

    @Test
    void should_reject_claude_code_prefix() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/x\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json", "{\"name\":\"claude-code-foo\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("RESERVED_NAME"));
    }

    @Test
    void should_reject_exact_reserved_name() throws Exception {
        byte[] t = tgz(Map.of(
                "package/package.json", "{\"name\":\"@demo/x\",\"version\":\"1.0.0\"}",
                "package/.claude-plugin/plugin.json",
                "{\"name\":\"claude-code-marketplace\",\"version\":\"1.0.0\"}"));
        assertThatThrownBy(() -> validator.validate(t))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getCode()).isEqualTo("RESERVED_NAME"));
    }
}
