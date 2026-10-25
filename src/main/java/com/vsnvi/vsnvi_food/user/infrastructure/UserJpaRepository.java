package com.vsnvi.vsnvi_food.user.infrastructure;

import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.domain.User;
import com.vsnvi.vsnvi_food.user.domain.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface UserJpaRepository extends JpaRepository<User, UUID>, UserRepository {

    @Override
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(Role role, UserStatus status, Pageable pageable);
}
