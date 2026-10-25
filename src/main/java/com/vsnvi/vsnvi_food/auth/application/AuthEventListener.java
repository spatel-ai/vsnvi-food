package com.vsnvi.vsnvi_food.auth.application;

import com.vsnvi.vsnvi_food.user.api.UserBlocked;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AuthEventListener {

    private final AuthService authService;

    /**
     * Revokes every refresh token of a blocked user; their access tokens expire within 15 minutes. Idempotent: a
     * redelivered event finds nothing left to revoke. Closing STOMP sessions comes with the WebSocket work.
     */
    @ApplicationModuleListener
    void on(UserBlocked event) {
        int revoked = authService.revokeAllTokensOf(event.userId());
        log.info("User {} blocked: revoked {} refresh token(s)", event.userId(), revoked);
    }
}
