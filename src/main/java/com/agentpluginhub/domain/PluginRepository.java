package com.agentpluginhub.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginRepository extends JpaRepository<Plugin, Long> {
    Optional<Plugin> findByPackageName(String packageName);
}
