package com.agentpluginhub.review;

import com.agentpluginhub.domain.SubmissionState;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 管理员审核台。Task 8 接入安全后加 @PreAuthorize("hasRole('ADMIN')")
// 并恢复 @AuthenticationPrincipal OidcUser principal 参数(当前 Spring Security 尚未引入)。
@RestController
public class ReviewController {

    private final ReviewService review;

    public ReviewController(ReviewService review) {
        this.review = review;
    }

    @GetMapping("/api/submissions")
    public List<SubmissionView> list(@RequestParam(value = "state", required = false) SubmissionState state) {
        return review.listSubmissions(state).stream().map(SubmissionView::of).toList();
    }

    @PostMapping("/api/submissions/{id}/review")
    public void startReview(@PathVariable Long id) {
        review.startReview(id, "admin");
    }

    @PostMapping("/api/submissions/{id}/approve")
    public void approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        review.approve(id, "admin", note(body));
    }

    @PostMapping("/api/submissions/{id}/reject")
    public void reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        review.reject(id, "admin", note(body));
    }

    private static String note(Map<String, String> body) {
        return body == null ? null : body.get("notes");
    }
}
