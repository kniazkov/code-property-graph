/* C++03 declaration-specifiers and recursive declarators. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.*;

/** Reads the C++ declarator grammar, composing from the name outward, not by token order. */
final class TypeParser {
    private static final Set<String> FUNDAMENTAL = Set.of("void", "bool", "char", "signed", "unsigned", "short", "long", "int", "wchar_t", "float", "double");
    private static final Set<String> STORAGE = Set.of("static", "extern", "inline", "register", "mutable", "auto");
    private final TokenCursor tokens;
    private final ExpressionParser expressions;
    /** Parser-only lookup: enough information to distinguish declarations from expressions. */
    private static final class Scope {
        final Scope parent;
        boolean prototype, classScope;
        final Map<String, Binding> names = new LinkedHashMap<>();
        final Set<String> tags = new HashSet<>();
        final Map<String, Scope> nested = new LinkedHashMap<>();
        final List<Scope> using = new ArrayList<>();
        Scope(Scope parent) { this.parent = parent; }
    }
    private record Binding(boolean type, TypedefDeclaration.Builder alias, Scope qualifier) {}
    private record Implicit(ClassDeclaration.Builder declaration, Scope owner) {}
    private final List<Implicit> implicitDeclarations = new ArrayList<>();
    private final Scope root = new Scope(null);
    private Scope scope = root;
    private final Deque<Scope> enclosing = new ArrayDeque<>();
    TypeParser(TokenCursor tokens, ExpressionParser expressions) { this.tokens = tokens; this.expressions = expressions; }
    void pushScope() { enclosing.push(scope); scope = new Scope(scope); }
    private void pushPrototypeScope() { pushScope(); scope.prototype = true; }
    List<ClassDeclaration.Builder> takeImplicitDeclarations() {
        var result = implicitDeclarations.stream().filter(item -> item.owner() == scope).map(Implicit::declaration).toList();
        implicitDeclarations.removeIf(item -> item.owner() == scope); return result;
    }
    private boolean tagIn(Scope owner, String name, Set<Scope> seen) {
        if (!seen.add(owner)) return false;
        if (owner.tags.contains(name)) return true;
        return owner.using.stream().anyMatch(imported -> tagIn(imported, name, seen));
    }
    private boolean hasTag(String name) {
        int separator = name.lastIndexOf("::");
        if (separator >= 0) {
            Scope owner = separator == 0 ? root : namespace(name.substring(0, separator));
            return owner != null && tagIn(owner, name.substring(separator + 2), new HashSet<>());
        }
        for (Scope owner = scope; owner != null; owner = owner.parent)
            if (tagIn(owner, name, new HashSet<>())) return true;
        return false;
    }
    private void introduceTag(String name, String keyword, TokenCursor.Mark start) {
        if (hasTag(name)) return;
        if (keyword.equals("enum")) throw tokens.error("C++03 enumeration must be defined before an elaborated use");
        if (name.contains("::")) throw tokens.error("A qualified elaborated type must name a previously declared class");
        Scope owner = scope;
        while (owner.prototype || owner.classScope) owner = owner.parent;
        owner.tags.add(name);
        Scope declarationScope = owner;
        Scope qualifier = owner.nested.computeIfAbsent(name, ignored -> new Scope(declarationScope));
        qualifier.classScope = true;
        owner.names.putIfAbsent(name, new Binding(true, null, qualifier));
        var declaration = ClassDeclaration.create().setName(name).setKeyword(keyword)
                .setMemberAccess(keyword.equals("class") ? "private" : "public").setImplicit(true);
        declaration.setDeclarationDefinition(false);
        implicitDeclarations.add(new Implicit(tokens.span(start, declaration), owner));
    }
    void pushNamedScope(String name) { pushNamedScope(name, false); }
    void pushNamedScope(String name, boolean classScope) {
        Scope owner = scope;
        enclosing.push(owner);
        scope = owner.nested.computeIfAbsent(name, ignored -> new Scope(owner));
        scope.classScope = classScope;
    }
    void popScope() { scope = enclosing.pop(); }
    Runnable inCurrentScope(Runnable action) {
        Scope captured = scope;
        return () -> { Scope previous = scope; scope = captured; try { action.run(); } finally { scope = previous; } };
    }
    void inQualifier(String qualifiedName, Runnable action) {
        int at = qualifiedName.lastIndexOf("::");
        Scope saved = scope;
        Scope owner = at < 0 ? null : namespace(qualifiedName.substring(0, at));
        if (owner != null) scope = owner;
        try { action.run(); } finally { scope = saved; }
    }
    void registerClass(String name) {
        scope.tags.add(name);
        Scope qualifier = scope.nested.computeIfAbsent(name, ignored -> new Scope(scope));
        qualifier.classScope = true;
        scope.names.putIfAbsent(name, new Binding(true, null, qualifier));
    }
    void declareName(String name) { if (!name.isEmpty()) scope.names.put(name, new Binding(false, null, null)); }
    void declareAlias(String name, TypedefDeclaration.Builder declaration) {
        Scope qualifier = typeScope(declaration.type().orElseThrow(), Collections.newSetFromMap(new IdentityHashMap<>()));
        scope.names.put(name, new Binding(true, declaration, qualifier));
    }
    private Scope typeScope(NodeBuilder<? extends TypeNode> type, Set<NodeBuilder<?>> seen) {
        if (!seen.add(type)) return null;
        if (type instanceof QualifiedType.Builder qualified) return typeScope(qualified.baseType().orElseThrow(), seen);
        if (type instanceof NamedType.Builder name) {
            if (name.alias().isPresent()) return typeScope(((TypedefDeclaration.Builder)name.alias().orElseThrow()).type().orElseThrow(), seen);
            return namespace(name.name().orElseThrow());
        }
        return null;
    }
    void namespaceAlias(String name, String target) {
        Scope found = namespace(target);
        if (found == null) throw tokens.error("Unknown namespace '" + target + "'");
        scope.nested.put(name, found);
    }
    void usingNamespace(String name) {
        Scope found = namespace(name);
        if (found == null) throw tokens.error("Unknown namespace '" + name + "'");
        scope.using.add(found);
    }
    void usingDeclaration(String name) {
        Binding found = binding(name);
        if (found != null) scope.names.put(name.substring(name.lastIndexOf("::") + 2), found);
    }
    private Scope namespace(String name) {
        boolean global = name.startsWith("::");
        String[] parts = (global ? name.substring(2) : name).split("::");
        for (Scope start = global ? root : scope; start != null; start = global ? null : start.parent) {
            Scope result = start;
            for (String part : parts) {
                Scope next = result.nested.get(part);
                if (next == null) {
                    Binding alias = local(result, part, new HashSet<>());
                    next = alias == null ? null : alias.qualifier();
                }
                result = next; if (result == null) break;
            }
            if (result != null) return result;
        }
        return null;
    }
    private Binding local(Scope start, String name, Set<Scope> visited) {
        if (!visited.add(start)) return null;
        Binding direct = start.names.get(name);
        if (direct != null) return direct;
        for (Scope imported : start.using) {
            Binding result = local(imported, name, visited);
            if (result != null) return result;
        }
        return null;
    }
    private Binding binding(String name) {
        int separator = name.lastIndexOf("::");
        if (separator >= 0) {
            Scope owner = separator == 0 ? root : namespace(name.substring(0, separator));
            return owner == null ? null : local(owner, name.substring(separator + 2), new HashSet<>());
        }
        for (Scope current = scope; current != null; current = current.parent) {
            Binding found = local(current, name, new HashSet<>());
            if (found != null) return found;
        }
        return null;
    }
    private TypedefDeclaration.Builder alias(String name) { var found = binding(name); return found == null ? null : found.alias(); }
    private boolean className(String name) { var found = binding(name); return found != null && found.type(); }
    private String nameAhead(int offset) {
        var name = new StringBuilder();
        if (tokens.peek(offset).equals("::")) { name.append("::"); offset++; }
        if (!CppLexer.isIdentifier(tokens.peek(offset))) return "";
        name.append(tokens.peek(offset++));
        while (tokens.peek(offset).equals("::") && CppLexer.isIdentifier(tokens.peek(offset + 1))) {
            name.append("::").append(tokens.peek(offset + 1)); offset += 2;
        }
        return name.toString();
    }
    boolean functionalTypeAhead() {
        if (FUNDAMENTAL.contains(tokens.peek())) return tokens.peek(1).equals("(");
        String name = nameAhead(0);
        if (!className(name)) return false;
        int offset = tokens.peek().equals("::") ? 2 : 1;
        while (tokens.peek(offset).equals("::") && CppLexer.isIdentifier(tokens.peek(offset + 1))) offset += 2;
        return tokens.peek(offset).equals("(");
    }
    boolean startsType() {
        String word = tokens.peek();
        return FUNDAMENTAL.contains(word) || word.equals("const") || word.equals("volatile") || word.equals("typedef")
                || STORAGE.contains(word) || Set.of("struct", "class", "union", "enum").contains(word)
                || className(nameAhead(0));
    }
    boolean startsDeclaration() {
        if (Set.of("using", "class", "struct", "union", "enum").contains(tokens.peek())) return true;
        if (!startsType()) return false;
        int next = tokens.peek().equals("::") ? 2 : 1;
        while (tokens.peek(next).equals("::") && CppLexer.isIdentifier(tokens.peek(next + 1))) next += 2;
        if (!tokens.peek(next).equals("(")) return true;
        String inside = tokens.peek(next + 1);
        return inside.equals("*") || inside.equals("&") || inside.equals("(") || memberPointerAhead(next + 1)
                || CppLexer.isIdentifier(inside) && tokens.peek(next + 2).equals(")");
    }
    record Specifiers(Supplier<NodeBuilder<? extends TypeNode>> type, boolean typedef, List<String> modifiers) {}
    Specifiers specifiers() {
        var start = tokens.start();
        int implicitStart = implicitDeclarations.size();
        boolean typedef = false, constant = false, vol = false;
        List<String> modifiers = new ArrayList<>(), words = new ArrayList<>();
        String name = null, elaboratedKeyword = null;
        TypedefDeclaration.Builder target = null;
        while (true) {
            if (tokens.take("typedef")) { if (typedef) throw tokens.error("Duplicate typedef specifier"); typedef = true; }
            else if (tokens.take("const")) { if (constant) throw tokens.error("Duplicate const qualifier"); constant = true; }
            else if (tokens.take("volatile")) { if (vol) throw tokens.error("Duplicate volatile qualifier"); vol = true; }
            else if (STORAGE.contains(tokens.peek())) { modifiers.add(tokens.peek()); tokens.advance(); }
            else if (name == null && FUNDAMENTAL.contains(tokens.peek())) { words.add(tokens.peek()); tokens.advance(); }
            else if (name == null && words.isEmpty() && Set.of("struct", "class", "union", "enum").contains(tokens.peek())) {
                var tagStart = tokens.start();
                elaboratedKeyword = tokens.peek(); tokens.advance(); name = qualifiedName(); introduceTag(name, elaboratedKeyword, tagStart);
            } else if (name == null && words.isEmpty() && (tokens.isIdentifier() || tokens.peek().equals("::"))) {
                name = qualifiedName(); target = alias(name);
            } else break;
        }
        if (name == null) {
            name = String.join(" ", words);
            if (FundamentalKind.fromSpelling(name).isEmpty()) throw tokens.error("Invalid C++03 type specifiers '" + name + "'");
        }
        var leaf = tokens.span(start, NamedType.create().setName(name));
        for (int index = implicitStart; index < implicitDeclarations.size(); index++) tokens.span(start, implicitDeclarations.get(index).declaration());
        if (target != null) leaf.setAlias(target);
        if (elaboratedKeyword != null) leaf.setElaboratedKeyword(elaboratedKeyword);
        final String spelling = name, keyword = elaboratedKeyword;
        final TypedefDeclaration.Builder binding = target;
        final boolean c = constant, v = vol;
        // Every declarator owns fresh syntax; alias targets deliberately remain shared references.
        final var range = leaf.build().range();
        final var provenance = leaf.build().provenance();
        Supplier<NodeBuilder<? extends TypeNode>> supplier = () -> {
            var named = NamedType.create().setName(spelling);
            if (binding != null) named.setAlias(binding);
            if (keyword != null) named.setElaboratedKeyword(keyword);
            range.ifPresent(named::setRange); provenance.ifPresent(named::setProvenance);
            if (!c && !v) return named;
            var qualified = QualifiedType.create().setBaseType(named).setConst(c).setVolatile(v);
            range.ifPresent(qualified::setRange); provenance.ifPresent(qualified::setProvenance);
            return qualified;
        };
        return new Specifiers(supplier, typedef, List.copyOf(modifiers));
    }
    String qualifiedName() {
        StringBuilder name = new StringBuilder();
        if (tokens.take("::")) name.append("::");
        name.append(tokens.identifier());
        while (tokens.peek().equals("::") && !tokens.peek(1).equals("*")) { tokens.advance(); name.append("::").append(tokens.identifier()); }
        return name.toString();
    }
    record Declarator(String name, Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>> apply) {}
    Declarator declarator(boolean abstractAllowed) { return declarator(abstractAllowed, false); }
    private Declarator declarator(boolean abstractAllowed, boolean newType) {
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
        if (!newType && tokens.peek().equals("(") && groupingAhead()) {
            tokens.advance(); var nested = declarator(abstractAllowed); tokens.expect(")"); name = nested.name(); direct = nested.apply();
        } else if (tokens.take("~")) name = "~" + tokens.identifier();
        else if (tokens.take("operator")) name = operatorName();
        else if (tokens.isIdentifier()) {
            name = tokens.identifier();
            while (tokens.take("::")) {
                name += "::";
                if (tokens.take("~")) name += "~";
                if (tokens.take("operator")) name += operatorName();
                else name += tokens.identifier();
            }
        }
        else if (!abstractAllowed) throw tokens.error("Expected declarator name");
        Scope declarationScope = scope;
        int qualification = name.lastIndexOf("::");
        if (qualification >= 0) { Scope owner = namespace(name.substring(0, qualification)); if (owner != null) scope = owner; }
        try {
        while (true) {
            if (tokens.take("[")) {
                var bound = tokens.peek().equals("]") ? null : expressions.parse(); tokens.expect("]");
                var previous = direct;
                direct = base -> { var array = ArrayType.create().setElementType(base); if (bound != null) array.setBound(bound); return previous.apply(tokens.span(start, array)); };
            } else if (!newType && tokens.peek().equals("(") && functionClauseAhead()) {
                tokens.advance();
                List<NodeBuilder<? extends ParameterDeclaration>> parameters = new ArrayList<>();
                boolean variadic = false;
                pushPrototypeScope();
                try {
                    if (tokens.peek().equals("void") && tokens.peek(1).equals(")")) tokens.advance();
                    else if (!tokens.peek().equals(")")) {
                        do {
                            if (tokens.take("...")) { variadic = true; break; }
                            var parameterStart = tokens.start();
                            var specifiers = specifiers();
                            if (specifiers.typedef()) throw tokens.error("typedef is not a parameter specifier");
                            if (specifiers.modifiers().stream().anyMatch(m -> !m.equals("register")) || specifiers.modifiers().size() > 1)
                                throw tokens.error("Only register storage is permitted on C++03 parameters");
                            var parameter = declarator(true);
                            var declaration = ParameterDeclaration.create().setName(parameter.name()).setModifiers(specifiers.modifiers())
                                    .setType(parameter.apply().apply(specifiers.type().get()));
                            declareName(parameter.name());
                            if (tokens.take("=")) declaration.setDefaultValue(expressions.parse());
                            parameters.add(tokens.span(parameterStart, declaration));
                            if (tokens.take("...")) { variadic = true; break; }
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
        } finally { scope = declarationScope; }
    }
    /** Defaults are written only in a function declaration, not in a function type-id. */
    void validateDefaultPlacement(NodeBuilder<? extends TypeNode> builder, boolean functionDeclaration) {
        TypeNode root = builder.build();
        root.walk(new NodeVisitor() {
            @Override public void visit(FunctionType function) {
                if ((!functionDeclaration || function != root)
                        && function.parameters().stream().anyMatch(parameter -> parameter.defaultValue().isPresent()))
                    throw tokens.error("Default arguments are permitted only in function declarations");
            }
        });
    }

    /** Obtains fresh signature children, retaining shared typedef reference targets. */
    FunctionType.Builder functionType(NodeBuilder<? extends TypeNode> type) {
        if (type instanceof FunctionType.Builder direct) return direct;
        var seen = Collections.newSetFromMap(new IdentityHashMap<NodeBuilder<?>, Boolean>());
        NodeBuilder<? extends TypeNode> current = type;
        while (current instanceof NamedType.Builder name && name.alias().isPresent()) {
            if (!seen.add(current)) throw tokens.error("Cyclic typedef declaration");
            current = ((TypedefDeclaration.Builder) name.alias().orElseThrow()).type().orElseThrow();
        }
        return current instanceof FunctionType.Builder function ? (FunctionType.Builder) copyType(function) : null;
    }
    @SuppressWarnings("unchecked")
    private NodeBuilder<? extends Expression> copyExpression(NodeBuilder<? extends Expression> expression) {
        return (NodeBuilder<? extends Expression>) expression.build().edit();
    }
    private NodeBuilder<? extends TypeNode> copyType(NodeBuilder<? extends TypeNode> type) {
        if (type instanceof NamedType.Builder name) {
            var result = NamedType.create().setName(name.name().orElseThrow());
            name.alias().ifPresent(result::setAlias); name.declaration().ifPresent(result::setDeclaration);
            name.elaboratedKeyword().ifPresent(result::setElaboratedKeyword);
            return result;
        }
        if (type instanceof PointerType.Builder pointer) return PointerType.create().setPointee(copyType(pointer.pointee().orElseThrow()));
        if (type instanceof ReferenceType.Builder reference) return ReferenceType.create().setReferredType(copyType(reference.referredType().orElseThrow()));
        if (type instanceof QualifiedType.Builder qualified) return QualifiedType.create().setBaseType(copyType(qualified.baseType().orElseThrow())).setConst(qualified.isConst()).setVolatile(qualified.isVolatile());
        if (type instanceof MemberPointerType.Builder pointer) return MemberPointerType.create().setOwnerType(copyType(pointer.ownerType().orElseThrow())).setPointee(copyType(pointer.pointee().orElseThrow()));
        if (type instanceof ArrayType.Builder array) {
            var result = ArrayType.create().setElementType(copyType(array.elementType().orElseThrow()));
            array.bound().ifPresent(bound -> result.setBound(copyExpression(bound))); return result;
        }
        if (type instanceof FunctionType.Builder function) {
            var result = FunctionType.create().setReturnType(copyType(function.returnType().orElseThrow()))
                    .setVariadic(function.variadic()).setConst(function.isConst()).setVolatile(function.isVolatile());
            for (var parameter : function.parameters()) {
                var original = (ParameterDeclaration.Builder) parameter;
                result.addParameter(ParameterDeclaration.create().setName(original.name().orElse(""))
                        .setModifiers(original.modifiers()).setType(copyType(original.type().orElseThrow())));
            }
            return result;
        }
        throw new IllegalArgumentException("Unsupported type syntax " + type.getClass().getName());
    }

    private String operatorName() {
        if (tokens.take("=")) return "operator=";
        String operation = tokens.peek();
        if (!operation.equals("new") && !operation.equals("delete")) throw tokens.error("Unsupported operator function name");
        tokens.advance();
        if (tokens.take("[")) { tokens.expect("]"); operation += "[]"; }
        return "operator " + operation;
    }

    private boolean functionClauseAhead() {
        if (tokens.peek(1).equals(")") || tokens.peek(1).equals("...")) return true;
        int position = tokens.checkpoint(); tokens.advance();
        try { return startsType(); } finally { tokens.restore(position); }
    }
    private boolean groupingAhead() {
        String next = tokens.peek(1);
        return next.equals("*") || next.equals("&") || next.equals("(") || memberPointerAhead(1)
                || !next.equals(")") && !next.equals("...") && !FUNDAMENTAL.contains(next) && !next.equals("const") && !next.equals("volatile") && !className(nameAhead(1)) && tokens.peek(2).equals(")");
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
    NodeBuilder<? extends TypeNode> newType() {
        var specs = specifiers();
        if (specs.typedef() || !specs.modifiers().isEmpty()) throw tokens.error("Storage specifier in new-type-id");
        var declaration = declarator(true, true);
        if (!declaration.name().isEmpty()) throw tokens.error("Name is not allowed in a new-type-id");
        return declaration.apply().apply(specs.type().get());
    }
    NodeBuilder<? extends TypeNode> abstractType() {
        var specs = specifiers();
        if (specs.typedef() || !specs.modifiers().isEmpty()) throw tokens.error("Storage specifier in type-id");
        var declaration = declarator(true);
        if (!declaration.name().isEmpty()) throw tokens.error("Name is not allowed in an abstract type-id");
        var result = declaration.apply().apply(specs.type().get());
        validateDefaultPlacement(result, false);
        return result;
    }
}
