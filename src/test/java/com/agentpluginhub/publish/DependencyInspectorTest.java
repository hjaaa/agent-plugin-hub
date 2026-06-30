package com.agentpluginhub.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyInspectorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DependencyInspector inspector = new DependencyInspector();
    private static final Set<String> NONE = Set.of();

    private ObjectNode pkg(String json) throws Exception {
        return (ObjectNode) mapper.readTree(json);
    }

    @Test
    void plain_dependency_is_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(
                pkg("{\"dependencies\":{\"left-pad\":\"^1\"}}"), NONE)).isTrue();
    }

    @Test
    void bundled_array_present_in_tarball_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"left-pad\":\"^1\"},\"bundleDependencies\":[\"left-pad\"]}"),
                Set.of("left-pad"))).isFalse();
    }

    @Test
    void bundled_array_absent_from_tarball_is_external() throws Exception {
        // 声明 bundleDependencies 但 tarball 未实际打包 → 仍是外部依赖(codex P2)
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"left-pad\":\"^1\"},\"bundleDependencies\":[\"left-pad\"]}"),
                NONE)).isTrue();
    }

    @Test
    void bundle_true_all_present_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"left-pad\":\"^1\"},\"bundleDependencies\":true}"),
                Set.of("left-pad"))).isFalse();
    }

    @Test
    void bundle_true_partially_absent_is_external() throws Exception {
        // bundleDependencies:true 但其中一个 dep 未打包 → 外部依赖
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"left-pad\":\"^1\",\"foo\":\"^2\"},\"bundleDependencies\":true}"),
                Set.of("left-pad"))).isTrue();
    }

    @Test
    void scoped_bundled_dependency_present_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"@acme/util\":\"^1\"},\"bundleDependencies\":[\"@acme/util\"]}"),
                Set.of("@acme/util"))).isFalse();
    }

    @Test
    void non_optional_peer_is_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(
                pkg("{\"peerDependencies\":{\"react\":\"^18\"}}"), NONE)).isTrue();
    }

    @Test
    void optional_peer_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"peerDependencies\":{\"react\":\"^18\"},\"peerDependenciesMeta\":{\"react\":{\"optional\":true}}}"),
                NONE)).isFalse();
    }

    @Test
    void no_dependencies_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg("{\"name\":\"x\"}"), NONE)).isFalse();
    }
}
