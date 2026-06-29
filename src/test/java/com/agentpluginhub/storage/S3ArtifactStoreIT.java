package com.agentpluginhub.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.storage.type=s3")
class S3ArtifactStoreIT extends AbstractIntegrationTest {

    @Autowired
    ArtifactStore store;

    @Test
    void should_round_trip_save_load_exists() {
        assertThat(store).isInstanceOf(S3ArtifactStore.class);
        String key = "demo-x-1.0.0.tgz";
        assertThat(store.exists(key)).isFalse();
        store.save(key, new byte[]{9, 8, 7});
        assertThat(store.exists(key)).isTrue();
        assertThat(store.load(key)).containsExactly(9, 8, 7);
    }
}
