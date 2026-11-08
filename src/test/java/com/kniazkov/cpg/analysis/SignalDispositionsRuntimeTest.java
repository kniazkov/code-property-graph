/* Explicit signal sentinel target contract. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SignalDispositionsRuntimeTest {
 @TempDir Path temp;
 private static final String CONTRACT="glibc-x86_64-lp64";
 private static final List<String> NAMES=List.of("SIG_DFL","SIG_IGN","SIG_ERR");
 private static PlatformProfile.Builder builder(){return PlatformProfile.builder().cRuntime("glibc").signalDispositionBinding(CONTRACT);}
 private final PlatformProfile profile=builder().build();
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project resolve(String code){return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(profile),code)).build());}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var out=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))out.add(type.cast(n));}});return out;}
 @Test void explicitContractAndSettingsAreIndependent()throws Exception{
  assertEquals("unavailable",PlatformProfile.defaults().signalDispositionBinding());
  assertEquals(profile,profile.toBuilder().build());assertEquals(profile,PlatformProfile.fromSettings(profile.toSettings()));
  assertNotEquals(profile.identityHash(),profile.toBuilder().signalDispositionBinding("unavailable").build().identityHash());
  assertEquals(CONTRACT,builder().sizeType("unsigned int").maxMultibyteLength(1).build().signalDispositionBinding());
  for(var b:List.of(builder().cRuntime("unknown"),builder().signalDispositionBinding("glibc"),builder().sizeBits("pointer",32).alignmentBits("pointer",32),builder().sizeBits("long",32).alignmentBits("long",32),builder().sizeBits("int",16).alignmentBits("int",16)))assertThrows(IllegalArgumentException.class,b::build);
  Path f=temp.resolve("profile.json");Files.writeString(f,com.kniazkov.json.Json.serialize(profile.toSettings()));assertEquals(profile,PlatformProfile.read(f));
 }
 @Test void castsHaveExactHandlerTypeWithoutFabricatedCalls(){
  for(String h:List.of("csignal","signal.h")){
   String ns=h.equals("csignal")?"std::":"";
   var p=resolve("#include <"+h+">\nvoid f(){void(*a)(int)=SIG_DFL;void(*b)(int)=SIG_IGN;void(*c)(int)=SIG_ERR;"+ns+"signal(1,a);"+ns+"raise(1);}");
   assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   var casts=nodes(p,ConversionExpression.class).stream().filter(c->c.kind()==ConversionExpression.Kind.C_STYLE_CAST).filter(c->TypeSemantics.expressionType(c,profile).map(t->t.canonicalIdentity().equals("pointer(function(int)->void)")).orElse(false)).toList();assertEquals(3,casts.size());
   for(var c:casts){assertEquals("pointer(function(int)->void)",TypeSemantics.expressionType(c,profile).orElseThrow().canonicalIdentity());assertEquals(ValueCategory.RVALUE,TypeSemantics.valueCategory(c,profile));assertTrue(ConstantEvaluator.evaluate(c,profile).isEmpty());}
   assertEquals(2,nodes(p,CallExpression.class).size());assertTrue(nodes(p,CallExpression.class).stream().allMatch(c->c.target().isPresent()));
  }
 }
 @Test void unavailableOnUseAndNoPreprocessorIntegerPromise(){
  for(String h:List.of("csignal","signal.h"))for(var p:List.of(PlatformProfile.defaults(),PlatformProfile.builder().cRuntime("glibc").build())){
   String include="#include <"+h+">\n";
   var pp=CppPreprocessor.preprocess(unit(p),include+"#if !defined(SIG_DFL)\n#error missing\n#endif\n");
   assertEquals(3,pp.definitions().stream().filter(d->NAMES.contains(d.name())&&d.unavailableReason()!=null).count());
   for(String n:NAMES)assertTrue(assertThrows(ParseException.class,()->Parser.parse(unit(p),include+"void(*p)(int)="+n+";")).getMessage().contains("signalDispositionBinding"));
  }
  for(String n:NAMES)assertThrows(ParseException.class,()->Parser.parse(unit(profile),"#include <csignal>\n#if "+n+"\n#endif"));
 }
 @Test void copiesReanalysisAndSavedAliasesKeepUnknownPointerValues(){
  var original=resolve("#include <csignal>\ntypedef void(*H)(int); int f(){H p=SIG_IGN;H&r=p;H*saved=&p;r=SIG_DFL;std::signal(1,*saved);return sizeof(SIG_ERR);}");
  for(var copy:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()))){
   var p=DataFlow.analyze(copy);assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   var assignment=nodes(p.files().getFirst(),AssignmentExpression.class).getFirst();
   assertTrue(p.flows().stream().flatMap(f->f.data().stream()).anyMatch(e->e.from()==assignment&&e.kind()==FunctionFlow.Dependency.DEFINITION));
   var size=nodes(p.files().getFirst(),SizeofExpression.class).getFirst();
   var hidden=nodes(size,ConversionExpression.class).stream().filter(c->c.kind()==ConversionExpression.Kind.C_STYLE_CAST).findFirst().orElseThrow();
   assertTrue(ConstantEvaluator.evaluate(size,profile).isPresent());assertTrue(ConstantEvaluator.evaluate(hidden,profile).isEmpty());
   assertTrue(p.flows().stream().flatMap(f->f.data().stream()).noneMatch(e->e.from()==hidden||e.to()==hidden));
   var json=com.kniazkov.cpg.serialization.ProjectJson.encode(p).toText("  ");assertTrue(json.contains("signalDispositionBinding"));assertTrue(json.contains("SIG_ERR"));
  }
 }
 @Test void undefAndTranslationUnitsDoNotLeakDefinitions(){
  var pp=CppPreprocessor.preprocess(unit(profile),"#include <csignal>\n#undef SIG_IGN\n#if defined(SIG_IGN)\n#error leaked\n#endif\n#define SIG_IGN 9\nint x=SIG_IGN;");
  assertTrue(pp.definitions().stream().anyMatch(d->d.name().equals("SIG_IGN")&&d.rawReplacement().trim().equals("9")));
  for(String h:List.of("csignal","signal.h")){
   var disabled=CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),"#include <"+h+">\n");
   assertEquals(3,disabled.definitions().stream().filter(d->NAMES.contains(d.name())&&d.unavailableReason()!=null).count());
  }
 }
 @Test void definitionsPreserveHeaderProfileAndPhysicalAuthority()throws Exception{
  for(String includes:List.of("#include <csignal>\n#include <signal.h>\n", "#include <signal.h>\n#include <csignal>\n")){
  var pp=CppPreprocessor.preprocess(unit(profile),includes+"SIG_DFL SIG_IGN SIG_ERR");
  var ds=pp.definitions().stream().filter(d->NAMES.contains(d.name())).toList();assertEquals(6,ds.size());assertEquals(6,ds.stream().map(d->d.id()).distinct().count());
  assertTrue(ds.stream().allMatch(d->d.originKind().equals("library")&&d.unavailableReason()==null&&d.sourceKey().contains(profile.identityHash())));
  var expected=Map.of("SIG_DFL","((void (*)(int)) 0)","SIG_IGN","((void (*)(int)) 1)","SIG_ERR","((void (*)(int)) -1)");
  for(var d:ds)assertEquals(expected.get(d.name()),d.rawReplacement().trim());
  }
  assertTrue(CppPreprocessor.preprocess(unit(profile),"SIG_DFL").definitions().stream().noneMatch(d->NAMES.contains(d.name())));
  Files.writeString(temp.resolve("signal.h"),"#define SIG_DFL 17\n");
  var real=new Build.TranslationUnit(unit(profile).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(profile);
  var p=NameResolver.resolve(Project.create().addFile(Parser.parse(real,"#include <signal.h>\nint f(){return SIG_DFL;}")).build());assertFalse(p.hasErrors());assertTrue(nodes(p,ConversionExpression.class).isEmpty());
 }
}
