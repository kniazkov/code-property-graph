/* C++03 #91 edited graph demand regression. MIT License. */
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
public class RelOpsEditProbe {
 public static void main(String[] args) {
  var p=NameResolver.resolve(Project.create().addFile(Parser.parse("main.cpp","#include <utility>\nstruct X {bool operator==(const X&)const;}; bool f(X a){return std::rel_ops::operator!=(a,a);}",Language.CPP,"c++03")).build());
  if(p.hasErrors()||p.templateInstances().size()!=1)throw new AssertionError("initial");
  var edit=new ProjectEditor(p);
  edit.remove(p.files().getFirst().items().stream().filter(n->n instanceof FunctionDeclaration).findFirst().orElseThrow());
  var q=NameResolver.resolve(edit.build());
  if(q.hasErrors()||!q.templateInstances().isEmpty()||!q.librarySources().getFirst().items().isEmpty())throw new AssertionError("stale demand");
  if(p.templateInstances().size()!=1)throw new AssertionError("mutated original");
  System.out.println("Edited rel_ops demand removal and original immutability passed");
 }
}
