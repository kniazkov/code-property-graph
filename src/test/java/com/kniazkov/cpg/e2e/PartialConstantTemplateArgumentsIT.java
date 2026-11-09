/* Strict C++03 oracle for named integral partial-specialization patterns. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PartialConstantTemplateArgumentsIT {
    @TempDir Path temp;
    private int execution;
    private static final PlatformProfile PROFILE = PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "2147483647").build();
    private static String program(String header) {
        return "#include <" + header + ">\n" + """
                const int selected=1;
                enum {choice=2};
                const int fixed=1;
                namespace pattern {enum {second=2};}
                template<class T,int N> struct Box {char data[N];char tail;};
                template<class T> struct Box<T,fixed> {long data;char tail;};
                template<class T> struct Box<T,pattern::second> {short data;char tail;};
                typedef Box<int,selected> ByConst;
                typedef Box<int,choice> ByEnum;
                unsigned long const_offset=offsetof(ByConst,tail);
                unsigned long enum_offset=offsetof(ByEnum,tail);
                enum {extent=offsetof(ByConst,tail)+1};
                char array[extent];
                int exact(unsigned long){return 1;}
                template<class T> int exact(T){return 0;}
                int exact_result=exact(offsetof(ByEnum,tail));
                int check_case(int n){switch(n){case offsetof(ByEnum,tail):return 1;default:return 0;}}
                int main(){return 0;}
                """;
    }
    private static Cpg.Options options(Cpg.Stage stage) {
        return new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(PROFILE);
    }
    private static void healthy(Project project) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        });
        return result;
    }
    private Path source(String label, String text) throws Exception {
        Path file = Files.createTempDirectory(temp, label).resolve("main.cpp");
        Files.writeString(file, text);
        return file;
    }

    @Test void namedArgumentsReselectThroughStagesCopiesEditsAndPackagedCli() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            Path input = source("original", program(header));
            verify(input, false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    healthy(snapshot);
                    assertFalse(snapshot.classTemplateInstances().isEmpty());
                    assertTrue(ProjectJson.encode(snapshot).toText("  ").contains("OffsetofExpression"));
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.replaceAll("\\s+", "").contains("Box<int,selected>"), generated);
                    assertTrue(generated.replaceAll("\\s+", "").contains("Box<int,choice>"), generated);
                    verify(source("generated-" + stage, generated), false);
                    var editor = new ProjectEditor(snapshot);
                    var constant = nodes(snapshot.files().getFirst(), VariableDeclaration.class).stream()
                            .filter(v -> v.name().equals("selected")).findFirst().orElseThrow();
                    var enumerator = nodes(snapshot.files().getFirst(), EnumeratorDeclaration.class).stream()
                            .filter(v -> v.name().equals("choice")).findFirst().orElseThrow();
                    editor.replace(constant.initializer().orElseThrow(), IntegerLiteral.create().setValue(3).build());
                    editor.replace(enumerator.value().orElseThrow(), IntegerLiteral.create().setValue(1).build());
                    Project edited = NameResolver.resolve(editor.build());
                    healthy(edited);
                    verify(source("edited-" + stage, Cpg.sources(edited, Language.CPP).get("main.cpp")), true);
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "cli")).source("main.cpp", program(header));
            Path profile = Files.createTempFile(temp, "profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verify(run.work().resolve("generated/main.cpp"), false);
        }
    }

    @Test void editsToPatternFactsReselectFoldedSource() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            Path input = source("header-edit", program(header));
            Project project = Cpg.read(input.getParent(), options(Cpg.Stage.FOLDED));
            healthy(project);
            var editor = new ProjectEditor(project);
            for (String name : List.of("fixed", "selected")) {
                var constant = nodes(project.files().getFirst(), VariableDeclaration.class).stream()
                        .filter(v -> v.name().equals(name)).findFirst().orElseThrow();
                editor.replace(constant.initializer().orElseThrow(), IntegerLiteral.create().setValue(name.equals("fixed") ? 2 : 3).build());
            }
            var pattern = nodes(project.files().getFirst(), EnumeratorDeclaration.class).stream()
                    .filter(e -> e.name().equals("second")).findFirst().orElseThrow();
            editor.replace(pattern.value().orElseThrow(), IntegerLiteral.create().setValue(4).build());
            Project changed = NameResolver.resolve(editor.build());
            healthy(changed);
            // ByConst<3> now uses the primary, ByEnum<2> the edited long pattern.
            verify(source("edited-headers", Cpg.sources(changed, Language.CPP).get("main.cpp")), true);
        }
    }

    private void verify(Path source, boolean edited) throws Exception {
        healthy(Cpg.read(source.getParent(), options(Cpg.Stage.RESOLVED)));
        Path expanded = materialize(source);
        healthy(Cpg.read(expanded.getParent(), options(Cpg.Stage.RESOLVED)));
        for (Path tested : List.of(source, expanded)) for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "native");
            Path wrapper = work.resolve("wrapper.cpp"), binary = work.resolve("probe");
            Files.writeString(wrapper, """
                    #include <cstddef>
                    #include <climits>
                    #include <cstdio>
                    #if !defined(__x86_64__) || !defined(__LP64__)
                    #error selected target mismatch
                    #endif
                    struct HostConst {%s;char tail;};
                    struct HostEnum {%s data;char tail;};
                    const std::size_t host_const=offsetof(HostConst,tail);
                    const std::size_t host_enum=offsetof(HostEnum,tail);
                    int is_size_t(std::size_t){return 1;}
                    template<class T> int is_size_t(T){return 0;}
                    #define main tested_main
                    #include "%s"
                    #undef main
                    typedef char const_ice[(__builtin_offsetof(ByConst,tail)==host_const)?1:-1];
                    typedef char enum_ice[(__builtin_offsetof(ByEnum,tail)==host_enum)?1:-1];
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        if(const_offset!=host_const || enum_offset!=host_enum) return 11;
                        if(exact_result!=1 || !is_size_t(__builtin_offsetof(ByEnum,tail))) return 12;
                        if(sizeof(array)!=host_const+1 || check_case(host_enum)!=1) return 13;
                        std::printf("const=%%lu enum=%%lu extent=%%lu\\n", const_offset, enum_offset, (unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(edited ? "char data[3]" : "long data", edited ? "long" : "short",
                            tested.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")));
            List<String> command = List.of(compiler, "-std=c++03", "-pedantic-errors", wrapper.toString(), "-o", binary.toString());
            success(command, work);
            String observations = success(List.of(binary.toString()), work);
            assertEquals(edited ? "const=3 enum=8 extent=4\n" : "const=8 enum=2 extent=9\n", observations);
            Path evidence = Files.createDirectories(Path.of("target/partial-constant-template-arguments-oracle")
                    .resolve(String.format("%03d-%s", ++execution, hash(tested).substring(0, 16)))
                    .resolve(compiler.equals("g++") ? "gcc" : "clang"));
            Files.writeString(evidence.resolve("tested.cpp"), Files.readString(tested));
            Files.writeString(evidence.resolve("wrapper.cpp"), Files.readString(wrapper));
            Files.writeString(evidence.resolve("observations.txt"), observations);
            Files.writeString(evidence.resolve("metadata.json"), Json.serialize(Map.of(
                    "compiler", compiler, "version", success(List.of(compiler, "--version"), work),
                    "target", success(List.of(compiler, "-dumpmachine"), work), "command", command,
                    "profile", PROFILE.toSettings(), "sourceSha256", hash(tested),
                    "materialized", tested.equals(expanded), "edited", edited)));
        }
    }
    private Path materialize(Path file) throws Exception {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(file), file.getParent(), List.of(), List.of(), "c++03")
                .withPlatform(PROFILE);
        var result = CppPreprocessor.preprocess(unit, Files.readString(file));
        assertTrue(result.expansions().stream().anyMatch(e -> e.name().equals("offsetof")));
        var out = new StringBuilder();
        boolean body = false;
        for (var expanded : result.tokens()) {
            var token = expanded.token();
            if (token.kind() == PreprocessingToken.Kind.HEADER_NAME) {
                assertFalse(body); out.append("#include ").append(token.spelling()).append('\n');
            } else if (token.kind() != PreprocessingToken.Kind.EOF) {
                if (!body) { out.append("#undef offsetof\n"); body = true; }
                assertNotEquals("offsetof", token.spelling());
                out.append(token.spelling()).append(' ');
            }
        }
        assertTrue(out.toString().contains("__builtin_offsetof"));
        return source("materialized", out.append('\n').toString());
    }
    private static String success(List<String> args, Path work) throws Exception {
        Path out = Files.createTempFile(work, "out", ".txt"), err = Files.createTempFile(work, "err", ".txt");
        Process process = new ProcessBuilder(args).directory(work.toFile())
                .redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), args.toString());
            assertEquals(0, process.exitValue(), () -> {
                try { return args + "\n" + Files.readString(out) + Files.readString(err); }
                catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            });
            return Files.readString(out);
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
