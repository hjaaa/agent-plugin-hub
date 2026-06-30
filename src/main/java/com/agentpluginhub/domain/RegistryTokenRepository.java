package com.agentpluginhub.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistryTokenRepository extends JpaRepository<RegistryToken, Long> {
    Optional<RegistryToken> findByTokenHashAndRevokedFalse(String tokenHash);
}
