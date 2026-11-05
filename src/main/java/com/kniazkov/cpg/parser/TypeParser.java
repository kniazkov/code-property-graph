/* C++03 declaration-specifiers and recursive declarators. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.*;

/** Reads the C++ declarator grammar, composing from the name outward, not by token order. */
final class TypeParser {
    private static final Set<String> FUNDAMENTAL = Set.of("void", "bool", "char", "signed", "unsigned", "short", "long", "int", "wchar_t", "float", "double");
    private static final Set<String> STORAGE = Set.of("static", "extern", "inline", "register", "mutable");
    private final TokenCursor tokens;
    private final ExpressionParser expressions;
    private final Deque<Map<String, TypedefDeclaration.Builder>> aliases = new ArrayDeque<>();
    private final Deque<Set<String>> hidden = new ArrayDeque<>();
    private final Deque<Set<String>> classNames = new ArrayDeque<>();
    TypeParser(TokenCursor tokens, ExpressionParser expressions) { this.tokens = tokens; this.expressions = expressions; pushScope(); }
    void pushScope() { aliases.push(new LinkedHashMap<>()); hidden.push(new HashSet<>()); classNames.push(new HashSet<>()); }
    void popScope() { aliases.pop(); hidden.pop(); classNames.pop(); }
    void registerClass(String name) { classNames.peek().add(name); }
    void declareName(String name) { if (!name.isEmpty()) hidden.peek().add(name); }
    void declareAlias(String name, TypedefDeclaration.Builder declaration) { hidden.peek().remove(name); aliases.peek().put(name, declaration); }
    private TypedefDeclaration.Builder alias(String name) {
        var names = hidden.iterator();
        for (var scope : aliases) { if (names.next().contains(name)) return null; if (scope.containsKey(name)) return scope.get(name); }
        return null;
    }
    private boolean className(String name) {
        var names = hidden.iterator();
        for (var scope : classNames) {
            if (names.next().contains(name)) return false;
            if (scope.contains(name)) return true;
        }
        return false;
    }
    boolean startsType() {
        String word = tokens.peek();
        return FUNDAMENTAL.contains(word) || word.equals("const") || word.equals("volatile") || word.equals("typedef")
                || STORAGE.contains(word) || word.equals("struct") || word.equals("class") || word.equals("enum")
                || alias(word) != null || className(word);
    }
    boolean startsDeclaration() {
        if (!startsType()) return false;
        if (!tokens.isIdentifier()) return true;
        int next = 1;
        while (tokens.peek(next).equals("::") && CppLexer.isIdentifier(tokens.peek(next + 1))) next += 2;
        if (!tokens.peek(next).equals("(")) return true;
        String inside = tokens.peek(next + 1);
        return inside.equals("*") || inside.equals("&") || inside.equals("(") || memberPointerAhead(next + 1);
    }
    record Specifiers(Supplier<NodeBuilder<? extends TypeNode>> type, boolean typedef, List<String> modifiers) {}
    Specifiers specifiers() {
        var start = tokens.start();
        boolean typedef = false, constant = false, vol = false;
        List<String> modifiers = new ArrayList<>(), words = new ArrayList<>();
        String name = null;
        TypedefDeclaration.Builder target = null;
        while (true) {
            if (tokens.take("typedef")) { if (typedef) throw tokens.error("Duplicate typedef specifier"); typedef = true; }
            else if (tokens.take("const")) { if (constant) throw tokens.error("Duplicate const qualifier"); constant = true; }
            else if (tokens.take("volatile")) { if (vol) throw tokens.error("Duplicate volatile qualifier"); vol = true; }
            else if (STORAGE.contains(tokens.peek())) { modifiers.add(tokens.peek()); tokens.advance(); }
            else if (name == null && FUNDAMENTAL.contains(tokens.peek())) { words.add(tokens.peek()); tokens.advance(); }
            else if (name == null && words.isEmpty() && (tokens.peek().equals("struct") || tokens.peek().equals("class") || tokens.peek().equals("enum"))) {
                tokens.advance(); name = qualifiedName(); registerClass(name);
            } else if (name == null && words.isEmpty() && tokens.isIdentifier()) {
                name = qualifiedName(); target = alias(name);
            } else break;
        }
        if (name == null) {
            name = String.join(" ", words);
            if (FundamentalKind.fromSpelling(name).isEmpty()) throw tokens.error("Invalid C++03 type specifiers '" + name + "'");
        }
        var leaf = tokens.span(start, NamedType.create().setName(name));
        if (target != null) leaf.setAlias(target);
        final String spelling = name;
        final TypedefDeclaration.Builder binding = target;
        final boolean c = constant, v = vol;
        // Every declarator owns fresh syntax; alias targets deliberately remain shared references.
        final var range = leaf.build().range();
        final var provenance = leaf.build().provenance();
        Supplier<NodeBuilder<? extends TypeNode>> supplier = () -> {
            var named = NamedType.create().setName(spelling);
            if (binding != null) named.setAlias(binding);
            range.ifPresent(named::setRange); provenance.ifPresent(named::setProvenance);
            if (!c && !v) return named;
            var qualified = QualifiedType.create().setBaseType(named).setConst(c).setVolatile(v);
            range.ifPresent(qualified::setRange); provenance.ifPresent(qualified::setProvenance);
            return qualified;
        };
        return new Specifiers(supplier, typedef, List.copyOf(modifiers));
    }
    private String qualifiedName() {
        StringBuilder name = new StringBuilder();
        if (tokens.take("::")) name.append("::");
        name.append(tokens.identifier());
        while (tokens.peek().equals("::") && !tokens.peek(1).equals("*")) { tokens.advance(); name.append("::").append(tokens.identifier()); }
        return name.toString();
    }
    record Declarator(String name, Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>> apply) {}
    Declarator declarator(boolean abstractAllowed) {
        var start = tokens.start();
        var prefixes = new ArrayList<Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>>>();
        while (true) {
            if (tokens.take("*")) {
                boolean c = tokens.take("const"), v = tokens.take("volatile");
                if (!c) c = tokens.take("const");
                final boolean constant = c, vol = v;
                prefixes.add(base -> qualify(tokens.span(start, PointerType.create().setPointee(base)), constant, vol, start));
            } else if (tokens.take("&")) prefixes.add(base -> tokens.span(start, ReferenceType.create().setReferredType(base)));
            else if (memberPointerAhead()) {
                String owner = qualifiedName(); tokens.expect("::"); tokens.expect("*");
                var ownerAlias = alias(owner);
                boolean c = tokens.take("const"), v = tokens.take("volatile");
                if (!c) c = tokens.take("const");
                final boolean constant = c, vol = v;
                prefixes.add(base -> {
                    var ownerType = NamedType.create().setName(owner); if (ownerAlias != null) ownerType.setAlias(ownerAlias);
                    return qualify(tokens.span(start, MemberPointerType.create().setOwnerType(ownerType).setPointee(base)), constant, vol, start);
                });
            } else break;
        }
        String name = "";
        Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>> direct = Function.identity();
        if (tokens.peek().equals("(") && groupingAhead()) {
            tokens.advance(); var nested = declarator(abstractAllowed); tokens.expect(")"); name = nested.name(); direct = nested.apply();
        } else if (tokens.isIdentifier()) name = tokens.identifier();
        else if (!abstractAllowed) throw tokens.error("Expected declarator name");
        while (true) {
            if (tokens.take("[")) {
                var bound = tokens.peek().equals("]") ? null : expressions.parse(); tokens.expect("]");
                var previous = direct;
                direct = base -> { var array = ArrayType.create().setElementType(base); if (bound != null) array.setBound(bound); return previous.apply(tokens.span(start, array)); };
            } else if (tokens.take("(")) {
                List<NodeBuilder<? extends ParameterDeclaration>> parameters = new ArrayList<>();
                boolean variadic = false;
                pushScope();
                try {
                    if (tokens.peek().equals("void") && tokens.peek(1).equals(")")) tokens.advance();
                    else if (!tokens.peek().equals(")")) {
                        do {
                            if (tokens.take("...")) { variadic = true; break; }
                            var parameterStart = tokens.start();
                            var specifiers = specifiers();
                            if (specifiers.typedef()) throw tokens.error("typedef is not a parameter specifier");
                            var parameter = declarator(true);
                            parameters.add(tokens.span(parameterStart, ParameterDeclaration.create().setName(parameter.name()).setType(parameter.apply().apply(specifiers.type().get()))));
                            declareName(parameter.name());
                        } while (tokens.take(","));
                    }
                    tokens.expect(")");
                } finally { popScope(); }
                boolean c = tokens.take("const"), v = tokens.take("volatile"); if (!c) c = tokens.take("const");
                final boolean varargs = variadic, constant = c, vol = v;
                var previous = direct;
                direct = base -> previous.apply(tokens.span(start, FunctionType.create().setReturnType(base).setParameters(parameters).setVariadic(varargs).setConst(constant).setVolatile(vol)));
            } else break;
        }
        var result = direct;
        return new Declarator(name, base -> {
            for (var prefix : prefixes) base = prefix.apply(base);
            return result.apply(base);
        });
    }
    private boolean groupingAhead() {
        String next = tokens.peek(1);
        return next.equals("*") || next.equals("&") || next.equals("(") || memberPointerAhead(1)
                || !next.equals(")") && !next.equals("...") && !FUNDAMENTAL.contains(next) && !next.equals("const") && !next.equals("volatile") && alias(next) == null && tokens.peek(2).equals(")");
    }
    private boolean memberPointerAhead() { return memberPointerAhead(0); }
    private boolean memberPointerAhead(int offset) {
        if (tokens.peek(offset).equals("::")) offset++;
        if (!CppLexer.isIdentifier(tokens.peek(offset))) return false;
        while (tokens.peek(offset + 1).equals("::")) {
            if (tokens.peek(offset + 2).equals("*")) return true;
            offset += 2;
            if (!CppLexer.isIdentifier(tokens.peek(offset))) return false;
        }
        return false;
    }
    private NodeBuilder<? extends TypeNode> qualify(NodeBuilder<? extends TypeNode> base, boolean c, boolean v, TokenCursor.Mark start) {
        return c || v ? tokens.span(start, QualifiedType.create().setBaseType(base).setConst(c).setVolatile(v)) : base;
    }
    NodeBuilder<? extends TypeNode> abstractType() {
        var specs = specifiers();
        if (specs.typedef() || !specs.modifiers().isEmpty()) throw tokens.error("Storage specifier in type-id");
        var declaration = declarator(true);
        if (!declaration.name().isEmpty()) throw tokens.error("Name is not allowed in an abstract type-id");
        return declaration.apply().apply(specs.type().get());
    }
}
