/* Explicit glibc external stream objects, with opaque FILE. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class StandardStreamsRuntimeTest {
 @TempDir Path temp;
 private static final String CONTRACT="glibc-x86_64-lp64";
 private static PlatformProfile.Builder builder(){return PlatformProfile.builder().cRuntime("glibc").standardStreamsBinding(CONTRACT);}
 private final PlatformProfile profile=builder().build();
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("streams.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project resolve(String s){return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(profile),s)).build());}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var out=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))out.add(type.cast(n));}});return out;}
 @Test void explicitContractValidationSerializationAndLegacyIndependence()throws Exception{
  assertEquals("unavailable",PlatformProfile.defaults().standardStreamsBinding());
  assertEquals(CONTRACT,builder().maxMultibyteLength(1).build().standardStreamsBinding());
  assertThrows(IllegalArgumentException.class,()->builder().bitsPerByte(16).sizeBits("char",16).build());
  for(var invalid:List.of(builder().cRuntime("unknown"),builder().sizeType("unsigned int"),builder().sizeBits("pointer",32).alignmentBits("pointer",32),builder().sizeBits("long",32).alignmentBits("long",32),builder().sizeBits("int",16).alignmentBits("int",16),builder().standardStreamsBinding("glibc")))assertThrows(IllegalArgumentException.class,invalid::build);
  assertEquals(profile,profile.toBuilder().build());assertEquals(profile,PlatformProfile.fromSettings(profile.toSettings()));
  assertNotEquals(profile.identityHash(),profile.toBuilder().standardStreamsBinding("unavailable").build().identityHash());
  Path path=temp.resolve("profile.json");Files.writeString(path,com.kniazkov.json.Json.serialize(profile.toSettings()));assertEquals(profile,PlatformProfile.read(path));
  var old=new LinkedHashMap<String,Object>(profile.toSettings());old.remove("standardStreamsBinding");assertEquals("unavailable",PlatformProfile.fromSettings(old).standardStreamsBinding());
 }
 @Test void bothHeadersExposeGlobalMutableOpaquePointersAndPreserveOrdinaryFunctions(){
  for(String h:List.of("cstdio","stdio.h")){
   String ns=h.equals("cstdio")?"std::":"";
   var p=resolve("#include <"+h+">\nint f(){"+ns+"FILE*p=stdin; stdout=stderr; return "+ns+"fputs(\"x\",stdout)+"+ns+"fgetc(p);}");
   assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   for(String name:List.of("stdin","stdout","stderr")){
    var v=nodes(p,VariableDeclaration.class).stream().filter(x->x.name().equals(name)&&LibraryDeclarations.isLibrary(x)).findFirst().orElseThrow();
    assertEquals("C",v.languageLinkage());assertEquals(Declaration.Linkage.EXTERNAL,v.linkage());assertFalse(v.isDefinition());assertTrue(v.initializer().isEmpty());
    assertEquals("pointer(opaque-library(library::FILE))",v.type().orElseThrow().canonicalIdentity());
   }
   assertTrue(nodes(p,CallExpression.class).stream().anyMatch(c->c.target().map(t->t.name().equals("fputs")).orElse(false)));
  }
 }
 @Test void noInventedStdStreamAliasOrFileLayout(){
  var bad=resolve("#include <cstdio>\nint f(){return std::stdout!=0;}");
  // The generic unresolved qualified-name diagnostic is outside this binding.
  assertEquals(1,nodes(bad,ScopeMemberExpression.class).size());
  assertTrue(nodes(bad,ScopeMemberExpression.class).getFirst().target().isEmpty());
  var p=resolve("#include <cstdio>\nint f(){return sizeof(std::FILE);}");
  var size=nodes(p,SizeofExpression.class).getFirst();assertTrue(ConstantEvaluator.evaluate(size,profile).isEmpty());
 }
 @Test void unavailableOnUseButDefinedUnusedHeadersRemainValid(){
  for(var p:List.of(PlatformProfile.defaults(),PlatformProfile.builder().cRuntime("glibc").build()))for(String h:List.of("cstdio","stdio.h")){
   var pp=CppPreprocessor.preprocess(unit(p),"#include <"+h+">\n#if !defined(stdin) || !defined(stdout) || !defined(stderr)\n#error absent\n#endif\n");
   for(String name:List.of("stdin","stdout","stderr")){
    assertNotNull(pp.definitions().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow().unavailableReason());
    for(String use:List.of("int f(){return "+name+"!=0;}","#if "+name+"\n#endif"))assertTrue(assertThrows(ParseException.class,()->Parser.parse(unit(p),"#include <"+h+">\n"+use)).getMessage().contains("standardStreamsBinding"));
   }
  }
 }
 @Test void selfReferencesQualifiedCaptureUndefAndProvenance(){
  String code="#include <cstdio>\n#include <stdio.h>\nint f(){int stdout=3;return stdout+(::stdout!=0);}\n#undef stdout\nint g(){return ::stdout!=0;}";
  var pp=CppPreprocessor.preprocess(unit(profile),code);
  for(String name:List.of("stdin","stdout","stderr")){
   var ds=pp.definitions().stream().filter(d->d.name().equals(name)).toList();assertEquals(2,ds.size());assertNotEquals(ds.get(0).id(),ds.get(1).id());
   assertTrue(ds.stream().allMatch(d->d.rawReplacement().equals(name)&&d.unavailableReason()==null&&d.originKind().equals("library")&&d.sourceKey().contains(profile.identityHash())));
  }
  var p=resolve(code);assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
 }
 @Test void directPointerAndReferenceWritesRetainDependencies(){
  for(String body:List.of("stdout=stderr;return stdout!=0;","std::FILE**p=&stdout;*p=stderr;return stdout!=0;","std::FILE*&r=stdout;r=stderr;return r!=0;","stdout=stderr;std::rand();return stdout!=0;")){
   var p=UnreadWrites.analyze(resolve("#include <cstdio>\n#include <cstdlib>\nint f(){"+body+"}"));assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   assertTrue(p.semanticDiagnostics().stream().noneMatch(d->d.code().equals(UnreadWrites.CODE)),()->body+p.semanticDiagnostics());
   var a=nodes(p,AssignmentExpression.class).getFirst();assertTrue(p.flows().stream().flatMap(f->f.data().stream()).anyMatch(e->e.from()==a&&e.kind()==FunctionFlow.Dependency.DEFINITION),body);
  }
 }
 @Test void unknownPointerLvalueOpaqueLayoutAndUnevaluatedSizeRemainHonest(){
  var original=resolve("#include <cstdio>\n#include <cwchar>\nint f(){return sizeof(stdout)+(stdout!=0)+std::fwide(stderr,0);}");
  for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()))){
   assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   for(var n:nodes(p,NameExpression.class).stream().filter(n->Set.of("stdout","stderr").contains(n.name())).toList()){
    assertEquals(ValueCategory.LVALUE,TypeSemantics.valueCategory(n,profile));
    assertTrue(ConstantEvaluator.evaluate(n,profile).isEmpty());
    assertEquals("pointer(opaque-library(library::FILE))",TypeSemantics.expressionType(n,profile).orElseThrow().canonicalIdentity());
   }
   var size=nodes(p,SizeofExpression.class).getFirst();assertTrue(ConstantEvaluator.evaluate(size,profile).isPresent());
   var flow=DataFlow.analyze(p);var hidden=nodes(nodes(flow,SizeofExpression.class).getFirst(),NameExpression.class).getFirst();
   assertTrue(flow.flows().stream().flatMap(f->f.data().stream()).noneMatch(e->e.from()==hidden||e.to()==hidden));
  }
 }
 @Test void actualHeaderPrecedenceAndTranslationUnitIsolation()throws Exception{
  assertTrue(CppPreprocessor.preprocess(unit(profile),"stdout").definitions().stream().noneMatch(d->d.name().equals("stdout")));
  Files.writeString(temp.resolve("stdio.h"),"#define stdout actual\nextern int actual;\n");
  var u=new Build.TranslationUnit(unit(profile).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(profile);
  var p=NameResolver.resolve(Project.create().addFile(Parser.parse(u,"#include <stdio.h>\nint f(){return stdout;}" )).build());assertFalse(p.hasErrors());
  assertTrue(nodes(p,VariableDeclaration.class).stream().noneMatch(v->v.name().equals("stdout")));
 }
}
