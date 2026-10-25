package com.vsnvi.vsnvi_food.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository {

    Credential save(Credential credential);

    Optional<Credential> findByUserId(UUID userId);
}
