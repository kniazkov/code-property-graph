/* Explicit live multibyte query contract, not a universal C90 size_t rule. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class MbCurMaxRuntimeTest {
 @TempDir Path temp;
 private static final String CONTRACT="glibc-x86_64-lp64-mb16";
 private static PlatformProfile.Builder builder(){return PlatformProfile.builder().cRuntime("glibc").mbCurMaxBinding(CONTRACT);}
 private final PlatformProfile profile=builder().build();
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project resolve(String code){return NameResolver.resolve(Project.create().addFile(Parser.parse(unit(profile),code)).build());}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var out=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))out.add(type.cast(n));}});return out;}
 @Test void exactContractValidationAndLegacyDefaultsRemainIndependent(){
  assertEquals(16,PlatformProfile.defaults().maxMultibyteLength());
  var legacy=new LinkedHashMap<String,Object>(PlatformProfile.defaults().toSettings());legacy.remove("maxMultibyteLength");legacy.remove("mbCurMaxBinding");legacy.put("cRuntime","glibc");
  var old=PlatformProfile.fromSettings(legacy);assertEquals(1,old.maxMultibyteLength());assertEquals("unavailable",old.mbCurMaxBinding());
  assertEquals("glibc",old.cRuntime());
  for(var invalid:List.of(builder().cRuntime("unknown"),builder().sizeType("unsigned int"),builder().maxMultibyteLength(1),builder().maxMultibyteLength(17),builder().sizeBits("pointer",32).alignmentBits("pointer",32),builder().sizeBits("long",32).alignmentBits("long",32),builder().mbCurMaxBinding("glibc")))
   assertThrows(IllegalArgumentException.class,invalid::build);
  assertThrows(IllegalArgumentException.class,()->builder().sizeBits("int",16).alignmentBits("int",16).build());
  assertThrows(IllegalArgumentException.class,()->builder().bitsPerByte(16).sizeBits("char",16).build());
 }
 @Test void settingsCopyIdentityAndJsonFileRoundTrip()throws Exception{
  assertEquals(profile,profile.toBuilder().build());assertEquals(profile,PlatformProfile.fromSettings(profile.toSettings()));
  assertNotEquals(profile.identityHash(),profile.toBuilder().mbCurMaxBinding("unavailable").build().identityHash());
  Path p=temp.resolve("profile.json");Files.writeString(p,com.kniazkov.json.Json.serialize(profile.toSettings()));assertEquals(profile,PlatformProfile.read(p));
  assertEquals(CONTRACT,profile.toSettings().get("mbCurMaxBinding"));
  assertNotEquals(unit(profile).variantId(),unit(profile.toBuilder().mbCurMaxBinding("unavailable").build()).variantId());
 }
 @Test void bothHeadersKeepOrdinaryFunctionsAndExactGlobalHelperType(){
  for(String header:List.of("cstdlib","stdlib.h")){
   String ns=header.equals("cstdlib")?"std::":"";
   var p=resolve("#include <"+header+">\n"+ns+"size_t f(){int __ctype_get_mb_cur_max=1; return MB_CUR_MAX+"+ns+"strtol(\"2\",0,10)+__ctype_get_mb_cur_max;}");
   assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   var helper=nodes(p,CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("__ctype_get_mb_cur_max")).orElse(false)).findFirst().orElseThrow();
   var target=helper.target().orElseThrow();assertTrue(LibraryDeclarations.isLibrary(target));assertEquals("C",target.languageLinkage());assertTrue(target.hasExceptionSpecification());assertTrue(target.exceptionTypes().isEmpty());assertTrue(target.body().isEmpty());
   assertEquals("unsigned long",target.returnType().orElseThrow().canonicalIdentity());
   var type=nodes(p,TypedefDeclaration.class).stream().filter(t->t.name().equals("size_t")).findFirst().orElseThrow();
   assertEquals(type.type().canonicalIdentity(),target.returnType().orElseThrow().canonicalIdentity());
   assertTrue(ConstantEvaluator.evaluate(helper,profile).isEmpty());
   assertEquals(ValueCategory.RVALUE,TypeSemantics.valueCategory(helper,profile));
   assertTrue(nodes(p,CallExpression.class).stream().anyMatch(c->c.target().map(t->t.name().equals("strtol")).orElse(false)));
  }
 }
 @Test void missingCapabilityRecordsUnavailableWithoutRejectingUnusedHeader(){
  for(var p:List.of(PlatformProfile.defaults(),PlatformProfile.builder().cRuntime("glibc").build()))for(String h:List.of("cstdlib","stdlib.h")){
   String include="#include <"+h+">\n";
   var pp=CppPreprocessor.preprocess(unit(p),include+"#if !defined(MB_CUR_MAX)\n#error missing\n#endif\n");
   assertNotNull(pp.definitions().stream().filter(d->d.name().equals("MB_CUR_MAX")).findFirst().orElseThrow().unavailableReason());
   for(String use:List.of("int x=MB_CUR_MAX;","#if MB_CUR_MAX\n#endif"))assertTrue(assertThrows(ParseException.class,()->Parser.parse(unit(p),include+use)).getMessage().contains("mbCurMaxBinding"));
  }
  assertThrows(ParseException.class,()->Parser.parse(unit(profile),"#include <cstdlib>\n#if MB_CUR_MAX\n#endif"));
 }
 @Test void queryIsUnknownRvalueAndSizeofDoesNotCall(){
  var p=DataFlow.analyze(resolve("#include <cstdlib>\n#include <clocale>\nint f(){unsigned long first=MB_CUR_MAX;std::setlocale(0,\"C\");unsigned long second=MB_CUR_MAX;return first+second+sizeof(MB_CUR_MAX);}"));
  assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
  var calls=nodes(p,CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("__ctype_get_mb_cur_max")).orElse(false)).toList();assertEquals(3,calls.size());
  for(var c:calls)assertTrue(ConstantEvaluator.evaluate(c,profile).isEmpty());
  var size=nodes(p,SizeofExpression.class).getFirst();var hidden=nodes(size,CallExpression.class).getFirst();assertTrue(ConstantEvaluator.evaluate(size,profile).isPresent());
  assertTrue(p.flows().stream().flatMap(f->f.data().stream()).noneMatch(e->e.from()==hidden||e.to()==hidden));
  // General scalar-call rvalue misuse diagnostics are not implemented by CPG.
  // Preserve the truthful category; strict native negative probes cover rejection.
  for(String misuse:List.of("MB_CUR_MAX=1;","++MB_CUR_MAX;","unsigned long*p=&MB_CUR_MAX;")) {
   var invalid=resolve("#include <cstdlib>\nint f(){"+misuse+"return 0;}");
   var call=nodes(invalid,CallExpression.class).getFirst();
   assertEquals(ValueCategory.RVALUE,TypeSemantics.valueCategory(call,profile));
   assertEquals("unsigned long",TypeSemantics.expressionType(call,profile).orElseThrow().canonicalIdentity());
  }
 }
 @Test void headerProvenanceIsolationAndRealHeaderPrecedence()throws Exception{
  assertTrue(CppPreprocessor.preprocess(unit(profile),"MB_CUR_MAX").definitions().stream().noneMatch(d->d.name().equals("MB_CUR_MAX")));
  var pp=CppPreprocessor.preprocess(unit(profile),"#include <cstdlib>\n#include <stdlib.h>\nMB_CUR_MAX");
  var ds=pp.definitions().stream().filter(d->d.name().equals("MB_CUR_MAX")).toList();assertEquals(2,ds.size());assertNotEquals(ds.get(0).id(),ds.get(1).id());
  assertTrue(ds.stream().allMatch(d->d.originKind().equals("library")&&d.unavailableReason()==null&&d.sourceKey().contains(profile.identityHash())));
  assertTrue(CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),"#include <cstdlib>\n").definitions().stream().filter(d->d.name().equals("MB_CUR_MAX")).allMatch(d->d.unavailableReason()!=null));
  Files.writeString(temp.resolve("stdlib.h"),"#define MB_CUR_MAX 3\n");var real=new Build.TranslationUnit(unit(profile).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(profile);
  var p=NameResolver.resolve(Project.create().addFile(Parser.parse(real,"#include <stdlib.h>\nint f(){return MB_CUR_MAX;}")).build());assertFalse(p.hasErrors());assertTrue(nodes(p,CallExpression.class).isEmpty());
 }
}
