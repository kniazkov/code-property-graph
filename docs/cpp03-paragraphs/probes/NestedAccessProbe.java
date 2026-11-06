/* Audit probe for the printed ISO/IEC 14882:2003 11.8/1 rule. MIT License. */
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
public final class NestedAccessProbe {
    public static void main(String[] args) {
        probe("nested-method", "class Outer { void secret(); public: class Inner { public: void use(Outer& o) { o.secret(); } }; };");
        probe("nested-field", "class Outer { int secret; public: class Inner { public: int use(Outer& o) { return o.secret; } }; };");
        probe("enclosing-method", "class Outer { class Inner { void secret(); }; void use(Inner& i) { i.secret(); } };");
        probe("enclosing-field", "class Outer { class Inner { int secret; }; int use(Inner& i) { return i.secret; } };");
        probe("nested-type", "class Outer { typedef int Secret; public: class Inner { Secret value; }; };");
    }
    private static void probe(String label, String source) {
        try {
            Project p = NameResolver.resolve(Project.create().addFile(Parser.parse(label + ".cpp", source, Language.CPP)).build());
            List<String> codes = new ArrayList<>();
            p.walk(new NodeVisitor() { @Override public void visit(Node n) { for (Diagnostic d : n.diagnostics()) codes.add(d.code()); } });
            System.out.println(label + ": " + codes);
        } catch (RuntimeException e) { System.out.println(label + ": PARSE/RESOLVE FAILURE " + e.getMessage()); }
    }
}
