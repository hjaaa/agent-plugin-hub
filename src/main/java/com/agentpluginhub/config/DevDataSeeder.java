package com.agentpluginhub.config;

import com.agentpluginhub.domain.Plugin;
import com.agentpluginhub.mapper.MapperQueries;
import com.agentpluginhub.mapper.PluginMapper;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.review.ReviewService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 仅 dev:空库时把演示插件走真实 发布→审批 流程种子化,便于真机 CC install 冒烟。
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final PluginMapper plugins;
    private final PublishingService publishing;
    private final ReviewService review;

    public DevDataSeeder(PluginMapper plugins, PublishingService publishing, ReviewService review) {
        this.plugins = plugins;
        this.publishing = publishing;
        this.review = review;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            if (MapperQueries.exists(plugins, Wrappers.<Plugin>lambdaQuery()
                    .eq(Plugin::getPackageName, "@demo/hello-plugin"))) {
                return;
            }
            ClassPathResource res = new ClassPathResource("dev/demo-hello-plugin-1.0.0.tgz");
            if (!res.exists()) {
                log.warn("dev seed tarball missing at classpath dev/demo-hello-plugin-1.0.0.tgz; skip seeding");
                return;
            }
            byte[] tarball;
            try (InputStream in = res.getInputStream()) {
                tarball = in.readAllBytes();
            }
            Long id = publishing.publish(tarball, "dev-seeder");
            review.approve(id, "dev-admin", "dev seed");
            log.info("dev seed: published @demo/hello-plugin@1.0.0");
        } catch (Exception e) {
            log.warn("dev seed failed, skip; {}", e.getMessage());
        }
    }
}
