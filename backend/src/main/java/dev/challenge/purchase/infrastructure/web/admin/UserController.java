package dev.challenge.purchase.infrastructure.web.admin;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.challenge.purchase.application.common.PageView;
import dev.challenge.purchase.application.user.UserAdminService;
import dev.challenge.purchase.application.user.UserView;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuários (ADMIN)")
public class UserController {

	private final UserAdminService users;

	public UserController(UserAdminService users) {
		this.users = users;
	}

	@GetMapping
	@Operation(summary = "Lista usuários")
	public PageView<UserView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		var paging = Paging.of(page, size);
		return users.list(paging.page(), paging.size());
	}

	@PostMapping
	@Operation(summary = "Cria usuário de qualquer papel e nível")
	public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest body) {
		var user = users.create(body.name(), body.email(), body.password(), body.role(), body.approverLevel(), body.costCenterId());
		return ResponseEntity.created(URI.create("/users/" + user.id())).body(user);
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Edita nome, papel, nível ou centro de custo", description = "Campos ausentes mantêm o valor atual. Um papel diferente de APROVADOR remove o nível.")
	public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
		return users.update(id, body.name(), body.role(), body.approverLevel(), body.costCenterId());
	}

	public record CreateUserRequest(
			@NotBlank(message = "informe o nome") @Size(max = 120, message = "o nome tem no máximo 120 caracteres") String name,
			@NotBlank(message = "informe o e-mail") @Email(message = "e-mail inválido") @Size(max = 254, message = "o e-mail tem no máximo 254 caracteres") String email,
			@NotBlank(message = "informe a senha") @Size(min = 8, max = 72, message = "a senha deve ter entre 8 e 72 caracteres") String password,
			@NotNull(message = "informe o papel") Role role,
			ApproverLevel approverLevel,
			@Positive(message = "centro de custo inválido") Long costCenterId) {
	}

	public record UpdateUserRequest(
			@Size(min = 1, max = 120, message = "o nome deve ter entre 1 e 120 caracteres") String name,
			Role role,
			ApproverLevel approverLevel,
			@Positive(message = "centro de custo inválido") Long costCenterId) {
	}

}
