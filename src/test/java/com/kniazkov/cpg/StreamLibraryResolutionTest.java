/* Stream declaration contracts and generic forward-template defaults. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StreamLibraryResolutionTest {
 private static Project resolve(String code) {
  var project=NameResolver.resolve(Project.create().addFile(Parser.parse("streams.cpp",code,Language.CPP)).build());
  assertTrue(project.semanticDiagnostics().isEmpty(),()->project.semanticDiagnostics().toString());
  return project;
 }
 private static void callsResolved(Project project) {
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){
   assertTrue(call.target().isPresent(),()->"Unresolved call at "+call.range());
  }});
 }
 @Test void defaultsFromForwardDeclarationsSubstituteTheirOwnParameters() {
  callsResolved(resolve("template<class T,class U=T> struct Box; template<class A,class B> struct Box { B get(); }; int f(){Box<int> b;return b.get();}"));
 }
 @Test void narrowAndWideStringStreamAliasesExposeInheritedStateAndPosition() {
  for(String prefix:new String[]{"","w"}) callsResolved(resolve("#include <sstream>\nvoid f(){std::"+prefix+"stringstream s;s.good();s.eof();s.fail();s.bad();s.clear();s.rdstate();s.exceptions();s.fill();s.width();s.precision();s.flags();s.str();s.seekg(0);s.tellg();s.seekp(0);s.tellp();s.rdbuf();}"));
 }
 @Test void inputAndOutputIteratorFamiliesResolveTheirOperators() {
  var project=resolve("#include <sstream>\n#include <iterator>\nvoid f(){std::stringstream s;std::istream_iterator<int> i(s), end;*i;++i;i++;i==end;i!=end;std::distance(i,end);std::ostream_iterator<int> o(s,\",\");*o=2;++o;o++;std::istreambuf_iterator<char> b(s),be;*b;++b;b++;b==be;b!=be;std::ostreambuf_iterator<char> out(s);*out='a';++out;out++;out.failed();}");
  callsResolved(project);
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node node){
   if(node instanceof Expression expression && !(node instanceof CallExpression) && !expression.operatorCandidates().isEmpty())
    assertEquals(CallExpression.Resolution.RESOLVED,expression.operatorResolution(),()->node.getClass()+" "+node.range());
  }});
 }
 @Test void parameterizedManipulatorsResolveWithoutRepresentationAssumptions() {
  var project=resolve("#include <sstream>\n#include <iomanip>\nvoid f(){std::stringstream s;s<<3<<std::setw(4)<<std::setprecision(2)<<std::setbase(10)<<std::setfill('0');s>>std::setw(4)>>std::setbase(10);}");
  callsResolved(project);
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node node){
   if(node instanceof ShiftLeftExpression || node instanceof ShiftRightExpression)
    assertEquals(CallExpression.Resolution.RESOLVED,((Expression)node).operatorResolution(),()->node.range().toString());
  }});
 }
 @Test void setfillRetainsItsCharacterTypeAndDoesNotInventExtraction() {
  for (String operation : new String[]{"s<<std::setfill(L'x')", "s>>std::setfill('x')"}) {
   var project=resolve("#include <sstream>\n#include <iomanip>\nvoid f(){std::stringstream s;"+operation+";}");
   project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node node){
    if(node instanceof ShiftLeftExpression || node instanceof ShiftRightExpression)
     assertTrue(((Expression)node).operatorTarget().isEmpty(),"No specified operation for mismatched setfill or extraction");
   }});
  }
 }

 @Test void classTemplateDeductionRejectsRepeatedBasesButAllowsVirtualDiamond() {
  for (boolean virtual : new boolean[]{false,true}) {
   String inheritance=virtual?"virtual ":"";
   var project=NameResolver.resolve(Project.create().addFile(Parser.parse("bases.cpp",
    "template<class T> struct B{};struct L:"+inheritance+"B<int>{};struct R:"+inheritance+"B<int>{};struct D:L,R{};template<class T>int f(B<T>&);int g(){D d;return f(d);}",Language.CPP)).build());
   project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){assertEquals(virtual,call.target().isPresent());}});
  }
 }
 @Test void templateDeductionKeepsDistinctNamespaceTypesSeparate() {
  var project=NameResolver.resolve(Project.create().addFile(Parser.parse("types.cpp",
   "namespace a{template<class T>struct B{};template<class T>int f(B<T>&);}namespace b{template<class T>struct B{};}int g(){b::B<int> x;return a::f(x);}",Language.CPP)).build());
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){assertTrue(call.target().isEmpty());}});
 }
 @Test void characterInsertionUsesSpecializedOperatorTemplates() {
  var project=resolve("#include <sstream>\nvoid f(){std::ostringstream s;s<<'x';s<<\"x\";}");
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(ShiftLeftExpression expression){assertTrue(expression.operatorTarget().isPresent());}});
 }

 @Test void nonTemplateOperatorWinsEquivalentMemberObjectConversion() {
  for (String parameter : new String[]{"A&", "A"}) {
   var project=resolve("struct A{template<class T>int operator+(T){return 1;}};int operator+("+parameter+",int){return 2;}int f(){A a;return a+1;}");
   project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(AddExpression expression){
    assertTrue(expression.operatorTarget().orElseThrow() instanceof FunctionDeclaration);
   }});
  }
 }

 @Test void linkingConvergesAcrossNonTemplateLayersBetweenTemplateCalls() {
  var project=resolve("struct A{};A operator+(A,A);A operator-(A,A);template<class T>T id(T x){return x;}A f(){A a;return id(id(a+a)-a-a-a);}");
  callsResolved(project);
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node node){
   if(node instanceof AddExpression || node instanceof SubtractExpression)assertTrue(((Expression)node).operatorTarget().isPresent());
  }});
 }

 @Test void iteratorDifferenceAliasDoesNotMergeUnrelatedBaseDeclarations() {
  var project=NameResolver.resolve(Project.create().addFile(Parser.parse("ambiguous-iterator.cpp",
   "#include <iterator>\nstruct L{typedef int difference_type;};struct R{typedef int difference_type;};struct I:L,R{typedef int value_type;typedef int* pointer;typedef int& reference;typedef std::input_iterator_tag iterator_category;};void f(I a,I b){std::distance(a,b);}",Language.CPP)).build());
  project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){assertTrue(call.target().isEmpty());}});
 }

}
