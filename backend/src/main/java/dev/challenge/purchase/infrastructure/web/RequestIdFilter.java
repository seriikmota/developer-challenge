package dev.challenge.purchase.infrastructure.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.challenge.purchase.application.common.Correlation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Correlates everything done for one HTTP request: reuses the client's {@code X-Request-Id} (when it is safe to log)
 * or generates one, returns it in the response, puts it in the logging MDC and logs one line per request.
 * Runs before Spring Security, so even 401 responses carry the id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";

	public static final String MDC_KEY = Correlation.MDC_KEY;

	/** Accepts UUIDs and similar ids; anything else (too long, spaces, control characters) is replaced. */
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

	private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

	/**
	 * Id of the request being handled by the current thread, or {@code null} outside a request.
	 */
	public static String currentRequestId() {
		return MDC.get(MDC_KEY);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		var received = request.getHeader(HEADER);
		var requestId = received != null && SAFE_ID.matcher(received).matches() ? received : UUID.randomUUID().toString();
		var start = System.nanoTime();
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		} finally {
			var millis = (System.nanoTime() - start) / 1_000_000;
			log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
			MDC.remove(MDC_KEY);
		}
	}

}
