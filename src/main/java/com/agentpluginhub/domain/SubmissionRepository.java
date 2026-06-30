package com.agentpluginhub.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByState(SubmissionState state);
}
