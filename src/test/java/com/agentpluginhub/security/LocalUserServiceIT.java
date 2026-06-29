package com.agentpluginhub.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentpluginhub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.storage.type=local",
        "app.security.bootstrap-admin-subject=boss-sub"})
class LocalUserServiceIT extends AbstractIntegrationTest {

    @Autowired
    LocalUserService users;

    @Test
    void bootstrap_subject_gets_admin_on_first_login() {
        assertThat(users.upsertAndLoadRoles("boss-sub", "boss@x.com")).contains("ADMIN");
    }

    @Test
    void normal_user_gets_author_by_default() {
        assertThat(users.upsertAndLoadRoles("normal-sub", "n@x.com")).containsExactly("AUTHOR");
    }
}
