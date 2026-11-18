package dev.challenge.purchase.infrastructure.web;

import java.util.ArrayList;

import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.application.error.InvalidInputException.FieldError;

/**
 * Validated {@code page} (from 0) and {@code size} (1 to {@value #MAX_SIZE}) query parameters.
 */
public record Paging(int page, int size) {

	public static final int MAX_SIZE = 100;

	public static Paging of(int page, int size) {
		var errors = new ArrayList<FieldError>();
		if (page < 0) {
			errors.add(new FieldError("page", "a página começa em 0"));
		}
		if (size < 1 || size > MAX_SIZE) {
			errors.add(new FieldError("size", "o tamanho da página deve ficar entre 1 e " + MAX_SIZE));
		}
		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
		return new Paging(page, size);
	}

}
