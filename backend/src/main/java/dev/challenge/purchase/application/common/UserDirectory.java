package dev.challenge.purchase.application.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserRepository;

/**
 * Resolves user names for views (owner, approver, actor of an event) with one query per batch of ids.
 */
@Component
public class UserDirectory {

	private final UserRepository users;

	public UserDirectory(UserRepository users) {
		this.users = users;
	}

	public Map<Long, String> namesOf(Collection<Long> ids) {
		var distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
		var names = new HashMap<Long, String>();
		if (!distinct.isEmpty()) {
			users.findAllById(distinct).forEach(user -> names.put(user.getId(), user.getName()));
		}
		return names;
	}

	public String nameOf(Long id) {
		return id == null ? null : users.findById(id).map(UserEntity::getName).orElse(null);
	}

}
