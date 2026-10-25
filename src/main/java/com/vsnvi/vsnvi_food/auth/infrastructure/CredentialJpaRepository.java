package com.vsnvi.vsnvi_food.auth.infrastructure;

import com.vsnvi.vsnvi_food.auth.domain.Credential;
import com.vsnvi.vsnvi_food.auth.domain.CredentialRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CredentialJpaRepository extends JpaRepository<Credential, UUID>, CredentialRepository {}
