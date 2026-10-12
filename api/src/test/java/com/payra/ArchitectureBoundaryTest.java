package com.payra;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;




/**
 * Enforces the module boundaries from {@code docs/architecture.md}:
 * every module is a direct subpackage of {@code com.payra}, its public surface is
 * {@code .api}, its implementation is {@code .internal}, and other modules may only
 * use the public surface.
 * 
 * <p>The module list is derived from the compiled code itself, so a new module becomes
 * protected the moment its first class appears - the rules cannot drift out of date.
 * That is also why this uses the ArchUnit core API from a plain JUnit test instead of
 * the {@code @ArchTest} fields: those only support a single static rule, not rules
 * generated per module at runtime.
 */
class ArchitectureBoundaryTest {
    private static final String ROOT = "com.payra";

    /**
     * Imported once: every rule below evaluates against the same bytecode snapshot.
     *
     * <p>Boot 4 compiles synthetic {@code *__BeanFactoryRegistrations} /
     * {@code *__BeanDefinitions} classes (framework plumbing) — excluded so the
     * rules judge only the code humans write.
     */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(location -> !location.contains("__"))
            .importPackages(ROOT);
    /** Every first-level package under {@code com.payra} that contains classes, e.g. "platform". */
    private static final Set<String> MODULES = CLASSES.stream()
            .map(JavaClass::getPackageName)
            .filter(pkg -> pkg.startsWith(ROOT+"."))
            .map(pkg -> pkg.substring(ROOT.length() + 1).split("\\.", 2)[0])
            .collect(Collectors.toCollection(TreeSet::new));

    @Test 
    void internals_are_private_to_their_module() {
        MODULES.forEach(module -> noClasses()
                .that().resideOutsideOfPackage(ROOT + "." + module + "..")
                .should().accessClassesThat().resideInAPackage(ROOT + "." + module + ".internal..")
                .as("module '" + module + "': .internal types must not be accessed from outside the module")
                .check(CLASSES));
    }

    @Test
    void api_must_not_depend_on_internal() {
        MODULES.forEach(module -> noClasses()
                .that().resideInAPackage(ROOT + "." + module + ".api..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + "." + module + ".internal..")
                .as("module '" + module + "': .api must not depend on .internal, or the public surface leaks implementation")
                // No module has an .api package yet; emptiness is legitimate until one appears.
                .allowEmptyShould(true)
                .check(CLASSES));
    }

    @Test
    void modules_are_free_of_cycles() {
        slices().matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .as("modules must not form dependency cycles")
                .check(CLASSES);
    }
}