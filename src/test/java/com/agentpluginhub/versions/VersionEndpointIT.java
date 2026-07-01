package com.agentpluginhub.versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.domain.PluginVersion;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.mapper.PluginVersionMapper;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.review.ReviewService;
import com.agentpluginhub.support.AbstractIntegrationTest;
import com.agentpluginhub.support.TestTarballs;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.type=local")
class VersionEndpointIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired PublishingService publishing;
    @Autowired ReviewService review;
    @Autowired PluginMapper plugins;
    @Autowired PluginVersionMapper versions;

    private static final String PKG = "@demo/verit";

    @BeforeEach
    void seed() throws Exception {
        for (String v : new String[]{"1.0.0", "1.1.0"}) {
            boolean published = MapperQueries.one(plugins, Wrappers.<Plugin>lambdaQuery()
                            .eq(Plugin::getPackageName, PKG))
                    .map(p -> MapperQueries.exists(versions, Wrappers.<PluginVersion>lambdaQuery()
                            .eq(PluginVersion::getPluginId, p.getId())
                            .eq(PluginVersion::getVersion, v)
                            .eq(PluginVersion::getStatus, "PUBLISHED")))
                    .orElse(false);
            if (!published) {
                review.approve(publishing.publish(TestTarballs.plugin(PKG, v), "alice"), "admin", "ok");
            }
        }
    }

    @Test
    void lists_versions_and_dist_tags_for_authenticated_user() throws Exception {
        String body = mvc.perform(get("/api/plugin-versions/" + PKG).with(oidcLogin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"package\":\"@demo/verit\"")
                .contains("\"latest\":\"1.1.0\"")
                .contains("\"stable\":\"1.0.0\"")
                .contains("\"version\":\"1.1.0\"")
                .contains("\"version\":\"1.0.0\"");
    }

    @Test
    void unknown_package_returns_404() throws Exception {
        mvc.perform(get("/api/plugin-versions/@x/none").with(oidcLogin()))
                .andExpect(status().isNotFound());
    }
}
