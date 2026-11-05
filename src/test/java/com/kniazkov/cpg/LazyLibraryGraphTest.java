/* Demand-only C++03 standard-library ownership. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LazyLibraryGraphTest {
    static Project resolve(String... texts) {
        var builder=Project.create(); int index=0;
        for(String text:texts) builder.addFile(Parser.parse("unit"+(index++)+".cpp",text,Language.CPP));
        return NameResolver.resolve(builder.build());
    }
    static <T extends Node> List<T> nodes(Node root,Class<T> kind) {
        var result=new ArrayList<T>(); root.walk(new NodeVisitor(){@Override public void visit(Node n){if(kind.isInstance(n))result.add(kind.cast(n));}}); return result;
    }
    @Test void includesOnlyRetainOneOwnedEmptyHeader() {
        var project=resolve("#include <vector>\nint f();", "#include <vector>\nint g();");
        assertEquals(1,project.standardLibrary().orElseThrow().headers().size());
        var header=project.librarySources().getFirst(); assertTrue(header.items().isEmpty());
        for(var include:nodes(project,IncludeDirective.class)) assertSame(header,include.libraryHeader().orElseThrow());
        assertTrue(resolve("int f();").standardLibrary().isEmpty());
    }
    @Test void concreteMembersAreDemandedSeparatelyAndHaveOrigins() {
        var project=resolve("#include <vector>\nint f() { std::vector<int> a; std::vector<double> b; a.push_back(1); return 0; }",
                "#include <vector>\nint g() { std::vector<int> c; c.push_back(2); return 0; }");
        assertFalse(project.hasErrors(),project.semanticDiagnostics().toString());
        var instances=project.classTemplateInstances().stream().filter(i->i.origin().name().equals("vector")).toList();
        assertEquals(2,instances.size());
        var calls=nodes(project,CallExpression.class).stream().filter(c->c.callee() instanceof MemberExpression m && m.member().equals("push_back")).toList();
        assertEquals(2,calls.size());
        var target=calls.getFirst().target().orElseThrow(); assertSame(target,calls.getLast().target().orElseThrow());
        assertTrue(target.templateOrigin().isPresent());
        assertEquals(1,instances.stream().filter(i->i.declaration().members().contains(target)).count());
        assertEquals(1,instances.stream().flatMap(i->i.declaration().members().stream()).filter(d->d instanceof CallableDeclaration c && c.name().equals("push_back")).count());
        assertTrue(nodes(project,CallableDeclaration.class).stream().noneMatch(c->c.name().equals("reserve")));
        assertTrue(nodes(project,ClassDeclaration.class).stream().noneMatch(c->c.name().equals("map")));
        assertEquals(1,nodes(project,ClassDeclaration.class).stream().filter(c->c.name().equals("vector")&&!c.templateParameters().isEmpty()).count());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        project.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.add(n));}});
        project.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.containsAll(n.references()));}});
        assertEquals(2,NameResolver.resolve(project).classTemplateInstances().stream().filter(i->i.origin().name().equals("vector")).count());
    }
    @Test void sourceEditsDiscardTheOldDemandClosure() {
        var project=resolve("#include <vector>\nint f() { std::vector<int> a; a.push_back(1); return 0; }");
        var edit=new ProjectEditor(project);
        edit.remove(project.files().getFirst().items().stream().filter(n->n instanceof FunctionDeclaration).findFirst().orElseThrow());
        var headerOnly=NameResolver.resolve(edit.build());
        assertEquals(1,headerOnly.librarySources().size()); assertTrue(headerOnly.librarySources().getFirst().items().isEmpty());
        assertTrue(headerOnly.classTemplateInstances().isEmpty());
        edit=new ProjectEditor(headerOnly); edit.remove(nodes(headerOnly,IncludeDirective.class).getFirst());
        assertTrue(NameResolver.resolve(edit.build()).standardLibrary().isEmpty());
        assertFalse(project.classTemplateInstances().isEmpty());
    }
    @Test void losingFunctionTemplateCandidatesDoNotMaterialize() {
        var project=resolve("#include <algorithm>\nusing namespace std; int max(int a,int b); int f(){return max(1,2);}");
        assertFalse(project.hasErrors(),project.semanticDiagnostics().toString());
        assertTrue(project.templateInstances().isEmpty());
        assertTrue(nodes(project.librarySources().getFirst(),CallableDeclaration.class).isEmpty());
    }
    @Test void templatedConstructorDoesNotHideInjectedClassName() {
        var source=Parser.parse("injected.cpp","template<class T> struct Box { template<class U> Box(U x); Box& operator=(const Box& x); };",Language.CPP).build();
        assertEquals(2,nodes(source,MethodDeclaration.class).size());
    }
    @Test void incompatibleProfilesKeepSeparateLibraryIdentities() {
        var builder=Project.create(); int index=0;
        var defaults=com.kniazkov.cpg.build.PlatformProfile.defaults();
        for(var profile:List.of(defaults,defaults.toBuilder().charSigned(false).build())) {
            var path=java.nio.file.Path.of("profile"+(index++)+".cpp").toAbsolutePath();
            var unit=new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
            builder.addFile(Parser.parse(unit,"#include <vector>\nstd::vector<int> item;"));
        }
        var project=NameResolver.resolve(builder.build());
        assertFalse(project.hasErrors(),project.semanticDiagnostics().toString());
        assertEquals(2,project.classTemplateInstances().stream().filter(i->i.origin().name().equals("vector")).count());
    }
    @Test void includeAvailabilityBeginsAtTheDirective() {
        var project=resolve("int before(){return std::abs(1); }\n#include <cstdlib>\nint after(){return std::abs(2);}");
        var calls=nodes(project.files().getFirst(),CallExpression.class);
        assertTrue(calls.getFirst().target().isEmpty());
        assertEquals("abs",calls.getLast().target().orElseThrow().name());
    }
    @Test void onlySelectedLibraryLifetimeDeclarationsSurvive() {
        var project=resolve("#include <vector>\nint f(){std::vector<int> a(3); std::vector<double> b; return 0;}");
        assertFalse(project.hasErrors(),project.semanticDiagnostics().toString());
        for(var variable:nodes(project.files().getFirst(),VariableDeclaration.class)) {
            var ctor=variable.constructorTarget().orElseThrow();
            assertEquals(CallableDeclaration.Kind.CONSTRUCTOR,ctor.kind());
            assertEquals(CallableDeclaration.Kind.DESTRUCTOR,variable.destructorTarget().orElseThrow().kind());
            var owner=ClassSemantics.objectClass(variable.type().orElseThrow()).orElseThrow();
            assertEquals(1,owner.members().stream().filter(d->d instanceof CallableDeclaration c && c.kind()==CallableDeclaration.Kind.CONSTRUCTOR).count());
            assertTrue(ctor.templateOrigin().isPresent());
        }
        assertTrue(project.librarySources().stream().allMatch(f->f.provenance().orElseThrow().unit().tokens().isEmpty()));
    }
    @Test void implementationDefinedTypesKeepUnknownRepresentationAndExplicitCategory() {
        var path=java.nio.file.Path.of("/__cpg_library__/opaque.hpp");
        var unit=new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03");
        var source=Parser.parseLibrary(unit,"namespace std { typedef __cpg_opaque_arithmetic_clock_t clock_t; typedef __cpg_opaque_unspecified_va_list va_list; }").build();
        var opaque=nodes(source,OpaqueLibraryType.class);
        assertEquals(2,opaque.size()); assertEquals(OpaqueLibraryType.Category.ARITHMETIC,opaque.getFirst().category());
        assertTrue(TypeSemantics.describe(opaque.getFirst(),com.kniazkov.cpg.build.PlatformProfile.defaults()).sizeBits().isEmpty());
        assertTrue(TypeSemantics.describe(opaque.getFirst(),com.kniazkov.cpg.build.PlatformProfile.defaults()).integerRange().isEmpty());
        assertTrue(ClassSemantics.objectClass(opaque.getFirst()).isEmpty());
        var value=VariableDeclaration.create().setName("tick").setType(opaque.getFirst().edit()).build();
        var expression=NameExpression.create().setName("tick").setTarget(value.edit()).build();
        var conversion=OverloadResolution.conversion(expression,NamedType.create().setName("int").build(),com.kniazkov.cpg.build.PlatformProfile.defaults(),true).orElseThrow();
        assertEquals(OverloadResolution.Rank.UNKNOWN,conversion.rank());
        assertThrows(com.kniazkov.cpg.parser.ParseException.class,()->Parser.parse("user.cpp","__cpg_opaque_arithmetic_clock_t tick;",Language.CPP));
    }
    @Test void includesDoNotLeakBetweenTranslationUnits() {
        var project=resolve("#include <vector>\nint f();", "int outside;");
        assertFalse(LibraryDeclarations.visible(project.files().getLast(),project.librarySources().getFirst()));
        assertThrows(com.kniazkov.cpg.parser.ParseException.class, ()->Parser.parse("outside.cpp","std::vector<int>* outside;",Language.CPP));
        assertTrue(project.classTemplateInstances().isEmpty());
    }
}
