/* Explicit symbolic infinity without fabricated runtime calls. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class HugeValRuntimeTest {
 @TempDir Path temp;
 private static PlatformProfile selected(){return PlatformProfile.builder().hugeVal("positive_infinity","gcc-clang-builtin-huge-val").build();}
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("huge.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project resolve(PlatformProfile p,String code){return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(p),code)).build());}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var out=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))out.add(type.cast(n));}});return out;}
 @Test void exactDoubleSymbolicLeafCopiesResolvesAndExportsWithoutCallable()throws Exception{
  var p=selected();
  for(String headers:List.of("#include <cmath>\n","#include <math.h>\n","#include <cmath>\n#include <math.h>\n","#include <math.h>\n#include <cmath>\n")){
   var graph=resolve(p,headers+"int kind(float);int kind(double);int kind(long double);int f(){return kind(HUGE_VAL);} double v=HUGE_VAL;");
   assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
   for(var copy:List.of(graph,graph.edit().build(),NameResolver.resolve(graph.edit().build()))){
    assertEquals(2,nodes(copy,CompilerIntrinsicExpression.class).size());
    for(var v:nodes(copy,CompilerIntrinsicExpression.class)){
     assertEquals("double",TypeSemantics.expressionType(v,p).orElseThrow().canonicalIdentity());
     assertEquals(ValueCategory.RVALUE,v.semanticInfo().orElseThrow().category());
     assertEquals(CompilerIntrinsicExpression.Kind.DOUBLE_POSITIVE_INFINITY,v.kind());
     assertTrue(v.children().isEmpty());assertTrue(v.references().isEmpty());
     assertTrue(ConstantEvaluator.evaluate(v,p).isEmpty());
    }
    assertEquals(1,nodes(copy.files().getFirst(),CallExpression.class).size());
    assertEquals("double",nodes(copy.files().getFirst(),CallExpression.class).getFirst().target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
    var json=ProjectJson.encode(copy).toText("  ");assertTrue(json.contains("double_positive_infinity"));assertTrue(json.contains("gcc-clang-builtin-huge-val"));
    com.kniazkov.json.Json.parse(json);
   }
  }
 }
 @Test void exactSelectedSyntaxOnlyAndNoUserDeclarationsOrQualifiedLookup(){
  var p=selected();var graph=resolve(p,"double x=__builtin_huge_val();");assertFalse(graph.hasErrors());
  assertThrows(ParseException.class,()->resolve(PlatformProfile.defaults(),"double x=__builtin_huge_val();"));
  for(String source:List.of("double x=__builtin_huge_val(1);","double x=__builtin_huge_val;","double x=__builtin_huge_valf();","double x=::__builtin_huge_val();","double __builtin_huge_val();","namespace x{double __builtin_huge_val();}","struct X{double __builtin_huge_val();};","double f(){X x;return x.__builtin_huge_val();}","double f(){return (&__builtin_huge_val)();}","double x=__builtin_huge_val()();","void f(){__builtin_huge_val()=1.0;}","void f(){++__builtin_huge_val();}"))
   assertThrows(ParseException.class,()->resolve(p,source),source);
 }
 @Test void floatingArithmeticAndConversionsStayConservativelyUnknown(){
  var p=selected();
  var graph=resolve(p,"#include <cmath>\ndouble a=HUGE_VAL-HUGE_VAL; double b=HUGE_VAL/HUGE_VAL; int c=static_cast<int>(HUGE_VAL); bool d=HUGE_VAL>0.0; unsigned long n=sizeof(HUGE_VAL);");
  assertFalse(graph.hasErrors(),()->graph.semanticDiagnostics().toString());
  for(var v:nodes(graph.files().getFirst(),VariableDeclaration.class)){
   var value=ConstantEvaluator.evaluate(v.initializer().orElseThrow(),p);
   if(v.name().equals("n"))assertEquals(java.math.BigInteger.valueOf(8),value.orElseThrow().number());
   else assertTrue(value.isEmpty(),v.name());
  }
  assertTrue(nodes(graph.files().getFirst(),CallExpression.class).isEmpty());
 }
 @Test void intrinsicIntroducesNoCallMemoryOrExceptionalEffects(){
  var graph=DataFlow.analyze(resolve(selected(),"#include <cmath>\nint f(){int x=7;double h=HUGE_VAL;return x+sizeof(HUGE_VAL);}\n"));
  assertFalse(graph.hasErrors());assertTrue(nodes(graph,CallExpression.class).isEmpty());
  assertTrue(graph.flows().stream().allMatch(FunctionFlow::complete));
  assertTrue(graph.flows().stream().flatMap(f->f.control().stream()).noneMatch(e->e.kind()==FunctionFlow.Branch.EXCEPTION||e.kind()==FunctionFlow.Branch.THROW));
  var x=nodes(graph,VariableDeclaration.class).stream().filter(v->v.name().equals("x")).findFirst().orElseThrow();
  var read=nodes(graph,NameExpression.class).stream().filter(n->n.name().equals("x")).findFirst().orElseThrow();
  assertTrue(graph.flows().stream().flatMap(f->f.data().stream()).anyMatch(e->e.from()==x&&e.to()==read));
  var unevaluated=nodes(nodes(graph,SizeofExpression.class).getFirst(),CompilerIntrinsicExpression.class).getFirst();
  assertTrue(graph.flows().stream().flatMap(f->f.data().stream()).noneMatch(e->e.from()==unevaluated||e.to()==unevaluated));
 }
 @Test void strictProfileRoundTripsAndRejectsUnsupportedContracts()throws Exception{
  var p=selected();assertEquals(p,p.toBuilder().build());assertEquals(p,PlatformProfile.fromSettings(p.toSettings()));
  assertTrue(PlatformProfile.defaults().hugeVal().isEmpty());assertTrue(PlatformProfile.builder().cRuntime("glibc").build().hugeVal().isEmpty());
  var empty=new LinkedHashMap<String,Object>(p.toSettings());empty.put("hugeVal",Map.of());assertTrue(PlatformProfile.fromSettings(empty).hugeVal().isEmpty());
  assertNotEquals(p.identityHash(),PlatformProfile.defaults().identityHash());
  for(String format:List.of("unknown","binary80","binary128"))assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().sizeBits("double",128).floatingFormat("double",format).hugeVal("positive_infinity","gcc-clang-builtin-huge-val").build());
  assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().sizeBits("double",128).hugeVal("positive_infinity","gcc-clang-builtin-huge-val").build());
  assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().hugeVal("finite","gcc-clang-builtin-huge-val").build());
  assertThrows(IllegalArgumentException.class,()->PlatformProfile.builder().hugeVal("positive_infinity","libc-function").build());
  Path path=temp.resolve("profile.json");Files.writeString(path,com.kniazkov.json.Json.serialize(p.toSettings()));assertEquals(p,PlatformProfile.read(path));
  for(String body:List.of("{\"kind\":\"positive_infinity\"}","{\"kind\":\"positive_infinity\",\"emission\":\"gcc-clang-builtin-huge-val\",\"value\":1}","{\"kind\":\"positive_infinity\",\"kind\":\"finite\",\"emission\":\"gcc-clang-builtin-huge-val\"}")){
   Files.writeString(path,"{\"inherits\":\"default\",\"hugeVal\":"+body+"}");assertThrows(IllegalArgumentException.class,()->PlatformProfile.read(path));
  }
 }
 @Test void generationRequiresAuthoritativeCapabilityAndRejectsCrossLanguage(){
  var bare=CompilerIntrinsicExpression.create().build();
  assertThrows(IllegalArgumentException.class,()->bare.source(Language.CPP));
  var approved=resolve(selected(),"double x=1.0;");
  var editor=new ProjectEditor(approved);editor.replace(nodes(approved,VariableDeclaration.class).getFirst().initializer().orElseThrow(),bare);
  var edited=NameResolver.resolve(editor.build());
  String source=com.kniazkov.cpg.Cpg.sources(edited,Language.CPP).get("huge.cpp");
  assertTrue(source.contains("__builtin_huge_val()"));
  assertEquals(1,nodes(resolve(selected(),source),CompilerIntrinsicExpression.class).size());
  assertThrows(IllegalArgumentException.class,()->com.kniazkov.cpg.Cpg.sources(edited,Language.JAVA));
  var captured=resolve(selected(),"double x=__builtin_huge_val();");
  var unauthorized=resolve(PlatformProfile.defaults(),"double x=1.0;");
  var moved=new ProjectEditor(unauthorized);moved.replace(nodes(unauthorized,VariableDeclaration.class).getFirst().initializer().orElseThrow(),nodes(captured,CompilerIntrinsicExpression.class).getFirst());
  var bad=moved.build();
  assertThrows(IllegalArgumentException.class,()->com.kniazkov.cpg.Cpg.sources(bad,Language.CPP));
  assertThrows(IllegalArgumentException.class,()->bad.source(Language.CPP));
  assertThrows(IllegalArgumentException.class,()->com.kniazkov.cpg.generation.Source.physicalSources(bad));
 }
 @Test void presenceUnavailableProvenanceIsolationAndRealHeaderPriority()throws Exception{
  var p=selected();
  for(String h:List.of("cmath","math.h")){
   String include="#include <"+h+">\n";
   var pp=CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),include+"#if !defined(HUGE_VAL)\n#error absent\n#endif\n");
   assertNotNull(pp.definitions().stream().filter(d->d.name().equals("HUGE_VAL")).findFirst().orElseThrow().unavailableReason());
   assertThrows(ParseException.class,()->resolve(PlatformProfile.defaults(),include+"double x=HUGE_VAL;"));
   assertThrows(ParseException.class,()->resolve(p,include+"#if HUGE_VAL\n#endif\n"));
  }
  var pp=CppPreprocessor.preprocess(unit(p),"#include <cmath>\n#include <math.h>\nHUGE_VAL");
  var defs=pp.definitions().stream().filter(d->d.name().equals("HUGE_VAL")).toList();assertEquals(2,defs.size());assertNotEquals(defs.get(0).id(),defs.get(1).id());assertTrue(defs.stream().allMatch(d->d.originKind().equals("library")&&d.unavailableReason()==null&&d.sourceKey().contains(p.identityHash())));
  assertTrue(CppPreprocessor.preprocess(unit(p),"HUGE_VAL").definitions().stream().noneMatch(d->d.name().equals("HUGE_VAL")));
  Parser.parse(unit(p),"#include <cmath>\n#undef HUGE_VAL\n#if defined(HUGE_VAL)\n#error defined\n#endif\nint x;");
  Files.writeString(temp.resolve("math.h"),"#define HUGE_VAL 7.0\n");var u=new Build.TranslationUnit(unit(p).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(p);
  assertEquals("7.0",CppPreprocessor.preprocess(u,"#include <math.h>\nHUGE_VAL").tokens().getFirst().token().spelling());
 }
}
