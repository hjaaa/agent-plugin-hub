package com.agentpluginhub.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginVersionRepository extends JpaRepository<PluginVersion, Long> {
    List<PluginVersion> findByPluginIdAndStatus(Long pluginId, String status);
    Optional<PluginVersion> findByPluginIdAndVersion(Long pluginId, String version);
    boolean existsByPluginIdAndVersionAndStatus(Long pluginId, String version, String status);
}
