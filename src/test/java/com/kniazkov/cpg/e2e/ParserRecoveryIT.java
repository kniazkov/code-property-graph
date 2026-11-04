/* Code Property Graph: usable diagnostic exports from damaged C++03 inputs. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserRecoveryIT {
    @TempDir Path temp;
    @Test void stoppedTranslationUnitDoesNotHideItsValidNeighborOrDiagnosticExports() throws Exception {
        ParserRecoveryAcceptance.verify(Path.of("src/test/resources/cpp03/parser-recovery"), temp);
    }
    @Test void missingInitializerHasZeroLengthErrorAndRecoveryContinues() throws Exception {
        ParserRecoveryAcceptance.missingInitializer(temp);
    }
    @Test void activeCompilerExtensionHasPreciseDiagnosticAndInactiveOneIsIgnored() throws Exception {
        ParserRecoveryAcceptance.extension(temp);
    }
    @Test void laterStandardLiteralsAndVariadicMacrosRemainRejected() throws Exception {
        ParserRecoveryAcceptance.editionBoundaries(temp);
    }
    @Test void malformedInputsAlwaysFinishAndKeepLaterUnits() throws Exception {
        ParserRecoveryAcceptance.progress(temp);
    }
}
