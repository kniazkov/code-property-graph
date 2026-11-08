/* Parser syntax occurrences preserve assembly references without sharing children. MIT License. */
package com.kniazkov.cpg.model;

import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParserSyntaxCopyTest {
    private static Project parse(String text) {
        return Project.create().addFile(Parser.parse("copy.cpp", text, Language.CPP)).build();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        }); return result;
    }
    private static void ownedAliases(Project project) {
        Set<TypedefDeclaration> aliases = Collections.newSetFromMap(new IdentityHashMap<>());
        aliases.addAll(nodes(project, TypedefDeclaration.class));
        for (NamedType name : nodes(project, NamedType.class)) name.alias().ifPresent(alias -> assertTrue(aliases.contains(alias), name.name()));
    }
    @Test void nestedArgumentsAndSizeofTypesKeepExactAliasTargets() {
        for (String declaration : List.of("typedef int Shape;", "typedef char Shape[2];")) {
            var p = parse(declaration + "template<class T> struct Box {char x;}; template<int N> struct Count {};"
                    + "Box<Shape> a,b; Box<Shape*> c,d; Box<Box<Shape> > e; Count<sizeof(Shape)> count;");
            assertFalse(p.hasErrors(), p.parseProblems().toString()); ownedAliases(p);
            var alias = nodes(p, TypedefDeclaration.class).getFirst();
            var uses = nodes(p, NamedType.class).stream().filter(n -> n.name().equals("Shape")).toList();
            assertTrue(uses.size() >= 6);
            uses.forEach(use -> assertSame(alias, use.alias().orElseThrow()));
            var variables = nodes(p, VariableDeclaration.class);
            var a = variables.stream().filter(v -> v.name().equals("a")).findFirst().orElseThrow();
            var b = variables.stream().filter(v -> v.name().equals("b")).findFirst().orElseThrow();
            assertNotSame(a.type().orElseThrow(), b.type().orElseThrow());
            assertNotSame(a.type().orElseThrow().editIdentity, b.type().orElseThrow().editIdentity);
            ownedAliases(p.edit().build());
        }
    }
    @Test void qualifiedAndShadowedAliasesAreNotReconnectedByName() {
        var p = parse("namespace api {typedef int Shape;} typedef char Shape[2];"
                + "template<class T> struct Box {char x;}; Box<api::Shape> outer1,outer2; Box<Shape*> global;"
                + "void f(){typedef long Shape; Box<Shape*> inner1,inner2;}");
        assertFalse(p.hasErrors(), p.parseProblems().toString()); ownedAliases(p);
        var vars = nodes(p, VariableDeclaration.class);
        for (String name : List.of("outer1", "outer2", "global", "inner1", "inner2")) {
            var variable = vars.stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
            var use = nodes(variable.type().orElseThrow(), NamedType.class).stream().filter(t -> t.alias().isPresent()).findFirst().orElseThrow();
            String expected = name.startsWith("outer") ? "int" : name.startsWith("inner") ? "long" : "char";
            var alias = use.alias().orElseThrow();
            var scalar = nodes(alias.type(), NamedType.class).getFirst();
            assertEquals(expected, scalar.name());
        }
        ownedAliases(p.edit().build());
    }
    @Test void copiedSyntaxClonesOwnedDeclarationsButSharesExternalTargets() {
        var external = VariableDeclaration.create().setName("same").setType(NamedType.create().setName("int"));
        var local = VariableDeclaration.create().setName("same").setType(NamedType.create().setName("int"));
        var block = BlockStatement.create().addItem(local)
                .addItem(ExpressionStatement.create().setExpression(NameExpression.create().setName("same").setTarget(local)))
                .addItem(ExpressionStatement.create().setExpression(NameExpression.create().setName("same").setTarget(external)));
        var first = block.copySyntax(); var second = block.copySyntax();
        var source = SourceFile.create().setPath("copy.cpp").setLanguage(Language.CPP).addItem(external)
                .addItem(FunctionDeclaration.create().setName("first").setReturnType(NamedType.create().setName("void")).setBody(first))
                .addItem(FunctionDeclaration.create().setName("second").setReturnType(NamedType.create().setName("void")).setBody(second));
        var p = Project.create().addFile(source).build();
        var blocks = nodes(p, BlockStatement.class);
        var global = nodes(p, VariableDeclaration.class).getFirst();
        for (BlockStatement value : blocks) {
            var owned = nodes(value, VariableDeclaration.class).getFirst();
            var refs = nodes(value, NameExpression.class);
            assertSame(owned, refs.get(0).target().orElseThrow());
            assertSame(global, refs.get(1).target().orElseThrow());
        }
        assertNotSame(nodes(blocks.get(0), VariableDeclaration.class).getFirst(), nodes(blocks.get(1), VariableDeclaration.class).getFirst());
        var copy = p.edit().build(); assertNotSame(p, copy);
        assertEquals(3, nodes(copy, VariableDeclaration.class).size());
    }
    @Test void editingAliasDefinitionRetainsReferencesInEveryDeclaratorCopy() {
        var p = parse("typedef char Shape[2]; template<class T> struct Box {char x;}; Box<Shape*> a,b;");
        var alias = nodes(p, TypedefDeclaration.class).getFirst();
        var bound = ((ArrayType) alias.type()).bound().orElseThrow();
        var edit = new ProjectEditor(p); edit.replace(bound, IntegerLiteral.create().setValue(3).build());
        var changed = edit.build(); ownedAliases(changed);
        var replacement = nodes(changed, TypedefDeclaration.class).getFirst();
        assertEquals(3, ((IntegerLiteral)((ArrayType)replacement.type()).bound().orElseThrow()).value());
        for (NamedType use : nodes(changed, NamedType.class)) use.alias().ifPresent(target -> assertSame(replacement,target));
        assertEquals(2, ((IntegerLiteral)bound).value());
    }
}
