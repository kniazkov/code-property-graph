/* Explicit enum representation and original C++03 promotion boundaries. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.*;
import java.util.*;
import java.math.BigInteger;
class EnumStorageLayoutTest {
 static final PlatformProfile P=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","128",null,"gcc-clang-x86_64-default-enum-v1").build();

 static Project parse(PlatformProfile p,String s){var u=new Build.TranslationUnit(new Build.PhysicalSource(Path.of("/tmp/review.cpp")),Path.of("/tmp"),List.of(),List.of(),"c++03").withPlatform(p);return NameResolver.resolve(Project.create().addFile(Parser.parse(u,s)).build());}
 static <T extends Node> List<T> nodes(Node n,Class<T> t){var out=new ArrayList<T>();n.walk(new NodeVisitor(){public void visit(Node v){if(t.isInstance(v))out.add(t.cast(v));}});return out;}
 static Expression result(Project p){return nodes(p,VariableDeclaration.class).stream().filter(x->x.name().equals("result")).findFirst().orElseThrow().initializer().orElseThrow();}
 static void value(Project p,PlatformProfile profile,long expected){if(p.hasErrors())throw new AssertionError(p.semanticDiagnostics()+" "+p.parseProblems());var evaluated=new IntegralConstants(p,profile).evaluate(result(p)).orElseThrow();org.junit.jupiter.api.Assertions.assertEquals("unsigned long",evaluated.type().spelling());var n=evaluated.number();if(!n.equals(BigInteger.valueOf(expected)))throw new AssertionError("expected "+expected+" got "+n);}
 static void check(String s,long n){value(parse(P,s),P,n);}
 static void reject(PlatformProfile profile,String s){var p=parse(profile,s);if(new IntegralConstants(p,profile).evaluate(result(p)).isPresent())throw new AssertionError("accepted "+s);if(!p.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"))throw new AssertionError(p.semanticDiagnostics());}
 static Project aliasEdit(Project p,String type){var alias=nodes(p,TypedefDeclaration.class).stream().filter(a->a.name().equals("T")).findFirst().orElseThrow();var e=new ProjectEditor(p);e.replace(alias.type(),NamedType.create().setName(type).build());return NameResolver.resolve(e.build());}
 static void retention(String s){var p=parse(P,s);value(p,P,4);var folded=NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(p)));value(folded,P,4);var edited=aliasEdit(folded,"long");value(edited,P,8);value(p,P,4);value(aliasEdit(NameResolver.resolve(ConstantFolder.fold(edited)),"int"),P,4);}
 @org.junit.jupiter.api.Test void independentlyRecoveredRegressionMatrix(){
 for(String t:List.of("char","signed char","unsigned char","short","unsigned short"))check("enum E{A=static_cast<"+t+">(1),B=static_cast<"+t+">(2),D=true?A:B,C=sizeof(true?A:B),F=sizeof(D)};struct S{char a[C+F];char tail;};unsigned long result=__builtin_offsetof(S,tail);",t.contains("short")?4:2);
 check("enum E{A=1};const E e=A;enum F{B=true?e:e};struct S{F f;char tail;};unsigned long result=__builtin_offsetof(S,tail);",4);
 check("enum E{A=sizeof(1/0)};struct S{char a[A];char tail;};unsigned long result=__builtin_offsetof(S,tail);",4);
 check("struct Base{int x;char tail;};enum E{A=__builtin_offsetof(Base,tail)};struct S{char a[A];char tail;};unsigned long result=__builtin_offsetof(S,tail);",4);
 var unknown=P.toBuilder().enumUnderlyingType("unknown").build();value(parse(unknown,"enum E{A=4294967296UL};struct S{E e;char tail;};unsigned long result=__builtin_offsetof(S,tail);"),unknown,8);
 value(parse(unknown,"enum E{A=1,B=2};struct S{char a[sizeof(true?A:B)+sizeof(A)];char tail;};unsigned long result=__builtin_offsetof(S,tail);"),unknown,8);
 var unsigned=P.toBuilder().enumUnderlyingType("unsigned-first").build();
 for(String expr:List.of("sizeof(+(true?A:B))","sizeof((true?A:B)+1)","sizeof(~(true?A:B))","sizeof(+(true?A:1U))"))reject(unsigned,"enum E{A=1,B=2};struct S{char a["+expr+"];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
 reject(unsigned,"enum E{A=1};enum F{B=2};struct S{char a[sizeof(+(true?A:B))];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
 for(var profile:List.of(P,unsigned,unknown))reject(profile,"enum E{A=-1,B=4294967295U};struct S{E e;};unsigned long result=__builtin_offsetof(S,e);");
 String pre="typedef int T;enum E{A=sizeof(T)*536870912UL};";
 String[][] forms={{"E object;","object"},{"struct Holder{E e;};Holder holder;","holder.e"},{"E objects[1];","objects[0]"},{"E get();","get()"},{"E get();E object=get();","object"},{"struct H{E e;};H h;E H::*pm;","h.*pm"},{"struct H{E e;};H* hp;E H::*pm;","hp->*pm"},{"E (*fp)();","fp()"},{"struct X{};X x;E operator+(X,X);","x+x"}};
 for(String[] form:forms){retention(pre+form[0]+"struct S{char a[sizeof(+("+form[1]+"))];char tail;};unsigned long result=__builtin_offsetof(S,tail);");}
 retention(pre+"struct S{char bytes[16];};unsigned long f(E param){unsigned long result=__builtin_offsetof(S,bytes[sizeof(+param)]);return result;}");

 }
 @org.junit.jupiter.api.Test void rawRangesUnderEachOriginalPolicy(){
  String[] enums={"", "A=1", "A=-1", "A=4294967295U", "A=4294967296UL", "A=9223372036854775807L", "A=18446744073709551615UL", "A=-2147483649L,B=4294967296UL"};
  for(String policy:List.of("int-first","unsigned-first","unknown"))for(int i=0;i<enums.length;i++){
   var profile=P.toBuilder().enumUnderlyingType(policy).build();
   value(parse(profile,"enum E{"+enums[i]+"};struct S{E e;char tail;};unsigned long result=__builtin_offsetof(S,tail);"),profile,i<4?4:8);
  }
 }
 @org.junit.jupiter.api.Test void originalPromotionsRemainUnchanged(){
  var u=P.toBuilder().enumUnderlyingType("unsigned-first").build();
  var p=parse(u,"enum E{A=1};");var e=nodes(p,EnumDeclaration.class).getFirst();
  org.junit.jupiter.api.Assertions.assertEquals("unsigned int",EnumSemantics.promoted(e,u).orElseThrow().spelling());
  check("enum E{A=1};struct S{char a[A];char tail;};unsigned long result=__builtin_offsetof(S,tail);",1);
  reject(u,"enum E{A=1};struct S{char a[A];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
 }
 @org.junit.jupiter.api.Test void optionalSchemaAndIdentities(){
  var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
  var member=old.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647","itanium-x86_64-lp64-v1").build();
  org.junit.jupiter.api.Assertions.assertEquals("c1119b1bab9350f65b8ed95bd338f4df5b32979f84d573ca66eeb664081e0f10",old.identityHash());
  org.junit.jupiter.api.Assertions.assertEquals("62a0fae7a740ee50f76a6eabf1ac439f0ed7f112f07db78a9c4d99e0db15f60e",member.identityHash());
  for(var profile:List.of(old,member)){
   var p=parse(profile,"enum E{A=1};struct S{E e;};unsigned long result=__builtin_offsetof(S,e);");
   org.junit.jupiter.api.Assertions.assertTrue(p.hasErrors());
   org.junit.jupiter.api.Assertions.assertFalse(profile.offsetof().orElseThrow().settings().containsKey("enumStorage"));
  }
  org.junit.jupiter.api.Assertions.assertEquals(P,PlatformProfile.fromSettings(P.toSettings()));
  org.junit.jupiter.api.Assertions.assertEquals(P,P.toBuilder().build());
  for(Object bad:Arrays.asList(null,"","other",1,true)){
   var m=new LinkedHashMap<String,Object>(P.offsetof().orElseThrow().settings());m.put("enumStorage",bad);
   org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->PlatformProfile.fromSettings(Map.of("inherits","default","offsetof",m)));
  }
  var m=new LinkedHashMap<String,Object>(P.offsetof().orElseThrow().settings());m.put("enumBytes",4);
  org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->PlatformProfile.fromSettings(Map.of("inherits","default","offsetof",m)));
  org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->P.toBuilder().sizeBits("int",16).build());
 }
 @org.junit.jupiter.api.Test void arraysUnionsAliasesAndCopies(){
  String s="enum E{A=4294967296UL};typedef E Alias;union U{Alias a[2];char c[17];};struct S{char c;U u;char tail;};unsigned long result=__builtin_offsetof(S,tail);";
  var p=parse(P,s);value(p,P,32);var copy=NameResolver.resolve(p.edit().build());value(copy,P,32);
  String generated=com.kniazkov.cpg.Cpg.sources(copy,Language.CPP).values().iterator().next();value(parse(P,generated),P,32);
 }
 @org.junit.jupiter.api.Test void missingPolicyAndIndependentOptions(){
  var both=P.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","128","itanium-x86_64-lp64-v1","gcc-clang-x86_64-default-enum-v1").build();
  String s="struct I;enum E{A=1};struct S{E e;int I::*p;char tail;};unsigned long result=__builtin_offsetof(S,tail);";
  value(parse(both,s),both,16);org.junit.jupiter.api.Assertions.assertTrue(parse(P,s).hasErrors());
 }
 @org.junit.jupiter.api.Test void unrelatedDeclarationsDoNotPoisonQuery(){
  check("struct Huge{char a[129];};enum Bad{A=sizeof(Huge)};const unsigned long ignored=sizeof(Huge);struct S{int x;char tail;};unsigned long result=__builtin_offsetof(S,tail);",4);
 }
 @org.junit.jupiter.api.Test void ceilingsApplyToRawInitializersAndNamedCaches(){
  for(String prefix:List.of("", "const unsigned long pre=sizeof(Huge);")){
   String s="struct Huge{char a[129];};"+prefix+"enum E{A=sizeof(Huge)};struct S{E e;char tail;};unsigned long result=__builtin_offsetof(S,tail);";
   var p=parse(P,s);org.junit.jupiter.api.Assertions.assertTrue(p.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),p.semanticDiagnostics().toString());
   var constants=new IntegralConstants(p,P);nodes(p,EnumeratorDeclaration.class).forEach(constants::apply);
   org.junit.jupiter.api.Assertions.assertTrue(constants.evaluate(result(p)).isEmpty());
  }
 }
 @org.junit.jupiter.api.Test void castsConstAndUnchosenBranchCompatibility(){
  var u=P.toBuilder().enumUnderlyingType("unsigned-first").build();
  for(String expr:List.of("static_cast<E>(A)","true?1:static_cast<E>(A)","true?1:e")){
   String s="enum E{A=1};const E e=A;struct S{char a["+expr+"];char tail;};unsigned long result=__builtin_offsetof(S,tail);";
   value(parse(P,s),P,1);reject(u,s);
  }
 }
 @org.junit.jupiter.api.Test void unevaluatedOverloadSelectionNeedsCompatiblePromotion(){
  String s="enum E{A=1};int choose(int);double choose(unsigned int);struct S{char a[sizeof(choose(A))];char tail;};unsigned long result=__builtin_offsetof(S,tail);";
  check(s,4);reject(P.toBuilder().enumUnderlyingType("unsigned-first").build(),s);
 }
 @org.junit.jupiter.api.Test void foreignEnumDoesNotAcquireSnapshotAuthority(){
  var p=parse(P,"enum E{A=1};struct S{E e;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
  var named=nodes(p,NamedType.class).stream().filter(n->n.declaration().orElse(null) instanceof EnumDeclaration).findFirst().orElseThrow();
  org.junit.jupiter.api.Assertions.assertFalse(PodLayout.layout(named,P).supported());
 }
 @org.junit.jupiter.api.Test void lexicalUnevaluatedOperatorsRetainTheirActualResultTypes(){
  check("enum E{A=1U,B=1L,C=sizeof(A<<B),D=sizeof(A<B),F=sizeof(A+B)};struct S{char a[C+D+F];char tail;};unsigned long result=__builtin_offsetof(S,tail);",13);
 }
 @org.junit.jupiter.api.Test void recursiveRepresentationAndTooSmallCeilingsRemainUnsupported(){
  org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),()->{
   var p=parse(P,"enum E{A=sizeof(E)};struct S{E e;char tail;};unsigned long result=__builtin_offsetof(S,tail);");
   org.junit.jupiter.api.Assertions.assertTrue(p.hasErrors());
   org.junit.jupiter.api.Assertions.assertTrue(new IntegralConstants(p,P).evaluate(result(p)).isEmpty());
  });
  var small=P.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","4",null,"gcc-clang-x86_64-default-enum-v1").build();
  var p=parse(small,"enum E{A=4294967296UL};struct S{E e;};unsigned long result=__builtin_offsetof(S,e);");
  org.junit.jupiter.api.Assertions.assertTrue(p.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"));
 }
 @org.junit.jupiter.api.Test void omissionDoesNotEnableLexicalFallback(){
  var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","128").build();
  String source="struct S{char a[8];};enum E{A=1U,B=__builtin_offsetof(S,a[sizeof(+A)])};unsigned long result=B;";
  org.junit.jupiter.api.Assertions.assertTrue(parse(old,source).hasErrors());
  var selected=parse(P,source);org.junit.jupiter.api.Assertions.assertFalse(selected.hasErrors());
  org.junit.jupiter.api.Assertions.assertEquals(BigInteger.valueOf(4),new IntegralConstants(selected,P).evaluate(result(selected)).orElseThrow().number());
 }
 @org.junit.jupiter.api.Test void logicalNotNeedsProvenOperandType(){
  for(String operand:List.of("object","missing")){
   var p=parse(P,"struct X{};X object;struct S{char a[sizeof(!"+operand+")];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
   org.junit.jupiter.api.Assertions.assertTrue(p.hasErrors());
   org.junit.jupiter.api.Assertions.assertTrue(new IntegralConstants(p,P).evaluate(result(p)).isEmpty());
  }
  check("enum E{A=1U,B=sizeof(!A)};struct S{char a[B];char tail;};unsigned long result=__builtin_offsetof(S,tail);",1);
 }
 @org.junit.jupiter.api.Test void conditionalConditionNeedsProvenTypeWithoutEvaluation(){
  for(String condition:List.of("object","missing","!object","!missing"))for(boolean lexical:List.of(false,true)){
   String expression="sizeof("+condition+"?A:B)";
   String source="struct X{};X object;enum E{A=1,B=2"+(lexical?",C="+expression:"")+"};struct S{char a["+(lexical?"C":expression)+"];char tail;};unsigned long result=__builtin_offsetof(S,tail);";
   var p=parse(P,source);org.junit.jupiter.api.Assertions.assertTrue(p.hasErrors());
   org.junit.jupiter.api.Assertions.assertTrue(new IntegralConstants(p,P).evaluate(result(p)).isEmpty());
  }
  check("enum E{A=1,B=2,C=sizeof((1/0)?A:B)};struct S{char a[C];char tail;};unsigned long result=__builtin_offsetof(S,tail);",4);
  check("enum E{A=1,B=2};struct S{char a[sizeof(1.0?A:B)];char tail;};unsigned long result=__builtin_offsetof(S,tail);",4);
  check("int*p;enum E{A=1,B=2};struct S{char a[sizeof(p?A:B)+sizeof(!p)];char tail;};unsigned long result=__builtin_offsetof(S,tail);",5);
 }
 @org.junit.jupiter.api.Test void resolvedLogicalNotUsesItsActualReturnType(){
  check("struct X{};X object;long operator!(X);struct S{char a[sizeof(!object)];char tail;};unsigned long result=__builtin_offsetof(S,tail);",8);
  String source="enum E{A=1};struct X{};X object;E operator!(X);struct S{char a[sizeof(+(!object))];char tail;};unsigned long result=__builtin_offsetof(S,tail);";
  check(source,4);reject(P.toBuilder().enumUnderlyingType("unsigned-first").build(),source);
  retention("typedef int T;enum E{A=sizeof(T)*536870912UL};struct X{};X object;E operator!(X);struct S{char a[sizeof(+(!object))];char tail;};unsigned long result=__builtin_offsetof(S,tail);");
 }
}
