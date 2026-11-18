package dev.challenge.purchase.application.auth;

import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;

/**
 * Issues the access token returned by the login. Implemented by the security infrastructure (JWT).
 */
public interface AccessTokenIssuer {

	AccessToken issue(UserEntity user);

}
