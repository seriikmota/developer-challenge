package dev.challenge.purchase.application.common;

import org.slf4j.MDC;

/**
 * Id of the HTTP request being handled ({@code X-Request-Id}), kept in the logging MDC by the web layer.
 */
public final class Correlation {

	public static final String MDC_KEY = "requestId";

	private Correlation() {
	}

	public static String currentId() {
		return MDC.get(MDC_KEY);
	}

}
