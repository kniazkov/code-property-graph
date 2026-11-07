/* Cross-catalogue external objects preserve declarations and share storage. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryExternalObjectsTest {
    private static final PlatformProfile PROFILE=PlatformProfile.builder().cRuntime("glibc")
            .standardStreamsBinding("glibc-x86_64-lp64").build();
    private static Build.TranslationUnit unit(String name,PlatformProfile profile) {
        Path path=Path.of(name).toAbsolutePath();
        return new Build.TranslationUnit(new Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
    }
    private static Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit("streams.cpp",PROFILE),source)).build());
    }
    private static <T extends Node> List<T> nodes(Node root,Class<T> type) {
        var result=new ArrayList<T>();
        root.walk(new NodeVisitor(){@Override public void visit(Node node){if(type.isInstance(node))result.add(type.cast(node));}});
        return result;
    }
    private static List<VariableDeclaration> objects(Project project,String name) {
        return nodes(project,VariableDeclaration.class).stream().filter(v -> v.name().equals(name)&&LibraryDeclarations.isLibrary(v)).toList();
    }
    private static void assertOwned(Project project) {
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        project.walk(new NodeVisitor(){@Override public void visit(Node node){assertTrue(owned.add(node));}});
        for(Node node:owned) assertTrue(owned.containsAll(node.references()),node.getClass().getSimpleName());
    }
    private static void assertShared(Project project,String name) {
        var objects=objects(project,name);
        assertEquals(2,objects.size(),name);
        assertEquals(1,objects.stream().filter(v -> v.previousDeclaration().isPresent()).count());
        assertSame(objects.getFirst(),objects.getLast().previousDeclaration().orElseThrow());
        var representatives=LibraryExternalObjects.representatives(project);
        for(var object:objects) {
            assertFalse(object.isDefinition()); assertTrue(object.definition().isEmpty()); assertTrue(object.initializer().isEmpty());
            assertEquals("C",object.languageLinkage()); assertEquals(Declaration.Linkage.EXTERNAL,object.linkage());
            assertSame(objects.getFirst(),representatives.get(object));
        }
        assertEquals(Set.of("cstdio.hpp","stdio.h.hpp"),objects.stream()
                .map(v -> Path.of(v.provenance().orElseThrow().unit().translationUnit()).getFileName().toString())
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,objects.stream().map(v -> v.provenance().orElseThrow().unit()).distinct().count());
        assertOwned(project);
    }
    @Test void mixedHeadersKeepBothOccurrencesAcrossCopyAndRecompute() {
        for(String first:List.of("cstdio","stdio.h")) {
            String second=first.equals("cstdio")?"stdio.h":"cstdio";
            var original=resolve("#include <"+first+">\n#include <"+second+">\n"
                    +"int f(){stdout=stdin;return std::fputs(\"x\",stdout)+fflush(stderr);}");
            for(var project:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()))) {
                assertFalse(project.hasErrors(),()->project.semanticDiagnostics().toString());
                for(String name:List.of("stdin","stdout","stderr"))assertShared(project,name);
                assertEquals(2,nodes(project.files().getFirst(),CallExpression.class).stream().filter(c -> c.target().isPresent()).count());
            }
        }
    }
    @Test void declarationDemandDoesNotRequireUsingBothSpellingsAndUnusedObjectsStayAbsent() {
        var project=resolve("#include <cstdio>\n#include <stdio.h>\nint f(){return stdout!=0;}");
        assertShared(project,"stdout");
        assertTrue(objects(project,"stdin").isEmpty());assertTrue(objects(project,"stderr").isEmpty());
        var edit=new ProjectEditor(project);
        edit.remove(nodes(project.files().getFirst(),FunctionDeclaration.class).getFirst());
        var unused=NameResolver.resolve(edit.build());
        assertTrue(nodes(unused,VariableDeclaration.class).isEmpty());
        assertTrue(unused.librarySources().stream().allMatch(f -> f.items().isEmpty()));
        var single=resolve("#include <cstdio>\nint f(){return stdout!=0;}");
        assertEquals(1,objects(single,"stdout").size());
        assertTrue(objects(single,"stdout").getFirst().previousDeclaration().isEmpty());
    }
    @Test void availabilityRemainsPointAndTranslationUnitAware() {
        var project=resolve("int early(){return stdout!=0;}\n#include <cstdio>\n"
                +"int middle(){return stdout!=0;}\n#include <stdio.h>\nint late(){return stdout!=0;}");
        var uses=nodes(project.files().getFirst(),NameExpression.class).stream().filter(n -> n.name().equals("stdout")).toList();
        assertTrue(uses.getFirst().target().isEmpty());
        assertTrue(uses.get(1).target().isPresent());assertTrue(uses.getLast().target().isPresent());
        assertNotSame(uses.get(1).target().orElseThrow(),uses.getLast().target().orElseThrow());
        var reps=LibraryExternalObjects.representatives(project);
        assertSame(reps.get(uses.get(1).target().orElseThrow()),reps.get(uses.getLast().target().orElseThrow()));
        var builder=Project.create().addFile(Parser.parse(unit("included.cpp",PROFILE),"#include <cstdio>\nint a(){return stdout!=0;}"))
                .addFile(Parser.parse(unit("outside.cpp",PROFILE),"int b(){return stdout!=0;}"));
        var separate=NameResolver.resolve(builder.build());
        assertTrue(nodes(separate.files().getLast(),NameExpression.class).getFirst().target().isEmpty());
    }
    @Test void changedHeaderOccurrenceStillReadsTheSameCellThroughCallsPointersAndReferences() {
        for(String first:List.of("cstdio","stdio.h"))for(String body:List.of(
                "stdout=stderr;", "FILE**p=&stdout;*p=stderr;", "FILE*&r=stdout;r=stderr;")) {
            String second=first.equals("cstdio")?"stdio.h":"cstdio";
            String declaration=body.replace("FILE",first.equals("cstdio")?"std::FILE":"FILE");
            var original=resolve("#include <"+first+">\nvoid change(){"+declaration+"}\n"
                    +"#include <"+second+">\nint f(){change();return stdout!=0;}");
            for(var snapshot:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()))) {
                var project=DataFlow.analyze(snapshot);
                assertFalse(project.hasErrors(),()->project.semanticDiagnostics().toString());
                var write=nodes(project.files().getFirst(),AssignmentExpression.class).getFirst();
                var use=nodes(nodes(project.files().getFirst(),ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
                assertTrue(project.flows().stream().flatMap(flow -> flow.data().stream()).anyMatch(edge ->
                        edge.from()==write&&edge.to()==use&&edge.kind()==FunctionFlow.Dependency.DEFINITION),first+body);
            }
        }
    }
    @Test void libraryCallsKeepNamedGlobalPointerStorageConservative() {
        var project=DataFlow.analyze(resolve("#include <cstdio>\n#include <stdio.h>\n#include <cstdlib>\n"
                +"int f(){stdout=stderr;std::rand();return stdout!=0;}"));
        var call=nodes(project.files().getFirst(),CallExpression.class).getFirst();
        var use=nodes(nodes(project.files().getFirst(),ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        assertTrue(project.flows().stream().flatMap(flow -> flow.data().stream()).anyMatch(edge ->
                edge.from()==call&&edge.to()==use&&edge.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void fullProfilesNeverShareExternalObjectIdentity() {
        var builder=Project.create(); int index=0;
        for(var profile:List.of(PROFILE,PROFILE.toBuilder().charSigned(false).build()))
            builder.addFile(Parser.parse(unit("profile"+(index++)+".cpp",profile),
                    "#include <cstdio>\n#include <stdio.h>\nint f(){return stdout!=0;}"));
        var project=NameResolver.resolve(builder.build());
        assertFalse(project.hasErrors(),()->project.semanticDiagnostics().toString());
        var groups=LibraryExternalObjects.groups(project);
        assertEquals(2,groups.size());assertTrue(groups.values().stream().allMatch(group -> group.size()==2));
        assertEquals(2,groups.keySet().stream().map(LibraryExternalObjects.Identity::profile).distinct().count());
        for(var group:groups.values())assertSame(group.getFirst(),group.getLast().previousDeclaration().orElseThrow());
    }
    private static SourceFile.Builder catalogue(String name,String declarations,PlatformProfile profile) {
        return Parser.parseLibrary(unit("/__cpg_library__/"+name+".hpp",profile),declarations);
    }
    @Test void identityRejectsTypeLinkageScopeDefinitionAndOwnershipCollisions() {
        var project=Project.create().setLibrarySources(List.of(
                catalogue("first","extern \"C\" {extern int* item;}",PROFILE),
                catalogue("same","extern \"C\" {extern int* item;}",PROFILE),
                catalogue("wrong_type","extern \"C\" {extern long* item;}",PROFILE),
                catalogue("wrong_linkage","extern int* item;",PROFILE),
                catalogue("namespace","namespace other {extern \"C\" {extern int* item;}}",PROFILE),
                catalogue("definition","extern \"C\" {int* item=0;}",PROFILE),
                catalogue("wrong_profile","extern \"C\" {extern int* item;}",PROFILE.toBuilder().charSigned(false).build())))
                .addFile(Parser.parse(unit("ordinary.cpp",PROFILE),"extern \"C\" {extern int* item;}" )).build();
        var groups=LibraryExternalObjects.groups(project);
        assertEquals(3,groups.size());assertEquals(List.of(2,1,1),groups.values().stream().map(List::size).toList());
        var reps=LibraryExternalObjects.representatives(project);
        for(int index:List.of(3,4,5))for(var object:nodes(project.librarySources().get(index),VariableDeclaration.class))
            assertFalse(reps.containsKey(object));
        assertFalse(reps.containsKey(nodes(project.files().getFirst(),VariableDeclaration.class).getFirst()));
    }
    @Test void unrelatedImportedNamesAndIncompatibleOrdinaryRedeclarationsStayErrors() {
        var unrelated=resolve("namespace first {extern int value;} namespace second {extern int value;}"
                +"using namespace first;using namespace second;int f(){return value;}");
        assertTrue(unrelated.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("ambiguous-name")));
        var incompatible=resolve("extern \"C\" {extern int* value;extern long* value;} int f(){return value!=0;}");
        assertTrue(incompatible.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("conflicting-declaration")));
        var ordinary=resolve("extern \"C\" {extern int* value;extern int* value;} int f(){return value!=0;}");
        assertFalse(ordinary.hasErrors(),()->ordinary.semanticDiagnostics().toString());
        var declarations=nodes(ordinary,VariableDeclaration.class);
        assertSame(declarations.getFirst(),declarations.getLast().previousDeclaration().orElseThrow());
        assertTrue(LibraryExternalObjects.groups(ordinary).isEmpty());
    }
    @Test void qualifiedStdLookupDoesNotInventAnAliasForGlobalCObjects() {
        var project=resolve("#include <cstdio>\n#include <stdio.h>\nint f(){return std::stdout!=0;}");
        var use=nodes(project.files().getFirst(),ScopeMemberExpression.class).getFirst();
        assertEquals("stdout",use.member());assertTrue(use.target().isEmpty());
        assertTrue(objects(project,"stdout").isEmpty());
    }
    @Test void iostreamObjectsRemainOrdinaryCppLibraryDeclarations() {
        var project=resolve("#include <iostream>\n#include <ostream>\nint f(){std::cout.flush();return 0;}");
        assertFalse(project.hasErrors(),()->project.semanticDiagnostics().toString());
        assertEquals(1,objects(project,"cout").size());
        assertEquals("C++",objects(project,"cout").getFirst().languageLinkage());
        assertTrue(objects(project,"cout").getFirst().previousDeclaration().isEmpty());
        assertTrue(LibraryExternalObjects.groups(project).isEmpty());
    }
}
