/* Bounded borrowed va_list contracts, not general lifecycle proof. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class BorrowedVarargsTest {
 @TempDir Path temp;
 static PlatformProfile profile() { return PlatformProfile.builder().floatingFormat("long double","binary80").varargs("gcc-clang-builtin","x86_64-sysv-lp64-borrowed-v2").build(); }
 static final String SOURCE = "#include <cstdarg>\ntypedef std::va_list List; int leaf(List borrowed){int a=va_arg(borrowed,int);int b=va_arg(borrowed,int);return a+b;} int f(int n,...){List ap;va_start(ap,n);int result=leaf(ap);va_end(ap);return result;}";
 Project read(String s, PlatformProfile p) { var u=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(p);return NameResolver.resolve(Project.create().addFile(Parser.parse(u,s)).build()); }
 static <T extends Node> List<T> nodes(Node p, Class<T> t) {var a=new ArrayList<T>();p.walk(new NodeVisitor(){@Override public void visit(Node n){if(t.isInstance(n))a.add(t.cast(n));}});return a;}
 static void healthy(Project p){assertFalse(p.hasErrors(),()->p.parseProblems()+" / "+p.semanticDiagnostics());}
 @Test void explicitProfileCopiesStagesBindingsAndOpaqueLayout() throws Exception {
  var p=read(SOURCE,profile());healthy(p);
  assertEquals(profile().toSettings(),PlatformProfile.fromSettings(profile().toSettings()).toSettings());
  var old=PlatformProfile.builder().floatingFormat("long double","binary80").varargs("gcc-clang-builtin","x86_64-sysv-lp64-v1").build();assertTrue(read(SOURCE,old).hasErrors());
  Files.writeString(temp.resolve("main.cpp"),SOURCE);
  for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)){
   var graph=Cpg.read(temp,new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(profile()));healthy(graph);
   healthy(graph.edit().build());healthy(NameResolver.resolve(graph.edit().build()));
   healthy(read(Cpg.sources(graph,Language.CPP).get("main.cpp"),profile()));
   assertTrue(ProjectJson.encode(graph).toString().contains("borrowed-v2"));
   var helper=nodes(graph,CallableDeclaration.class).stream().filter(f->f.name().equals("leaf")).findFirst().orElseThrow();
   var facts=TypeSemantics.describe(helper.parameters().getFirst().type().orElseThrow(),profile());assertTrue(facts.sizeBytes().isEmpty());assertTrue(facts.alignmentBits().isEmpty());
  }
 }
 @Test void callerCursorMutationAndHelperStateSurviveFolding() throws Exception {
  Files.writeString(temp.resolve("main.cpp"),SOURCE);var p=Cpg.read(temp,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(profile()));healthy(p);
  var caller=p.flows().stream().filter(f->f.entry().name().equals("f")).findFirst().orElseThrow();var ops=nodes(caller.entry(),VaListExpression.class);
  var helper=p.flows().stream().filter(f->f.entry().name().equals("leaf")).findFirst().orElseThrow();var args=nodes(helper.entry(),VaListExpression.class);
  assertTrue(caller.data().stream().anyMatch(e->e.from()==args.getLast() && e.to()==ops.getLast() && e.kind()==FunctionFlow.Dependency.DEFINITION));
  assertTrue(helper.data().stream().anyMatch(e->e.from()==args.getFirst()&&e.to()==args.getLast()&&e.kind()==FunctionFlow.Dependency.DEFINITION));
  var folded=ConstantFolder.fold(p);assertEquals(4,nodes(folded,VaListExpression.class).size());assertEquals(1,nodes(folded,CallExpression.class).size());
 }
 @Test void invalidAndUnsupportedFormsRemainRejectedWithoutNativeExecution(){
  for(String s:List.of(
   SOURCE + " List global; int main(){return leaf(global);}",
   SOURCE + " List global; int value=leaf(global);",
   SOURCE + " List global; int defaults(int x=leaf(global)){return x;}",
   SOURCE + " struct S{List ap;}; S object; int main(){return leaf(object.ap);}",
   SOURCE + " struct S{List ap;}; S object; int (*fn)(List)=leaf;int main(){return fn(object.ap);}",
   SOURCE + " int forward(List ap){return leaf(ap);}",
   SOURCE + " int two(List a,List b){return va_arg(a,int)+va_arg(b,int);}",
   SOURCE.replace("int result=leaf(ap);", "int (*ptr)(List)=leaf;int result=ptr(ap);"),
   SOURCE.replace("int result=leaf(ap);", "int result=leaf(n?ap:ap);"),
   SOURCE.replace("leaf(List borrowed)","leaf(List borrowed,int extra)").replace("leaf(ap)","leaf(ap,va_arg(ap,int))"),
   SOURCE.replace("int result=leaf(ap);va_end(ap);","int result=leaf(ap);va_arg(ap,int);va_end(ap);"),
   SOURCE.replace("int result=leaf(ap);va_end(ap);","int result=leaf(ap);leaf(ap);va_end(ap);"),
   SOURCE.replace("va_start(ap,n);int result","int result"),
   SOURCE.replace("int leaf(List borrowed){int a=va_arg(borrowed,int);int b=va_arg(borrowed,int);return a+b;}","int leaf(List borrowed);"),
   SOURCE.replace("int a=va_arg(borrowed,int);","va_end(borrowed);int a=va_arg(borrowed,int);"),
   SOURCE.replace("int a=va_arg(borrowed,int);","List copy=borrowed;int a=va_arg(borrowed,int);"),
   SOURCE.replace("List borrowed","List& borrowed"), SOURCE.replace("List borrowed","const List borrowed"),
   SOURCE.replace("List ap;","static List ap;"), SOURCE.replace("int a=va_arg(borrowed,int);","int a=va_arg(borrowed,float);"))) assertTrue(read(s,profile()).hasErrors(),s);
  healthy(read(SOURCE+" List global; unsigned long width=sizeof(leaf(global));",profile()));
 }
 @Test void nestedCursorUseGetsSpecificSubsetDiagnosticWithoutNativeExecution() {
  String source=SOURCE.replace("leaf(List borrowed)","leaf(List borrowed,int extra)").replace("leaf(ap)","leaf(ap,va_arg(ap,int))");
  var graph=read(source,profile());assertTrue(graph.parseProblems().isEmpty());
  assertTrue(graph.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-varargs-borrowed-call")),()->graph.semanticDiagnostics().toString());
 }
 @Test void directStatementAndTypedPointerRetrievalAreAdmitted() {
  healthy(read(SOURCE.replace("int result=leaf(ap);va_end(ap);return result;","leaf(ap);va_end(ap);return 0;"),profile()));
  String pointer="#include <cstdarg>\ntypedef std::va_list List; int* leaf(List p){return va_arg(p,int*);} int f(int n,...){List ap;va_start(ap,n);int* result=leaf(ap);va_end(ap);return *result;}";
  var graph=read(pointer,profile());healthy(graph);healthy(NameResolver.resolve(graph.edit().build()));
  var arg=nodes(graph,VaListExpression.class).stream().filter(v->v.kind()==VaListExpression.Kind.ARG).findFirst().orElseThrow();
  assertInstanceOf(PointerType.class,TypeSemantics.unqualified(arg.requestedType().orElseThrow()));
 }
 @Test void crossTranslationUnitAndProfileBoundariesRemainUnsupported() throws Exception {
  String helper="#include <cstdarg>\nint leaf(std::va_list ap){return va_arg(ap,int);}";
  String caller="#include <cstdarg>\nint leaf(std::va_list);int f(int n,...){std::va_list ap;va_start(ap,n);int r=leaf(ap);va_end(ap);return r;}";
  var old=PlatformProfile.builder().floatingFormat("long double","binary80").varargs("gcc-clang-builtin","x86_64-sysv-lp64-v1").build();
  for(var hp:List.of(profile(),old)) {
   var hu=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("helper.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(hp);
   var cu=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("caller.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(profile());
   assertTrue(NameResolver.resolve(Project.create().addFile(Parser.parse(hu,helper)).addFile(Parser.parse(cu,caller)).build()).hasErrors());
  }
 }
 @Test void realParameterBindingEditsAndReversal(){
  var p=read(SOURCE,profile());healthy(p);String original=ProjectJson.encode(p).toString();
  var e=new ProjectEditor(p);for(var param:nodes(p,ParameterDeclaration.class))if(param.name().equals("borrowed"))e.replace(param,param.edit().setName("cursor").build());
  for(var name:nodes(p,NameExpression.class))if(name.name().equals("borrowed"))e.replace(name,name.edit().setName("cursor").build());
  var changed=NameResolver.resolve(e.build());healthy(changed);String emitted=Cpg.sources(changed,Language.CPP).get("main.cpp");assertTrue(emitted.contains("cursor"));healthy(read(emitted,profile()));
  var reverse=new ProjectEditor(changed);for(var param:nodes(changed,ParameterDeclaration.class))if(param.name().equals("cursor"))reverse.replace(param,param.edit().setName("borrowed").build());
  for(var name:nodes(changed,NameExpression.class))if(name.name().equals("cursor"))reverse.replace(name,name.edit().setName("borrowed").build());
  healthy(NameResolver.resolve(reverse.build()));assertEquals(original,ProjectJson.encode(p).toString());
 }
}
