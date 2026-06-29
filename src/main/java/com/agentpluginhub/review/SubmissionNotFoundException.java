package com.agentpluginhub.review;

public class SubmissionNotFoundException extends RuntimeException {

    public SubmissionNotFoundException(Long id) {
        super("submission not found: " + id);
    }
}
