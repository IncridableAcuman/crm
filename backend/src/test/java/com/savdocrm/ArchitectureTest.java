package com.savdocrm;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
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
}
