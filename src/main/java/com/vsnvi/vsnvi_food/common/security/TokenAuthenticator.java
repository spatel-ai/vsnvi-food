package com.vsnvi.vsnvi_food.common.security;

/**
 * SPI that turns a bearer access token into a principal. Implemented by {@code auth} with jjwt and used by both the HTTP
 * filter chain and STOMP {@code CONNECT} (ADR-006).
 */
public interface TokenAuthenticator {

    /**
     * @throws InvalidTokenException if the token is malformed, badly signed, from the wrong issuer or expired
     */
    AuthenticatedUser authenticate(String token);
}
