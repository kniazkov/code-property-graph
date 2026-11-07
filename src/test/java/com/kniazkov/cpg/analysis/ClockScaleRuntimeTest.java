/* Explicit CLOCKS_PER_SEC and clock_t target facts. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ClockScaleRuntimeTest {
 @TempDir Path temp;
 private static PlatformProfile profile(String type,String value){return PlatformProfile.builder().clockScale("long",type,value).build();}
 private final PlatformProfile unsigned=profile("unsigned int","4294967295");
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("clock.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project resolve(PlatformProfile p,String code){return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(p),code)).build());}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var out=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))out.add(type.cast(n));}});return out;}
 @Test void exactTypesAndRenderFormsUseIndependentBoundaryOracles(){
  String[][] cases={{"int","1","1"},{"int","2147483647","2147483647"},{"unsigned int","4294967295","4294967295U"},{"long","1","1L"},{"long","9223372036854775807","9223372036854775807L"},{"unsigned long","18446744073709551615","18446744073709551615UL"}};
  for(String[] c:cases){var p=profile(c[0],c[1]);assertEquals(c[2],p.clockScale().orElseThrow().replacement());
   var graph=resolve(p,"#include <ctime>\n"+c[0]+" f(){return CLOCKS_PER_SEC;}");assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
   Expression value=nodes(graph.files().getFirst(),ReturnStatement.class).getFirst().value().orElseThrow();
   while(value instanceof ConversionExpression c1 && !c1.isExplicit()) value=c1.operand();
   assertEquals(c[0],TypeSemantics.expressionType(value,p).orElseThrow().canonicalIdentity());
   Parser.parse(unit(p),"#include <ctime>\n#if CLOCKS_PER_SEC != "+c[2]+"\n#error incorrect value\n#endif\nint x;");
  }
  Parser.parse(unit(unsigned),"#include <ctime>\n#if CLOCKS_PER_SEC + 1 != 4294967296UL\n#error preprocessing width\n#endif\nint x;");
  var wrapping=resolve(unsigned,"#include <ctime>\nunsigned int wrapped=CLOCKS_PER_SEC+1U;");
  var initializer=nodes(wrapping.files().getFirst(),VariableDeclaration.class).getFirst().initializer().orElseThrow();
  assertEquals(java.math.BigInteger.ZERO,ConstantEvaluator.evaluate(initializer,unsigned).orElseThrow().number());
 }
 @Test void boundedPositiveIntegralFactsAreIndependent(){
  for(String type:List.of("short","unsigned short","char","bool","wchar_t","long long","double")){
   assertThrows(IllegalArgumentException.class,()->profile(type,"1"));
   assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().clockScale(type,"int","1").build());
  }
  for(String value:List.of("0","-1","4294967296","1U","+1","01","1.0","- 1"))assertThrows(IllegalArgumentException.class,()->profile("unsigned int",value));
  var p=PlatformProfile.builder().clockScale("int","unsigned long","18446744073709551615").build();
  assertEquals("int",p.clockScale().orElseThrow().clockType());
  assertEquals("18446744073709551615UL",p.clockScale().orElseThrow().replacement());
 }
 @Test void settingsCopyIdentityStrictFilesAndDefaultOpaque()throws Exception{
  assertTrue(PlatformProfile.defaults().clockScale().isEmpty());assertTrue(PlatformProfile.builder().cRuntime("glibc").build().clockScale().isEmpty());
  var empty=new LinkedHashMap<String,Object>(unsigned.toSettings());empty.put("clockScale",Map.of());
  assertTrue(PlatformProfile.fromSettings(empty).clockScale().isEmpty());
  assertEquals(unsigned,unsigned.toBuilder().build());assertEquals(unsigned,PlatformProfile.fromSettings(unsigned.toSettings()));
  assertNotEquals(unsigned.identityHash(),PlatformProfile.defaults().identityHash());assertNotEquals(unsigned.identityHash(),profile("int","1").identityHash());
  Path p=temp.resolve("profile.json");Files.writeString(p,com.kniazkov.json.Json.serialize(unsigned.toSettings()));assertEquals(unsigned,PlatformProfile.read(p));
  for(String body:List.of("{\"clockType\":\"long\"}","{\"clockType\":\"long\",\"expressionType\":\"int\",\"value\":1}","{\"clockType\":\"long\",\"expressionType\":\"int\",\"value\":\"1\",\"value\":\"2\"}")){
   Files.writeString(p,"{\"inherits\":\"default\",\"clockScale\":"+body+"}");assertThrows(IllegalArgumentException.class,()->PlatformProfile.read(p));
  }
  var unknown=resolve(PlatformProfile.defaults(),"#include <ctime>\nstd::clock_t f(){return std::clock();}");assertFalse(unknown.hasErrors());
  var alias=nodes(unknown,TypedefDeclaration.class).stream().filter(t->t.name().equals("clock_t")).findFirst().orElseThrow();assertInstanceOf(OpaqueLibraryType.class,alias.type().canonicalType());
 }
 @Test void headersShareClockTypeIndependentlyOfMacroType(){
  for(String headers:List.of("#include <ctime>\n","#include <time.h>\n","#include <ctime>\n#include <time.h>\n","#include <time.h>\n#include <ctime>\n")){
   String ns=headers.contains("ctime")?"std::":"";
   for(String type:List.of("int","unsigned int","long","unsigned long")){
    var profile=PlatformProfile.builder().clockScale(type,"int","1000").build();
    var p=resolve(profile,headers+ns+"clock_t f(){return "+ns+"clock();}");assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
    for(var graph:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()))){
     for(var alias:nodes(graph,TypedefDeclaration.class).stream().filter(t->t.name().equals("clock_t")).toList())assertEquals(type,alias.type().canonicalIdentity());
     for(var call:nodes(graph.files().getFirst(),CallExpression.class))assertEquals(type,call.target().orElseThrow().returnType().orElseThrow().canonicalIdentity());
    }
   }
  }
 }
 @Test void targetProfilesAndOtherTimeTypesStayIsolated(){
  var builder=Project.create();int i=0;
  for(String type:List.of("int","unsigned long")){
   var profile=PlatformProfile.builder().clockScale(type,"long","1000").build();
   var u=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("unit"+(i++)+".cpp")),temp,List.of(),List.of(),"c++03").withPlatform(profile);
   builder.addFile(Parser.parse(u,"#include <ctime>\n#include <time.h>\nstd::clock_t f(){return std::clock();} std::time_t t;"));
  }
  var graph=NameResolver.resolve(builder.build());assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
  assertEquals(List.of("int","unsigned long"),graph.files().stream().map(f->nodes(f,FunctionDeclaration.class).getFirst().returnType().orElseThrow().canonicalIdentity()).toList());
  for(var alias:nodes(graph,TypedefDeclaration.class).stream().filter(t->t.name().equals("time_t")).toList())assertInstanceOf(OpaqueLibraryType.class,alias.type().canonicalType());
 }
 @Test void unavailablePresenceAndHeaderIsolation()throws Exception{
  for(String h:List.of("ctime","time.h")){
   String include="#include <"+h+">\n";var pp=CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),include+"#if !defined(CLOCKS_PER_SEC)\n#error missing\n#endif\n");
   assertNotNull(pp.definitions().stream().filter(d->d.name().equals("CLOCKS_PER_SEC")).findFirst().orElseThrow().unavailableReason());
   for(String use:List.of("int x=CLOCKS_PER_SEC;","#if CLOCKS_PER_SEC\n#endif"))assertTrue(assertThrows(ParseException.class,()->Parser.parse(unit(PlatformProfile.defaults()),include+use)).getMessage().contains("clockScale"));
  }
  assertTrue(CppPreprocessor.preprocess(unit(unsigned),"CLOCKS_PER_SEC").definitions().stream().noneMatch(d->d.name().equals("CLOCKS_PER_SEC")));
  var pp=CppPreprocessor.preprocess(unit(unsigned),"#include <ctime>\n#include <time.h>\nCLOCKS_PER_SEC");var defs=pp.definitions().stream().filter(d->d.name().equals("CLOCKS_PER_SEC")).toList();assertEquals(2,defs.size());assertNotEquals(defs.get(0).id(),defs.get(1).id());assertTrue(defs.stream().allMatch(d->d.unavailableReason()==null&&d.originKind().equals("library")&&d.sourceKey().contains(unsigned.identityHash())));
  var undefined=CppPreprocessor.preprocess(unit(unsigned),"#include <ctime>\n#undef CLOCKS_PER_SEC\n#if defined(CLOCKS_PER_SEC)\n#error still defined\n#endif\nCLOCKS_PER_SEC");
  assertEquals("CLOCKS_PER_SEC",undefined.tokens().stream().map(t->t.token().spelling()).filter(t->t.equals("CLOCKS_PER_SEC")).findFirst().orElseThrow());
  Files.writeString(temp.resolve("time.h"),"#define CLOCKS_PER_SEC 7\n");var u=new Build.TranslationUnit(unit(unsigned).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(unsigned);
  assertEquals("7",CppPreprocessor.preprocess(u,"#include <time.h>\nCLOCKS_PER_SEC").tokens().getFirst().token().spelling());
 }
}
