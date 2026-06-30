package com.agentpluginhub.common;

import com.agentpluginhub.publish.DuplicatePublishException;
import com.agentpluginhub.publish.ValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    // 未知包/产物 → 404,沿用 npm 习惯的 JSON 错误体
    @ExceptionHandler({PackageNotFoundException.class, ArtifactNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Not found"));
    }

    // 发布校验失败 → 422 + actionable 反馈(code/message)
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ValidationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
    }

    // 不可变发布冲突 → 409
    @ExceptionHandler(DuplicatePublishException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicatePublishException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // 非法状态跃迁 / 并发审批冲突 / 唯一约束冲突(并发双发布撞 uk_version_plugin_ver) → 409
    @ExceptionHandler({com.agentpluginhub.review.IllegalTransitionException.class,
            org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> handleConflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    // submission 不存在 → 404
    @ExceptionHandler(com.agentpluginhub.review.SubmissionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSubmissionMissing(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }
}
