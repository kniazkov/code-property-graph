/* Actual CPG offsetof materialization checked against independent strict C++03 native facts. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.TypeSemantics;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OffsetofRuntimeIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<String> FLAGS = List.of("-std=c++03", "-pedantic-errors");
    private static final List<List<String>> HEADERS = List.of(List.of("cstddef"), List.of("stddef.h"),
            List.of("cstddef", "stddef.h"), List.of("stddef.h", "cstddef"));
    // Explicit selected contract: no production host detection and no libc/float-format dependency.
    private static final PlatformProfile PROFILE = PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647").build();
    private static final String TYPES = """
            struct Leaf {char marker; int value;};
            union Payload {char bytes[13]; double weight; Leaf leaf;};
            typedef Leaf Row[3];
            typedef int (*Callback)(int);
            struct Outer {char prefix; Payload payload; Row rows[2]; void *pointer; Callback callback; char tail;};
            typedef Outer Alias;
            namespace api {typedef Alias Record;}
            """;
    private record Scalar(String key, String spelling, String profileType) {}
    private static final List<Scalar> SCALARS = List.of(
            new Scalar("bool", "bool", "bool"), new Scalar("char", "char", "char"),
            new Scalar("signed_char", "signed char", "signed char"), new Scalar("unsigned_char", "unsigned char", "unsigned char"),
            new Scalar("short", "short", "short"), new Scalar("unsigned_short", "unsigned short", "unsigned short"),
            new Scalar("int", "int", "int"), new Scalar("unsigned_int", "unsigned int", "unsigned int"),
            new Scalar("long", "long", "long"), new Scalar("unsigned_long", "unsigned long", "unsigned long"),
            new Scalar("wchar_t", "wchar_t", "wchar_t"), new Scalar("float", "float", "float"),
            new Scalar("double", "double", "double"), new Scalar("long_double", "long double", "long double"),
            new Scalar("object_pointer", "void *", "pointer"), new Scalar("function_pointer", "Callback", "pointer"));

    @Test void realBuiltinSurvivesApiCliCopyEditsReparseGreenAndFolded() throws Exception {
        assertEquals(probe("g++"), probe("clang++"), "Fresh independent compilers must agree on the selected ABI facts");
        assertEquals("unknown", PROFILE.cRuntime());
        for (var headers : HEADERS) {
            Path work = Files.createTempDirectory(temp, "offsetof-flow-");
            var cli = new CliSandbox(work).source("main.cpp", program(headers));
            var project = Cpg.read(cli.input, options(PROFILE, Cpg.Stage.RESOLVED));
            verifyGraph(project, headers.size());
            var copy = project.edit().build();
            verifyGraph(copy, headers.size());
            verifyGraph(NameResolver.resolve(copy), headers.size());
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--platform-profile", settings.toString())
                    .expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            Path copied = writeSource(work, "copied", Cpg.sources(copy, Language.CPP).get("main.cpp"));
            for (Path source : List.of(cli.input.resolve("main.cpp"), copied, run.work().resolve("generated/main.cpp")))
                verifyGenerated(source, headers.size(), false);
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(PROFILE, stage));
                verifyGraph(transformed, headers.size());
                verifyGraph(transformed.edit().build(), headers.size());
                Path source = writeSource(work, stage.name().toLowerCase(Locale.ROOT),
                        Cpg.sources(transformed, Language.CPP).get("main.cpp"));
                verifyGenerated(source, headers.size(), false);
                if (stage == Cpg.Stage.FOLDED) {
                    var foldedEditor = new ProjectEditor(transformed);
                    foldedEditor.replace(variable(transformed, "value").type().orElseThrow(),
                            NamedType.create().setName("long").build());
                    var foldedEdited = NameResolver.resolve(foldedEditor.build());
                    verifyGraph(foldedEdited, headers.size());
                    verifyGenerated(writeSource(work, "folded-edited", Cpg.sources(foldedEdited, Language.CPP).get("main.cpp")),
                            headers.size(), true);
                }
            }
            // A layout-changing edit outside macro syntax must change native values and all dependent ICEs.
            var editor = new ProjectEditor(project);
            editor.replace(variable(project, "value").type().orElseThrow(), NamedType.create().setName("long").build());
            var edited = NameResolver.resolve(editor.build());
            verifyGraph(edited, headers.size());
            verifyGraph(edited.edit().build(), headers.size());
            Path source = writeSource(work, "edited", Cpg.sources(edited, Language.CPP).get("main.cpp"));
            verifyGenerated(source, headers.size(), true);
        }
    }

    @Test void unavailableCapabilityRetainsApiCliErrorJsonAndUnusedRequiredMacroPresence() throws Exception {
        var empty = PlatformProfile.fromSettings(Map.of("inherits", "default", "offsetof", Map.of()));
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().cRuntime("glibc").build(), empty)) {
            assertTrue(profile.offsetof().isEmpty());
            for (String header : List.of("cstddef", "stddef.h")) {
                Path work = Files.createTempDirectory(temp, "offsetof-unavailable-");
                String include = "#include <" + header + ">\n";
                var cli = new CliSandbox(work).source("main.cpp", include
                        + "#if !defined(offsetof)\n#error missing required macro\n#endif\nint main(){return 0;}\n");
                var unused = Cpg.read(cli.input, options(profile, Cpg.Stage.RESOLVED));
                healthy(unused);
                healthy(unused.edit().build());
                var definition = unused.files().getFirst().provenance().orElseThrow().unit().definitions().stream()
                        .filter(d -> d.name().equals("offsetof")).findFirst().orElseThrow();
                assertTrue(definition.functionLike());
                assertNotNull(definition.unavailableReason());
                Path settings = work.resolve("profile.json");
                Files.writeString(settings, Json.serialize(profile.toSettings()));
                cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--platform-profile", settings.toString()).expectStatus(0);
                for (String use : List.of("struct S {int member;}; unsigned long n=offsetof(S,member); int main(){return 0;}\n",
                        "#if offsetof(S,member)\n#endif\nint main(){return 0;}\n")) {
                    Files.writeString(cli.input.resolve("main.cpp"), include + use);
                    String error = assertThrows(ParseException.class,
                            () -> Cpg.read(cli.input, options(profile, Cpg.Stage.RESOLVED))).getMessage();
                    assertTrue(error.contains("offsetof"), error);
                    var failed = Cpg.readRecovering(cli.input, options(profile, Cpg.Stage.RESOLVED));
                    assertTrue(failed.hasErrors());
                    errorJson(ProjectJson.encode(failed).toText("  "));
                    errorJson(ProjectJson.encode(failed.edit().build()).toText("  "));
                    assertThrows(IllegalArgumentException.class, () -> Cpg.sources(failed, Language.CPP));
                    var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--platform-profile", settings.toString())
                            .expectStatus(1);
                    errorJson(Files.readString(run.work().resolve("snapshot.json")));
                    assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
                }
            }
        }
    }

    @Test void selectedObjectCeilingIsCheckedWithTypeOnlyNativeCompilationNotAllocation() throws Exception {
        Path work = Files.createTempDirectory(temp, "offsetof-ceiling-");
        String source = "#include <cstddef>\nstruct Limit {char bytes[2147483647];};\n"
                + "enum {last=offsetof(Limit,bytes[2147483646])};\n"
                + "typedef char verified[last==2147483646 ? 1 : -1];\n";
        Path input = writeSource(work, "input", source);
        healthy(Cpg.read(input.getParent(), options(PROFILE, Cpg.Stage.RESOLVED)));
        Path expanded = materialize(input);
        Path artifacts = Files.createDirectories(Path.of("target/offsetof-oracle"));
        for (String compiler : COMPILERS) {
            var command = new ArrayList<String>(List.of(compiler));
            command.addAll(FLAGS);
            command.addAll(List.of("-fsyntax-only", expanded.toString()));
            var checked = success(command, work);
            String prefix = compiler.equals("g++") ? "gcc" : "clang";
            Files.writeString(artifacts.resolve(prefix + "-ceiling.json"), Json.serialize(Map.of(
                    "command", command, "status", checked.code(), "stderr", checked.err(),
                    "supportedObjectBytes", "2147483647", "allocation", "none; declarations and ICEs only",
                    "sourceSha256", hash(expanded))));
            Files.writeString(artifacts.resolve(prefix + "-ceiling.cpp"), Files.readString(expanded));
        }
        // Exceeding the selected project subset is distinct from native size_t representability.
        String exceeded = "#include <cstddef>\nstruct Limit {char bytes[2147483648UL];}; unsigned long n=offsetof(Limit,bytes[0]);\n";
        Path bad = writeSource(work, "exceeded", exceeded);
        var rejected = Cpg.read(bad.getParent(), options(PROFILE, Cpg.Stage.RESOLVED));
        assertTrue(rejected.hasErrors());
        assertTrue(rejected.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"), rejected.semanticDiagnostics().toString());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(rejected, Language.CPP));
    }

    @Test void nativePackingEvidenceCannotBeCertifiedByTheNaturalLayoutCapability() throws Exception {
        String shape = "struct Packed {char tag; int value;}; typedef Packed Alias;\n";
        String packed = "#pragma pack(push,1)\n" + shape + "#pragma pack(pop)\n";
        for (String variant : List.of("inline", "header", "inactive", "operator", "macro-operator")) {
            Path work = Files.createTempDirectory(temp, "offsetof-pack-" + variant + "-");
            Path source = work.resolve("main.cpp");
            String declarations;
            if (variant.equals("header")) {
                Files.writeString(work.resolve("packed.h"), packed);
                declarations = "#include \"packed.h\"\n";
            } else if (variant.equals("operator") || variant.equals("macro-operator")) {
                String push = variant.equals("operator") ? "_Pragma(\"pack(push,1)\")"
                        : "PACK";
                declarations = (variant.equals("macro-operator") ? "#define PACK _Pragma(\"pack(push,1)\")\n" : "")
                        + "void configure(){" + push + ";}\n" + shape + "void reset(){_Pragma(\"pack(pop)\");}\n";
            } else declarations = variant.equals("inline") ? packed
                    : "#if 0\n#pragma pack(push,1)\n#endif\n" + shape;
            Files.writeString(source, "#include <cstddef>\n#include <cstdio>\n" + declarations
                    + "unsigned long observed=offsetof(Alias,value);\n"
                    + "int main(){std::printf(\"OFFSET %lu\\n\",observed);return 0;}\n");
            var project = Cpg.read(work, options(PROFILE, Cpg.Stage.RESOLVED));
            if (variant.equals("inactive")) {
                healthy(project);
                assertTrue(Cpg.sources(project, Language.CPP).get("main.cpp").contains("offsetof"));
            } else {
                assertTrue(project.hasErrors());
                assertTrue(project.semanticDiagnostics().toString().contains("UNSUPPORTED_PRAGMA"), project.semanticDiagnostics().toString());
                assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
                for (var expression : nodes(project, OffsetofExpression.class))
                    assertThrows(IllegalArgumentException.class, () -> expression.source(Language.CPP));
            }
            for (String compiler : COMPILERS) {
                Path binary = work.resolve(compiler.equals("g++") ? "gcc-probe" : "clang-probe");
                var command = new ArrayList<String>(List.of(compiler));
                command.addAll(FLAGS);
                command.addAll(List.of(source.toString(), "-o", binary.toString()));
                success(command, work);
                var run = success(List.of(binary.toString()), work);
                assertEquals("", run.err());
                assertEquals(BigInteger.valueOf(variant.equals("inactive") ? 4 : 1), observations(run.out()).get("OFFSET"),
                        "Active native packing must never be reported as the natural-layout value 4");
                Path evidence = Files.createDirectories(Path.of("target/offsetof-oracle/pragma-packing")
                        .resolve(variant).resolve(compiler.equals("g++") ? "gcc" : "clang"));
                Files.writeString(evidence.resolve("main.cpp"), Files.readString(source));
                if (variant.equals("header")) Files.writeString(evidence.resolve("packed.h"), packed);
                Files.writeString(evidence.resolve("observations.txt"), run.out());
                Files.writeString(evidence.resolve("metadata.json"), Json.serialize(Map.of(
                        "compiler", compiler, "compileCommand", command, "profileIdentityHash", PROFILE.identityHash(),
                        "sourceSha256", hash(source), "binarySha256", hash(binary),
                        "selectedCapabilityAccepted", variant.equals("inactive"), "observations", run.out())));
            }
        }
    }

    private static Cpg.Options options(PlatformProfile profile, Cpg.Stage stage) {
        return new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(profile);
    }
    private static String includes(List<String> headers) {
        return headers.stream().map(h -> "#include <" + h + ">\n").reduce("", String::concat);
    }
    private static String program(List<String> headers) {
        return includes(headers) + "#include <cstdio>\n#if !defined(offsetof)\n#error missing required macro\n#endif\n" + TYPES + """
                int exact_type(unsigned int) {return 0;}
                int exact_type(long) {return 0;}
                int exact_type(unsigned long) {return 1;}
                unsigned long zero = offsetof(Outer, prefix);
                unsigned long union_value = offsetof(Payload, weight);
                unsigned long nested = offsetof(api::Record, payload.leaf.value);
                unsigned long matrix = offsetof(Alias, rows[1][2].value);
                unsigned long tail = offsetof(Outer, tail);
                enum {position=offsetof(Outer, rows[1][2].value)};
                char extent[offsetof(Outer, rows[1][2].value)+1];
                int calls=0;
                unsigned long touch(){++calls; return 0;}
                int classify(unsigned long n){switch(n){case offsetof(Outer,tail):return 1; default:return 0;}}
                int main(){
                    unsigned long effect=touch()+offsetof(Outer,tail);
                    if(effect!=tail || calls!=1 || classify(tail)!=1) return 11;
                    if(exact_type(offsetof(Outer,tail))!=1) return 12;
                    if(position!=matrix || sizeof(extent)!=matrix+1) return 13;
                    std::printf("TEST_zero %lu\\nTEST_union %lu\\nTEST_nested %lu\\nTEST_matrix %lu\\nTEST_tail %lu\\n",
                            zero,union_value,nested,matrix,tail);
                    std::printf("TEST_extent %lu\\nTEST_type %d\\nTEST_calls %d\\n",sizeof(extent),exact_type(tail),calls);
                    return 0;
                }
                """;
    }
    private void verifyGenerated(Path source, int headerCount, boolean edited) throws Exception {
        var reparsed = Cpg.read(source.getParent(), options(PROFILE, Cpg.Stage.RESOLVED));
        verifyGraph(reparsed, headerCount);
        nativeRuntime(source, edited);
        Path expanded = materialize(source);
        var materialized = Cpg.read(expanded.getParent(), options(PROFILE, Cpg.Stage.RESOLVED));
        healthy(materialized);
        assertFalse(nodes(materialized, OffsetofExpression.class).isEmpty());
        nativeRuntime(expanded, edited);
    }
    private static void verifyGraph(Project project, int headerCount) throws Exception {
        healthy(project);
        var expressions = nodes(project, OffsetofExpression.class);
        assertFalse(expressions.isEmpty());
        var owned = nodes(project, Node.class);
        for (var expression : expressions) {
            assertEquals("unsigned long", TypeSemantics.expressionType(expression, PROFILE).orElseThrow().canonicalIdentity());
            assertEquals(ValueCategory.RVALUE, TypeSemantics.valueCategory(expression, PROFILE));
            assertFalse(expression.steps().isEmpty());
            for (var member : nodes(expression, OffsetofExpression.Member.class))
                assertTrue(owned.contains(member.target().orElseThrow()), "No cross-snapshot member binding");
        }
        assertTrue(nodes(project, CallableDeclaration.class).stream().noneMatch(d -> d.name().contains("builtin_offsetof")));
        assertTrue(nodes(project, CallExpression.class).stream().noneMatch(c -> c.target().map(d -> d.name().contains("builtin_offsetof")).orElse(false)));
        var calls = nodes(project, CallExpression.class);
        assertEquals(1, calls.stream().filter(c -> c.target().map(d -> d.name().equals("touch")).orElse(false)).count());
        for (var call : calls.stream().filter(c -> c.target().map(d -> d.name().equals("exact_type")).orElse(false)).toList())
            assertEquals("unsigned long", call.target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
        String json = ProjectJson.encode(project).toText("  ");
        Json.parse(json);
        assertTrue(json.contains("OffsetofExpression"));
        assertTrue(json.contains("gcc-clang-builtin"));
        var preprocessing = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(PROFILE, preprocessing.configuration().platform());
        var definitions = preprocessing.definitions().stream().filter(d -> d.name().equals("offsetof")).toList();
        assertEquals(headerCount, definitions.size());
        assertEquals(headerCount, definitions.stream().map(d -> d.sourceKey()).distinct().count());
        for (var definition : definitions) {
            assertTrue(definition.functionLike());
            assertNull(definition.unavailableReason());
            assertEquals("library", definition.originKind());
            assertEquals("(__builtin_offsetof(type, member))", definition.rawReplacement());
            assertTrue(project.externalInput(definition.sourceKey()).isPresent());
        }
        assertTrue(preprocessing.expansions().stream().anyMatch(event -> event.name().equals("offsetof")));
    }
    private static void errorJson(String json) {
        for (String expected : List.of("unavailable-library-macro", "unavailableReason", "offsetof", "main.cpp"))
            assertTrue(json.contains(expected), expected + " / " + json);
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        });
        return result;
    }
    private static VariableDeclaration variable(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }
    private static void healthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }
    private Path writeSource(Path work, String directory, String source) throws Exception {
        Path file = Files.createDirectory(work.resolve(directory)).resolve("main.cpp");
        Files.writeString(file, source);
        return file;
    }

    private Path materialize(Path source) throws Exception {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(source), source.getParent(), List.of(), List.of(), "c++03")
                .withPlatform(PROFILE);
        var preprocessing = CppPreprocessor.preprocess(unit, Files.readString(source));
        assertTrue(preprocessing.expansions().stream().anyMatch(event -> event.name().equals("offsetof")));
        assertFalse(preprocessing.tokens().stream().anyMatch(t -> t.token().spelling().equals("offsetof")),
                "Native body must contain CPG's actual expansion, not the host macro");
        var output = new StringBuilder();
        boolean body = false;
        for (var expanded : preprocessing.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                assertFalse(body, "Fixture includes must remain a prefix, never be hoisted");
                output.append("#include ").append(token.spelling()).append('\n');
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                if (!body) { output.append("#undef offsetof\n"); body = true; }
                assertNotEquals("offsetof", token.spelling());
                output.append(token.spelling()).append(' ');
            }
        }
        assertTrue(body);
        assertTrue(output.toString().contains("__builtin_offsetof"));
        Path file = Files.createTempDirectory(temp, "offsetof-materialized-").resolve("main.cpp");
        Files.writeString(file, output.append('\n'));
        return file;
    }

    private void nativeRuntime(Path source, boolean edited) throws Exception {
        for (String compiler : COMPILERS) {
            Path work = Files.createTempDirectory(temp, "offsetof-native-");
            Path wrapper = work.resolve("oracle.cpp"), binary = work.resolve("oracle");
            // Independent host declarations capture real header values before the tested input undefines offsetof.
            // They never replace or reconstruct the implementation under test.
            Files.writeString(wrapper, """
                    #include <cstddef>
                    #include <stddef.h>
                    #include <cstdio>
                    struct HostLeaf {char marker; %s value;};
                    union HostPayload {char bytes[13]; double weight; HostLeaf leaf;};
                    typedef HostLeaf HostRow[3];
                    typedef int (*HostCallback)(int);
                    struct HostOuter {char prefix; HostPayload payload; HostRow rows[2]; void *pointer; HostCallback callback; char tail;};
                    const unsigned long host_zero=offsetof(HostOuter,prefix);
                    const unsigned long host_union=offsetof(HostPayload,weight);
                    const unsigned long host_nested=offsetof(HostOuter,payload.leaf.value);
                    const unsigned long host_matrix=offsetof(HostOuter,rows[1][2].value);
                    const unsigned long host_tail=offsetof(HostOuter,tail);
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main(){
                        std::printf("HOST_zero %%lu\\nHOST_union %%lu\\nHOST_nested %%lu\\nHOST_matrix %%lu\\nHOST_tail %%lu\\n",
                                host_zero,host_union,host_nested,host_matrix,host_tail);
                        std::printf("HOST_extent %%lu\\nHOST_type 1\\nHOST_calls 1\\n",host_matrix+1);
                        return tested_main();
                    }
                    """.formatted(edited ? "long" : "int", source.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")));
            var command = new ArrayList<String>(List.of(compiler));
            command.addAll(FLAGS);
            command.addAll(List.of(wrapper.toString(), "-o", binary.toString()));
            success(command, work);
            var run = success(List.of(binary.toString()), work);
            assertEquals("", run.err());
            var facts = observations(run.out());
            for (String key : List.of("zero", "union", "nested", "matrix", "tail", "extent", "type", "calls"))
                assertEquals(facts.get("HOST_" + key), facts.get("TEST_" + key), compiler + " / " + source + " / " + key);
            assertEquals(BigInteger.valueOf(edited ? 112 : 68), facts.get("TEST_matrix"), "The edit must change a real nested offset");
            assertEquals(BigInteger.valueOf(edited ? 136 : 88), facts.get("TEST_tail"));
            String symbols = success(List.of("nm", "-u", binary.toString()), work).out();
            assertFalse(symbols.toLowerCase(Locale.ROOT).contains("offsetof"), "No fabricated runtime offset helper");
            String sourceHash = hash(source);
            Path evidence = Files.createDirectories(Path.of("target/offsetof-oracle/runtime")
                    .resolve((edited ? "edited-" : "original-") + sourceHash.substring(0, 16))
                    .resolve(compiler.equals("g++") ? "gcc" : "clang"));
            Files.writeString(evidence.resolve("tested.cpp"), Files.readString(source));
            Files.writeString(evidence.resolve("wrapper.cpp"), Files.readString(wrapper));
            Files.writeString(evidence.resolve("observations.txt"), run.out());
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("compiler", compiler);
            metadata.put("compileCommand", command);
            metadata.put("profileIdentityHash", PROFILE.identityHash());
            metadata.put("editedLeafValueType", edited ? "long" : "int");
            metadata.put("testedSourceSha256", sourceHash);
            metadata.put("wrapperSourceSha256", hash(wrapper));
            metadata.put("binarySha256", hash(binary));
            metadata.put("observationsSha256", hash(evidence.resolve("observations.txt")));
            metadata.put("hostMacroDisabled", Files.readString(source).contains("#undef offsetof"));
            metadata.put("undefinedSymbols", symbols);
            Files.writeString(evidence.resolve("metadata.json"), Json.serialize(metadata));
        }
    }

    private Map<String, BigInteger> probe(String compiler) throws Exception {
        Path work = Files.createTempDirectory(temp, "offsetof-probe-");
        Path source = work.resolve("probe.cpp"), binary = work.resolve("probe");
        var code = new StringBuilder("""
                #include <cstddef>
                #include <stddef.h>
                #include <climits>
                #include <cstdio>
                #if !defined(__x86_64__) || !defined(__LP64__)
                #error This independent oracle requires the explicitly selected x86_64 LP64 target
                #endif
                typedef int (*Callback)(int);
                struct Owner {char tag; int value;};
                int exact_type(unsigned long){return 1;}
                template<class T> int exact_type(T){return 0;}
                """);
        for (var scalar : SCALARS)
            code.append("struct align_").append(scalar.key()).append(" {char tag; ").append(scalar.spelling()).append(" value;};\n");
        code.append("int main(){\n");
        print(code, "CHAR_BIT", "CHAR_BIT");
        print(code, "CPP_VERSION", "__cplusplus");
        print(code, "SIZE_TYPE", "exact_type(sizeof(Owner))");
        print(code, "HOST_OFFSET_TYPE", "exact_type(offsetof(Owner,value))");
        print(code, "HOST_OFFSET", "offsetof(Owner,value)");
        for (var scalar : SCALARS) {
            print(code, "SIZE_" + scalar.key(), "sizeof(" + scalar.spelling() + ")");
            print(code, "ALIGN_" + scalar.key(), "offsetof(align_" + scalar.key() + ",value)");
        }
        code.append("#undef offsetof\n");
        print(code, "BUILTIN_TYPE", "exact_type(__builtin_offsetof(Owner,value))");
        print(code, "BUILTIN_OFFSET", "__builtin_offsetof(Owner,value)");
        code.append("return 0;}\n");
        Files.writeString(source, code);
        var command = new ArrayList<String>(List.of(compiler));
        command.addAll(FLAGS);
        command.addAll(List.of(source.toString(), "-o", binary.toString()));
        success(command, work);
        var run = success(List.of(binary.toString()), work);
        assertEquals("", run.err());
        var facts = observations(run.out());
        assertEquals(BigInteger.valueOf(8), facts.get("CHAR_BIT"));
        assertEquals(BigInteger.valueOf(199711), facts.get("CPP_VERSION"));
        for (String key : List.of("SIZE_TYPE", "HOST_OFFSET_TYPE", "BUILTIN_TYPE")) assertEquals(BigInteger.ONE, facts.get(key));
        assertEquals(BigInteger.valueOf(4), facts.get("HOST_OFFSET"));
        assertEquals(facts.get("HOST_OFFSET"), facts.get("BUILTIN_OFFSET"));
        for (var scalar : SCALARS) {
            assertEquals(BigInteger.valueOf(PROFILE.sizeBits(scalar.profileType()).orElseThrow()),
                    facts.get("SIZE_" + scalar.key()).multiply(facts.get("CHAR_BIT")), scalar.spelling() + " storage");
            assertEquals(BigInteger.valueOf(PROFILE.alignmentBits(scalar.profileType()).orElseThrow()),
                    facts.get("ALIGN_" + scalar.key()).multiply(facts.get("CHAR_BIT")), scalar.spelling() + " alignment");
        }
        var metadata = new LinkedHashMap<String, Object>();
        String version = success(List.of(compiler, "--version"), work).out();
        String target = success(List.of(compiler, "-dumpmachine"), work).out().trim();
        assertTrue(target.startsWith("x86_64"), target);
        metadata.put("compiler", compiler);
        metadata.put("version", version);
        metadata.put("target", target);
        metadata.put("flags", FLAGS);
        metadata.put("compileCommand", command);
        metadata.put("profile", PROFILE.toSettings());
        metadata.put("profileIdentityHash", PROFILE.identityHash());
        metadata.put("observations", run.out());
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("probeBinarySha256", hash(binary));
        metadata.put("alignmentOracle", "real offsetof(named wrapper with char prefix and T value, value)");
        metadata.put("scope", "Explicit closed x86_64 SysV LP64 subset; scalar storage/alignment and object/function pointers, not arbitrary ABI certification");
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v", source.toString(), "-o", "/dev/null"), work).err());
        metadata.put("compilerSearchDirectories", success(List.of(compiler, "-print-search-dirs"), work).out());
        metadata.put("compilerAndSysrootInvocation", success(List.of(compiler, "-std=c++03", "-pedantic-errors", "-###", "-c",
                source.toString(), "-o", work.resolve("not-created.o").toString()), work).err());
        Path launcher = Path.of(success(List.of("sh", "-c", "command -v \"$1\"", "offsetof", compiler), work).out().trim()).toRealPath();
        metadata.put("compilerLauncher", launcher.toString());
        metadata.put("compilerLauncherSha256", hash(launcher));
        String frontend = success(List.of(compiler, "-print-prog-name=cc1plus"), work).out().trim();
        if (version.contains("clang")) {
            String directory = version.lines().filter(line -> line.startsWith("InstalledDir: "))
                    .map(line -> line.substring("InstalledDir: ".length())).findFirst().orElseThrow();
            frontend = Path.of(directory).resolve("clang++").toString();
        }
        Path frontendPath = Path.of(frontend).toRealPath();
        metadata.put("compilerFrontend", frontendPath.toString());
        metadata.put("compilerFrontendSha256", hash(frontendPath));
        String includeTrace = success(List.of(compiler, "-std=c++03", "-H", "-E", source.toString(), "-o", "/dev/null"), work).err();
        var hashes = new TreeMap<String, String>();
        for (String line : includeTrace.lines().toList()) {
            if (!line.matches("\\.+ .*")) continue;
            Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
            if (Files.isRegularFile(header)) hashes.put(header.toRealPath().toString(), hash(header));
        }
        for (String header : List.of("/cstddef", "/stddef.h", "/climits", "/cstdio"))
            assertTrue(hashes.keySet().stream().anyMatch(path -> path.endsWith(header)), includeTrace);
        metadata.put("selectedHeaderSha256", hashes);
        var nativeDefinitions = new LinkedHashMap<String, List<String>>();
        for (var headers : HEADERS) {
            Path macroSource = work.resolve("headers-" + nativeDefinitions.size() + ".cpp");
            Files.writeString(macroSource, includes(headers));
            var definitions = success(List.of(compiler, "-std=c++03", "-dM", "-E", macroSource.toString()), work).out()
                    .lines().filter(line -> line.startsWith("#define offsetof(")).toList();
            assertEquals(1, definitions.size());
            assertTrue(definitions.getFirst().contains("__builtin_offsetof"), definitions.toString());
            nativeDefinitions.put(String.join(",", headers), definitions);
        }
        metadata.put("nativeOffsetofDefinitions", nativeDefinitions);
        Path artifacts = Files.createDirectories(Path.of("target/offsetof-oracle"));
        String prefix = compiler.equals("g++") ? "gcc" : "clang";
        Files.writeString(artifacts.resolve(prefix + "-evidence.json"), Json.serialize(metadata));
        Files.writeString(artifacts.resolve(prefix + "-probe.cpp"), code);
        return facts;
    }
    private static void print(StringBuilder source, String key, String expression) {
        source.append("std::printf(\"").append(key).append(" %lu\\n\", static_cast<unsigned long>(").append(expression).append("));\n");
    }
    private static Map<String, BigInteger> observations(String output) {
        var values = new LinkedHashMap<String, BigInteger>();
        for (String line : output.lines().toList()) {
            String[] words = line.split(" ");
            assertEquals(2, words.length, line);
            assertNull(values.put(words[0], new BigInteger(words[1])), "Duplicate observation: " + line);
        }
        return Collections.unmodifiableMap(values);
    }
    private static String hash(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private record Run(int code, String out, String err) {}
    private static Run success(List<String> args, Path work) throws Exception {
        var run = command(args, work);
        assertEquals(0, run.code(), () -> args + "\n" + run.out() + "\n" + run.err());
        return run;
    }
    private static Run command(List<String> args, Path work) throws Exception {
        Path stdout = Files.createTempFile(work, "stdout-", ".txt"), stderr = Files.createTempFile(work, "stderr-", ".txt");
        var process = new ProcessBuilder(args).directory(work.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Timed out: " + args);
            return new Run(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }
}
