/* Source-coordinate D.7 inventory and host-independent use. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.LazyLibraryGraphTest.nodes;
class StrstreamLibraryTest {
    private static final List<PlatformProfile> PROFILES = List.of(PlatformProfile.defaults(),
        PlatformProfile.builder().sizeType("unsigned int").build());
    private static Build.TranslationUnit unit(PlatformProfile profile) {
        Path path=Path.of("/__no_host_stl__/strstream.cpp");
        return new Build.TranslationUnit(new Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
    }
    private static Project resolve(String code,PlatformProfile profile) {
        var p=NameResolver.resolve(Project.create().addFile(Parser.parse(unit(profile),"#include <strstream>\n"+code)).build());
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString()); return p;
    }
    private static String shape(Node n) {
        String head=n.getClass().getSimpleName();
        if(n instanceof TypeNode t) head += ":"+t.canonicalIdentity();
        if(n instanceof CallableDeclaration c) head += ":"+c.kind()+":"+c.name()+":"+c.modifiers()+":"+c.access()+":"+c.isConst()+":"+c.isVolatile();
        if(n instanceof ClassDeclaration c) head += ":"+c.name();
        if(n instanceof TypedefDeclaration t) head += ":"+t.name();
        if(n instanceof BaseSpecifier b) head += ":"+b.access()+":"+b.isVirtual();
        if(n instanceof NameExpression e) head += ":"+e.name();
        if(n instanceof IntegerLiteral e) head += ":"+e.bigValue();
        if(n instanceof BooleanLiteral e) head += ":"+e.value();
        return head+"["+n.children().stream().map(StrstreamLibraryTest::shape).reduce("",(a,b)->a+"|"+b)+"]";
    }
    @Test void d7IndependentCoordinateInventoryPreservesEverySignature() throws Exception {
        String expected=Files.readString(Path.of("src/test/resources/library-catalogue/strstream-d7-inventory.hpp"));
        for(var profile:PROFILES) {
            String includes="#include <streambuf>\n#include <istream>\n#include <ostream>\n#include <cstddef>\n#include <cstdio>\n";
            var actual=Parser.parseLibrary(unit(profile),includes+StandardLibraryCatalogue.declarations("strstream",profile)).build();
            var reference=Parser.parseLibrary(unit(profile),expected).build();
            assertEquals(nodes(reference,ClassDeclaration.class).stream().map(StrstreamLibraryTest::shape).toList(),
                nodes(actual,ClassDeclaration.class).stream().map(StrstreamLibraryTest::shape).toList());
            assertEquals(4,nodes(actual,ClassDeclaration.class).size());
            assertEquals(39,nodes(actual,CallableDeclaration.class).size());
        }
        assertEquals(Set.of("istream","ostream","streambuf","cstddef","cstdio"),StandardLibraryCatalogue.dependencies("strstream"));
        assertTrue(StandardLibraryCatalogue.templateNames("strstream").isEmpty());
        assertEquals(Set.of("strstreambuf","istrstream","ostrstream","strstream","std::strstreambuf","std::istrstream","std::ostrstream","std::strstream"),StandardLibraryCatalogue.typeNames("strstream"));
    }
    private static void verifyTargets(Project p) {
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        for(var call:nodes(p.files().getFirst(),CallExpression.class)) assertTrue(call.target().isPresent(),()->"Unresolved "+call.range());
        for(var v:nodes(p.files().getFirst(),VariableDeclaration.class))
            if(v.name().startsWith("obj")) { assertTrue(v.constructorTarget().isPresent(),v.name()); assertTrue(v.destructorTarget().isPresent(),v.name()); }
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.add(n));}});
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.containsAll(n.references()),()->n.getClass()+" references");}});
    }
    @Test void allConstructorFamiliesAndPublicMembersResolveOnBothProfilesAndCopies() {
        String code="""
void* allocate(std::size_t); void release(void*);
void f(char* c, const char* cc, signed char* s, const signed char* cs, unsigned char* u, const unsigned char* cu) {
std::strstreambuf obj0, obj1(32), obj2(&allocate,&release), obj3(c,8), obj4(c,8,c), obj5(cc,8), obj6(s,8), obj7(s,8,s), obj8(cs,8), obj9(u,8), obj10(u,8,u), obj11(cu,8);
std::istrstream obj12(c),obj13(cc),obj14(c,8),obj15(cc,8);
std::ostrstream obj16,obj17(c,8),obj18(c,8,std::ios_base::out);
std::strstream obj19,obj20(c,8),obj21(c,8,std::ios_base::in|std::ios_base::out);
obj0.freeze(); obj0.freeze(false); obj0.str(); obj0.pcount();
obj12.rdbuf(); obj12.str(); obj12.good();
obj16.rdbuf(); obj16.freeze(); obj16.freeze(false); obj16.str(); obj16.pcount(); obj16.good();
obj19.rdbuf(); obj19.freeze(); obj19.freeze(false); obj19.str(); obj19.pcount(); obj19.good();
}
void constants(const std::strstreambuf& b,const std::istrstream& i,const std::ostrstream& o,const std::strstream& s) { b.pcount();i.rdbuf();o.rdbuf();o.pcount();s.rdbuf();s.pcount(); }
""";
        for(var profile:PROFILES) {var p=resolve(code,profile);verifyTargets(p);verifyTargets(NameResolver.resolve(p.edit().build()));}
    }
    @Test void bitmaskExpressionsRetainContractTypeWithoutInventingValues() {
        for(var profile:PROFILES) {
            var p=resolve("void f(){std::ios_base::openmode mode=std::ios_base::in|std::ios_base::out;mode & std::ios_base::out;mode ^ std::ios_base::in;~mode;std::ios_base::in | std::ios_base::dec;}",profile);
            for(var e:nodes(p.files().getFirst(),Expression.class)) {
                if(e instanceof BitwiseOrExpression || e instanceof BitwiseAndExpression || e instanceof BitwiseXorExpression || e instanceof BitwiseNotExpression) {
                    boolean mixed=e instanceof BitwiseOrExpression b && (b.right() instanceof NameExpression n && n.name().endsWith("dec") || b.right() instanceof MemberExpression m && m.member().equals("dec"));
                    var type=TypeSemantics.expressionType(e,profile);
                    if(mixed) assertTrue(type.isEmpty());
                    else {assertEquals("openmode",((OpaqueLibraryType)type.orElseThrow().canonicalType()).name());assertTrue(TypeSemantics.describe(type.orElseThrow(),profile).integerRange().isEmpty());}
                }
            }
        }
    }
    @Test void protectedHooksResolveWithDefaultsAndKeepAccessChecks() {
        for(var profile:PROFILES) {
            var p=resolve("struct Buffer : std::strstreambuf { void hooks(){overflow();pbackfail();underflow();seekoff(0,std::ios_base::beg);seekpos(std::streampos());setbuf(0,0);} };",profile);
            verifyTargets(p);
            var outside=NameResolver.resolve(Project.create().addFile(Parser.parse(unit(profile),"#include <strstream>\nvoid f(std::strstreambuf& b){b.underflow();}")).build());
            assertTrue(nodes(outside,CallExpression.class).stream().flatMap(c->c.diagnostics().stream()).anyMatch(d->d.code().equals("inaccessible-overload")));
        }
    }
    @Test void minimalUseAndIncludeOnlyAreLazyAndRebuildDropsOldDemand() {
        for(var profile:PROFILES) {
            var empty=resolve("int f();",profile);
            assertEquals(1,empty.librarySources().size());assertTrue(empty.librarySources().getFirst().items().isEmpty());
            var p=resolve("void f(){std::ostrstream obj;obj.freeze(false);}",profile);verifyTargets(p);
            assertTrue(nodes(p,ClassDeclaration.class).stream().noneMatch(c->Set.of("vector","map","istrstream","strstream").contains(c.name())));
            assertTrue(nodes(p,CallableDeclaration.class).stream().noneMatch(c->c.name().equals("pcount")));
            var edit=new ProjectEditor(p);edit.remove(nodes(p.files().getFirst(),FunctionDeclaration.class).getFirst());
            var rebuilt=NameResolver.resolve(edit.build());
            assertTrue(rebuilt.librarySources().stream().allMatch(f->f.items().isEmpty()));
            assertFalse(p.librarySources().getFirst().items().isEmpty());
        }
    }
    @Test void freezeAndStrRemainOpaqueReceiverEffects() {
        var p=UnreadWrites.analyze(resolve("int f(){std::ostrstream obj;obj.freeze();char* p=obj.str();obj.freeze(false);return obj.pcount();}",PROFILES.getFirst()));
        verifyTargets(p);
        var f=nodes(p.files().getFirst(),FunctionDeclaration.class).getFirst();
        var flow=p.flows().stream().filter(x->x.entry()==f).findFirst().orElseThrow();
        var use=nodes(nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var effects=nodes(f,CallExpression.class).stream().filter(c->c.target().map(t->Set.of("freeze","str").contains(t.name())).orElse(false)).toList();
        assertEquals(3,effects.size());
        for(var effect:effects) {assertTrue(effect.target().orElseThrow().body().isEmpty());assertTrue(flow.data().stream().anyMatch(e->e.from()==effect&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));}
        assertTrue(nodes(p,PrintStatement.class).isEmpty());
    }
}
