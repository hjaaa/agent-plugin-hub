package com.agentpluginhub.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IntegrityUtilTest {

    // 空输入的 sha512 SRI 是业界公认固定值,可作 golden 向量
    @Test
    void should_compute_sha512_sri_when_input_empty() {
        assertThat(IntegrityUtil.sriSha512(new byte[0]))
                .isEqualTo("sha512-z4PhNX7vuL3xVChQ1m2AB9Yg5AULVxXcg/SpIdNs6c5H0NE8XYXysP+DGNKHfuwvY7kxvUdBeoGlODJ6+SfaPg==");
    }

    // "abc" 的 sha1 是经典测试向量
    @Test
    void should_compute_sha1_hex_when_input_abc() {
        assertThat(IntegrityUtil.hexSha1("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
    }
}
