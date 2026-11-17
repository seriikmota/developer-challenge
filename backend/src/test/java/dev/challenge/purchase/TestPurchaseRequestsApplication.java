package dev.challenge.purchase;

import org.springframework.boot.SpringApplication;

public class TestPurchaseRequestsApplication {

	public static void main(String[] args) {
		SpringApplication.from(PurchaseRequestsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
