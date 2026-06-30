package com.agentpluginhub.disttag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.domain.PluginVersionRepository;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.review.ReviewService;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestTarballs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.type=local")
class DistTagControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired PublishingService publishing;
    @Autowired ReviewService review;
    @Autowired PluginRepository plugins;
    @Autowired PluginVersionRepository versions;

    private static final String PKG = "@demo/dtit";

    // 单例容器跨测试类/方法复用 → 幂等:已 PUBLISHED 的 (pkg, version) 不再重复发布
    // (否则触发 DuplicatePublishException)。每个测试前确保两版本就绪,stable 由首发停在 1.0.0。
    @BeforeEach
    void seedTwoVersions() throws Exception {
        // 1.0.0:首发 → latest=stable=1.0.0;1.1.0:审批 → latest=1.1.0、stable=1.0.0
        for (String v : new String[]{"1.0.0", "1.1.0"}) {
            if (!alreadyPublished(v)) {
                review.approve(publishing.publish(TestTarballs.plugin(PKG, v), "alice"), "admin", "ok");
            }
        }
    }

    private boolean alreadyPublished(String version) {
        return plugins.findByPackageName(PKG)
                .map(p -> versions.existsByPluginIdAndVersionAndStatus(p.getId(), version, "PUBLISHED"))
                .orElse(false);
    }

    @Test
    void promote_then_rollback_stable_reflects_in_marketplace_and_packument() throws Exception {
        var admin = oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // 提升 stable → 1.1.0
        mvc.perform(put("/api/dist-tags/stable/plugins/" + PKG)
                        .with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.1.0\"}"))
                .andExpect(status().isOk());

        // marketplace 反映 1.1.0
        String mk1 = mvc.perform(get("/marketplace.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mk1).contains("@demo/dtit").contains("\"version\":\"1.1.0\"");

        // packument 同时含 latest+stable(scoped 包名 capture-all,MockMvc 用字面斜杠)
        String pk = mvc.perform(get("/registry/" + PKG)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(pk).contains("\"latest\":\"1.1.0\"").contains("\"stable\":\"1.1.0\"");

        // 回滚 stable → 1.0.0(产物不动)
        mvc.perform(put("/api/dist-tags/stable/plugins/" + PKG)
                        .with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());

        String mk2 = mvc.perform(get("/marketplace.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mk2).contains("@demo/dtit").contains("\"version\":\"1.0.0\"");
    }

    @Test
    void version_not_published_returns_404() throws Exception {
        mvc.perform(put("/api/dist-tags/stable/plugins/" + PKG)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"9.9.9\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalid_tag_returns_422() throws Exception {
        mvc.perform(put("/api/dist-tags/beta/plugins/" + PKG)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void non_admin_returns_403() throws Exception {
        mvc.perform(put("/api/dist-tags/stable/plugins/" + PKG)
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isForbidden());
    }
}
