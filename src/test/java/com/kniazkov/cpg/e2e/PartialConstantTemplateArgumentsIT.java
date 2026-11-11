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

    @Test void nestedPartialDefaultsSurviveStagesEditsAndPackagedCli() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = "#include <" + header + ">\n" + """
                    const int seed=2;
                    const int fixed=seed;
                    namespace nested {
                    template<class T,int N=fixed> struct Key {};
                    template<class T> struct Box {char data;char tail;};
                    template<class T> struct Box<Key<T> > {long data;char tail;};
                    template<class T> struct Box<Key<T*> > {short data;char tail;};
                    }
                    template<class T,class V,V N=258> struct Converted {};
                    template<class T> struct ConvertBox {char data;char tail;};
                    template<class T> struct ConvertBox<Converted<T,unsigned char> > {long data;char tail;};
                    template<class T,bool N=2> struct Boolean {};
                    template<class T> struct BoolBox {char data;char tail;};
                    template<class T> struct BoolBox<Boolean<T> > {long data;char tail;};
                    enum {second=2};
                    template<class T,int N=second,unsigned char M=258> struct Multi {};
                    template<class T> struct MultiBox {char data;char tail;};
                    template<class T> struct MultiBox<Multi<T> > {long data;char tail;};
                    template<class T,class U> struct Pair {char data;char tail;};
                    template<class T> struct Pair<T,T> {long data;char tail;};
                    typedef nested::Box<nested::Key<int,2> > ByExplicit;
                    typedef nested::Box<nested::Key<int> > ByOmitted;
                    typedef nested::Box<nested::Key<int,3> > ByThree;
                    typedef nested::Box<nested::Key<int*> > ByOrdered;
                    typedef ConvertBox<Converted<int,unsigned char,2> > ByByte;
                    typedef BoolBox<Boolean<int,true> > ByBool;
                    typedef MultiBox<Multi<int,2,2> > ByMulti;
                    typedef Pair<nested::Key<int>,nested::Key<int,fixed> > ByRepeated;
                    unsigned long explicit_offset=offsetof(ByExplicit,tail);
                    unsigned long omitted_offset=offsetof(ByOmitted,tail);
                    unsigned long three_offset=offsetof(ByThree,tail);
                    unsigned long ordered_offset=offsetof(ByOrdered,tail);
                    unsigned long byte_offset=offsetof(ByByte,tail);
                    unsigned long bool_offset=offsetof(ByBool,tail);
                    unsigned long multi_offset=offsetof(ByMulti,tail);
                    unsigned long repeated_offset=offsetof(ByRepeated,tail);
                    enum {extent=offsetof(ByExplicit,tail)+1};
                    char array[extent];
                    int main(){return 0;}
                    """;
            Path input = source("partial-default-original", text);
            verifyPartialDefaults(input, false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    assertPartialDefaultIdentity(snapshot, false);
                    String originalJson = ProjectJson.encode(snapshot).toText("  ");
                    assertNotNull(Json.parse(originalJson));
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.replaceAll("\\s+", "").contains("Box<Key<T>>"), generated);
                    Path original = source("partial-default-generated-" + stage, generated);
                    verifyPartialDefaults(original, false);
                    Project changed = editDefaultSeed(snapshot, 3);
                    assertPartialDefaultIdentity(changed, true);
                    String changedJson = ProjectJson.encode(changed).toText("  ");
                    String changedSource = Cpg.sources(changed, Language.CPP).get("main.cpp");
                    Path edited = source("partial-default-edited-" + stage, changedSource);
                    assertNotEquals(hash(original), hash(edited));
                    verifyPartialDefaults(edited, true);
                    Project restored = editDefaultSeed(changed, 2);
                    assertPartialDefaultIdentity(restored, false);
                    verifyPartialDefaults(source("partial-default-restored-" + stage,
                            Cpg.sources(restored, Language.CPP).get("main.cpp")), false);
                    assertPartialDefaultIdentity(snapshot, false);
                    assertEquals(generated, Cpg.sources(snapshot, Language.CPP).get("main.cpp"));
                    assertEquals(originalJson, ProjectJson.encode(snapshot).toText("  "));
                    assertPartialDefaultIdentity(changed, true);
                    assertEquals(changedSource, Cpg.sources(changed, Language.CPP).get("main.cpp"));
                    assertEquals(changedJson, ProjectJson.encode(changed).toText("  "));
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "partial-default-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "partial-default-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verifyPartialDefaults(run.work().resolve("generated/main.cpp"), false);
        }
    }

    private static final List<String> TYPE_DEFAULT_ALIASES = List.of("ByOmitted", "ByType", "ByExplicit",
            "ByThree", "ByWrongType", "ByOrdered", "ByPure", "ByCv", "ByCvMismatch", "ByBool", "BySigned",
            "ByMulti", "ByRepeated", "ByBoth", "ByReverse");

    private static String typeDefaultProgram(String header) {
        String declarations = "#include <" + header + ">\n" + """
                const int seed=258;
                const int fixed=seed;
                namespace nested {
                template<class T,class V=unsigned char,V N=fixed> struct Key {};
                template<class T> struct Box {char data;char tail;};
                template<class T> struct Box<Key<T> >;
                template<class U> struct Box<Key<U> > {long data;char tail;};
                template<class T> struct Box<Key<T*> > {short data;char tail;};
                }
                template<class T,class V=int> struct Pure {};
                template<class T> struct PureBox {char data;char tail;};
                template<class T> struct PureBox<Pure<T> > {long data;char tail;};
                template<class T,class V=const int> struct Cv {};
                template<class T> struct CvBox {char data;char tail;};
                template<class T> struct CvBox<Cv<T> > {long data;char tail;};
                template<class T,class V=bool,V N=2> struct Boolean {};
                template<class T> struct BoolBox {char data;char tail;};
                template<class T> struct BoolBox<Boolean<T> > {long data;char tail;};
                template<class T,class V=signed char,V N=-7> struct Signed {};
                template<class T> struct SignedBox {char data;char tail;};
                template<class T> struct SignedBox<Signed<T> > {long data;char tail;};
                template<class T,class V=unsigned char,V N=258,class W=short,W M=7> struct Multi {};
                template<class T> struct MultiBox {char data;char tail;};
                template<class T> struct MultiBox<Multi<T> > {long data;char tail;};
                template<class T,class U> struct Pair {char data;char tail;};
                template<class T> struct Pair<T,T> {long data;char tail;};
                namespace nested {
                template<class T,class U> struct Both {char data;char tail;};
                template<class T> struct Both<Key<T,bool>,Key<T,unsigned char> > {long data;char tail;};
                template<class T,class U> struct Reverse {char data;char tail;};
                template<class T> struct Reverse<Key<T,unsigned char>,Key<T,bool> > {long data;char tail;};
                }
                typedef nested::Box<nested::Key<int> > ByOmitted;
                typedef nested::Box<nested::Key<int,unsigned char> > ByType;
                typedef nested::Box<nested::Key<int,unsigned char,2> > ByExplicit;
                typedef nested::Box<nested::Key<int,unsigned char,3> > ByThree;
                typedef nested::Box<nested::Key<int,unsigned short,258> > ByWrongType;
                typedef nested::Box<nested::Key<int*> > ByOrdered;
                typedef PureBox<Pure<int,int> > ByPure;
                typedef CvBox<Cv<int,const int> > ByCv;
                typedef CvBox<Cv<int,int> > ByCvMismatch;
                typedef BoolBox<Boolean<int,bool,true> > ByBool;
                typedef SignedBox<Signed<int,signed char,-7> > BySigned;
                typedef MultiBox<Multi<int,unsigned char,2,short,7> > ByMulti;
                typedef Pair<nested::Key<int>,nested::Key<int,unsigned char,fixed> > ByRepeated;
                typedef nested::Both<nested::Key<int,bool,true>,nested::Key<int,unsigned char,2> > ByBoth;
                typedef nested::Reverse<nested::Key<int,unsigned char,2>,nested::Key<int,bool,true> > ByReverse;
                """;
        var result = new StringBuilder(declarations);
        for (String alias : TYPE_DEFAULT_ALIASES)
            result.append("unsigned long offset_").append(alias).append("=offsetof(").append(alias).append(",tail);\n");
        return result.append("enum {extent=offsetof(ByExplicit,tail)+1};char array[extent];int main(){return 0;}\n").toString();
    }

    private static int typeDefaultOffset(String alias, int edit) {
        if (alias.equals("ByOrdered")) return 2;
        if (alias.equals("ByCvMismatch")) return 1;
        if (alias.equals("ByWrongType")) return edit == 2 ? 8 : 1;
        if (alias.equals("ByThree")) return edit == 1 ? 8 : 1;
        if (alias.equals("ByExplicit")) return edit == 0 ? 8 : 1;
        if (alias.equals("ByType") || alias.equals("ByRepeated")) return edit == 2 ? 1 : 8;
        if (alias.equals("ByBoth") || alias.equals("ByReverse")) return edit == 1 ? 1 : 8;
        return 8;
    }

    private static void assertTypeDefaultIdentity(Project project, int edit) {
        healthy(project);
        var targets = new LinkedHashMap<String, ClassDeclaration>();
        for (String name : TYPE_DEFAULT_ALIASES) {
            var alias = nodes(project.files().getFirst(), TypedefDeclaration.class).stream()
                    .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
            var type = assertInstanceOf(NamedType.class, alias.type().canonicalType());
            var declaration = assertInstanceOf(ClassDeclaration.class, type.declaration().orElseThrow());
            targets.put(name, declaration);
            var instance = project.classTemplateInstances().stream()
                    .filter(i -> i.declaration() == declaration).findFirst().orElseThrow();
            int expected = typeDefaultOffset(name, edit);
            assertEquals(expected == 1 ? TemplateKind.PRIMARY : TemplateKind.PARTIAL_SPECIALIZATION,
                    instance.selected().templateKind(), name);
            var data = declaration.members().stream().filter(VariableDeclaration.class::isInstance)
                    .map(VariableDeclaration.class::cast).filter(v -> v.name().equals("data")).findFirst().orElseThrow();
            assertEquals(expected == 1 ? "char" : expected == 2 ? "short" : "long",
                    data.type().orElseThrow().canonicalIdentity(), name);
        }
        if (edit == 0) assertSame(targets.get("ByOmitted"), targets.get("ByExplicit"));
        if (edit == 1) assertSame(targets.get("ByOmitted"), targets.get("ByThree"));
        if (edit == 2) assertSame(targets.get("ByOmitted"), targets.get("ByWrongType"));
        assertNotSame(targets.get("ByCv"), targets.get("ByCvMismatch"));
    }

    @Test void nestedIntegralTypeDefaultsSurviveLifecycleAndNativeOracle() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = typeDefaultProgram(header);
            verifyTypeDefaults(source("type-default-original", text), 0);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Path input = source("type-default-stage", text);
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    assertTypeDefaultIdentity(snapshot, 0);
                    String json = ProjectJson.encode(snapshot).toText("  ");
                    assertNotNull(Json.parse(json));
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.replaceAll("\\s+", "").contains("Box<Key<U>>"), generated);
                    // Compile each stage's source; copies and reanalysis are also checked above.
                    if (snapshot == project) verifyTypeDefaults(source("type-default-generated-" + stage, generated), 0);
                    Project seedChanged = editDefaultSeed(snapshot, 259);
                    assertTypeDefaultIdentity(seedChanged, 1);
                    var key = nodes(snapshot.files().getFirst(), ClassDeclaration.class).stream()
                            .filter(c -> c.name().equals("Key") && c.templateKind() == TemplateKind.PRIMARY)
                            .findFirst().orElseThrow();
                    var editor = new ProjectEditor(snapshot);
                    editor.replace(key.templateParameters().get(1).defaultArgument().orElseThrow().type().orElseThrow(),
                            NamedType.create().setName("unsigned short").build());
                    Project typeChanged = NameResolver.resolve(editor.build());
                    assertTypeDefaultIdentity(typeChanged, 2);
                    if (snapshot == project) {
                        verifyTypeDefaults(source("type-default-seed-edited-" + stage,
                                Cpg.sources(seedChanged, Language.CPP).get("main.cpp")), 1);
                        verifyTypeDefaults(source("type-default-type-edited-" + stage,
                                Cpg.sources(typeChanged, Language.CPP).get("main.cpp")), 2);
                    }
                    assertTypeDefaultIdentity(snapshot, 0);
                    assertEquals(generated, Cpg.sources(snapshot, Language.CPP).get("main.cpp"));
                    assertEquals(json, ProjectJson.encode(snapshot).toText("  "));
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "type-default-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "type-default-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verifyTypeDefaults(run.work().resolve("generated/main.cpp"), 0);
        }
    }

    private static String typedefDefaultProgram(String header) {
        return typeDefaultProgram(header)
                .replace("const int seed=258;", """
                        typedef int Integer;
                        namespace cfg {
                        typedef unsigned char KeyByte;
                        typedef KeyByte Count;
                        typedef const Integer ConstInteger;
                        typedef bool Boolean;
                        typedef signed char Signed;
                        typedef unsigned char Byte;
                        typedef short Short;
                        }
                        const int seed=258;
                        """)
                .replace("class V=unsigned char,V N=fixed", "class V=cfg::Count,V N=fixed")
                .replace("class V=int>", "class V=Integer>")
                .replace("class V=const int>", "class V=cfg::ConstInteger>")
                .replace("class V=bool,V N=2", "class V=cfg::Boolean,V N=2")
                .replace("class V=signed char,V N=-7", "class V=cfg::Signed,V N=-7")
                .replace("class V=unsigned char,V N=258,class W=short", "class V=cfg::Byte,V N=258,class W=cfg::Short");
    }

    @Test void nestedIntegralTypedefDefaultsSurviveLifecycleAndNativeOracle() throws Exception {
        for (String header : List.of("cstddef", "stddef.h")) {
            String text = typedefDefaultProgram(header);
            verifyTypeDefaults(source("typedef-default-original", text), 0);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Path input = source("typedef-default-stage", text);
                Project project = Cpg.read(input.getParent(), options(stage));
                for (Project snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project.edit().build()))) {
                    assertTypeDefaultIdentity(snapshot, 0);
                    String json = ProjectJson.encode(snapshot).toText("  ");
                    assertNotNull(Json.parse(json));
                    String generated = Cpg.sources(snapshot, Language.CPP).get("main.cpp");
                    assertTrue(generated.replaceAll("\\s+", "").contains("Box<Key<U>>"), generated);
                    // Compile each stage's source; copies and reanalysis are also checked above.
                    if (snapshot == project) verifyTypeDefaults(source("typedef-default-generated-" + stage, generated), 0);
                    Project seedChanged = editDefaultSeed(snapshot, 259);
                    assertTypeDefaultIdentity(seedChanged, 1);
                    var keyAlias = nodes(snapshot.files().getFirst(), TypedefDeclaration.class).stream()
                            .filter(t -> t.name().equals("KeyByte")).findFirst().orElseThrow();
                    var editor = new ProjectEditor(snapshot);
                    editor.replace(keyAlias.type(), NamedType.create().setName("unsigned short").build());
                    Project typeChanged = NameResolver.resolve(editor.build());
                    assertTypeDefaultIdentity(typeChanged, 2);
                    var restoreAlias = nodes(typeChanged.files().getFirst(), TypedefDeclaration.class).stream()
                            .filter(t -> t.name().equals("KeyByte")).findFirst().orElseThrow();
                    var restore = new ProjectEditor(typeChanged);
                    restore.replace(restoreAlias.type(), NamedType.create().setName("unsigned char").build());
                    Project restored = NameResolver.resolve(restore.build());
                    assertTypeDefaultIdentity(restored, 0);
                    if (snapshot == project) {
                        verifyTypeDefaults(source("typedef-default-restored-" + stage,
                                Cpg.sources(restored, Language.CPP).get("main.cpp")), 0);
                        verifyTypeDefaults(source("typedef-default-seed-edited-" + stage,
                                Cpg.sources(seedChanged, Language.CPP).get("main.cpp")), 1);
                        verifyTypeDefaults(source("typedef-default-type-edited-" + stage,
                                Cpg.sources(typeChanged, Language.CPP).get("main.cpp")), 2);
                    }
                    assertTypeDefaultIdentity(snapshot, 0);
                    assertEquals(generated, Cpg.sources(snapshot, Language.CPP).get("main.cpp"));
                    assertEquals(json, ProjectJson.encode(snapshot).toText("  "));
                }
            }
            var cli = new CliSandbox(Files.createTempDirectory(temp, "typedef-default-cli")).source("main.cpp", text);
            Path profile = Files.createTempFile(temp, "typedef-default-profile", ".json");
            Files.writeString(profile, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                    "--platform-profile", profile.toString()).expectStatus(0);
            assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
            verifyTypeDefaults(run.work().resolve("generated/main.cpp"), 0);
        }
    }

    private void verifyTypeDefaults(Path source, int edit) throws Exception {
        assertTypeDefaultIdentity(Cpg.read(source.getParent(), options(Cpg.Stage.RESOLVED)), edit);
        Path expanded = materialize(source);
        assertTypeDefaultIdentity(Cpg.read(expanded.getParent(), options(Cpg.Stage.RESOLVED)), edit);
        for (Path tested : List.of(source, expanded)) for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "type-default-native");
            Path wrapper = work.resolve("wrapper.cpp"), binary = work.resolve("probe");
            var code = new StringBuilder("""
                    #include <cstddef>
                    #include <climits>
                    #include <cstdio>
                    #if !defined(__x86_64__) || !defined(__LP64__)
                    #error selected target mismatch
                    #endif
                    #define main tested_main
                    #include "%s"
                    #undef main
                    """.formatted(tested.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"")));
            for (String alias : TYPE_DEFAULT_ALIASES)
                code.append("typedef char ice_").append(alias).append("[(__builtin_offsetof(").append(alias)
                        .append(",tail)==").append(typeDefaultOffset(alias, edit)).append(")?1:-1];\n");
            code.append("int main(){if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;\n");
            var expected = new StringBuilder();
            for (String alias : TYPE_DEFAULT_ALIASES) {
                int offset = typeDefaultOffset(alias, edit);
                code.append("if(offset_").append(alias).append("!=").append(offset).append(") return 11;\n")
                        .append("std::printf(\"").append(alias).append("=%lu\\n\",offset_").append(alias).append(");\n");
                expected.append(alias).append('=').append(offset).append('\n');
            }
            code.append("if(sizeof(array)!=").append(typeDefaultOffset("ByExplicit", edit) + 1)
                    .append(") return 12; return tested_main();}\n");
            Files.writeString(wrapper, code);
            List<String> command = List.of(compiler, "-std=c++03", "-pedantic-errors", wrapper.toString(), "-o", binary.toString());
            success(command, work);
            String observations = success(List.of(binary.toString()), work);
            assertEquals(expected.toString(), observations);
            Path evidence = Files.createDirectories(Path.of("target/partial-constant-template-arguments-oracle")
                    .resolve(String.format("type-defaults-%03d-%s", ++execution, hash(tested).substring(0, 16)))
                    .resolve(compiler.equals("g++") ? "gcc" : "clang"));
            Files.writeString(evidence.resolve("tested.cpp"), Files.readString(tested));
            Files.writeString(evidence.resolve("wrapper.cpp"), code);
            Files.writeString(evidence.resolve("observations.txt"), observations);
            Files.writeString(evidence.resolve("metadata.json"), Json.serialize(Map.of(
                    "compiler", compiler, "version", success(List.of(compiler, "--version"), work),
                    "target", success(List.of(compiler, "-dumpmachine"), work), "command", command,
                    "profile", PROFILE.toSettings(), "sourceSha256", hash(tested),
                    "materialized", tested.equals(expanded), "edit", edit)));
        }
    }

    private static void assertPartialDefaultIdentity(Project project, boolean edited) {
        healthy(project);
        var targets = new LinkedHashMap<String, ClassDeclaration>();
        for (String name : List.of("ByExplicit", "ByOmitted", "ByThree", "ByOrdered", "ByByte", "ByBool", "ByMulti", "ByRepeated")) {
            var alias = nodes(project.files().getFirst(), TypedefDeclaration.class).stream()
                    .filter(t -> t.name().equals(name)).findFirst().orElseThrow();
            var type = assertInstanceOf(NamedType.class, alias.type().canonicalType());
            var declaration = assertInstanceOf(ClassDeclaration.class, type.declaration().orElseThrow());
            targets.put(name, declaration);
            var instance = project.classTemplateInstances().stream()
                    .filter(i -> i.declaration() == declaration).findFirst().orElseThrow();
            boolean partial = name.equals("ByExplicit") ? !edited : !name.equals("ByThree") || edited;
            assertEquals(partial ? TemplateKind.PARTIAL_SPECIALIZATION : TemplateKind.PRIMARY,
                    instance.selected().templateKind(), name);
            var data = declaration.members().stream().filter(VariableDeclaration.class::isInstance)
                    .map(VariableDeclaration.class::cast).filter(v -> v.name().equals("data")).findFirst().orElseThrow();
            assertEquals(!partial ? "char" : name.equals("ByOrdered") ? "short" : "long",
                    data.type().orElseThrow().canonicalIdentity(), name);
        }
        assertSame(targets.get("ByOmitted"), targets.get(edited ? "ByThree" : "ByExplicit"));
        assertNotSame(targets.get("ByExplicit"), targets.get("ByThree"));
    }

    private void verifyPartialDefaults(Path source, boolean edited) throws Exception {
        assertPartialDefaultIdentity(Cpg.read(source.getParent(), options(Cpg.Stage.RESOLVED)), edited);
        Path expanded = materialize(source);
        assertPartialDefaultIdentity(Cpg.read(expanded.getParent(), options(Cpg.Stage.RESOLVED)), edited);
        for (Path tested : List.of(source, expanded)) for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "partial-default-native");
            Path wrapper = work.resolve("wrapper.cpp"), binary = work.resolve("probe");
            int explicit = edited ? 1 : 8, three = edited ? 8 : 1;
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
                    typedef char explicit_ice[(__builtin_offsetof(ByExplicit,tail)==%d)?1:-1];
                    typedef char omitted_ice[(__builtin_offsetof(ByOmitted,tail)==8)?1:-1];
                    typedef char three_ice[(__builtin_offsetof(ByThree,tail)==%d)?1:-1];
                    typedef char ordered_ice[(__builtin_offsetof(ByOrdered,tail)==2)?1:-1];
                    int same_type(ByOmitted*){return 1;}
                    template<class T> int same_type(T*){return 0;}
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        if(explicit_offset!=%d || omitted_offset!=8 || three_offset!=%d || ordered_offset!=2) return 11;
                        if(byte_offset!=8 || bool_offset!=8 || multi_offset!=8 || repeated_offset!=8) return 12;
                        if(sizeof(array)!=%d || !same_type((%s*)0)) return 13;
                        std::printf("explicit=%%lu omitted=%%lu three=%%lu ordered=%%lu byte=%%lu bool=%%lu multi=%%lu repeated=%%lu extent=%%lu\\n",
                            explicit_offset,omitted_offset,three_offset,ordered_offset,byte_offset,bool_offset,
                            multi_offset,repeated_offset,(unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\""),
                            explicit, three, explicit, three, explicit + 1, edited ? "ByThree" : "ByExplicit"));
            List<String> command = List.of(compiler, "-std=c++03", "-pedantic-errors", wrapper.toString(), "-o", binary.toString());
            success(command, work);
            String observations = success(List.of(binary.toString()), work);
            assertEquals("explicit=" + explicit + " omitted=8 three=" + three
                    + " ordered=2 byte=8 bool=8 multi=8 repeated=8 extent=" + (explicit + 1) + "\n", observations);
            Path evidence = Files.createDirectories(Path.of("target/partial-constant-template-arguments-oracle")
                    .resolve(String.format("partial-defaults-%03d-%s", ++execution, hash(tested).substring(0, 16)))
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
