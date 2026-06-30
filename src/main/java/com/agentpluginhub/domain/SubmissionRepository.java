package com.agentpluginhub.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByState(SubmissionState state);

    // 统计仍引用同一 pending key 且处于指定状态的 submission 数量(afterCommit 删 blob 前做引用检查)
    long countByTarballRefAndStateIn(String tarballRef, Collection<SubmissionState> states);
}
