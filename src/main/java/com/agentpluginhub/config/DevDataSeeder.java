package com.agentpluginhub.config;

import com.agentpluginhub.domain.PluginRepository;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.review.ReviewService;
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

    private final PluginRepository plugins;
    private final PublishingService publishing;
    private final ReviewService review;

    public DevDataSeeder(PluginRepository plugins, PublishingService publishing, ReviewService review) {
        this.plugins = plugins;
        this.publishing = publishing;
        this.review = review;
    }

    @Override
    public void run(String... args) throws Exception {
        if (plugins.findByPackageName("@demo/hello-plugin").isPresent()) {
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
    }
}
