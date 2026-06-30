package com.agentpluginhub.versions;

import java.time.Instant;

// 单个已发布版本的治理读视图
public record VersionDetail(String version, String status, Instant publishedAt,
        String uploadedBy, long sizeBytes) {
}
