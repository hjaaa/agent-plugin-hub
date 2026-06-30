package com.agentpluginhub.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistTagRepository extends JpaRepository<DistTag, Long> {
    List<DistTag> findByPluginId(Long pluginId);
    Optional<DistTag> findByPluginIdAndTag(Long pluginId, String tag);
    List<DistTag> findByPluginIdIn(Collection<Long> pluginIds);
}
