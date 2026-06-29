package com.agentpluginhub.review;

import com.agentpluginhub.domain.Submission;

// 审核台读模型(避免直接暴露实体)
public record SubmissionView(Long id, String packageName, String version, String pluginName,
        String state, String submitter, String reviewer) {

    public static SubmissionView of(Submission s) {
        return new SubmissionView(s.getId(), s.getPackageName(), s.getVersion(), s.getPluginName(),
                s.getState().name(), s.getSubmitter(), s.getReviewer());
    }
}
