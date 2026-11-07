/* Explicit WEOF/wint_t model and nonnegative character envelope. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class WeofRuntimeTest {
 @TempDir Path temp;
 private static PlatformProfile profile(String type,String value){return PlatformProfile.builder().wideInteger(type,value,"nonnegative-wchar").build();}
 private final PlatformProfile unsigned=profile("unsigned int","4294967295");
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("wide.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project resolve(PlatformProfile p,String code){return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(p),code)).build());}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var out=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))out.add(type.cast(n));}});return out;}
 @Test void exactTypesAndRenderFormsUseIndependentBoundaryOracles(){
  String[][] cases={{"int","-1","(-1)"},{"int","-2147483648","(-2147483647 - 1)"},{"unsigned int","4294967295","4294967295U"},{"long","-1","(-1L)"},{"long","-9223372036854775808","(-9223372036854775807L - 1L)"},{"long","2147483648","2147483648L"},{"unsigned long","18446744073709551615","18446744073709551615UL"}};
  for(String[] c:cases){var p=profile(c[0],c[1]);assertEquals(c[2],p.wideInteger().orElseThrow().replacement(p));
   var graph=resolve(p,"#include <cwchar>\n"+c[0]+" f(){return WEOF;}");assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
   Expression value=nodes(graph.files().getFirst(),ReturnStatement.class).getFirst().value().orElseThrow();
   while(value instanceof ConversionExpression c1 && !c1.isExplicit()) value=c1.operand();
   assertEquals(c[0],TypeSemantics.expressionType(value,p).orElseThrow().canonicalIdentity());
   Parser.parse(unit(p),"#include <cwctype>\n#if WEOF != "+c[2]+"\n#error incorrect value\n#endif\nint x;");
  }
  for(String type:List.of("long","unsigned long")){
   var p=PlatformProfile.builder().sizeBits("wchar_t",16).alignmentBits("wchar_t",16).wcharType("short").wideExecutionEncoding("UTF-16").wideInteger(type,"32768","nonnegative-wchar").build();
   assertEquals(type.equals("long")?"32768L":"32768UL",p.wideInteger().orElseThrow().replacement(p));
   var graph=resolve(p,"#include <cwchar>\nint f(int);int f(long);int f(unsigned long);int g(){return f(WEOF);}");
   assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
   assertEquals(type,nodes(graph.files().getFirst(),CallExpression.class).getFirst().target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
  }
  Parser.parse(unit(unsigned),"#include <cwchar>\n#if WEOF + 1 != 4294967296UL\n#error preprocessing width\n#endif\nint x;");
  var wrapping=resolve(unsigned,"#include <cwchar>\nunsigned int wrapped=WEOF+1U;");
  var initializer=nodes(wrapping.files().getFirst(),VariableDeclaration.class).getFirst().initializer().orElseThrow();
  assertEquals(java.math.BigInteger.ZERO,ConstantEvaluator.evaluate(initializer,unsigned).orElseThrow().number());
 }
 @Test void boundedDomainRejectsOverlapButAllowsNegativeWithinWcharRange(){
  assertTrue(profile("int","-1").integerRange("wchar_t").orElseThrow().contains(java.math.BigInteger.valueOf(-1)));
  for(String type:List.of("short","unsigned short","char","bool","wchar_t","long long"))assertThrows(IllegalArgumentException.class,()->profile(type,"-1"));
  for(String value:List.of("0","65","2147483647","4294967296","-1"))assertThrows(IllegalArgumentException.class,()->profile("unsigned int",value));
  assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().wcharType("unsigned int").wideInteger("int","-1","nonnegative-wchar").build());
  assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().wideInteger("int","-1","all-wchar").build());
  for(String value:List.of("1U","+1","01","1.0","- 1"))assertThrows(IllegalArgumentException.class,()->profile("int",value));
 }
 @Test void settingsCopyIdentityStrictFilesAndDefaultOpaque()throws Exception{
  assertTrue(PlatformProfile.defaults().wideInteger().isEmpty());assertTrue(PlatformProfile.builder().cRuntime("glibc").build().wideInteger().isEmpty());
  assertEquals(unsigned,unsigned.toBuilder().build());assertEquals(unsigned,PlatformProfile.fromSettings(unsigned.toSettings()));
  assertNotEquals(unsigned.identityHash(),PlatformProfile.defaults().identityHash());assertNotEquals(unsigned.identityHash(),profile("int","-1").identityHash());
  Path p=temp.resolve("profile.json");Files.writeString(p,com.kniazkov.json.Json.serialize(unsigned.toSettings()));assertEquals(unsigned,PlatformProfile.read(p));
  for(String body:List.of("{\"wintType\":\"int\"}","{\"wintType\":\"int\",\"weof\":-1,\"characterDomain\":\"nonnegative-wchar\"}","{\"wintType\":\"int\",\"weof\":\"-1\",\"weof\":\"-2\",\"characterDomain\":\"nonnegative-wchar\"}")){
   Files.writeString(p,"{\"inherits\":\"default\",\"wideInteger\":"+body+"}");assertThrows(IllegalArgumentException.class,()->PlatformProfile.read(p));
  }
  var unknown=resolve(PlatformProfile.defaults(),"#include <cwchar>\nstd::wint_t f(){return std::btowc(0);}");assertFalse(unknown.hasErrors());
  var alias=nodes(unknown,TypedefDeclaration.class).stream().filter(t->t.name().equals("wint_t")).findFirst().orElseThrow();assertInstanceOf(OpaqueLibraryType.class,alias.type().canonicalType());
 }
 @Test void headersSignaturesAndCharacterTraitsShareConfiguredType(){
  for(String first:List.of("cwchar","wchar.h","cwctype","wctype.h")){
   String ns=first.endsWith(".h")?"":"std::";
   var p=resolve(unsigned,"#include <"+first+">\n#include <cwchar>\n#include <cwctype>\n#include <string>\n"+ns+"wint_t f(){std::char_traits<wchar_t>::int_type v=WEOF;return std::towlower(v)+std::btowc(0)+WEOF;}");
   assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   for(var graph:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()))){
    for(var alias:nodes(graph,TypedefDeclaration.class).stream().filter(t->t.name().equals("wint_t")).toList())assertEquals("unsigned int",alias.type().canonicalIdentity());
    for(var call:nodes(graph.files().getFirst(),CallExpression.class))assertEquals("unsigned int",call.target().orElseThrow().returnType().orElseThrow().canonicalIdentity());
    var v=nodes(graph.files().getFirst(),VariableDeclaration.class).stream().filter(x->x.name().equals("v")).findFirst().orElseThrow();assertEquals("unsigned int",v.type().orElseThrow().canonicalIdentity());
   }
  }
 }
 @Test void compatibleCatalogueAliasesDoNotMergeOrdinaryNamesOrTargetProfiles(){
  var unrelated=resolve(unsigned,"namespace a{typedef unsigned int value;}namespace b{typedef long value;}using namespace a;using namespace b;value x;");
  assertTrue(unrelated.semanticDiagnostics().stream().anyMatch(d->d.code().equals("ambiguous-name")));
  var bad=resolve(unsigned,"#include <cwchar>\nnamespace std{typedef long wint_t;}std::wint_t f(){return WEOF;}");assertTrue(bad.hasErrors());
  var builder=Project.create();int i=0;
  for(var profile:List.of(unsigned,profile("int","-1"))){var u=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("unit"+(i++)+".cpp")),temp,List.of(),List.of(),"c++03").withPlatform(profile);builder.addFile(Parser.parse(u,"#include <cwchar>\n#include <cwctype>\nstd::wint_t f(){return WEOF;}"));}
  var graph=NameResolver.resolve(builder.build());assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
  assertEquals(List.of("unsigned int","int"),graph.files().stream().map(f->nodes(f,FunctionDeclaration.class).getFirst().returnType().orElseThrow().canonicalIdentity()).toList());
 }
 @Test void unavailablePresenceAndHeaderIsolation()throws Exception{
  for(String h:List.of("cwchar","wchar.h","cwctype","wctype.h")){
   String include="#include <"+h+">\n";var pp=CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),include+"#if !defined(WEOF)\n#error missing\n#endif\n");
   assertNotNull(pp.definitions().stream().filter(d->d.name().equals("WEOF")).findFirst().orElseThrow().unavailableReason());
   for(String use:List.of("int x=WEOF;","#if WEOF\n#endif"))assertTrue(assertThrows(ParseException.class,()->Parser.parse(unit(PlatformProfile.defaults()),include+use)).getMessage().contains("wideInteger"));
  }
  assertTrue(CppPreprocessor.preprocess(unit(unsigned),"WEOF").definitions().stream().noneMatch(d->d.name().equals("WEOF")));
  var pp=CppPreprocessor.preprocess(unit(unsigned),"#include <cwchar>\n#include <cwctype>\nWEOF");var defs=pp.definitions().stream().filter(d->d.name().equals("WEOF")).toList();assertEquals(2,defs.size());assertNotEquals(defs.get(0).id(),defs.get(1).id());assertTrue(defs.stream().allMatch(d->d.unavailableReason()==null&&d.originKind().equals("library")&&d.sourceKey().contains(unsigned.identityHash())));
  Files.writeString(temp.resolve("wchar.h"),"#define WEOF 7\n");var u=new Build.TranslationUnit(unit(unsigned).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(unsigned);
  assertEquals("7",CppPreprocessor.preprocess(u,"#include <wchar.h>\nWEOF").tokens().getFirst().token().spelling());
 }
}
