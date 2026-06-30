package com.agentpluginhub.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtifactKeysTest {

    @Test
    void key_is_flat_with_content_tag() {
        assertThat(ArtifactKeys.canonical("@demo/hello-plugin", "1.0.0", "abcdef0123456789"))
                .isEqualTo("demo-hello-plugin-1.0.0-abcdef012345.tgz");
    }

    @Test
    void scoped_and_flat_packages_do_not_collide() {
        // @demo/hello-plugin 去斜杠后与字面包名 demo-hello-plugin 同形;
        // 内容必不同 → shasum 不同 → key 不再碰撞(P2 回归)
        String scoped = ArtifactKeys.canonical("@demo/hello-plugin", "1.0.0", "aaaaaaaaaaaa1111");
        String flat = ArtifactKeys.canonical("demo-hello-plugin", "1.0.0", "bbbbbbbbbbbb2222");
        assertThat(scoped).isNotEqualTo(flat);
    }

    @Test
    void same_name_version_different_content_yields_different_key() {
        String a = ArtifactKeys.canonical("@demo/p", "1.0.0", "111111111111aaaa");
        String b = ArtifactKeys.canonical("@demo/p", "1.0.0", "222222222222bbbb");
        assertThat(a).isNotEqualTo(b);
    }
}
