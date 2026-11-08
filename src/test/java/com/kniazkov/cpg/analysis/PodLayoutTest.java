/* Conservative preparatory POD/layout query; not offsetof macro support. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PodLayoutTest {
 @Test void manuallyConstructedAliasArrayCycleTerminatesWithoutInventingLayout(){
  var alias=TypedefDeclaration.create().setName("Loop");
  var use=NamedType.create().setName("Loop").setAlias(alias);
  alias.setType(ArrayType.create().setElementType(use).setBound(IntegerLiteral.create().setValue(1)));
  reason(PodLayout.layout(use.build(),profile),PodLayout.Reason.RECURSIVE_TYPE);
 }
 @TempDir Path temp;
 private final PlatformProfile profile=PlatformProfile.defaults();
 private Project parse(String text){var u=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(profile);return NameResolver.resolve(Project.create().addFile(Parser.parse(u,text)).build());}
 private static <T extends Node> List<T> nodes(Node n,Class<T> type){var out=new ArrayList<T>();n.walk(new NodeVisitor(){public void visit(Node v){if(type.isInstance(v))out.add(type.cast(v));}});return out;}
 private static ClassDeclaration owner(Project p,String name){return nodes(p,ClassDeclaration.class).stream().filter(c->c.name().equals(name)&&c.isDefinition()).findFirst().orElseThrow();}
 private static VariableDeclaration variable(Project p,String name){return nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();}
 private static Expression integer(long n){return IntegerLiteral.create().setValue(BigInteger.valueOf(n)).build();}
 private static PodLayout.Layout ok(PodLayout.Result<PodLayout.Layout> r){assertTrue(r.supported(),()->r.failure().toString());assertTrue(r.failure().isEmpty());return r.value().orElseThrow();}
 private static void reason(PodLayout.Result<?> r,PodLayout.Reason expected){assertFalse(r.supported());assertTrue(r.value().isEmpty());assertEquals(expected,r.failure().orElseThrow().reason());assertFalse(r.failure().orElseThrow().detail().isBlank());}
 private static final String SHAPES="struct Inner {char tag; int value;}; union Payload {char c; double d; Inner item;}; struct Outer {char lead; Payload payload; Inner list[3]; unsigned long tail;}; typedef Outer Alias; Alias root;";
 @Test void recursiveStructUnionArraysAndAliasesHaveIndependentOffsets(){
  var p=parse(SHAPES);assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
  var inner=ok(PodLayout.layout(owner(p,"Inner"),profile));assertEquals(BigInteger.valueOf(8),inner.sizeBytes());assertEquals(32,inner.alignmentBits());assertEquals(List.of(BigInteger.ZERO,BigInteger.valueOf(4)),inner.fields().stream().map(PodLayout.Field::offsetBytes).toList());
  var union=ok(PodLayout.layout(owner(p,"Payload"),profile));assertEquals(BigInteger.valueOf(8),union.sizeBytes());assertEquals(64,union.alignmentBits());assertTrue(union.fields().stream().allMatch(f->f.offsetBytes().signum()==0));
  var type=variable(p,"root").type().orElseThrow();var outer=ok(PodLayout.layout(type,profile));assertEquals(BigInteger.valueOf(48),outer.sizeBytes());assertEquals(List.of(0L,8L,16L,40L).stream().map(BigInteger::valueOf).toList(),outer.fields().stream().map(PodLayout.Field::offsetBytes).toList());
  var offset=PodLayout.offset(type,List.of(new PodLayout.Member("list"),new PodLayout.Index(integer(2)),new PodLayout.Member("value")),profile);assertTrue(offset.supported(),()->offset.failure().toString());assertEquals(BigInteger.valueOf(36),offset.value().orElseThrow().offsetBytes());assertEquals("int",offset.value().orElseThrow().type().canonicalIdentity());
  assertEquals(BigInteger.valueOf(12),PodLayout.offset(type,List.of(new PodLayout.Member("payload"),new PodLayout.Member("item"),new PodLayout.Member("value")),profile).value().orElseThrow().offsetBytes());
 }
 @Test void harmlessMethodsAndImplicitSpecialMembersDoNotRejectPod(){
  for(String body:List.of("int x; void f();", "int x; operator int() const;", "int x; Good& operator=(int);", "int x; private: void f(); protected: operator int() const;", "static int global; int x;")){
   var p=parse("struct Good {"+body+"};");assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());assertEquals(BigInteger.valueOf(4),ok(PodLayout.layout(owner(p,"Good"),profile)).sizeBytes());
  }
 }
 @Test void userSpecialMembersReferencesAccessAndNonPodSubobjectsAreRejected(){
  for(String body:List.of("int x; Bad();", "int x; ~Bad();", "int x; Bad& operator=(Bad);", "int x; Bad& operator=(const Bad&);", "int x; Bad& operator=(volatile Bad&);", "int x; virtual void f();", "private: int x;", "int& x;")){
   var p=parse("struct Bad {"+body+"};");reason(PodLayout.layout(owner(p,"Bad"),profile),PodLayout.Reason.NOT_POD);
  }
  var p=parse("struct Base {int x;}; struct Bad:Base {int y;}; struct User {~User();}; struct Nested {User u;};");reason(PodLayout.layout(owner(p,"Bad"),profile),PodLayout.Reason.NOT_POD);reason(PodLayout.layout(owner(p,"Nested"),profile),PodLayout.Reason.NOT_POD);
 }
 @Test void unsupportedAbiIsNotMislabelledInvalidCpp(){
  var p=parse("enum E {one}; struct EnumField {E e;}; struct A {int x;}; struct MemberPointer {int A::*p;}; template<class T> struct Generic {T value;};");
  reason(PodLayout.layout(owner(p,"EnumField"),profile),PodLayout.Reason.UNSUPPORTED_TYPE);reason(PodLayout.layout(owner(p,"MemberPointer"),profile),PodLayout.Reason.UNSUPPORTED_TYPE);reason(PodLayout.layout(owner(p,"Generic"),profile),PodLayout.Reason.UNSUPPORTED_TEMPLATE);
 }
 @Test void pointersBreakRecursionButByValueCyclesAndIncompleteUseDoNot(){
  var p=parse("struct Forward; Forward* pointer; struct Forward {int x;}; struct Link {Link* next; int value;}; struct Cycle {Cycle self;};");
  assertEquals(BigInteger.valueOf(16),ok(PodLayout.layout(owner(p,"Link"),profile)).sizeBytes());
  reason(PodLayout.layout(owner(p,"Cycle"),profile),PodLayout.Reason.RECURSIVE_TYPE);
  var pointer=(PointerType)variable(p,"pointer").type().orElseThrow();assertEquals(BigInteger.valueOf(8),ok(PodLayout.layout(pointer,profile)).sizeBytes());
  reason(PodLayout.layout(pointer.pointee(),profile),PodLayout.Reason.INCOMPLETE_TYPE);
 }
 @Test void targetPolicyChangesOnlyThisQueryAndNeverGuesses(){
  var p=parse(SHAPES);var outer=owner(p,"Outer");
  assertEquals(BigInteger.valueOf(32),ok(PodLayout.layout(outer,profile.toBuilder().fieldAlignment("packed1").build())).sizeBytes());
  reason(PodLayout.layout(outer,profile.toBuilder().aggregateLayout("unknown").build()),PodLayout.Reason.UNKNOWN_LAYOUT);
  assertTrue(TypeSemantics.layout(owner(p,"Payload"),profile).isEmpty(),"Generic union sizeof/layout remains intentionally unchanged");
 }
 @Test void invalidMemberPathsAndIndicesAreExplicitAndUnevaluated(){
  var p=parse("struct Root {static int s; void method(); int values[3];}; Root root; int f(); int dynamic=f(); int dead=0?f():1;");var type=variable(p,"root").type().orElseThrow();
  reason(PodLayout.offset(type,List.of(new PodLayout.Member("s")),profile),PodLayout.Reason.STATIC_MEMBER);
  reason(PodLayout.offset(type,List.of(new PodLayout.Member("method")),profile),PodLayout.Reason.FUNCTION_MEMBER);
  reason(PodLayout.offset(type,List.of(new PodLayout.Member("missing")),profile),PodLayout.Reason.MISSING_MEMBER);
  for(long n:List.of(-1L,3L))reason(PodLayout.offset(type,List.of(new PodLayout.Member("values"),new PodLayout.Index(integer(n))),profile),PodLayout.Reason.INDEX_OUT_OF_BOUNDS);
  reason(PodLayout.offset(type,List.of(new PodLayout.Member("values"),new PodLayout.Index(variable(p,"dynamic").initializer().orElseThrow())),profile),PodLayout.Reason.NON_CONSTANT_INDEX);
  reason(PodLayout.offset(type,List.of(new PodLayout.Member("values"),new PodLayout.Index(variable(p,"dead").initializer().orElseThrow())),profile),PodLayout.Reason.NON_CONSTANT_INDEX);
  var bit=parse("struct Bits {unsigned int bit:2;};");reason(PodLayout.layout(owner(bit,"Bits"),profile),PodLayout.Reason.BIT_FIELD);
 }
 @Test void snapshotOwnedConstantsEnableBoundsWithoutAcceptingForeignDeclarations(){
  var p=parse("const int count=3; enum {last=2}; struct Box {int values[count];}; Box root; int chosen=last;");
  for(var q:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()))){
   var type=variable(q,"root").type().orElseThrow();assertEquals(BigInteger.valueOf(12),ok(PodLayout.layout(q,type,profile)).sizeBytes());
   var index=variable(q,"chosen").initializer().orElseThrow();
   var result=PodLayout.offset(q,type,List.of(new PodLayout.Member("values"),new PodLayout.Index(index)),profile);assertTrue(result.supported(),()->result.failure().toString());assertEquals(BigInteger.valueOf(8),result.value().orElseThrow().offsetBytes());
  }
  var other=parse("int other;");assertFalse(PodLayout.layout(other,variable(p,"root").type().orElseThrow(),profile).supported());
 }
 @Test void enumeratorDependenciesDoNotCreateArtificialCycles(){
  var p=parse("enum {A=1,B=A+1,C}; struct First {int x[A];}; struct Second {int x[B];}; struct Third {int x[C];};");
  assertEquals(BigInteger.valueOf(4),ok(PodLayout.layout(p,owner(p,"First"),profile)).sizeBytes());
  assertEquals(BigInteger.valueOf(8),ok(PodLayout.layout(p,owner(p,"Second"),profile)).sizeBytes());
  assertEquals(BigInteger.valueOf(12),ok(PodLayout.layout(p,owner(p,"Third"),profile)).sizeBytes());
 }
 @Test void emptyAggregatesAndUnavailableBoundFactsRemainExplicit(){
  var p=parse("struct Empty {}; union EmptyUnion {}; union U {int i;}; struct Bound {int values[sizeof(U)];};");
  assertEquals(BigInteger.ONE,ok(PodLayout.layout(owner(p,"Empty"),profile)).sizeBytes());assertEquals(BigInteger.ONE,ok(PodLayout.layout(owner(p,"EmptyUnion"),profile)).sizeBytes());
  reason(PodLayout.layout(p,owner(p,"Bound"),profile),PodLayout.Reason.UNKNOWN_ARRAY_BOUND);
 }
 @Test void targetSizeTypeBoundsProductsOffsetsAccumulationAndPadding(){
  var narrow=profile.toBuilder().sizeType("unsigned int").sizeBits("int",16).alignmentBits("int",16)
          .wcharType("long").sizeBits("wchar_t",64).alignmentBits("wchar_t",64).build();
  for(var target:List.of(narrow,profile.toBuilder().sizeType("unsigned int").build(),profile)){
   int bits=target.sizeBits(target.sizeType()).orElseThrow();var max=BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
   var p=parse("struct Product {int a["+max+"UL];}; struct Prefix {char first; char a["+max+"UL];}; "
       +"struct AlignedOffset {char a["+max+"UL]; short last;}; "
       +"struct Padding {short first; char a["+max.subtract(BigInteger.TWO)+"UL];}; "
       +"union UnionPadding {char a["+max+"UL]; short last;}; "
       +"union UnionProduct {int a["+max+"UL]; char c;}; struct Exact {char a["+max+"UL];}; Exact root;");
   for(String name:List.of("Product","Prefix","AlignedOffset","Padding","UnionPadding","UnionProduct"))
    reason(PodLayout.layout(owner(p,name),target),PodLayout.Reason.SIZE_NOT_REPRESENTABLE);
   // Necessary representability only: this does not promise compiler allocation of this object.
   assertEquals(max,ok(PodLayout.layout(owner(p,"Exact"),target)).sizeBytes());
   var last=IntegerLiteral.create().setValue(max.subtract(BigInteger.ONE)).setCppType("unsigned long").build();
   var offset=PodLayout.offset(variable(p,"root").type().orElseThrow(),List.of(new PodLayout.Member("a"),new PodLayout.Index(last)),target);
   assertTrue(offset.supported(),()->offset.failure().toString());assertEquals(max.subtract(BigInteger.ONE),offset.value().orElseThrow().offsetBytes());
  }
 }
 @Test void missingOrInvalidScalarFactsNeverYieldKnownLayout(){
  var scalar=NamedType.create().setName("int").build();
  reason(PodLayout.layout(scalar,profile.toBuilder().alignmentBits("int",null).build()),PodLayout.Reason.UNKNOWN_LAYOUT);
  assertThrows(IllegalArgumentException.class,()->profile.toBuilder().sizeBits("int",0).build());
  assertThrows(IllegalArgumentException.class,()->profile.toBuilder().alignmentBits("int",24).build());
 }
 @Test void copiesReanalysisAndTypeEditsNeverReuseStaleLayout(){
  var p=parse("struct Item {char tag; int value;}; Item root;");
  for(var q:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()))){var l=ok(PodLayout.layout(owner(q,"Item"),profile));assertEquals(BigInteger.valueOf(8),l.sizeBytes());assertTrue(l.fields().stream().allMatch(f->nodes(q,VariableDeclaration.class).contains(f.declaration())));}
  var edit=new ProjectEditor(p);edit.replace(variable(p,"value").type().orElseThrow(),NamedType.create().setName("double").build());var changed=NameResolver.resolve(edit.build());
  assertEquals(BigInteger.valueOf(16),ok(PodLayout.layout(owner(changed,"Item"),profile)).sizeBytes());assertEquals(BigInteger.valueOf(8),ok(PodLayout.layout(owner(p,"Item"),profile)).sizeBytes());
 }
}
