/* Internal C++03 POD layout checked against independent native observations. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PodLayoutIT {
    @TempDir Path temp;
    private static final List<String> COMPILERS = List.of("g++", "clang++");
    private static final List<String> FLAGS = List.of("-std=c++03", "-pedantic-errors");
    // This is an explicit test target, never a profile inferred from a native observation.
    private static final PlatformProfile PROFILE = PlatformProfile.defaults().toBuilder()
            .name("pod-layout-native-x86_64-lp64").floatingFormat("long double", "binary80").build();
    private static final String DEFINITIONS = """
            struct Leaf {
                char marker;
                int value;
                int harmless() const { return 1; }
                static int count;
            };
            typedef Leaf LeafAlias;
            typedef LeafAlias LeafRow[3];
            typedef int IntRow[4];
            union Payload {
                char bytes[13];
                int number;
                double weight;
                LeafAlias leaf;
                int harmless() const { return 2; }
            };
            typedef Payload PayloadAlias;
            struct Scalars {
                bool b;
                char c;
                signed char sc;
                unsigned char uc;
                short s;
                unsigned short us;
                int i;
                unsigned int ui;
                long l;
                unsigned long ul;
                wchar_t wc;
                float f;
                double d;
                long double ld;
                void *ptr;
            };
            struct Outer {
                char prefix;
                PayloadAlias payload;
                LeafRow matrix[2];
                IntRow grid[2];
                Scalars scalars;
                char tail;
                int harmless() const { return 3; }
            };
            typedef Outer OuterAlias;
            namespace api { typedef OuterAlias Record; }
            LeafAlias leafRoot;
            PayloadAlias unionRoot;
            Scalars scalarRoot;
            api::Record root;
            """;

    private record Owner(String declaration, String spelling, String variable, List<String> fields) {}
    private static final List<Owner> OWNERS = List.of(
            new Owner("Leaf", "LeafAlias", "leafRoot", List.of("marker", "value")),
            new Owner("Payload", "PayloadAlias", "unionRoot", List.of("bytes", "number", "weight", "leaf")),
            new Owner("Scalars", "Scalars", "scalarRoot", List.of("b", "c", "sc", "uc", "s", "us", "i", "ui",
                    "l", "ul", "wc", "f", "d", "ld", "ptr")),
            new Owner("Outer", "api::Record", "root", List.of("prefix", "payload", "matrix", "grid", "scalars", "tail")));

    private record Scalar(String key, String spelling, String profileType) {}
    // long long is deliberately absent: it is not a strict C++03 fundamental type.
    private static final List<Scalar> SCALARS = List.of(
            new Scalar("bool", "bool", "bool"), new Scalar("char", "char", "char"),
            new Scalar("signed_char", "signed char", "signed char"),
            new Scalar("unsigned_char", "unsigned char", "unsigned char"),
            new Scalar("short", "short", "short"), new Scalar("unsigned_short", "unsigned short", "unsigned short"),
            new Scalar("int", "int", "int"), new Scalar("unsigned_int", "unsigned int", "unsigned int"),
            new Scalar("long", "long", "long"), new Scalar("unsigned_long", "unsigned long", "unsigned long"),
            new Scalar("wchar_t", "wchar_t", "wchar_t"), new Scalar("float", "float", "float"),
            new Scalar("double", "double", "double"), new Scalar("long_double", "long double", "long double"),
            new Scalar("pointer", "void *", "pointer"));

    private record PathCase(String variable, String owner, String designator, List<PodLayout.Step> steps,
                            List<String> memberNames, String terminalType) {}
    private static PodLayout.Member member(String name) { return new PodLayout.Member(name); }
    private static PodLayout.Index index(int value) {
        return new PodLayout.Index(IntegerLiteral.create().setValue(value).build());
    }
    private static final List<PathCase> PATHS = List.of(
            new PathCase("root", "api::Record", "payload.leaf.value",
                    List.of(member("payload"), member("leaf"), member("value")),
                    List.of("payload", "leaf", "value"), "edited-value"),
            new PathCase("root", "api::Record", "payload.bytes[12]",
                    List.of(member("payload"), member("bytes"), index(12)), List.of("payload", "bytes"), "char"),
            new PathCase("root", "api::Record", "matrix[0][0].marker",
                    List.of(member("matrix"), index(0), index(0), member("marker")), List.of("matrix", "marker"), "char"),
            new PathCase("root", "api::Record", "matrix[1][2].value",
                    List.of(member("matrix"), index(1), index(2), member("value")), List.of("matrix", "value"), "edited-value"),
            new PathCase("root", "api::Record", "grid[1][3]",
                    List.of(member("grid"), index(1), index(3)), List.of("grid"), "int"),
            new PathCase("root", "api::Record", "scalars.ld",
                    List.of(member("scalars"), member("ld")), List.of("scalars", "ld"), "long double"),
            new PathCase("unionRoot", "PayloadAlias", "leaf.value",
                    List.of(member("leaf"), member("value")), List.of("leaf", "value"), "edited-value"));

    @Test void independentNativeSizesAlignmentsAndOffsetsSurviveCopyReanalysisAndFieldEdit() throws Exception {
        Path evidence = Files.createTempDirectory(Files.createDirectories(
                Path.of("target/pod-layout-oracle").toAbsolutePath()), "run-");
        var originalFacts = bothCompilers(evidence, "original", DEFINITIONS);
        var original = read("original", DEFINITIONS);
        verify(original, originalFacts, false);
        var copy = original.edit().build();
        assertNotSame(variable(original, "root"), variable(copy, "root"));
        verify(copy, originalFacts, false);
        verify(NameResolver.resolve(copy), originalFacts, false);
        String emitted = Cpg.sources(copy, Language.CPP).get("definitions.cpp");
        assertNotNull(emitted);
        assertEquals(originalFacts, bothCompilers(evidence, "generated-copy", emitted));
        verify(read("generated-copy", emitted), originalFacts, false);

        var oldType = field(owner(original, "Leaf"), "value").type().orElseThrow();
        var editor = new ProjectEditor(original);
        editor.replace(oldType, NamedType.create().setName("long").build());
        var edited = NameResolver.resolve(editor.build());
        String independentlyEdited = DEFINITIONS.replace("int value;", "long value;");
        assertNotEquals(DEFINITIONS, independentlyEdited);
        var editedFacts = bothCompilers(evidence, "independent-field-edit", independentlyEdited);
        assertNotEquals(originalFacts.get("SIZE_Leaf"), editedFacts.get("SIZE_Leaf"));
        assertNotEquals(originalFacts.get("FIELD_Leaf_value"), editedFacts.get("FIELD_Leaf_value"));
        assertNotEquals(originalFacts.get("PATH_3"), editedFacts.get("PATH_3"));
        verify(edited, editedFacts, true);
        verify(edited.edit().build(), editedFacts, true);
        verify(NameResolver.resolve(edited.edit().build()), editedFacts, true);
        String editedSource = Cpg.sources(edited, Language.CPP).get("definitions.cpp");
        assertNotNull(editedSource);
        assertEquals(editedFacts, bothCompilers(evidence, "generated-field-edit", editedSource));
        verify(read("generated-field-edit", editedSource), editedFacts, true);
        // Repeated queries cannot retain edits or declaration links from another snapshot.
        verify(original, originalFacts, false);
        assertEquals("int", oldType.canonicalIdentity());
    }

    private Project read(String name, String definitions) throws Exception {
        assertFalse(definitions.contains("offsetof"), "CPG sees declarations only, never the native oracle");
        Path input = Files.createDirectory(temp.resolve(name));
        Files.writeString(input.resolve("definitions.cpp"), definitions);
        var project = Cpg.read(input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03").withPlatform(PROFILE));
        assertHealthy(project);
        return project;
    }

    private static void verify(Project project, Map<String, BigInteger> facts, boolean edited) {
        assertHealthy(project);
        assertEquals(PROFILE, TypeSemantics.profile(project.files().getFirst()));
        var declarations = nodes(project, VariableDeclaration.class);
        for (var expected : OWNERS) {
            var type = variable(project, expected.variable()).type().orElseThrow();
            var layout = supported(PodLayout.layout(type, PROFILE));
            var declarationLayout = supported(PodLayout.layout(owner(project, expected.declaration()), PROFILE));
            assertEquals(declarationLayout, layout, "Alias and class-declaration queries must agree");
            assertEquals(facts.get("SIZE_" + expected.declaration()), layout.sizeBytes(), expected.spelling());
            assertEquals(facts.get("ALIGN_" + expected.declaration()).multiply(BigInteger.valueOf(PROFILE.bitsPerByte())),
                    BigInteger.valueOf(layout.alignmentBits()), expected.spelling());
            assertEquals(expected.fields(), layout.fields().stream().map(f -> f.declaration().name()).toList());
            for (var field : layout.fields()) {
                assertSame(field(owner(project, expected.declaration()), field.declaration().name()), field.declaration());
                BigInteger nativeOffset = facts.get("FIELD_" + expected.declaration() + "_" + field.declaration().name());
                assertEquals(nativeOffset, field.offsetBytes());
                var offset = supported(PodLayout.offset(type, List.of(member(field.declaration().name())), PROFILE));
                assertEquals(nativeOffset, offset.offsetBytes());
                assertEquals(List.of(field.declaration()), offset.members());
            }
        }
        for (int i = 0; i < PATHS.size(); i++) {
            var path = PATHS.get(i);
            var offset = supported(PodLayout.offset(variable(project, path.variable()).type().orElseThrow(), path.steps(), PROFILE));
            assertEquals(facts.get("PATH_" + i), offset.offsetBytes(), path.owner() + "." + path.designator());
            assertEquals(path.memberNames(), offset.members().stream().map(VariableDeclaration::name).toList());
            assertTrue(offset.members().stream().allMatch(declarations::contains), "Path bindings must belong to this snapshot");
            String expectedType = path.terminalType().equals("edited-value") ? edited ? "long" : "int" : path.terminalType();
            assertEquals(expectedType, offset.type().canonicalIdentity());
        }
    }

    private static <T> T supported(PodLayout.Result<T> result) {
        assertTrue(result.supported(), () -> "Unexpected unsupported query: " + result.failure());
        assertTrue(result.failure().isEmpty());
        return result.value().orElseThrow();
    }

    private Map<String, BigInteger> bothCompilers(Path root, String variant, String definitions) throws Exception {
        Map<String, BigInteger> first = null;
        for (String compiler : COMPILERS) {
            var observed = probe(Files.createDirectories(root.resolve(variant).resolve(compiler.replace('+', 'p'))),
                    compiler, definitions);
            verifyProfile(observed);
            if (first == null) first = observed;
            else assertEquals(first, observed, "Independent GCC and Clang layout observations must agree");
        }
        return Objects.requireNonNull(first);
    }

    private static void verifyProfile(Map<String, BigInteger> facts) {
        assertEquals(BigInteger.valueOf(PROFILE.bitsPerByte()), facts.get("CHAR_BIT"));
        assertEquals(BigInteger.valueOf(199711), facts.get("CPP_VERSION"));
        assertEquals("unsigned long", PROFILE.sizeType());
        assertEquals(BigInteger.ONE, facts.get("SIZE_TYPE"));
        assertEquals(BigInteger.ONE, facts.get("OFFSET_TYPE"));
        for (var scalar : SCALARS) {
            assertEquals(BigInteger.valueOf(PROFILE.sizeBits(scalar.profileType()).orElseThrow()),
                    facts.get("SIZE_scalar_" + scalar.key()).multiply(facts.get("CHAR_BIT")), scalar.spelling() + " storage");
            assertEquals(BigInteger.valueOf(PROFILE.alignmentBits(scalar.profileType()).orElseThrow()),
                    facts.get("ALIGN_scalar_" + scalar.key()).multiply(facts.get("CHAR_BIT")), scalar.spelling() + " alignment");
        }
    }

    private Map<String, BigInteger> probe(Path work, String compiler, String definitions) throws Exception {
        Path source = work.resolve("oracle.cpp"), declarationSource = work.resolve("definitions.cpp"), binary = work.resolve("oracle");
        Files.writeString(declarationSource, definitions);
        Files.writeString(source, oracleSource());
        var compile = new ArrayList<String>(List.of(compiler));
        compile.addAll(FLAGS);
        compile.addAll(List.of(source.toString(), "-o", binary.toString()));
        success(compile, work);
        var run = success(List.of(binary.toString()), work);
        assertEquals("", run.err());
        Files.writeString(work.resolve("observations.txt"), run.out());
        var facts = new LinkedHashMap<String, BigInteger>();
        for (String line : run.out().lines().toList()) {
            String[] parts = line.split(" ");
            assertEquals(2, parts.length, line);
            assertNull(facts.put(parts[0], new BigInteger(parts[1])), "Duplicate oracle key: " + line);
        }

        String version = success(List.of(compiler, "--version"), work).out();
        String target = success(List.of(compiler, "-dumpmachine"), work).out().trim();
        assertTrue(target.startsWith("x86_64"), "Explicit selected native target: " + target);
        String trace = success(List.of(compiler, "-std=c++03", "-pedantic-errors", "-###", "-c",
                source.toString(), "-o", work.resolve("not-created.o").toString()), work).err();
        String included = success(List.of(compiler, "-std=c++03", "-H", "-E", source.toString(), "-o", "/dev/null"), work).err();
        Files.writeString(work.resolve("include-trace.txt"), included);
        var headers = new TreeMap<String, String>();
        for (String line : included.lines().toList()) {
            if (!line.matches("\\.+ .*")) continue;
            Path header = Path.of(line.replaceFirst("^\\.+ ", ""));
            if (Files.isRegularFile(header)) {
                header = header.toRealPath();
                if (!header.equals(declarationSource.toRealPath())) headers.put(header.toString(), hash(header));
            }
        }
        for (String required : List.of("/cstddef", "/stddef.h", "/climits", "/cstdio"))
            assertTrue(headers.keySet().stream().anyMatch(path -> path.endsWith(required)), included);
        String macros = success(List.of(compiler, "-std=c++03", "-dM", "-E", source.toString()), work).out();
        Path macroSource = work.resolve("native-macros.txt");
        Files.writeString(macroSource, macros);
        var offsetofDefinitions = macros.lines().filter(line -> line.startsWith("#define offsetof(")).toList();
        assertEquals(1, offsetofDefinitions.size(), "Record the actual native offsetof definition");
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("compiler", compiler);
        metadata.put("version", version);
        metadata.put("target", target);
        metadata.put("flags", FLAGS);
        metadata.put("compileCommand", compile);
        metadata.put("compilerAndSysrootInvocation", trace);
        metadata.put("compilerSearchDirectories", success(List.of(compiler, "-print-search-dirs"), work).out());
        metadata.put("includeSearch", success(List.of(compiler, "-std=c++03", "-E", "-v", source.toString(), "-o", "/dev/null"), work).err());
        Path launcher = Path.of(success(List.of("sh", "-c", "command -v \"$1\"", "pod-layout", compiler), work).out().trim()).toRealPath();
        metadata.put("compilerLauncher", launcher.toString());
        metadata.put("compilerLauncherSha256", hash(launcher));
        // A compiler launcher may be a wrapper. Record the actual compiler frontend too.
        String frontend = success(List.of(compiler, "-print-prog-name=cc1plus"), work).out().trim();
        if (version.contains("clang")) {
            String installed = version.lines().filter(line -> line.startsWith("InstalledDir: "))
                    .map(line -> line.substring("InstalledDir: ".length())).findFirst().orElseThrow();
            frontend = Path.of(installed).resolve("clang++").toString();
        }
        Path frontendPath = Path.of(frontend).toRealPath();
        metadata.put("compilerFrontend", frontendPath.toString());
        metadata.put("compilerFrontendSha256", hash(frontendPath));
        metadata.put("selectedHeaderSha256", headers);
        metadata.put("nativeOffsetofDefinition", offsetofDefinitions.getFirst());
        metadata.put("nativeMacrosSha256", hash(macroSource));
        metadata.put("profile", PROFILE.toSettings());
        metadata.put("profileIdentityHash", PROFILE.identityHash());
        Path profile = work.resolve("profile.json");
        Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
        metadata.put("profileFileSha256", hash(profile));
        metadata.put("definitionsSha256", hash(declarationSource));
        metadata.put("probeSourceSha256", hash(source));
        metadata.put("probeBinarySha256", hash(binary));
        metadata.put("observationsSha256", hash(work.resolve("observations.txt")));
        metadata.put("alignmentOracle", "offsetof(named_wrapper_with_char_prefix_and_T_value, value), in native bytes");
        metadata.put("scope", "POD layout helper only; CPG never parses or enables offsetof. Strict C++03 excludes long long.");
        Files.writeString(work.resolve("metadata.json"), Json.serialize(metadata));
        return Collections.unmodifiableMap(facts);
    }

    private static String oracleSource() {
        var source = new StringBuilder("""
                #include <cstddef>
                #include <stddef.h>
                #include <climits>
                #include <cstdio>
                #if !defined(__x86_64__) || !defined(__LP64__)
                #error This independent oracle requires the explicitly selected x86_64 LP64 target
                #endif
                #include "definitions.cpp"
                int exact_size_type(unsigned long) { return 1; }
                template<class T> int exact_size_type(T) { return 0; }
                """);
        for (var scalar : SCALARS)
            source.append("struct align_scalar_").append(scalar.key()).append(" { char prefix; ")
                    .append(scalar.spelling()).append(" value; };\n");
        for (var owner : OWNERS)
            source.append("struct align_").append(owner.declaration()).append(" { char prefix; ")
                    .append(owner.spelling()).append(" value; };\n");
        source.append("int main() {\n");
        print(source, "CHAR_BIT", "CHAR_BIT");
        print(source, "CPP_VERSION", "__cplusplus");
        print(source, "SIZE_TYPE", "exact_size_type(sizeof(Outer))");
        print(source, "OFFSET_TYPE", "exact_size_type(offsetof(Outer, tail))");
        for (var scalar : SCALARS) {
            print(source, "SIZE_scalar_" + scalar.key(), "sizeof(" + scalar.spelling() + ")");
            print(source, "ALIGN_scalar_" + scalar.key(), "offsetof(align_scalar_" + scalar.key() + ", value)");
        }
        for (var owner : OWNERS) {
            print(source, "SIZE_" + owner.declaration(), "sizeof(" + owner.spelling() + ")");
            print(source, "ALIGN_" + owner.declaration(), "offsetof(align_" + owner.declaration() + ", value)");
            for (String field : owner.fields())
                print(source, "FIELD_" + owner.declaration() + "_" + field, "offsetof(" + owner.spelling() + ", " + field + ")");
        }
        for (int i = 0; i < PATHS.size(); i++)
            print(source, "PATH_" + i, "offsetof(" + PATHS.get(i).owner() + ", " + PATHS.get(i).designator() + ")");
        return source.append("return 0;\n}\n").toString();
    }

    private static void print(StringBuilder source, String key, String expression) {
        source.append("std::printf(\"").append(key).append(" %lu\\n\", static_cast<unsigned long>(")
                .append(expression).append("));\n");
    }

    private static ClassDeclaration owner(Project project, String name) {
        return nodes(project, ClassDeclaration.class).stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }
    private static VariableDeclaration field(ClassDeclaration owner, String name) {
        return owner.members().stream().filter(value -> value instanceof VariableDeclaration field && field.name().equals(name))
                .map(VariableDeclaration.class::cast).findFirst().orElseThrow();
    }
    private static VariableDeclaration variable(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (kind.isInstance(node)) result.add(kind.cast(node)); }
        });
        return result;
    }
    private static void assertHealthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }
    private static String hash(Path path) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private record Run(int code, String out, String err) {}
    private static Run success(List<String> args, Path work) throws Exception {
        Path stdout = Files.createTempFile(work, "stdout-", ".txt"), stderr = Files.createTempFile(work, "stderr-", ".txt");
        var process = new ProcessBuilder(args).directory(work.toFile()).redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Timed out: " + args);
            var run = new Run(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
            assertEquals(0, run.code(), () -> args + "\n" + run.out() + "\n" + run.err());
            return run;
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }
}
