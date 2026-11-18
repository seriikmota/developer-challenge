package dev.challenge.purchase.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The domain must stay plain Java: testable without starting a server or a database.
 */
@AnalyzeClasses(packages = "dev.challenge.purchase", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

	@ArchTest
	static final ArchRule domainDoesNotDependOnFrameworks = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..",
					"jakarta.persistence..",
					"jakarta.servlet..",
					"org.hibernate..",
					"com.fasterxml.jackson..",
					"tools.jackson..")
			.because("domain rules must be testable without HTTP, ORM or a running server");

	@ArchTest
	static final ArchRule domainDoesNotDependOnOuterLayers = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
			.because("dependencies point inwards: infrastructure -> application -> domain");

}
