package com.agentpluginhub.publish;

public record PublishResponse(Long submissionId, String packageName, String version, String state) {
}
