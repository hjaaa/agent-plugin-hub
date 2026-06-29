package com.agentpluginhub.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class DependencyInspectorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DependencyInspector inspector = new DependencyInspector();

    private ObjectNode pkg(String json) throws Exception {
        return (ObjectNode) mapper.readTree(json);
    }

    @Test
    void plain_dependency_is_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(
                pkg("{\"dependencies\":{\"left-pad\":\"^1\"}}"))).isTrue();
    }

    @Test
    void bundled_array_dependency_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"left-pad\":\"^1\"},\"bundleDependencies\":[\"left-pad\"]}")))
                .isFalse();
    }

    @Test
    void bundle_dependencies_true_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"dependencies\":{\"left-pad\":\"^1\"},\"bundleDependencies\":true}"))).isFalse();
    }

    @Test
    void non_optional_peer_is_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(
                pkg("{\"peerDependencies\":{\"react\":\"^18\"}}"))).isTrue();
    }

    @Test
    void optional_peer_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg(
                "{\"peerDependencies\":{\"react\":\"^18\"},\"peerDependenciesMeta\":{\"react\":{\"optional\":true}}}")))
                .isFalse();
    }

    @Test
    void no_dependencies_is_not_external() throws Exception {
        assertThat(inspector.hasExternalDependencies(pkg("{\"name\":\"x\"}"))).isFalse();
    }
}
