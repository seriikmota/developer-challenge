package dev.challenge.purchase.infrastructure.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;

/**
 * Lets controllers declare an {@link Actor} parameter: the logged user, built from the validated JWT claims.
 */
public class CurrentActorResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return Actor.class.equals(parameter.getParameterType());
	}

	@Override
	public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new AuthenticationCredentialsNotFoundException("Autenticação necessária.");
		}
		return toActor(jwt);
	}

	static Actor toActor(Jwt jwt) {
		var costCenter = jwt.getClaim(JwtClaimNames.COST_CENTER_ID);
		var level = jwt.getClaimAsString(JwtClaimNames.APPROVER_LEVEL);
		return new Actor(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString(JwtClaimNames.ROLE)), costCenter == null ? null : ((Number) costCenter).longValue(), level == null ? null : ApproverLevel.valueOf(level));
	}

}
