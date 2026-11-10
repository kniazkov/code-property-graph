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

    @Test void booleanPartialConversionsSurviveStagesAndHeaderEdits() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = program(header)
                    .replace("const int fixed=1;", "const int seed=-7;const int fixed=seed;")
                    .replace("template<class T,int N> struct Box {char data[N];char tail;};",
                            "template<class T,bool N> struct Box {char data[3];char tail;};")
                    .replace("template<class T> struct Box<T,pattern::second> {short data;char tail;};",
                            "template<class T,bool N> struct Other {short data;char tail;};"
                            + "template<class T> struct Other<T,fixed> {long data;char tail;};")
                    .replace("typedef Box<int,choice> ByEnum;", "typedef Other<int,false> ByEnum;");
            Path input = source("boolean-original", text);
            verify(input, false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    healthy(snapshot);
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.contains("fixed"));
                    verify(source("boolean-generated-" + stage, generated), false);
                    var seed = nodes(snapshot.files().getFirst(), VariableDeclaration.class).stream()
                            .filter(v -> v.name().equals("seed")).findFirst().orElseThrow();
                    var editor = new ProjectEditor(snapshot);
                    editor.replace(seed.initializer().orElseThrow(), IntegerLiteral.create().setValue(0).build());
                    Project edited = NameResolver.resolve(editor.build());
                    healthy(edited);
                    verify(source("boolean-edited-" + stage, Cpg.sources(edited, Language.CPP).get("main.cpp")), true);
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "boolean-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "boolean-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verify(run.work().resolve("generated/main.cpp"), false);
        }
    }

    @Test void unsignedPartialConversionsSurviveStagesAndHeaderEdits() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = program(header)
                    .replace("const int selected=1;", "const int selected=-1;")
                    .replace("const int fixed=1;", "const int seed=511;const int fixed=seed;")
                    .replace("template<class T,int N> struct Box {char data[N];char tail;};",
                            "template<class T,unsigned char N> struct Box {char data[3];char tail;};")
                    .replace("template<class T> struct Box<T,pattern::second> {short data;char tail;};",
                            "template<class T,unsigned char N> struct Other {short data;char tail;};"
                            + "template<class T> struct Other<T,fixed> {long data;char tail;};")
                    .replace("typedef Box<int,choice> ByEnum;", "typedef Other<int,0> ByEnum;");
            Path input = source("unsigned-original", text);
            verify(input, false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    healthy(snapshot);
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.contains("fixed"));
                    verify(source("unsigned-generated-" + stage, generated), false);
                    var seed = nodes(snapshot.files().getFirst(), VariableDeclaration.class).stream()
                            .filter(v -> v.name().equals("seed")).findFirst().orElseThrow();
                    var editor = new ProjectEditor(snapshot);
                    editor.replace(seed.initializer().orElseThrow(), IntegerLiteral.create().setValue(256).build());
                    Project edited = NameResolver.resolve(editor.build());
                    healthy(edited);
                    verify(source("unsigned-edited-" + stage, Cpg.sources(edited, Language.CPP).get("main.cpp")), true);
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "unsigned-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "unsigned-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verify(run.work().resolve("generated/main.cpp"), false);
        }
    }

    @Test void concretePrecedingPatternTypesSurviveStagesAndHeaderEdits() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = program(header)
                    .replace("const int selected=1;", "const int selected=-1;")
                    .replace("const int fixed=1;", "const int seed=511;const int fixed=seed;")
                    .replace("template<class T,int N> struct Box {char data[N];char tail;};",
                            "template<class T,T N,class U> struct Box {char data[3];char tail;};")
                    .replace("template<class T> struct Box<T,pattern::second> {short data;char tail;};",
                            "template<class T,unsigned char N> struct Other {short data;char tail;};"
                            + "template<class T> struct Other<T,fixed> {long data;char tail;};")
                    .replace("template<class T> struct Box<T,fixed> {long data;char tail;};", "template<class U> struct Box<unsigned char,fixed,U> {long data;char tail;};")
                    .replace("typedef Box<int,selected> ByConst;", "typedef Box<unsigned char,selected,int> ByConst;")
                    .replace("typedef Box<int,choice> ByEnum;", "typedef Other<int,0> ByEnum;");
            Path input = source("dependent-original", text);
            verify(input, false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    healthy(snapshot);
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.contains("fixed"));
                    verify(source("dependent-generated-" + stage, generated), false);
                    var seed = nodes(snapshot.files().getFirst(), VariableDeclaration.class).stream()
                            .filter(v -> v.name().equals("seed")).findFirst().orElseThrow();
                    var editor = new ProjectEditor(snapshot);
                    editor.replace(seed.initializer().orElseThrow(), IntegerLiteral.create().setValue(256).build());
                    Project edited = NameResolver.resolve(editor.build());
                    healthy(edited);
                    verify(source("dependent-edited-" + stage, Cpg.sources(edited, Language.CPP).get("main.cpp")), true);
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "dependent-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "dependent-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verify(run.work().resolve("generated/main.cpp"), false);
        }
    }

    @Test void nestedNamedPatternsSurviveStagesAndHeaderEdits() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = program(header)
                    .replace("const int fixed=1;", "const int seed=1;const int fixed=seed;")
                    .replace("template<class T,int N> struct Box {char data[N];char tail;};",
                            "template<class T,int N> struct Key {};"
                            + "template<class T> struct Box {char data[3];char tail;};")
                    .replace("Box<T,fixed>", "Box<Key<T,fixed> >")
                    .replace("Box<T,pattern::second>", "Box<Key<T,pattern::second> >")
                    .replace("Box<int,selected>", "Box<Key<int,selected> >")
                    .replace("Box<int,choice>", "Box<Key<int,choice> >");
            verifyNestedLifecycle(text, "nested-named", 2, true);
        }
    }

    @Test void nestedConversionsUseTheirOwnConcretePrecedingTypes() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            for (String kind : List.of("bool", "unsigned", "preceding")) {
                boolean bool = kind.equals("bool"), preceding = kind.equals("preceding");
                String key = preceding ? "template<class V,V N,class T> struct Key {};"
                        : "template<class T," + (bool ? "bool" : "unsigned char") + " N> struct Key {};";
                String pattern = preceding ? "Key<unsigned char,fixed,T>" : "Key<T,fixed>";
                String selected = preceding ? "Key<unsigned char,selected,int>" : "Key<int,selected>";
                String fallback = preceding ? "Key<unsigned char,0,int>" : "Key<int,false>";
                String text = program(header)
                        .replace("const int selected=1;", "const int selected=-1;")
                        .replace("const int fixed=1;", "const int seed=" + (bool ? "-7" : "511") + ";const int fixed=seed;")
                        .replace("template<class T,int N> struct Box {char data[N];char tail;};",
                                key + "template<class T> struct Box {char data[3];char tail;};")
                        .replace("Box<T,fixed>", "Box<" + pattern + " >")
                        .replace("template<class T> struct Box<T,pattern::second> {short data;char tail;};",
                                "template<class T> struct Other {short data;char tail;};"
                                + "template<class T> struct Other<" + pattern + " > {long data;char tail;};")
                        .replace("Box<int,selected>", "Box<" + selected + " >")
                        .replace("Box<int,choice>", "Other<" + fallback + " >");
                verifyNestedLifecycle(text, "nested-" + kind, bool ? 0 : 256, false);
            }
        }
    }

    @Test void nestedActualIntegralDefaultsSurviveStagesEditsIdentityAndPackagedCli() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = "#include <" + header + ">\n" + """
                    const int seed=2;
                    const int fixed=seed;
                    template<class T,int N=fixed> struct Key {};
                    template<class T> struct Box {char data;char tail;};
                    template<class T> struct Box<Key<T,2> > {long data;char tail;};
                    template<class A,class B> struct Same {char data;char tail;};
                    template<class T> struct Same<T,T> {long data;char tail;};
                    typedef Box<Key<int> > ByDefault;
                    typedef Box<Key<int,fixed> > ByExplicit;
                    typedef Box<Key<int,3> > ByThree;
                    typedef Same<Key<int>,Key<int,fixed> > ByRepeated;
                    unsigned long default_offset=offsetof(ByDefault,tail);
                    unsigned long explicit_offset=offsetof(ByExplicit,tail);
                    unsigned long three_offset=offsetof(ByThree,tail);
                    unsigned long repeated_offset=offsetof(ByRepeated,tail);
                    enum {extent=offsetof(ByDefault,tail)+1};
                    char array[extent];
                    int main(){return 0;}
                    """;
            Path input = source("nested-default-original", text);
            verifyNestedDefaults(input, false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    healthy(snapshot);
                    assertDefaultIdentity(snapshot, false);
                    String originalJson = ProjectJson.encode(snapshot).toText("  ");
                    assertNotNull(Json.parse(originalJson));
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.replaceAll("\\s+", "").contains("Box<Key<int>>"), generated);
                    verifyNestedDefaults(source("nested-default-generated-" + stage, generated), false);
                    Project changed = editDefaultSeed(snapshot, 3);
                    assertDefaultIdentity(changed, true);
                    String changedJson = ProjectJson.encode(changed).toText("  ");
                    String changedSource = Cpg.sources(changed, Language.CPP).get("main.cpp");
                    assertNotEquals(generated, changedSource);
                    verifyNestedDefaults(source("nested-default-edited-" + stage, changedSource), true);
                    Project restored = editDefaultSeed(changed, 2);
                    assertDefaultIdentity(restored, false);
                    verifyNestedDefaults(source("nested-default-restored-" + stage,
                            Cpg.sources(restored, Language.CPP).get("main.cpp")), false);
                    assertDefaultIdentity(snapshot, false);
                    assertEquals(generated, Cpg.sources(snapshot, Language.CPP).get("main.cpp"));
                    assertEquals(originalJson, ProjectJson.encode(snapshot).toText("  "));
                    assertDefaultIdentity(changed, true);
                    assertEquals(changedSource, Cpg.sources(changed, Language.CPP).get("main.cpp"));
                    assertEquals(changedJson, ProjectJson.encode(changed).toText("  "));
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "nested-default-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "nested-default-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verifyNestedDefaults(run.work().resolve("generated/main.cpp"), false);
        }
    }

    private static Project editDefaultSeed(Project snapshot, int value) {
        var seed = nodes(snapshot.files().getFirst(), VariableDeclaration.class).stream()
                .filter(v -> v.name().equals("seed")).findFirst().orElseThrow();
        var editor = new ProjectEditor(snapshot);
        editor.replace(seed.initializer().orElseThrow(), IntegerLiteral.create().setValue(value).build());
        Project result = NameResolver.resolve(editor.build());
        healthy(result);
        return result;
    }

    private static void assertDefaultIdentity(Project project, boolean edited) {
        var targets = new LinkedHashMap<String, ClassDeclaration>();
        for (String name : List.of("ByDefault", "ByExplicit", "ByThree", "ByRepeated")) {
            var alias = nodes(project.files().getFirst(), TypedefDeclaration.class).stream()
                    .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
            var type = assertInstanceOf(NamedType.class, alias.type().canonicalType());
            var declaration = assertInstanceOf(ClassDeclaration.class, type.declaration().orElseThrow());
            targets.put(name, declaration);
            var instance = project.classTemplateInstances().stream()
                    .filter(i -> i.declaration() == declaration).findFirst().orElseThrow();
            boolean partial = name.equals("ByRepeated") || !name.equals("ByThree") && !edited;
            assertEquals(partial ? TemplateKind.PARTIAL_SPECIALIZATION : TemplateKind.PRIMARY,
                    instance.selected().templateKind(), name);
        }
        assertSame(targets.get("ByDefault"), targets.get("ByExplicit"));
        if (edited) assertSame(targets.get("ByDefault"), targets.get("ByThree"));
        else assertNotSame(targets.get("ByDefault"), targets.get("ByThree"));
    }

    private void verifyNestedDefaults(Path source, boolean edited) throws Exception {
        Project reparsed = Cpg.read(source.getParent(), options(Cpg.Stage.RESOLVED));
        healthy(reparsed);
        assertDefaultIdentity(reparsed, edited);
        Path expanded = materialize(source);
        Project materialized = Cpg.read(expanded.getParent(), options(Cpg.Stage.RESOLVED));
        healthy(materialized);
        assertDefaultIdentity(materialized, edited);
        for (Path tested : List.of(source, expanded)) for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "nested-default-native");
            Path wrapper = work.resolve("wrapper.cpp"), binary = work.resolve("probe");
            Files.writeString(wrapper, """
                    #include <cstddef>
                    #include <climits>
                    #include <cstdio>
                    #if !defined(__x86_64__) || !defined(__LP64__)
                    #error selected target mismatch
                    #endif
                    #define main tested_main
                    #include "%s"
                    #undef main
                    typedef char default_ice[(__builtin_offsetof(ByDefault,tail)==%d)?1:-1];
                    typedef char explicit_ice[(__builtin_offsetof(ByExplicit,tail)==%d)?1:-1];
                    typedef char three_ice[(__builtin_offsetof(ByThree,tail)==1)?1:-1];
                    typedef char repeated_ice[(__builtin_offsetof(ByRepeated,tail)==8)?1:-1];
                    int same_type(ByDefault*){return 1;}
                    template<class T> int same_type(T*){return 0;}
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        if(default_offset!=%d || explicit_offset!=%d || three_offset!=1 || repeated_offset!=8) return 11;
                        if(sizeof(array)!=%d || !same_type((ByExplicit*)0)) return 12;
                        std::printf("default=%%lu explicit=%%lu three=%%lu repeated=%%lu extent=%%lu\\n",
                            default_offset, explicit_offset, three_offset, repeated_offset, (unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\""),
                            edited ? 1 : 8, edited ? 1 : 8, edited ? 1 : 8, edited ? 1 : 8, edited ? 2 : 9));
            List<String> command = List.of(compiler, "-std=c++03", "-pedantic-errors", wrapper.toString(), "-o", binary.toString());
            success(command, work);
            String observations = success(List.of(binary.toString()), work);
            assertEquals(edited ? "default=1 explicit=1 three=1 repeated=8 extent=2\n"
                    : "default=8 explicit=8 three=1 repeated=8 extent=9\n", observations);
            Path evidence = Files.createDirectories(Path.of("target/partial-constant-template-arguments-oracle")
                    .resolve(String.format("defaults-%03d-%s", ++execution, hash(tested).substring(0, 16)))
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

    private void verifyNestedLifecycle(String text, String label, int replacement, boolean editEnum) throws Exception {
        Path input = source(label, text);
        verify(input, false);
        for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            Project project = Cpg.read(input.getParent(), options(stage));
            for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                healthy(snapshot);
                assertNestedSelection(snapshot, false);
                assertFalse(snapshot.classTemplateInstances().isEmpty());
                String json = ProjectJson.encode(snapshot).toText("  ");
                assertNotNull(Json.parse(json));
                assertTrue(json.contains("OffsetofExpression"));
                String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                assertTrue(generated.contains("fixed") && generated.contains("Key<"), generated);
                verify(source(label + "-generated-" + stage, generated), false);
                var editor = new ProjectEditor(snapshot);
                var seed = nodes(snapshot.files().getFirst(), VariableDeclaration.class).stream()
                        .filter(v -> v.name().equals("seed")).findFirst().orElseThrow();
                editor.replace(seed.initializer().orElseThrow(), IntegerLiteral.create().setValue(replacement).build());
                if (editEnum) {
                    var second = nodes(snapshot.files().getFirst(), EnumeratorDeclaration.class).stream()
                            .filter(e -> e.name().equals("second")).findFirst().orElseThrow();
                    editor.replace(second.value().orElseThrow(), IntegerLiteral.create().setValue(4).build());
                }
                Project edited = NameResolver.resolve(editor.build());
                healthy(edited);
                assertNestedSelection(edited, true);
                String changed = Cpg.sources(edited, Language.CPP).get("main.cpp");
                assertNotEquals(generated, changed);
                verify(source(label + "-edited-" + stage, changed), true);
                // A real edit invalidates selection in the new snapshot, not its predecessor.
                assertNestedSelection(snapshot, false);
                assertEquals(generated, Cpg.sources(snapshot, Language.CPP).get("main.cpp"));
                assertEquals(json, ProjectJson.encode(snapshot).toText("  "));
            }
        }
        var cli = new CliSandbox(Files.createTempDirectory(temp, label + "-cli")).source("main.cpp", text);
        Path profile = Files.createTempFile(temp, label + "-profile", ".json");
        Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
        var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                "--platform-profile", profile.toString()).expectStatus(0);
        assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
        verify(run.work().resolve("generated/main.cpp"), false);
    }

    private static void assertNestedSelection(Project project, boolean edited) {
        for (String name : List.of("ByConst", "ByEnum")) {
            var alias = nodes(project.files().getFirst(), TypedefDeclaration.class).stream()
                    .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
            var type = assertInstanceOf(NamedType.class, alias.type().canonicalType());
            var declaration = assertInstanceOf(ClassDeclaration.class, type.declaration().orElseThrow());
            assertTrue(project.classTemplateInstances().stream().anyMatch(i -> i.declaration() == declaration));
            var data = declaration.members().stream().filter(VariableDeclaration.class::isInstance)
                    .map(VariableDeclaration.class::cast).filter(v -> v.name().equals("data")).findFirst().orElseThrow();
            var field = data.type().orElseThrow().canonicalType();
            if (edited && name.equals("ByConst")) {
                var array = assertInstanceOf(ArrayType.class, field);
                assertEquals("char", array.elementType().canonicalIdentity());
                assertEquals(3, assertInstanceOf(IntegerLiteral.class, array.bound().orElseThrow()).value());
            } else assertEquals(name.equals("ByConst") || edited ? "long" : "short", field.canonicalIdentity());
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
