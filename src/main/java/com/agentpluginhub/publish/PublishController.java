package com.agentpluginhub.publish;

import com.agentpluginhub.domain.Submission;
import com.agentpluginhub.domain.SubmissionRepository;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 作者上传入口。Task 8 接入安全后加 @PreAuthorize("hasRole('AUTHOR')")
// 并改用 @AuthenticationPrincipal OidcUser 获取真实身份;
// 当前阶段 submitter 统一回退 "anonymous"。
@RestController
@RequestMapping("/api/plugins")
public class PublishController {

    private final PublishingService publishing;
    private final SubmissionRepository submissions;

    public PublishController(PublishingService publishing, SubmissionRepository submissions) {
        this.publishing = publishing;
        this.submissions = submissions;
    }

    @PostMapping
    public ResponseEntity<PublishResponse> publish(
            @RequestParam("file") MultipartFile file) throws IOException {
        // Task 8 接入安全后替换为 OidcUser.getSubject()
        String submitter = "anonymous";
        Long id = publishing.publish(file.getBytes(), submitter);
        Submission s = submissions.findById(id).orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new PublishResponse(id, s.getPackageName(), s.getVersion(), s.getState().name()));
    }
}
