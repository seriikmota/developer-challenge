package dev.challenge.purchase.application.error;

import java.util.List;

/**
 * Input that passed bean validation but is still invalid, e.g. a reference to a cost center that does not exist (400).
 */
public final class InvalidInputException extends ApplicationException {

	private final List<FieldError> errors;

	public InvalidInputException(List<FieldError> errors) {
		super("VALIDATION_ERROR", "A requisição tem campos inválidos.");
		this.errors = List.copyOf(errors);
	}

	public static InvalidInputException of(String field, String message) {
		return new InvalidInputException(List.of(new FieldError(field, message)));
	}

	public List<FieldError> errors() {
		return errors;
	}

	public record FieldError(String field, String message) {
	}

}
