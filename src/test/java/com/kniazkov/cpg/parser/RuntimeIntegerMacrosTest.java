/* Explicit per-target integer facts; no runtime values inferred from glibc. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.build.*;
import java.nio.file.*;
import java.util.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;
class RuntimeIntegerMacrosTest {
    @TempDir Path temp;
    private static final Map<String,List<String>> EXPECTED = Map.of(
            "cerrno",List.of("EDOM","ERANGE","EILSEQ"),
            "clocale",List.of("LC_ALL","LC_COLLATE","LC_CTYPE","LC_MONETARY","LC_NUMERIC","LC_TIME"),
            "csignal",List.of("SIGABRT","SIGFPE","SIGILL","SIGINT","SIGSEGV","SIGTERM"),
            "cstdio",List.of("_IOFBF","_IOLBF","_IONBF","BUFSIZ","EOF","FOPEN_MAX","FILENAME_MAX","L_tmpnam","SEEK_CUR","SEEK_END","SEEK_SET","TMP_MAX"),
            "cstdlib",List.of("EXIT_FAILURE","EXIT_SUCCESS","RAND_MAX"));
    private PlatformProfile synthetic() throws Exception { return PlatformProfile.read(Path.of("src/test/resources/runtime-integer-macros/synthetic-profile.json")); }
    private Build.TranslationUnit unit(PlatformProfile p) { return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p); }
    private CppPreprocessor.Result pp(String source,PlatformProfile p) { return CppPreprocessor.preprocess(unit(p),source); }
    private String text(CppPreprocessor.Result r) {return String.join(" ",r.tokens().stream().filter(t->!Set.of(WHITESPACE,NEWLINE,EOF,HEADER_NAME).contains(t.token().kind())).map(t->t.token().spelling()).toList());}
    @Test void exactThirtyNamesBothAliasesAndTargetValuesInConditionalsAndExpressions() throws Exception {
        assertEquals(30,EXPECTED.values().stream().mapToInt(List::size).sum());
        var names=new TreeSet<String>();EXPECTED.values().forEach(names::addAll);assertEquals(names,RuntimeIntegerMacros.names());
        for(var entry:EXPECTED.entrySet()) for(String header:List.of(entry.getKey(),entry.getKey().substring(1)+".h")) {
            var p=synthetic();StringBuilder source=new StringBuilder("#include <"+header+">\n");
            for(String name:entry.getValue()) source.append("#if ").append(name).append(" != ").append(p.runtimeIntegerMacros().get(name).value().toString()).append("\n#error wrong fact\n#endif\n").append(name).append(";\n");
            var result=pp(source.toString(),p);
            var definitions=result.definitions().stream().filter(d->names.contains(d.name())).toList();
            assertEquals(new HashSet<>(entry.getValue()),definitions.stream().map(CppPreprocessor.Definition::name).collect(java.util.stream.Collectors.toSet()));
            assertTrue(definitions.stream().allMatch(d->d.unavailableReason()==null && d.originKind().equals("library")));
            assertTrue(definitions.stream().allMatch(d->d.sourceKey().contains(p.identityHash())));
            assertEquals(Set.of(temp.resolve("main.cpp").toString()),result.sources().keySet());
        }
    }
    @Test void absentFactsNeverBecomeZeroEvenInsideIfAndDefinedOnlyReportsPresence() {
        for(var p:List.of(PlatformProfile.defaults(),PlatformProfile.builder().cRuntime("glibc").build())) {
            assertTrue(p.runtimeIntegerMacros().isEmpty());
            var result=pp("#include <cerrno>\n#if defined(EDOM)\npresent\n#endif\n",p);assertEquals("present",text(result));
            assertTrue(result.definitions().stream().filter(d->EXPECTED.get("cerrno").contains(d.name())).allMatch(d->d.unavailableReason()!=null));
            for(String use:List.of("EDOM", "#if EDOM == 0\n#endif")) {
                var error=assertThrows(ParseException.class,()->pp("#include <cerrno>\n"+use,p));
                assertEquals("unavailable-library-macro",error.code());assertTrue(error.reason().contains("runtimeIntegerMacros.EDOM"));
            }
        }
    }
    @Test void trustedDeclarationDefaultsRemainOpaqueButUserCodeAndDirectivesStillRejectMissingFacts() {
        String declaration="#include <cstdio>\nvoid f(int = EOF);\n";
        var catalogue=Parser.parseLibrary(unit(PlatformProfile.defaults()),declaration).build();
        var names=new ArrayList<com.kniazkov.cpg.model.NameExpression>();
        catalogue.walk(new com.kniazkov.cpg.model.NodeVisitor(){@Override public void visit(com.kniazkov.cpg.model.NameExpression n){names.add(n);}});
        assertTrue(names.stream().anyMatch(n->n.name().equals("EOF")));
        assertThrows(ParseException.class,()->Parser.parse(unit(PlatformProfile.defaults()),declaration));
        assertThrows(ParseException.class,()->Parser.parseLibrary(unit(PlatformProfile.defaults()),"#include <cstdio>\n#if EOF\n#endif\n"));
        var profile=PlatformProfile.builder().runtimeIntegerMacro("EOF","int","-7").build();
        var configured=Parser.parseLibrary(unit(profile),declaration).build();
        var configuredNames=new ArrayList<com.kniazkov.cpg.model.NameExpression>();
        configured.walk(new com.kniazkov.cpg.model.NodeVisitor(){@Override public void visit(com.kniazkov.cpg.model.NameExpression n){configuredNames.add(n);}});
        assertTrue(configuredNames.isEmpty());
    }
    @Test void partialFactsRepeatedIncludesAndTranslationUnitsRemainIndependent() {
        var p=PlatformProfile.builder().runtimeIntegerMacro("EDOM","int","-33").build();
        assertEquals("( - 33 )",text(pp("#include <cerrno>\n#undef EDOM\n#include <errno.h>\nEDOM",p)));
        assertEquals("EDOM",text(pp("EDOM",p)));
        assertThrows(ParseException.class,()->pp("#include <cerrno>\nERANGE",p));
        assertThrows(ParseException.class,()->pp("#include <cerrno>\nEDOM",PlatformProfile.defaults()));
    }
    @Test void actualHeadersOverrideTheTargetManifest() throws Exception {
        Files.writeString(temp.resolve("errno.h"),"#define EDOM 123\n");var base=unit(synthetic());
        var configured=new Build.TranslationUnit(base.source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(synthetic());
        assertEquals("123 ERANGE",text(CppPreprocessor.preprocess(configured,"#include <errno.h>\nEDOM ERANGE")));
    }
    @Test void canonicalOrderingRoundTripsAndIntMinimumNeverUsesAnOutOfRangePositiveLiteral() throws Exception {
        var p=synthetic();assertEquals(p,PlatformProfile.fromSettings(p.toSettings()));assertEquals(p,p.toBuilder().build());
        var b=PlatformProfile.builder().name(p.name());var keys=new ArrayList<>(p.runtimeIntegerMacros().keySet());Collections.reverse(keys);
        for(String name:keys)b.runtimeIntegerMacro(name,"int",p.runtimeIntegerMacros().get(name).value().toString());
        assertEquals(p.stableIdentity(),b.build().stableIdentity());assertEquals(p.identityHash(),b.build().identityHash());
        assertNotEquals(unit(p).variantId(),unit(PlatformProfile.defaults()).variantId());
        assertEquals("(-2147483647 - 1)",p.runtimeIntegerMacros().get("EOF").replacement(p));
        assertEquals("7",p.runtimeIntegerMacros().get("EXIT_SUCCESS").replacement(p));
        assertEquals(BigInteger.valueOf(-33),p.runtimeIntegerMacros().get("EDOM").value());
        assertThrows(UnsupportedOperationException.class,()->p.runtimeIntegerMacros().clear());
    }
    @Test void targetInt16MinimumRetainsItsIntExpressionTypeForm() {
        var p=PlatformProfile.builder().sizeBits("int",16).alignmentBits("int",16).wcharType("long")
                .sizeBits("wchar_t",64).alignmentBits("wchar_t",64).runtimeIntegerMacro("EOF","int","-32768").build();
        assertEquals("(-32767 - 1)",p.runtimeIntegerMacros().get("EOF").replacement(p));
    }
    @Test void fileLoadingRejectsDuplicateAndEscapedEquivalentKeys() throws Exception {
        String fact="{\"type\":\"int\",\"value\":\"1\"}";
        var documents=List.of(
                "{\"inherits\":\"default\",\"runtimeIntegerMacros\":{\"EDOM\":"+fact+",\"EDOM\":"+fact+"}}",
                "{\"inherits\":\"default\",\"runtimeIntegerMacros\":{\"EDOM\":{\"type\":\"int\",\"type\":\"int\",\"value\":\"1\"}}}",
                "{\"inherits\":\"default\",\"runtimeIntegerMacros\":{\"EDOM\":{\"type\":\"int\",\"value\":\"1\",\"value\":\"2\"}}}",
                "{\"inherits\":\"default\",\"runtimeIntegerMacros\":{\"EDOM\":"+fact+",\"\\u0045DOM\":"+fact+"}}",
                "{\"inherits\":\"default\",\"sizes\":{\"int\":32,\"int\":16}}"
        );
        for(String document:documents) {
            Path file=temp.resolve("duplicate.json");Files.writeString(file,document);
            assertTrue(assertThrows(IllegalArgumentException.class,()->PlatformProfile.read(file)).getMessage().contains("Duplicate JSON key"));
            Path database=temp.resolve("compile_commands.json");
            Files.writeString(database,"[{\"directory\":\".\",\"file\":\"main.cpp\",\"arguments\":[\"c++\",\"main.cpp\"],\"platformProfile\":"+document+"}]");
            assertTrue(assertThrows(IllegalArgumentException.class,()->CompilationDatabase.read(database)).getMessage().contains("Duplicate JSON key"));
        }
    }
    private void invalid(String name,String value) {assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().runtimeIntegerMacro(name,"int",value).build());}
    @Test void validationSeparatesHistoricalConstraintsFromBoundedIntPolicy() {
        for(String value:List.of("", "1.0","1e2","0x10","+1","01"," 1","(1)","2147483648","-2147483649")) invalid("EDOM",value);
        for(String name:List.of("EDOM","ERANGE","EILSEQ")) invalid(name,"0");
        invalid("SIGABRT","0");invalid("SIGTERM","-1");invalid("EOF","0");invalid("BUFSIZ","255");invalid("FOPEN_MAX","7");invalid("TMP_MAX","24");invalid("RAND_MAX","32766");invalid("FILENAME_MAX","0");invalid("L_tmpnam","0");
        invalid("CLOCKS_PER_SEC","1000");invalid("WEOF","-1");invalid("errno","1");invalid("LLONG_MAX","1");
        for(var family:List.of(List.of("EDOM","EILSEQ"),List.of("LC_ALL","LC_TIME"),List.of("SIGINT","SIGABRT"),List.of("_IOFBF","_IONBF"),List.of("SEEK_SET","SEEK_END")))
            assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().runtimeIntegerMacro(family.get(0),"int","1").runtimeIntegerMacro(family.get(1),"int","1").build());
        assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().runtimeIntegerMacro("EDOM","long","1"));
        for(Object value:List.of(1,1.0,true,List.of(1))) assertThrows(IllegalArgumentException.class,()->PlatformProfile.fromSettings(Map.of("inherits","default","runtimeIntegerMacros",Map.of("EDOM",Map.of("type","int","value",value)))));
        assertThrows(IllegalArgumentException.class,()->PlatformProfile.fromSettings(Map.of("inherits","default","runtimeIntegerMacros",Map.of("EDOM",Map.of("type","int","value","1","extra","x")))));
    }
}
