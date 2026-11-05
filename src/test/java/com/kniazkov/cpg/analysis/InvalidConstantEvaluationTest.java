/* Invalid operands cannot establish constant values. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InvalidConstantEvaluationTest {
    @TempDir Path directory;
    @Test void diagnosedSizeofAndInvalidDescendantsRemainUnknown() throws Exception {
        for (String source : List.of(
                "struct S { unsigned b:3; }; int f(){ S s; return sizeof(s.b); }",
                "int f(){ return false && sizeof(void); }",
                "int f(){ return true ? 0 : const_cast<int>(0); }")) {
            var expression = returned(source);
            assertTrue(ConstantEvaluator.hasSemanticErrors(expression), source);
            assertTrue(ConstantEvaluator.evaluate(expression, PlatformProfile.defaults()).isEmpty(), source);
        }
    }
    @Test void explicitBitfieldValueConversionIsStillSizeable() throws Exception {
        var expression = returned("struct S { unsigned b:3; }; int f(){ S s; return sizeof((int)s.b); }");
        assertFalse(ConstantEvaluator.hasSemanticErrors(expression));
        assertEquals(BigInteger.valueOf(4), ConstantEvaluator.evaluate(expression, PlatformProfile.defaults()).orElseThrow().number());
    }
    private Expression returned(String source) throws Exception {
        Files.writeString(directory.resolve("invalid.cpp"), source);
        var project = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        var result = new ArrayList<Expression>();
        project.walk(new NodeVisitor() { @Override public void visit(ReturnStatement statement) {
            statement.value().ifPresent(result::add);
        }});
        return result.getFirst();
    }
}
