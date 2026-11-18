package dev.challenge.purchase.application.user;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.application.common.PageView;
import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.application.error.InvalidInputException.FieldError;
import dev.challenge.purchase.application.error.NotFoundException;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserRepository;

/**
 * User management by an ADMIN (the role check is done by the web layer before calling).
 */
@Service
public class UserAdminService {

	private final UserRepository users;

	private final CostCenterRepository costCenters;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public UserAdminService(UserRepository users, CostCenterRepository costCenters, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.costCenters = costCenters;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageView<UserView> list(int page, int size) {
		return PageView.of(users.findAll(PageRequest.of(page, size, Sort.by("name", "id"))), UserView::of);
	}

	@Transactional
	public UserView create(String name, String email, String password, Role role, ApproverLevel approverLevel, Long costCenterId) {
		var normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
		validateProfile(role, approverLevel, costCenterId);
		if (users.existsByEmailIgnoreCase(normalizedEmail)) {
			throw emailTaken();
		}
		try {
			return UserView.of(users.saveAndFlush(new UserEntity(name.strip(), normalizedEmail, passwordEncoder.encode(password), role, approverLevel, costCenterId, clock.instant())));
		} catch (DataIntegrityViolationException e) {
			throw emailTaken();
		}
	}

	/**
	 * Partial update: {@code null} keeps the current value. A role other than APROVADOR clears the approver level.
	 */
	@Transactional
	public UserView update(Long id, String name, Role role, ApproverLevel approverLevel, Long costCenterId) {
		var user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuário " + id + " não encontrado."));
		var newRole = role != null ? role : user.getRole();
		if (approverLevel != null && newRole != Role.APROVADOR) {
			throw InvalidInputException.of("approverLevel", "somente APROVADOR tem nível de aprovação");
		}
		var newLevel = newRole != Role.APROVADOR ? null : approverLevel != null ? approverLevel : user.getApproverLevel();
		var newCostCenter = costCenterId != null ? costCenterId : user.getCostCenterId();
		validateProfile(newRole, newLevel, newCostCenter);
		user.update(name != null ? name.strip() : user.getName(), newRole, newLevel, newCostCenter, clock.instant());
		return UserView.of(users.saveAndFlush(user));
	}

	private void validateProfile(Role role, ApproverLevel approverLevel, Long costCenterId) {
		List<FieldError> errors = new ArrayList<>();
		if (role == Role.APROVADOR && approverLevel == null) {
			errors.add(new FieldError("approverLevel", "APROVADOR precisa de nível de aprovação (NIVEL_1 ou NIVEL_2)"));
		}
		if (role != Role.APROVADOR && approverLevel != null) {
			errors.add(new FieldError("approverLevel", "somente APROVADOR tem nível de aprovação"));
		}
		if (role != Role.ADMIN && costCenterId == null) {
			errors.add(new FieldError("costCenterId", "somente ADMIN pode ficar sem centro de custo"));
		}
		if (costCenterId != null && !costCenters.existsById(costCenterId)) {
			errors.add(new FieldError("costCenterId", "centro de custo " + costCenterId + " não existe"));
		}
		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
	}

	private static ConflictException emailTaken() {
		return new ConflictException("EMAIL_ALREADY_REGISTERED", "Já existe um usuário com este e-mail.");
	}

}
