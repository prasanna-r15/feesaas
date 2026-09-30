package com.feesaas.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/** Keeps the modular monolith honest. Rules become meaningful as modules are added. */
@AnalyzeClasses(packages = "com.feesaas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] MODULES = {
            "auth", "tenant", "user", "configuration", "customer", "fee", "payment", "membership",
            "attendance", "batch", "notification", "whatsapp", "report", "subscription", "audit", "media",
            "imports", "personal", "groupsplit", "catalog"};

    @ArchTest
    static final ArchRule shared_kernel_does_not_depend_on_modules =
            noClasses().that().resideInAPackage("com.feesaas.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(prefix(MODULES, "com.feesaas.", ".."))
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_live_in_api_packages =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("..api..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_is_free_of_web_and_http_concerns =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auth_domain_does_not_depend_on_api_or_infra =
            noClasses().that().resideInAPackage("com.feesaas.auth.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.feesaas.auth.api..", "com.feesaas.auth.infra..", "com.feesaas.auth.application..");

    private static String[] prefix(String[] names, String pre, String post) {
        return java.util.Arrays.stream(names).map(n -> pre + n + post).toArray(String[]::new);
    }
}
