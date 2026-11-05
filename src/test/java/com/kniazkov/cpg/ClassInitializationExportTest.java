/* C++03 class syntax round trip. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassInitializationExportTest {
    static final String TEXT = "struct Base { int b; Base(int n) : b(n) {} }; class C : public Base { int first; public: C(int n) : Base(n), first(n) {} C(const C& other) : Base(other.b), first(other.first) {} ~C() {} C& operator=(const C& other) { first = other.first; return *this; } int value() const; protected: mutable int last; private: static int count; }; int C::count = 0; int C::value() const { return first; } int main() { C c(3); C d = c; d = c; return d.value() - 3; }";
    static Project parse(String text) { return NameResolver.resolve(Project.create().addFile(Parser.parse("class.cpp", text, Language.CPP)).build()); }
    @Test void exportsAndCopyPreserveClassesAndInitializers() {
        var project = parse(TEXT);
        String source = Source.render(project.files().getFirst(), Language.CPP);
        assertTrue(source.contains("C(int n) : Base(n), first(n)"), source);
        assertTrue(source.contains("C c(3)"), source);
        assertTrue(source.contains("protected:"), source);
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertDoesNotThrow(() -> DotVisualizer.toDot(project, false));
        var reparsed = parse(source);
        assertEquals(project.files().getFirst().items().size(), reparsed.files().getFirst().items().size());
        assertTrue(project.diagnostics().isEmpty(), project.diagnostics().toString());
    }
}
