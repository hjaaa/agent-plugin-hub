package com.agentpluginhub.review;

import com.agentpluginhub.domain.SubmissionState;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 管理员审核台;所有端点需 ADMIN 角色。
@RestController
public class ReviewController {

    private final ReviewService review;

    public ReviewController(ReviewService review) {
        this.review = review;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/submissions")
    public List<SubmissionView> list(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(value = "state", required = false) SubmissionState state) {
        return review.listSubmissions(state).stream().map(SubmissionView::of).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/submissions/{id}/review")
    public void startReview(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable Long id) {
        String reviewer = principal != null ? principal.getSubject() : "admin";
        review.startReview(id, reviewer);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/submissions/{id}/approve")
    public void approve(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reviewer = principal != null ? principal.getSubject() : "admin";
        review.approve(id, reviewer, note(body));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/submissions/{id}/reject")
    public void reject(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reviewer = principal != null ? principal.getSubject() : "admin";
        review.reject(id, reviewer, note(body));
    }

    private static String note(Map<String, String> body) {
        return body == null ? null : body.get("notes");
    }
}
