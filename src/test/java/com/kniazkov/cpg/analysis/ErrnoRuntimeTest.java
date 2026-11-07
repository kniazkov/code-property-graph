/* Explicit mutable glibc errno binding, never a host-derived constant. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ErrnoRuntimeTest {
 @TempDir Path temp;
 private final PlatformProfile glibc=PlatformProfile.builder().cRuntime("glibc").build();
 private Build.TranslationUnit unit(PlatformProfile p){return new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("errno.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);}
 private Project analyze(String source){return UnreadWrites.analyze(NameResolver.resolve(Project.create().addFile(Parser.parse(unit(glibc),source)).build()));}
 private static <T extends Node> List<T> nodes(Node root,Class<T> type){var result=new ArrayList<T>();root.walk(new NodeVisitor(){public void visit(Node n){if(type.isInstance(n))result.add(type.cast(n));}});return result;}
 @Test void bothHeadersExposeWritableIntLvalueThroughRealGlobalLibraryHelper(){
  for(String h:List.of("cerrno","errno.h")){
   var p=analyze("#include <"+h+">\nint f(){int __errno_location=1; errno=0; ++errno; errno+=1; int&r=errno; int*p=&errno; r=7; return *p+errno+sizeof(errno)+__errno_location;}");
   assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   var calls=nodes(p,CallExpression.class);assertFalse(calls.isEmpty());
   for(var call:calls){assertTrue(call.target().isPresent());assertEquals("__errno_location",call.target().orElseThrow().name());assertTrue(LibraryDeclarations.isLibrary(call.target().orElseThrow()));}
   for(var d:nodes(p,DereferenceExpression.class)){assertTrue(ConstantEvaluator.evaluate(d,glibc).isEmpty());assertEquals("int",TypeSemantics.expressionType(d,glibc).orElseThrow().canonicalIdentity());}
   assertTrue(p.semanticDiagnostics().stream().noneMatch(d->d.code().equals(UnreadWrites.CODE)));
   assertFalse(StandardLibraryCatalogue.declarations(h,glibc).contains("extern int errno"));
   assertEquals("extern \"C\" int* __errno_location() throw();",StandardLibraryCatalogue.declarations(h,glibc));
  }
 }
 @Test void unknownRuntimeIncludesAreUsableButValueUseAndIfAreUnavailable(){
  for(String h:List.of("cerrno","errno.h")){
   var pp=CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),"#include <"+h+">\n#if !defined(errno)\n#error missing macro\n#endif\n");
   var d=pp.definitions().stream().filter(x->x.name().equals("errno")).findFirst().orElseThrow();assertNotNull(d.unavailableReason());
   for(String use:List.of("int f(){return errno;}","#if errno\n#endif"))assertTrue(assertThrows(ParseException.class,()->Parser.parse(unit(PlatformProfile.defaults()),"#include <"+h+">\n"+use)).getMessage().contains("cRuntime=glibc"));
  }
  assertThrows(ParseException.class,()->Parser.parse(unit(glibc),"#include <cerrno>\n#if errno\n#endif\n"));
 }
 @Test void directSavedPointerAndReferenceReadsStayMutableAndHaveWriteDependencies(){
  for(String body:List.of("errno=0; return errno;","int*p=&errno; *p=0; return errno;","int&r=errno; r=0; return errno;","int&r=errno; int&s=r; s=0; return errno;","int&r=errno; r=0; std::strtol(\"9999999999999999999999999999\",0,10); return r;","int*p=&errno; *p=0; std::strtol(\"9999999999999999999999999999\",0,10); return *p;")){
   var p=analyze("#include <cerrno>\n#include <cstdlib>\nint f(){"+body+"}");assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
   assertTrue(p.semanticDiagnostics().stream().noneMatch(d->d.code().equals(UnreadWrites.CODE)),()->body+p.semanticDiagnostics());
   var assignment=nodes(p,AssignmentExpression.class).getFirst();
   assertTrue(p.flows().stream().flatMap(f->f.data().stream()).anyMatch(e->e.from()==assignment&&e.kind()==FunctionFlow.Dependency.DEFINITION),body);
   for(var d:nodes(p,DereferenceExpression.class))assertTrue(ConstantEvaluator.evaluate(d,glibc).isEmpty());
  }
 }
 @Test void sizeofDoesNotEvaluateTheAccessorOrPublishCallEffects(){
  var p=analyze("#include <cerrno>\nint f(){errno=7;return sizeof(errno);}");
  var size=nodes(p,SizeofExpression.class).getFirst();
  var unevaluated=nodes(size,CallExpression.class).getFirst();
  assertTrue(p.flows().stream().flatMap(f->f.data().stream()).noneMatch(e->e.from()==unevaluated||e.to()==unevaluated));
  assertTrue(ConstantEvaluator.evaluate(size,glibc).isPresent());
 }
 @Test void activationIsolationRealHeaderPrecedenceAndDefinitionProvenance()throws Exception{
  assertTrue(CppPreprocessor.preprocess(unit(glibc),"errno").definitions().stream().noneMatch(d->d.name().equals("errno")));
  var pp=CppPreprocessor.preprocess(unit(glibc),"#include <cerrno>\n#include <errno.h>\nint x=errno;");
  var defs=pp.definitions().stream().filter(d->d.name().equals("errno")).toList();assertEquals(2,defs.size());assertNotEquals(defs.get(0).id(),defs.get(1).id());
  assertTrue(defs.stream().allMatch(d->d.unavailableReason()==null&&d.originKind().equals("library")&&d.sourceKey().contains(glibc.identityHash())));
  assertTrue(CppPreprocessor.preprocess(unit(PlatformProfile.defaults()),"#include <cerrno>\n").definitions().stream().filter(d->d.name().equals("errno")).allMatch(d->d.unavailableReason()!=null));
  Files.writeString(temp.resolve("errno.h"),"#define errno actual_errno\nextern int actual_errno;\n");
  var real=new Build.TranslationUnit(unit(glibc).source(),temp,List.of(new Build.IncludePath("-I",temp)),List.of(),"c++03").withPlatform(glibc);
  var p=NameResolver.resolve(Project.create().addFile(Parser.parse(real,"#include <errno.h>\nint f(){return errno;}" )).build());assertFalse(p.hasErrors());assertTrue(nodes(p,CallExpression.class).isEmpty());
 }
}
