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

    @Test
    void should_reject_when_filename_is_null() {
        assertThatThrownBy(() -> newStore().load(null))
                .isInstanceOf(ArtifactNotFoundException.class);
    }

    @Test
    void should_reject_when_filename_is_blank() {
        assertThatThrownBy(() -> newStore().load("   "))
                .isInstanceOf(ArtifactNotFoundException.class);
    }

    @Test
    void should_reject_when_filename_contains_forward_slash() {
        assertThatThrownBy(() -> newStore().load("sub/evil.tgz"))
                .isInstanceOf(ArtifactNotFoundException.class);
    }

    @Test
    void should_reject_when_filename_contains_backslash() {
        assertThatThrownBy(() -> newStore().load("sub\\evil.tgz"))
                .isInstanceOf(ArtifactNotFoundException.class);
    }
}
