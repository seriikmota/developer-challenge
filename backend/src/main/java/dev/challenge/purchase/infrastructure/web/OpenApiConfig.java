package dev.challenge.purchase.infrastructure.web;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.challenge.purchase.domain.user.Actor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI document at {@code /v3/api-docs}, UI at {@code /swagger-ui}. Use "Authorize" with the token from
 * {@code POST /auth/login}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String BEARER = "bearer-jwt";

	static {
		// the logged user is resolved from the token, it is not a request parameter
		SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor.class);
	}

	@Bean
	OpenAPI purchaseRequestsOpenApi() {
		return new OpenAPI()
				.info(new Info().title("Purchase Requests API").version("v1").description("Solicitações de compra com cadeia de aprovação, orçamento por centro de custo e histórico auditável."))
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}

}
