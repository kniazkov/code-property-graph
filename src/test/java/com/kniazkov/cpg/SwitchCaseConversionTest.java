/* Printed C++03 6.4.2/2: case identity follows the promoted condition type. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwitchCaseConversionTest {
    private Project resolve(String source, PlatformProfile profile) {
        var path = Path.of("switch.cpp").toAbsolutePath();
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build());
    }
    private Project resolve(String source) { return resolve(source, PlatformProfile.defaults()); }
    private long duplicates(Project project) {
        return project.semanticDiagnostics().stream().filter(d -> d.code().equals("duplicate-case")).count();
    }
    private String selection(String type, String first, String second) {
        return "void f(" + type + " x){switch(x){case " + first + ":break;case " + second + ":break;}}";
    }
    @Test void unsignedCasesAreComparedAfterTargetWidthConversion() {
        assertEquals(1, duplicates(resolve(selection("unsigned int", "-1", "4294967295U"))));
        assertEquals(1, duplicates(resolve(selection("unsigned long", "-1", "18446744073709551615UL"))));
        assertEquals(1, duplicates(resolve(selection("unsigned int", "0", "4294967296UL"))));
        assertEquals(0, duplicates(resolve(selection("unsigned long", "-1", "4294967295U"))));
    }
    @Test void targetProfileRatherThanHostWidthsControlsCollisions() {
        var narrow = PlatformProfile.defaults().toBuilder().sizeBits("int", 16).alignmentBits("int", 16)
                .wcharType("long").sizeBits("wchar_t", 64).alignmentBits("wchar_t", 64).build();
        String source = selection("unsigned int", "-1", "65535U");
        assertEquals(0, duplicates(resolve(source)));
        assertEquals(1, duplicates(resolve(source, narrow)));
        assertEquals(1, duplicates(resolve(selection("unsigned short", "-1", "65535U"), narrow)));
    }
    @Test void narrowIntegralAndBoolConditionsArePromotedBeforeCaseConversion() {
        for (String type : List.of("unsigned char", "unsigned short", "bool"))
            assertEquals(0, duplicates(resolve(selection(type, "0", type.equals("bool") ? "2" : type.equals("unsigned char") ? "256" : "65536"))), type);
        assertEquals(0, duplicates(resolve(selection("unsigned short", "-1", "65535"))));
        assertEquals(1, duplicates(resolve(selection("bool", "true", "1"))));
        assertEquals(1, duplicates(resolve(selection("const unsigned int&", "-1", "4294967295U"))));
        assertEquals(1, duplicates(resolve("typedef unsigned int U;" + selection("U", "-1", "4294967295U"))));
    }
    @Test void wcharPromotionUsesProfileRangeNotItsNominalRank() {
        var profile = PlatformProfile.defaults().toBuilder().wcharType("unsigned short")
                .sizeBits("wchar_t", 16).alignmentBits("wchar_t", 16).wideExecutionEncoding("UTF-16").build();
        assertEquals(0, duplicates(resolve(selection("wchar_t", "-1", "65535"), profile)));
    }
    @Test void conditionDeclarationsAndNestedSwitchesHaveIndependentTypes() {
        assertEquals(1, duplicates(resolve("void f(){switch(unsigned int x=0){case -1:break;case 4294967295U:break;}}")));
        var project = resolve("void f(unsigned int u,long s){switch(u){case -1:switch(s){case -1:break;case 4294967295U:break;}break;case 4294967295U:break;}}");
        assertEquals(1, duplicates(project));
    }
    @Test void signedOutOfRangeConversionDoesNotInventImplementationDefinedWrapping() {
        assertEquals(0, duplicates(resolve(selection("int", "-1", "4294967295U"))));
        assertEquals(0, duplicates(resolve(selection("long", "-1", "18446744073709551615UL"))));
        assertEquals(1, duplicates(resolve(selection("int", "1", "1U"))));
    }
    @Test void unknownEnumClassAndBitfieldPromotionRemainConservative() {
        for (String source : List.of(
                "enum E{zero};" + selection("E", "-1", "4294967295U"),
                "struct C{operator unsigned int();};" + selection("C", "-1", "4294967295U"),
                "struct S{unsigned int b:3;};void f(S s){switch(s.b){case -1:break;case 4294967295U:break;}}",
                "struct S{unsigned int b:3;void f(){switch(b){case -1:break;case 4294967295U:break;}}};"))
            assertEquals(0, duplicates(resolve(source)), source);
        assertEquals(1, duplicates(resolve("enum E{zero};" + selection("E", "1", "1"))));
    }
}
