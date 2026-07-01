package com.agentpluginhub.publish;

import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.mapper.SubmissionMapper;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 作者上传入口;需 AUTHOR 角色,由 OIDC 登录后 LocalUserService 分配。
@RestController
@RequestMapping("/api/plugins")
public class PublishController {

    private final PublishingService publishing;
    private final SubmissionMapper submissions;

    public PublishController(PublishingService publishing, SubmissionMapper submissions) {
        this.publishing = publishing;
        this.submissions = submissions;
    }

    @PreAuthorize("hasRole('AUTHOR')")
    @PostMapping
    public ResponseEntity<PublishResponse> publish(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam("file") MultipartFile file) throws IOException {
        String submitter = principal != null ? principal.getSubject() : "anonymous";
        Long id = publishing.publish(file.getBytes(), submitter);
        Submission s = submissions.selectById(id);
        if (s == null) {
            throw new IllegalStateException("Submission not found: " + id);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new PublishResponse(id, s.getPackageName(), s.getVersion(), s.getState().name()));
    }
}
