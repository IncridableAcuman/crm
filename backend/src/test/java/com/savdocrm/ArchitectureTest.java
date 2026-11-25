package com.savdocrm;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.savdocrm", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_should_not_depend_on_repositories =
            noClasses().that().areAnnotatedWith(RestController.class)
                    .should().dependOnClassesThat().areAnnotatedWith(Repository.class)
                    .because("Controllers must interact with Application Services rather than directly accessing Repositories");

    @ArchTest
    static final ArchRule controllers_should_only_reside_in_web_packages =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("..web..")
                    .orShould().resideInAPackage("..health..")
                    .because("Controllers should be located in web or health packages");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_web =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..web..")
                    .because("Domain model must not have dependencies on web layer");

    @ArchTest
    static final ArchRule catalog_domain_should_not_depend_on_application_infrastructure_or_web =
            noClasses().that().resideInAPackage("..modules.catalog.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.catalog.application..",
                            "..modules.catalog.infrastructure..",
                            "..modules.catalog.web..",
                            "..web..",
                            "org.springframework.."
                    )
                    .because("Catalog domain layer must stay isolated from application, infrastructure, Spring framework, and web concerns");

    @ArchTest
    static final ArchRule catalog_module_should_not_depend_on_identity_or_downstream_modules =
            noClasses().that().resideInAPackage("..modules.catalog..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..modules.identity..",
                            "..modules.inventory..",
                            "..modules.sales..",
                            "..modules.purchases..",
                            "..modules.parties..",
                            "..modules.finance..",
                            "..modules.analytics..",
                            "..modules.tenancy.web.."
                    )
                    .because("Catalog bounded context may only depend on shared and tenancy core, never identity, downstream modules, or other modules' web controllers");

    @ArchTest
    static final ArchRule catalog_core_layers_should_not_depend_on_web =
            noClasses().that().resideInAnyPackage(
                            "..modules.catalog.domain..",
                            "..modules.catalog.application..",
                            "..modules.catalog.infrastructure.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("..web..", "org.springframework.web..")
                    .because("Catalog domain, application, and infrastructure layers must remain decoupled from web/HTTP concerns");

    @ArchTest
    static final ArchRule other_modules_should_not_access_catalog_repositories_directly =
            noClasses().that().resideOutsideOfPackage("..modules.catalog..")
                    .should().dependOnClassesThat().resideInAPackage("..modules.catalog.infrastructure..")
                    .because("Other bounded contexts must interact with Catalog through public application/facade contracts rather than Catalog repositories");

    @ArchTest
    static final ArchRule identity_should_not_depend_on_catalog =
            noClasses().that().resideInAPackage("..modules.identity..")
                    .should().dependOnClassesThat().resideInAPackage("..modules.catalog..")
                    .because("Identity should communicate tenant creation via TenantCreatedEvent rather than directly depending on Catalog");

    @ArchTest
    static final ArchRule modules_should_have_no_circular_dependencies =
            SlicesRuleDefinition.slices()
                    .matching("com.savdocrm.modules.(*)..")
                    .should().beFreeOfCycles()
                    .because("Bounded contexts must form an acyclic dependency graph");
}
