package dev.challenge.purchase.infrastructure.web.auth;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.challenge.purchase.application.auth.AuthService;
import dev.challenge.purchase.application.user.UserView;
import dev.challenge.purchase.domain.user.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@SecurityRequirements
	@Operation(summary = "Cadastro público", description = "Cria sempre um usuário SOLICITANTE. Outros papéis são criados por um ADMIN.")
	public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest body) {
		var user = authService.register(body.name(), body.email(), body.password(), body.costCenterId());
		return ResponseEntity.created(URI.create("/users/" + user.getId())).body(UserView.of(user));
	}

	@PostMapping("/login")
	@SecurityRequirements
	@Operation(summary = "Autentica e devolve um JWT de curta duração")
	public LoginResponse login(@Valid @RequestBody LoginRequest body) {
		var result = authService.login(body.email(), body.password());
		return new LoginResponse(result.token().value(), "Bearer", result.token().expiresAt(), UserView.of(result.user()));
	}

	@GetMapping("/me")
	@Operation(summary = "Usuário logado")
	public UserView me(Actor actor) {
		return UserView.of(authService.me(actor.userId()));
	}

	public record RegisterRequest(
			@NotBlank(message = "informe o nome") @Size(max = 120, message = "o nome tem no máximo 120 caracteres") String name,
			@NotBlank(message = "informe o e-mail") @Email(message = "e-mail inválido") @Size(max = 254, message = "o e-mail tem no máximo 254 caracteres") String email,
			@NotBlank(message = "informe a senha") @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres") String password,
			@NotNull(message = "informe o centro de custo") @Positive(message = "centro de custo inválido") Long costCenterId) {
	}

	public record LoginRequest(
			@NotBlank(message = "informe o e-mail") String email,
			@NotBlank(message = "informe a senha") String password) {
	}

	public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {
	}

}
