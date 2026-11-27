package com.quipux.playlist;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Dependency rules between layers. Empty sets are allowed because a layer may have no classes yet.
 */
class ArchitectureTest {

    private static final String ROOT = "com.quipux.playlist";
    private static final String FIXTURE_ROOT = ROOT + ".archfixture";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURE_ROOT);

    @Test
    void domainDependsOnlyOnItselfAndTheJdk() {
        domainRule(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationDependsOnlyOnDomainAndReactor() {
        applicationRule(ROOT).check(PRODUCTION_CLASSES);
    }

    @Test
    void infrastructureDoesNotDependOnBootstrap() {
        noClasses().that().resideInAPackage(ROOT + ".infrastructure..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".bootstrap..")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainRuleDetectsAForbiddenDependency() {
        assertThatThrownBy(() -> domainRule(FIXTURE_ROOT).check(FIXTURE_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.util.StringUtils");
    }

    @Test
    void applicationRuleDetectsAForbiddenDependency() {
        assertThatThrownBy(() -> applicationRule(FIXTURE_ROOT).check(FIXTURE_CLASSES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("org.springframework.util.ObjectUtils");
    }

    private static ArchRule domainRule(String root) {
        return classes().that().resideInAPackage(root + ".domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(root + ".domain..", "java..")
                .allowEmptyShould(true);
    }

    private static ArchRule applicationRule(String root) {
        return classes().that().resideInAPackage(root + ".application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        root + ".application..", root + ".domain..",
                        "java..", "reactor..", "org.reactivestreams..")
                .allowEmptyShould(true);
    }
}
