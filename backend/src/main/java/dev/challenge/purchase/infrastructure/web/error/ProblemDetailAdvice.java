package dev.challenge.purchase.infrastructure.web.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.challenge.purchase.application.error.ApplicationException;
import dev.challenge.purchase.application.error.AuthenticationFailedException;
import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.application.error.ForbiddenException;
import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.application.error.NotFoundException;
import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.infrastructure.web.RequestIdFilter;

/**
 * Turns every error into {@code application/problem+json} (RFC 9457) with {@code type}, {@code title},
 * {@code status}, {@code detail}, a machine-readable {@code code}, the {@code requestId} and, for validation,
 * {@code errors[]}.
 */
@RestControllerAdvice
public class ProblemDetailAdvice extends ResponseEntityExceptionHandler {

	static final String TYPE_BASE = "https://example.com/problems/";

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);

	private static final Map<String, String> TITLES = Map.ofEntries(
			Map.entry("VALIDATION_ERROR", "Requisição inválida"),
			Map.entry("MALFORMED_REQUEST", "Corpo da requisição ilegível"),
			Map.entry("UNAUTHORIZED", "Autenticação necessária"),
			Map.entry("INVALID_TOKEN", "Token inválido ou expirado"),
			Map.entry("INVALID_CREDENTIALS", "Credenciais inválidas"),
			Map.entry("FORBIDDEN", "Ação não permitida"),
			Map.entry("NOT_FOUND", "Recurso não encontrado"),
			Map.entry("CONCURRENT_MODIFICATION", "Conflito de concorrência"),
			Map.entry("EMAIL_ALREADY_REGISTERED", "E-mail já cadastrado"),
			Map.entry("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key reutilizada"),
			Map.entry("INVALID_TRANSITION", "Transição inválida"),
			Map.entry("NOT_EDITABLE", "Solicitação não editável"),
			Map.entry("NOT_OWNER", "Ação exclusiva do dono"),
			Map.entry("SELF_APPROVAL_FORBIDDEN", "Autoaprovação proibida"),
			Map.entry("APPROVER_NOT_ELIGIBLE", "Aprovador não elegível"),
			Map.entry("DUPLICATE_APPROVER", "Aprovador repetido"),
			Map.entry("COMMENT_REQUIRED", "Comentário obrigatório"),
			Map.entry("BUDGET_EXCEEDED", "Orçamento insuficiente"),
			Map.entry("BUDGET_BELOW_CONSUMED", "Orçamento abaixo do consumido"),
			Map.entry("INTERNAL_ERROR", "Erro interno"));

	@ExceptionHandler(DomainException.class)
	ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
		return respond(HttpStatus.UNPROCESSABLE_CONTENT, ex.code().name(), ex.getMessage());
	}

	@ExceptionHandler(ApplicationException.class)
	ResponseEntity<ProblemDetail> handleApplication(ApplicationException ex) {
		var status = switch (ex) {
			case NotFoundException e -> HttpStatus.NOT_FOUND;
			case ForbiddenException e -> HttpStatus.FORBIDDEN;
			case ConflictException e -> HttpStatus.CONFLICT;
			case InvalidInputException e -> HttpStatus.BAD_REQUEST;
			case AuthenticationFailedException e -> HttpStatus.UNAUTHORIZED;
		};
		var problem = problem(status, ex.code(), ex.getMessage());
		if (ex instanceof InvalidInputException invalid) {
			problem.setProperty("errors", invalid.errors().stream().map(error -> new FieldProblem(error.field(), error.message())).toList());
		}
		return ResponseEntity.status(status).body(problem);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
		return respond(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "O recurso foi alterado por outra requisição. Recarregue os dados e tente novamente.");
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		if (ex instanceof InvalidBearerTokenException) {
			return respond(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "O token é inválido ou expirou. Faça login novamente.");
		}
		return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticação necessária: envie o token no header Authorization: Bearer <token>.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
		return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "Seu papel não permite esta ação.");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro inesperado. Informe o requestId ao suporte.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldProblem> errors = new ArrayList<>();
		ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(new FieldProblem(error.getField(), error.getDefaultMessage())));
		ex.getBindingResult().getGlobalErrors().forEach(error -> errors.add(new FieldProblem(error.getObjectName(), error.getDefaultMessage())));
		var problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A requisição tem " + errors.size() + " campo(s) inválido(s).");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().headers(headers).body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var problem = problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "O corpo da requisição não é um JSON válido para este endpoint.");
		return ResponseEntity.badRequest().headers(headers).body(problem);
	}

	/**
	 * Framework errors (404 of unknown path, 405, 415...) keep Spring's detail and get the same extra fields.
	 */
	@Override
	protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail problem && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
			var status = HttpStatus.resolve(statusCode.value());
			enrich(problem, status == null ? "HTTP_" + statusCode.value() : status.name());
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String code, String detail) {
		return ResponseEntity.status(status).body(problem(status, code, detail));
	}

	static ProblemDetail problem(HttpStatus status, String code, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(TITLES.getOrDefault(code, status.getReasonPhrase()));
		enrich(problem, code);
		return problem;
	}

	private static void enrich(ProblemDetail problem, String code) {
		problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
		problem.setProperty("code", code);
		problem.setProperty("requestId", RequestIdFilter.currentRequestId());
	}

	record FieldProblem(String field, String message) {
	}

}
