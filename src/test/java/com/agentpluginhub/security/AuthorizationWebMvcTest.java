package com.agentpluginhub.security;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import com.agentpluginhub.domain.SubmissionState;
import com.agentpluginhub.publish.PublishingService;
import com.agentpluginhub.review.ReviewService;
import com.agentpluginhub.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 只验证 URL/方法级授权规则;业务服务 mock 掉,避免依赖真实业务逻辑。
// 继承 AbstractIntegrationTest 以获取 Testcontainers DB/MinIO,供 Spring 上下文启动。
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.storage.type=local")
class AuthorizationWebMvcTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PublishingService publishing;

    @MockBean
    ReviewService review;

    @MockBean
    SubmissionRepository submissions;

    @BeforeEach
    void setup() {
        // publish() 返回 null(mocked),controller 会 findById(null);stub 返回最小 Submission 避免 500
        Submission stub = new Submission();
        stub.setPackageName("test-pkg");
        stub.setVersion("1.0.0");
        stub.setState(SubmissionState.SUBMITTED);
        Mockito.when(submissions.findById(any())).thenReturn(Optional.of(stub));
    }

    @Test
    void publish_requires_author_role() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "p.tgz", "application/octet-stream",
                new byte[]{1});
        // 无角色 → 403
        mvc.perform(multipart("/api/plugins").file(file).with(oidcLogin()))
                .andExpect(status().isForbidden());
        // 有 AUTHOR → 放行(201 Created)
        mvc.perform(multipart("/api/plugins").file(file).with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void approve_requires_admin_role() throws Exception {
        mvc.perform(post("/api/submissions/1/approve").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/submissions/1/approve").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void marketplace_json_is_public() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/marketplace.json"))
                .andExpect(status().isOk());
    }

    @Test
    void list_submissions_requires_admin_role() throws Exception {
        Mockito.when(review.listSubmissions(any())).thenReturn(List.of());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/submissions")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/submissions")
                .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void start_review_requires_admin_role() throws Exception {
        mvc.perform(post("/api/submissions/1/review").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/submissions/1/review").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void reject_requires_admin_role() throws Exception {
        mvc.perform(post("/api/submissions/1/reject").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_AUTHOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/submissions/1/reject").with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void healthz_is_public() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/healthz"))
                .andExpect(status().isOk());
    }
}
