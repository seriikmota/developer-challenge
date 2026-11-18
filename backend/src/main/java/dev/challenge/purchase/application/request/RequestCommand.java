package dev.challenge.purchase.application.request;

import java.time.LocalDate;
import java.util.List;

import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.LineItem;

/**
 * Content of a request, as created or edited by its owner. The total is always computed from the items.
 *
 * @param costCenterId {@code null} means the owner's cost center (on creation) or the current one (on edition)
 */
public record RequestCommand(String title, String description, Category category, Long costCenterId, LocalDate neededBy, List<LineItem> items) {

	public RequestCommand {
		items = List.copyOf(items);
	}

}
