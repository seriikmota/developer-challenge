package dev.challenge.purchase.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.challenge.purchase.domain.approval.ApprovalChainFactory;
import dev.challenge.purchase.domain.approval.ApprovalEligibilityPolicy;
import dev.challenge.purchase.domain.request.RequestActionPolicy;
import dev.challenge.purchase.domain.request.RequestStateMachine;

/**
 * The domain classes are plain Java (no annotations); this is where they become Spring beans.
 */
@Configuration(proxyBeanMethods = false)
public class DomainConfig {

	@Bean
	RequestStateMachine requestStateMachine() {
		return new RequestStateMachine();
	}

	@Bean
	ApprovalChainFactory approvalChainFactory() {
		return new ApprovalChainFactory();
	}

	@Bean
	ApprovalEligibilityPolicy approvalEligibilityPolicy() {
		return new ApprovalEligibilityPolicy();
	}

	@Bean
	RequestActionPolicy requestActionPolicy(RequestStateMachine stateMachine, ApprovalEligibilityPolicy approvalPolicy) {
		return new RequestActionPolicy(stateMachine, approvalPolicy);
	}

}
