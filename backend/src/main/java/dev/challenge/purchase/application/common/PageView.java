package dev.challenge.purchase.application.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Paginated response: {@code { data, page, size, totalElements, totalPages }}.
 */
public record PageView<T>(List<T> data, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageView<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageView<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}

	/**
	 * Pages a list already filtered in memory.
	 */
	public static <T> PageView<T> of(List<T> all, int page, int size) {
		var from = Math.min((long) page * size, all.size());
		var to = Math.min(from + size, all.size());
		var totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
		return new PageView<>(List.copyOf(all.subList((int) from, (int) to)), page, size, all.size(), totalPages);
	}

}
