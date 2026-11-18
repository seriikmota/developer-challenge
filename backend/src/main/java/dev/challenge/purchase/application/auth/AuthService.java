package dev.challenge.purchase.application.auth;

import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.application.error.AuthenticationFailedException;
import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserRepository;

/**
 * Public registration, login and the logged user's data.
 */
@Service
public class AuthService {

	private static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos.";

	private final UserRepository users;

	private final CostCenterRepository costCenters;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenIssuer tokenIssuer;

	private final Clock clock;

	/** Compared against when the e-mail is unknown, so a login takes the same time whether the user exists or not. */
	private final String dummyHash;

	public AuthService(UserRepository users, CostCenterRepository costCenters, PasswordEncoder passwordEncoder, AccessTokenIssuer tokenIssuer, Clock clock) {
		this.users = users;
		this.costCenters = costCenters;
		this.passwordEncoder = passwordEncoder;
		this.tokenIssuer = tokenIssuer;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	/**
	 * Public registration: always creates a SOLICITANTE. Other roles are created by an ADMIN.
	 */
	@Transactional
	public UserEntity register(String name, String email, String password, Long costCenterId) {
		var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
		if (users.existsByEmailIgnoreCase(normalizedEmail)) {
			throw emailTaken();
		}
		if (!costCenters.existsById(costCenterId)) {
			throw InvalidInputException.of("costCenterId", "Centro de custo " + costCenterId + " não existe.");
		}
		try {
			return users.saveAndFlush(new UserEntity(name.strip(), normalizedEmail, passwordEncoder.encode(password), Role.SOLICITANTE, null, costCenterId, clock.instant()));
		} catch (DataIntegrityViolationException e) {
			// two registrations with the same e-mail at the same time: the unique index decides
			throw emailTaken();
		}
	}

	@Transactional(readOnly = true)
	public LoginResult login(String email, String password) {
		var user = users.findByEmailIgnoreCase(email.strip());
		var hash = user.map(UserEntity::getPasswordHash).orElse(dummyHash);
		var matches = passwordEncoder.matches(password, hash);
		if (user.isEmpty() || !matches) {
			throw new AuthenticationFailedException("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
		}
		return new LoginResult(tokenIssuer.issue(user.get()), user.get());
	}

	@Transactional(readOnly = true)
	public UserEntity me(Long userId) {
		return users.findById(userId).orElseThrow(() -> new AuthenticationFailedException("UNAUTHORIZED", "O usuário do token não existe mais. Faça login novamente."));
	}

	private static ConflictException emailTaken() {
		return new ConflictException("EMAIL_ALREADY_REGISTERED", "Já existe um usuário com este e-mail.");
	}

	public record LoginResult(AccessToken token, UserEntity user) {
	}

}
