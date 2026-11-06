/* Printed C++03 5.2.8/6: typeinfo must precede each use. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TypeidHeaderValidationTest {
    @TempDir Path directory;
    private Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse(directory.resolve("main.cpp").toString(), source, Language.CPP)).build());
    }
    private Project physical(String source, Map<String,String> headers) {
        var path = directory.resolve("main.cpp");
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), directory,
                List.of(new Build.IncludePath("-I", directory)), List.of(), "c++03");
        var sources = new HashMap<String,String>();
        headers.forEach((name, contents) -> sources.put(directory.resolve(name).toString(), contents));
        sources.put(path.toString(), source);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source, sources)).build());
    }
    private List<TypeidExpression> uses(Node root) {
        var result = new ArrayList<TypeidExpression>();
        root.walk(new NodeVisitor() { @Override public void visit(TypeidExpression expression) { result.add(expression); } });
        return result;
    }
    private boolean missing(TypeidExpression expression) {
        return expression.diagnostics().stream().anyMatch(d -> d.code().equals("missing-typeinfo"));
    }
    private void valid(Project project) {
        assertFalse(project.hasErrors(), () -> project.semanticDiagnostics().toString());
        assertFalse(uses(project).isEmpty());
        assertTrue(uses(project).stream().noneMatch(this::missing));
    }
    @Test void everyUseRequiresHeaderIncludingVoidPointersAndUnevaluatedOperands() {
        var project = resolve("struct S; void f(int i) { typeid(int); typeid(i); typeid(S*); typeid(void); sizeof(typeid(int)); }");
        assertEquals(5, uses(project).size());
        assertTrue(uses(project).stream().allMatch(this::missing));
    }
    @Test void laterIncludeDoesNotRepairEarlierUse() {
        var project = resolve("void early(){typeid(int);}\n#include <typeinfo>\nvoid late(){typeid(int);}\n");
        assertTrue(missing(uses(project).get(0)));
        assertFalse(missing(uses(project).get(1)));
        valid(resolve("#include <typeinfo>\nstruct S; void f(){typeid(int);typeid(S*);typeid(void);}"));
    }
    @Test void unrelatedHeadersAndTypeInfoForwardDeclarationsDoNotSatisfyRequirement() {
        for (String prefix : List.of("#include <exception>\n", "namespace std {class type_info;}\n"))
            assertTrue(missing(uses(resolve(prefix + "void f(){typeid(int);}")).getFirst()));
    }
    @Test void macroIncludesAndInactiveBranchesUseExecutedOrder() {
        valid(resolve("#define RTTI <typeinfo>\n#include RTTI\nvoid f(){typeid(int);}"));
        var macroUse = resolve("#define INSPECT typeid(int)\nvoid early(){INSPECT;}\n#include <typeinfo>\nvoid late(){INSPECT;}");
        assertTrue(missing(uses(macroUse).getFirst()));
        assertFalse(missing(uses(macroUse).getLast()));
        var project = resolve("#if 0\n#include <typeinfo>\n#endif\nvoid f(){typeid(int);}");
        assertTrue(missing(uses(project).getFirst()));
    }
    @Test void includesDoNotLeakAcrossTranslationUnits() {
        var project = NameResolver.resolve(Project.create()
                .addFile(Parser.parse(directory.resolve("a.cpp").toString(), "#include <typeinfo>\nvoid a(){typeid(int);}", Language.CPP))
                .addFile(Parser.parse(directory.resolve("b.cpp").toString(), "void b(){typeid(int);}", Language.CPP)).build());
        assertFalse(missing(uses(project.files().get(0)).getFirst()));
        assertTrue(missing(uses(project.files().get(1)).getFirst()));
    }
    @Test void localWrappersAndRepeatedHeaderOccurrencesKeepTheirOwnOrder() {
        var project = physical("#include \"use.hpp\"\n#include \"wrapper.hpp\"\n#include \"use.hpp\"\n",
                Map.of("use.hpp", "static void f(){typeid(int);}\n", "wrapper.hpp", "#include <typeinfo>\n"));
        assertTrue(missing(uses(project).get(0)));
        assertFalse(missing(uses(project).get(1)));
        valid(physical("#include \"wrapper.hpp\"\nvoid f(){typeid(int);}", Map.of("wrapper.hpp", "#include <typeinfo>\n")));
    }
    @Test void physicalAndEmptyTypeinfoHeadersRetainIncludeEvidence() {
        for (String contents : List.of("", "namespace std { class type_info {}; }\n")) {
            var project = physical("#define RTTI <typeinfo>\n#include RTTI\nvoid f(){typeid(int);}", Map.of("typeinfo", contents));
            valid(project);
            var directive = project.files().getFirst().provenance().orElseThrow().unit().directives().stream()
                    .filter(d -> "typeinfo".equals(d.includedHeader())).findFirst().orElseThrow();
            assertTrue(directive.startToken() >= 0);
            String json = ProjectJson.encode(project).toText("");
            assertTrue(json.contains("\"includedHeader\""));
            assertEquals(json, ProjectJson.encode(project.edit().build()).toText(""));
        }
        var late = physical("void early(){typeid(int);}\n#include <typeinfo>\nvoid late(){typeid(int);}", Map.of("typeinfo", ""));
        assertTrue(missing(uses(late).getFirst()));
        assertFalse(missing(uses(late).getLast()));
    }
    @Test void failedOrInactivePhysicalIncludesDoNotRecordSuccessfulActivation() {
        var path = directory.resolve("failed.cpp");
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), directory,
                List.of(new Build.IncludePath("-I", directory)), List.of(), "c++03");
        String source = "void early(){typeid(int); }\n#include <typeinfo>\n";
        var file = Parser.parseRecovering(unit, source, Map.of(path.toString(), source,
                directory.resolve("typeinfo").toString(), "#error cannot include this header\n")).build();
        assertTrue(file.hasErrors());
        assertTrue(file.provenance().orElseThrow().unit().directives().stream()
                .noneMatch(d -> "typeinfo".equals(d.includedHeader())));
        assertTrue(missing(uses(NameResolver.resolve(Project.create().addFile(file.edit()).build())).getFirst()));
        var inactive = physical("#if 0\n#include <typeinfo>\n#endif\nvoid f(){typeid(int);}", Map.of("typeinfo", ""));
        assertTrue(missing(uses(inactive).getFirst()));
        assertTrue(inactive.files().getFirst().provenance().orElseThrow().unit().directives().stream()
                .noneMatch(d -> "typeinfo".equals(d.includedHeader())));
    }
    @Test void templateInstantiationDoesNotMoveTheOriginalUsePastTheHeader() {
        var early = resolve("template<class T> void f(){typeid(T);}\n#include <typeinfo>\nvoid use(){f<int>();}");
        assertFalse(early.templateInstances().isEmpty());
        assertTrue(uses(early).stream().allMatch(this::missing));
        valid(resolve("#include <typeinfo>\ntemplate<class T> void f(){typeid(T);}\nvoid use(){f<int>();}"));
        var cls = resolve("template<class T> struct C {void f(){typeid(T);}};\n#include <typeinfo>\nvoid use(){C<int> c;c.f();}");
        assertFalse(cls.classTemplateInstances().isEmpty());
        assertTrue(uses(cls).stream().allMatch(this::missing));
    }
    @Test void removedOrMovedModeledIncludesDoNotReuseStalePreprocessingEvidence() {
        var project = resolve("#include <typeinfo>\nvoid f(){typeid(int);}");
        var file = project.files().getFirst();
        var include = file.items().stream().filter(IncludeDirective.class::isInstance).findFirst().orElseThrow();
        var removed = new ProjectEditor(project);
        removed.remove(include);
        assertTrue(missing(uses(NameResolver.resolve(removed.build())).getFirst()));
        var reordered = new ProjectEditor(project);
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        file.items().stream().filter(item -> item != include).forEach(item -> items.add(item.edit()));
        items.add(include.edit());
        reordered.replace(file, file.edit().setItems(items).build());
        assertTrue(missing(uses(NameResolver.resolve(reordered.build())).getFirst()));
    }
    private Project moveDeclaration(Project original, Class<? extends Declaration> kind, String destinationSource) {
        var project = original.edit()
                .addFile(Parser.parse(directory.resolve("destination.cpp").toString(), destinationSource, Language.CPP)).build();
        var source = project.files().get(0);
        var destination = project.files().get(1);
        Node moved = source.items().stream().filter(kind::isInstance).findFirst().orElseThrow();
        var remaining = new ArrayList<NodeBuilder<? extends Node>>();
        source.items().stream().filter(item -> item != moved).forEach(item -> remaining.add(item.edit()));
        var relocated = new ArrayList<NodeBuilder<? extends Node>>();
        relocated.add(moved.edit());
        // A parser-only placeholder makes the destination call's template syntax
        // available; it is replaced by the actual moved definition before resolution.
        destination.items().stream().filter(item -> !declarationName(item).equals(declarationName(moved)))
                .forEach(item -> relocated.add(item.edit()));
        var editor = new ProjectEditor(project);
        editor.replace(source, source.edit().setItems(remaining).build());
        editor.replace(destination, destination.edit().setItems(relocated).build());
        return NameResolver.resolve(editor.build());
    }
    private String declarationName(Node node) {
        return node instanceof CallableDeclaration callable ? callable.name()
                : node instanceof ClassDeclaration cls ? cls.name() : "";
    }
    @Test void movingWrittenFunctionsCannotCarryAnotherTranslationUnitsHeader() {
        for (boolean actual : List.of(false, true)) {
            String source = "#include <typeinfo>\nvoid f(){typeid(int);}";
            var original = actual ? physical(source,Map.of("typeinfo","")) : resolve(source);
            var moved = moveDeclaration(original,FunctionDeclaration.class,"void destination(){}");
            assertTrue(uses(moved.files().getFirst()).isEmpty());
            assertEquals(1,uses(moved.files().get(1)).size());
            assertTrue(missing(uses(moved.files().get(1)).getFirst()),actual ? "physical" : "opaque");
            assertTrue(missing(uses(NameResolver.resolve(moved.edit().build())).getFirst()));
        }
    }
    @Test void movingOnlyAnExpressionCannotCarryPhysicalHeaderEvidence() {
        for (boolean actual : List.of(false,true)) {
            String source = "#include <typeinfo>\nvoid f(){typeid(int);}";
            var original = actual ? physical(source,Map.of("typeinfo","")) : resolve(source);
            var project = original.edit().addFile(Parser.parse(directory.resolve("destination.cpp").toString(),
                    "void destination(){typeid(long);}",Language.CPP)).build();
            var editor = new ProjectEditor(project);
            editor.replace(uses(project.files().get(1)).getFirst(),uses(project.files().getFirst()).getFirst().edit().build());
            var moved = NameResolver.resolve(editor.build());
            assertFalse(missing(uses(moved.files().getFirst()).getFirst()));
            assertTrue(missing(uses(moved.files().get(1)).getFirst()));
        }
    }
    @Test void movedFunctionAndClassTemplatesUseTheirCurrentDefinitionsTranslationUnit() {
        for (boolean actual : List.of(false, true)) {
            String function = "#include <typeinfo>\ntemplate<class T> void f(){typeid(T);}";
            var moved = moveDeclaration(actual ? physical(function,Map.of("typeinfo","")) : resolve(function),
                    FunctionDeclaration.class,"template<class T> void f(){} void use(){f<int>();}");
            assertFalse(moved.templateInstances().isEmpty());
            assertTrue(uses(moved).stream().allMatch(this::missing));
            assertThrows(IllegalArgumentException.class,() -> moved.source(Language.CPP));
            String cls = "#include <typeinfo>\ntemplate<class T> struct C {void f(){typeid(T);}};";
            var movedClass = moveDeclaration(actual ? physical(cls,Map.of("typeinfo","")) : resolve(cls),
                    ClassDeclaration.class,"template<class T> struct C {void f(){}}; void use(){C<int> c;c.f();}");
            assertFalse(movedClass.classTemplateInstances().isEmpty());
            assertTrue(uses(movedClass).stream().allMatch(this::missing));
        }
    }
    @Test void copyingAnOrdinaryFunctionDoesNotMoveItsEarlierOccurrence() {
        var project = resolve("void f(){typeid(int); }\n#include <typeinfo>\nvoid anchor(){}");
        var file = project.files().getFirst();
        var function = (FunctionDeclaration)file.items().getFirst();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        file.items().forEach(item -> items.add(item.edit()));
        items.add(function.edit().setName("g"));
        var editor = new ProjectEditor(project);
        editor.replace(file,file.edit().setItems(items).build());
        var edited = NameResolver.resolve(editor.build());
        assertEquals(2,uses(edited).size());
        assertTrue(missing(uses(edited).getFirst()));
        assertFalse(missing(uses(edited).getLast()));
        var copied = NameResolver.resolve(edited.edit().build());
        assertTrue(missing(uses(copied).getFirst()));
        assertFalse(missing(uses(copied).getLast()));
    }
    @Test void clonedTemplatesRetainSeparateWrittenAndInstantiationPositions() {
        var project = Project.create().addFile(Parser.parse(directory.resolve("clone.cpp").toString(),
                "template<class T> void f(){typeid(T); }\n#include <typeinfo>\nvoid use(){f<int>();}",Language.CPP)).build();
        var file = project.files().getFirst();
        var function = (FunctionDeclaration)file.items().getFirst();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        file.items().forEach(item -> items.add(item.edit()));
        items.add(function.edit().setName("g"));
        var extra = Parser.parse(directory.resolve("extra.cpp").toString(),
                "template<class T> void g(){} void late(){g<int>();}",Language.CPP).build();
        items.add(extra.items().getLast().edit());
        var editor = new ProjectEditor(project);
        editor.replace(file,file.edit().setItems(items).build());
        var edited = NameResolver.resolve(editor.build());
        var written = uses(edited.files().getFirst());
        assertEquals(2,written.size());
        assertTrue(missing(written.getFirst()));
        assertFalse(missing(written.getLast()));
        assertEquals(2,edited.templateInstances().size());
        for (var instance : edited.templateInstances())
            assertEquals(instance.origin().name().equals("f"),missing(uses(instance).getFirst()));
        var rebuilt = NameResolver.resolve(edited.edit().build());
        for (var instance : rebuilt.templateInstances())
            assertEquals(instance.origin().name().equals("f"),missing(uses(instance).getFirst()));
    }
    @Test void wholeProjectCopiesAndRebuildsRetainLegitimatePhysicalTemplateEvidence() {
        for (boolean actual : List.of(false, true)) {
            String source = "#include <typeinfo>\ntemplate<class T> void f(){typeid(T);}"
                    + "template<class T> struct C {void f(){typeid(T);}};void use(){f<int>();C<int> c;c.f();}";
            var project = actual ? physical(source,Map.of("typeinfo","")) : resolve(source);
            assertFalse(project.templateInstances().isEmpty());
            assertFalse(project.classTemplateInstances().isEmpty());
            valid(project);
            valid(NameResolver.resolve(project.edit().build()));
            valid(NameResolver.resolve(new ProjectEditor(project).build()));
            valid(NameResolver.resolve(NameResolver.resolve(project.edit().build())));
        }
    }
    private void assertSelectedDefinitionHeader(Project project, boolean missingHeader, boolean specialization) {
        assertEquals(1,project.templateInstances().size());
        var instance = project.templateInstances().getFirst();
        assertNotSame(instance.origin(),instance.selected());
        assertTrue(instance.selected().body().isPresent());
        assertTrue(project.files().getFirst().items().contains(instance.selected()));
        assertEquals(specialization,instance.selected().templateKind()==TemplateKind.FULL_SPECIALIZATION);
        assertFalse(uses(instance).isEmpty());
        assertTrue(uses(instance).stream().allMatch(use -> missing(use)==missingHeader));
        assertEquals(!missingHeader,instance.complete());
        assertEquals(missingHeader ? "incomplete-instantiation" : "instantiated",instance.status());
        assertSelectedDefinitionReference(project);
    }
    private void assertSelectedDefinitionReference(Project project) {
        var instance = project.templateInstances().getFirst();
        assertTrue(project.files().getFirst().items().contains(instance.selected()));
        assertTrue(instance.references().contains(instance.selected()));
        assertEquals(instance.selected().range(),instance.declaration().range());
        var document = (Map<?,?>)ProjectJson.encode(project).toJavaObject();
        var exported = (Map<?,?>)((List<?>)document.get("templateInstances")).getFirst();
        var file = (Map<?,?>)((List<?>)document.get("files")).getFirst();
        var items = (List<?>)file.get("items");
        var selected = (Map<?,?>)items.get(project.files().getFirst().items().indexOf(instance.selected()));
        var origin = (Map<?,?>)items.get(project.files().getFirst().items().indexOf(instance.origin()));
        assertEquals(selected.get("id"),exported.get("selectedId"));
        assertEquals(origin.get("id"),exported.get("originId"));
        assertNotEquals(exported.get("originId"),exported.get("selectedId"));
        assertNotEquals(((Map<?,?>)exported.get("declaration")).get("id"),exported.get("selectedId"));
    }
    @Test void forwardDeclarationsUseTheCopiedDefinitionsHeaderPosition() {
        for (boolean before : List.of(false,true)) {
            String source = "template<class T> void f();\n"
                    + (before ? "#include <typeinfo>\n" : "")
                    + "template<class T> void f(){typeid(T); }\n"
                    + (before ? "" : "#include <typeinfo>\n") + "void use(){f<int>();}";
            var project = resolve(source);
            assertSelectedDefinitionHeader(project,!before,false);
            assertFalse(project.templateInstances().getFirst().origin().body().isPresent());
            var copy = project.edit().build();
            assertNotSame(project.templateInstances().getFirst().selected(),copy.templateInstances().getFirst().selected());
            assertSelectedDefinitionReference(copy);
            assertTrue(ProjectJson.encode(project).toText("").contains("\"selectedId\""));
            assertSelectedDefinitionHeader(NameResolver.resolve(copy),!before,false);
            assertSelectedDefinitionHeader(NameResolver.resolve(project),!before,false);
        }
    }
    @Test void explicitSpecializationsUseTheirOwnHeaderPositionInsteadOfThePrimaryTemplate() {
        for (boolean beforeSpecialization : List.of(false,true)) {
            String source = "template<class T> void f(){typeid(T); }\n"
                    + (beforeSpecialization ? "#include <typeinfo>\n" : "")
                    + "template<> void f<int>(){typeid(int); }\n"
                    + (beforeSpecialization ? "" : "#include <typeinfo>\n") + "void use(){f<int>();}";
            var project = resolve(source);
            assertSelectedDefinitionHeader(project,!beforeSpecialization,true);
            assertTrue(uses(project.templateInstances().getFirst().origin()).stream().allMatch(this::missing));
            var copy = project.edit().build();
            assertNotSame(project.templateInstances().getFirst().selected(),copy.templateInstances().getFirst().selected());
            assertSelectedDefinitionReference(copy);
            assertSelectedDefinitionHeader(NameResolver.resolve(copy),!beforeSpecialization,true);
            assertSelectedDefinitionHeader(NameResolver.resolve(project),!beforeSpecialization,true);
        }
    }
    @Test void validSpecializationCallsRetainTheSelectedDefinition() {
        var project = resolve("#include <typeinfo>\ntemplate<class T> void f(){typeid(T); }\n"
                + "template<> void f<int>(){typeid(int); }\nvoid use(){f<int>();}");
        valid(project);
        assertSelectedDefinitionHeader(project,false,true);
        assertSelectedDefinitionHeader(NameResolver.resolve(project.edit().build()),false,true);
    }
    @Test void explicitInstantiationRequestsRetainTheSelectedDefinition() {
        // No explicit specialization: combining both forms is not a positive C++03 oracle (14.7/5).
        var project = resolve("#include <typeinfo>\ntemplate<class T> void f(){typeid(T); }\n"
                + "template void f<int>();");
        valid(project);
        for (var snapshot : List.of(project,NameResolver.resolve(project.edit().build()))) {
            var instance = snapshot.templateInstances().getFirst();
            assertSame(instance.origin(),instance.selected());
            assertTrue(snapshot.files().getFirst().items().contains(instance.selected()));
            assertTrue(instance.causes().isEmpty());
            assertTrue(instance.complete());
            var document = (Map<?,?>)ProjectJson.encode(snapshot).toJavaObject();
            var exported = (Map<?,?>)((List<?>)document.get("templateInstances")).getFirst();
            assertEquals(exported.get("originId"),exported.get("selectedId"));
        }
    }
    @Test void editingTheSelectedDefinitionInvalidatesDerivedInstances() {
        for (String source : List.of(
                "template<class T> void f(); template<class T> void f(){} void use(){f<int>();}",
                "template<class T> void f(){} template<> void f<int>(){} void use(){f<int>();}")) {
            var project = resolve(source);
            var instance = project.templateInstances().getFirst();
            assertNotSame(instance.origin(),instance.selected());
            var editor = new ProjectEditor(project);
            editor.replace(instance.selected().body().orElseThrow(),BlockStatement.create().addItem(ReturnStatement.create()).build());
            var changed = editor.build();
            assertTrue(changed.templateInstances().isEmpty());
            var rebuilt = assertDoesNotThrow(() -> NameResolver.resolve(changed));
            assertFalse(rebuilt.hasErrors(),() -> rebuilt.semanticDiagnostics().toString());
            assertEquals(1,rebuilt.templateInstances().size());
            var selectedBody = rebuilt.templateInstances().getFirst().selected().body().orElseThrow();
            assertInstanceOf(ReturnStatement.class,selectedBody.items().getFirst());
            assertEquals(1,project.templateInstances().size());
            assertTrue(project.files().getFirst().items().contains(instance.selected()));
        }
    }
    @Test void templateIdentityRebuildDoesNotMutateOrEraseDiagnosticsOrAllowExport() {
        var original = resolve("template<class T> void f(){typeid(T);} void use(){f<int>();}");
        var before = original.semanticDiagnostics();
        assertFalse(before.isEmpty());
        assertTrue(uses(original).stream().allMatch(this::missing));
        var rebuilt = assertDoesNotThrow(() -> NameResolver.resolve(original));
        assertEquals(before,original.semanticDiagnostics(),"The previous snapshot must remain unchanged");
        assertEquals(before.stream().map(Diagnostic::code).toList(),rebuilt.semanticDiagnostics().stream().map(Diagnostic::code).toList());
        assertTrue(uses(rebuilt).stream().allMatch(this::missing));
        assertThrows(IllegalArgumentException.class,() -> original.source(Language.CPP));
        assertThrows(IllegalArgumentException.class,() -> rebuilt.source(Language.CPP));
    }
    @Test void missingHeaderDiagnosticIdentifiesTheExactTypeidSpanInApiAndJson() {
        String source = "void before() {\n    typeid(int);\n}\n#include <typeinfo>\nvoid after(){typeid(void);}";
        var project = resolve(source);
        var offending = uses(project).getFirst();
        assertTrue(missing(offending));
        assertFalse(missing(uses(project).getLast()));
        var range = offending.range().orElseThrow();
        assertEquals(directory.resolve("main.cpp").toString(),range.file());
        assertEquals(new SourcePosition(2,5,source.indexOf("typeid(int)")),range.start());
        assertEquals(new SourcePosition(2,16,source.indexOf("typeid(int)")+"typeid(int)".length()),range.end());
        var encoded = new ArrayList<Map<?,?>>();
        collectTypeidJson(ProjectJson.encode(project).toJavaObject(),encoded);
        var diagnostic = (Map<?,?>)((List<?>)encoded.stream().filter(node -> node.containsKey("diagnostics"))
                .findFirst().orElseThrow().get("diagnostics")).getFirst();
        assertEquals("missing-typeinfo",diagnostic.get("code"));
        var primary = (Map<?,?>)diagnostic.get("primaryRange");
        assertEquals(range.file(),primary.get("file"));
        var start = (Map<?,?>)primary.get("start");
        var end = (Map<?,?>)primary.get("end");
        assertEquals(2,((Number)start.get("line")).intValue());
        assertEquals(5,((Number)start.get("column")).intValue());
        assertEquals(range.start().offset(),((Number)start.get("offset")).intValue());
        assertEquals(16,((Number)end.get("column")).intValue());
        assertEquals(range.end().offset(),((Number)end.get("offset")).intValue());
    }
    private void collectTypeidJson(Object value, List<Map<?,?>> result) {
        if (value instanceof Map<?,?> map) {
            if ("TypeidExpression".equals(map.get("kind"))) result.add(map);
            map.values().forEach(child -> collectTypeidJson(child,result));
        } else if (value instanceof List<?> list) list.forEach(child -> collectTypeidJson(child,result));
    }
    @Test void missingHeaderDoesNotReplaceOperandCompletenessDiagnostics() {
        var project = resolve("struct S; void f(){typeid(S);}");
        var codes = uses(project).getFirst().diagnostics().stream().map(Diagnostic::code).toList();
        assertTrue(codes.containsAll(List.of("missing-typeinfo", "invalid-typeid")));
    }
}
