package dev.challenge.purchase.infrastructure.persistence.request;

import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.LineItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_item")
public class RequestItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "version_id", nullable = false, updatable = false)
	private RequestVersionEntity version;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false, length = 300)
	private String description;

	@Column(nullable = false)
	private int quantity;

	@Column(nullable = false, precision = 19, scale = 2)
	private Money unitPrice;

	@Column(nullable = false, precision = 19, scale = 2)
	private Money lineTotal;

	protected RequestItemEntity() {
	}

	RequestItemEntity(RequestVersionEntity version, int position, LineItem item) {
		this.version = version;
		this.position = position;
		this.description = item.description();
		this.quantity = item.quantity();
		this.unitPrice = item.unitPrice();
		this.lineTotal = item.lineTotal();
	}

	public LineItem toLineItem() {
		return new LineItem(description, quantity, unitPrice);
	}

	public Long getId() {
		return id;
	}

	public int getPosition() {
		return position;
	}

	public String getDescription() {
		return description;
	}

	public int getQuantity() {
		return quantity;
	}

	public Money getUnitPrice() {
		return unitPrice;
	}

	public Money getLineTotal() {
		return lineTotal;
	}

}
