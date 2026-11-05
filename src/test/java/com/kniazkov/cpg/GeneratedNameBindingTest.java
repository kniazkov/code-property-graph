/* Code Property Graph: C++ source emission retains selected declarations. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedNameBindingTest {
    private static Project parse(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("main.cpp", source, Language.CPP)).build());
    }
    private static Project addImport(Project project) {
        SourceFile file = project.files().getFirst();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        for (Node item : file.items()) {
            items.add(item.edit());
            if (item instanceof UsingDirective) items.add(UsingDirective.create().setNamespaceName("archive"));
        }
        var editor = new ProjectEditor(project);
        editor.replace(file, file.edit().setItems(items).build());
        return editor.build();
    }
    private static final String SOURCE = "namespace shop { typedef int OrderId; }\n"
            + "namespace archive { typedef long OrderId; }\nusing namespace shop;\nOrderId order = 7;\n";

    @Test void qualifiesSelectedAliasWhenANewImportMakesItsSpellingAmbiguous() {
        Project original = parse(SOURCE);
        String unchanged = Source.files(original, Language.CPP).get("main.cpp");
        assertTrue(unchanged.contains("OrderId order"));
        assertFalse(unchanged.contains("::shop::OrderId"));
        Project edited = addImport(original);
        String generated = Source.files(edited, Language.CPP).get("main.cpp");
        assertTrue(generated.contains("::shop::OrderId order"), generated);
        Project reparsed = parse(generated);
        assertFalse(reparsed.hasErrors());
        VariableDeclaration order = (VariableDeclaration) reparsed.files().getFirst().items().getLast();
        NamedType type = (NamedType) order.type().orElseThrow();
        NamespaceDeclaration shop = (NamespaceDeclaration) reparsed.files().getFirst().items().getFirst();
        assertSame(shop.members().getFirst(), type.alias().orElseThrow());
        assertEquals(unchanged, Source.files(original, Language.CPP).get("main.cpp"));
    }

    @Test void refusesUnknownAmbiguousAliasAndReportsLocationAndCandidates() {
        Project ambiguous = parse(SOURCE.replace("using namespace shop;", "using namespace shop; using namespace archive;"));
        String message = assertThrows(IllegalArgumentException.class, () -> Source.files(ambiguous, Language.CPP)).getMessage();
        assertTrue(message.contains("main.cpp:"), message);
        assertTrue(message.contains("::shop::OrderId"), message);
        assertTrue(message.contains("::archive::OrderId"), message);
    }

    @Test void refusesLocalShadowingWhenSelectedTargetCannotBeQualified() {
        Project original = parse("int f(){int value=1; {return value;}}");
        FunctionDeclaration function = (FunctionDeclaration) original.files().getFirst().items().getFirst();
        BlockStatement inner = (BlockStatement) function.body().orElseThrow().items().getLast();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        items.add(VariableDeclaration.create().setName("value").setType(NamedType.create().setName("int"))
                .setInitializer(IntegerLiteral.create().setValue(2)));
        inner.items().forEach(item -> items.add(item.edit()));
        var editor = new ProjectEditor(original);
        editor.replace(inner, inner.edit().setItems(items).build());
        Project shadowed = editor.build();
        String message = assertThrows(IllegalArgumentException.class, () -> Source.files(shadowed, Language.CPP)).getMessage();
        assertTrue(message.contains("selected declaration cannot be named safely"), message);
        assertTrue(message.contains("main.cpp:"), message);
        assertTrue(message.contains("candidates:"), message);
    }

    @Test void rejectsNewBetterOverloadRatherThanSilentlyChangingTheSelectedFunction() {
        Project original = parse("int choose(long x){return 1;} int main(){return choose(1);}");
        SourceFile file = original.files().getFirst();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        items.add(file.items().getFirst().edit());
        items.add(Parser.parse("extra.cpp", "int choose(int x){return 2;}", Language.CPP).build().items().getFirst().edit());
        items.add(file.items().getLast().edit());
        var editor = new ProjectEditor(original);
        editor.replace(file, file.edit().setItems(items).build());
        Project changed = editor.build();
        assertThrows(IllegalArgumentException.class, () -> Source.files(changed, Language.CPP));
    }

    @Test void rejectsNewBetterOperatorOverload() {
        Project original = parse("struct X {}; int operator+(X x,long n){return 1;} int main(){X x;return x+1;}");
        SourceFile file = original.files().getFirst();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        items.add(file.items().getFirst().edit());
        items.add(file.items().get(1).edit());
        items.add(Parser.parse("extra.cpp", "struct X; int operator+(X x,int n){return 2;}", Language.CPP).build().items().getLast().edit());
        items.add(file.items().getLast().edit());
        var editor = new ProjectEditor(original);
        editor.replace(file, file.edit().setItems(items).build());
        Project changed = editor.build();
        assertThrows(IllegalArgumentException.class, () -> Source.files(changed, Language.CPP));
    }

    @Test void rejectsChangedImplicitConversionWhenItsOriginalTargetIsExplicitlySelected() {
        Project original = parse("struct X { operator int(){return 1;} }; double f(){X x;return x;}");
        ClassDeclaration cls = (ClassDeclaration) original.files().getFirst().items().getFirst();
        ClassDeclaration added = (ClassDeclaration) Parser.parse("extra.cpp",
                "struct X { operator double(){return 2;} };", Language.CPP).build().items().getFirst();
        var members = new ArrayList<NodeBuilder<? extends Declaration>>();
        cls.members().forEach(member -> members.add((NodeBuilder<? extends Declaration>) member.edit()));
        members.add((NodeBuilder<? extends Declaration>) added.members().getFirst().edit());
        var editor = new ProjectEditor(original);
        editor.replace(cls, cls.edit().setMembers(members).build());
        Project changed = editor.build();
        var conversions = new ArrayList<ConversionExpression>();
        changed.walk(new NodeVisitor() { @Override public void visit(ConversionExpression conversion) { conversions.add(conversion); } });
        var select = new ProjectEditor(changed);
        select.bindOperator(conversions.getFirst(), (CallableDeclaration)
                ((ClassDeclaration) changed.files().getFirst().items().getFirst()).members().getFirst());
        Project selected = select.build();
        String message = assertThrows(IllegalArgumentException.class, () -> Source.files(selected, Language.CPP)).getMessage();
        assertTrue(message.contains("selected implicit conversion"), message);
        assertDoesNotThrow(() -> Source.files(original, Language.CPP));
    }

    @Test void validVirtualDispatchDoesNotCountAsNameAmbiguity() {
        Project project = parse("struct Base { virtual int value(){return 1;} }; struct Derived:Base { int value(){return 2;} };"
                + "int f(Base &base){return base.value();}");
        assertDoesNotThrow(() -> Source.files(project, Language.CPP));
    }
}
