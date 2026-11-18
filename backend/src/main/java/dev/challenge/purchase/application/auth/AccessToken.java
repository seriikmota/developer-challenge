package dev.challenge.purchase.application.auth;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {

}
