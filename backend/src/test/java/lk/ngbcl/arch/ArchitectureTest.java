package lk.ngbcl.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Guard-rails that keep the modular monolith honest and public APIs private-data-free. */
@AnalyzeClasses(packages = "lk.ngbcl", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** PUB-008 / NFR-014: public response DTOs may never carry contact details or internal notes. */
    @ArchTest
    static final ArchRule publicDtosHaveNoPrivateFields = fields()
            .that().areDeclaredInClassesThat().haveSimpleNameStartingWith("Public")
            .should().notHaveName("email").andShould().notHaveName("phone").andShould().notHaveName("address")
            .andShould().notHaveName("adminRemarks").andShould().notHaveName("remarks")
            .andShould().notHaveName("emergencyContact").andShould().notHaveName("dateOfBirth")
            .allowEmptyShould(true);

    /** Other modules use identity through its services, never its entities or repositories. */
    @ArchTest
    static final ArchRule identityInternalsStayInside = noClasses()
            .that().resideInAnyPackage("lk.ngbcl.tournament..", "lk.ngbcl.audit..", "lk.ngbcl.realtime..")
            .should().dependOnClassesThat().resideInAPackage("lk.ngbcl.identity.domain..")
            .allowEmptyShould(true);
}
