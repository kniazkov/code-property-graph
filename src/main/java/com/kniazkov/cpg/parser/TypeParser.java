/* C++03 declaration-specifiers and recursive declarators. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.*;

/** Reads the C++ declarator grammar, composing from the name outward, not by token order. */
final class TypeParser {
    private static final Set<String> FUNDAMENTAL = Set.of("void", "bool", "char", "signed", "unsigned", "short", "long", "int", "wchar_t", "float", "double");
    private static final Set<String> STORAGE = Set.of("static", "extern", "inline", "register", "mutable", "auto", "virtual");
    private final TokenCursor tokens;
    private final ExpressionParser expressions;
    /** Parser-only lookup: enough information to distinguish declarations from expressions. */
    private static final class Scope {
        final Scope parent;
        boolean prototype, classScope;
        final Map<String, Binding> names = new LinkedHashMap<>();
        final Set<String> tags = new HashSet<>();
        final Set<String> enums = new HashSet<>();
        final Set<String> templateTypes = new HashSet<>(), templateValues = new HashSet<>(), templates = new HashSet<>();
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
    /** Parser-only type-name availability; semantic declarations come from the catalogue. */
    void includeSystemHeader(String header) {
        if (!header.equals("vector")) return;
        Scope saved = scope; scope = root;
        pushNamedScope("std");
        try { registerClass("vector"); declareTemplate("vector"); registerClass("allocator"); declareTemplate("allocator"); }
        finally { popScope(); scope = saved; }
    }
    void registerEnum(String name) { registerClass(name); scope.enums.add(name); }
    void validateValueTemplateParameter(NodeBuilder<? extends TypeNode> builder) {
        TypeNode type = TypeIdentity.canonicalType(builder.build());
        while (type instanceof QualifiedType qualified) type = TypeIdentity.canonicalType(qualified.baseType());
        if (!(type instanceof NamedType named)) return; // Pointers/references and adjusted arrays/functions are permitted.
        if (named.fundamentalKind().isPresent()) {
            if (Set.of("void", "float", "double", "long double").contains(named.fundamentalKind().orElseThrow().spelling()))
                throw tokens.error("C++03 non-type template parameters require integral, enum, pointer or reference types");
            return;
        }
        if (named.dependent()) return;
        String name = named.name(); int separator = name.lastIndexOf("::");
        if (separator >= 0) {
            Scope owner = separator == 0 ? root : namespace(name.substring(0, separator));
            if (owner != null && owner.enums.contains(name.substring(separator + 2))) return;
        } else for (Scope current = scope; current != null; current = current.parent)
            if (current.names.containsKey(name)) { if (current.enums.contains(name)) return; else break; }
        if (className(name)) throw tokens.error("A C++03 non-type template parameter cannot have class type");
    }
    void registerClass(String name) {
        scope.tags.add(name);
        Scope qualifier = scope.nested.computeIfAbsent(name, ignored -> new Scope(scope));
        qualifier.classScope = true;
        scope.names.putIfAbsent(name, new Binding(true, null, qualifier));
    }
    void declareTemplateType(String name) { scope.names.put(name, new Binding(true, null, null)); scope.templateTypes.add(name); }
    void declareTemplateValue(String name) { declareName(name); scope.templateValues.add(name); }
    void declareTemplate(String name) { scope.templates.add(name); }
    boolean templateName(String name) {
        int separator = name.lastIndexOf("::");
        if (separator >= 0) {
            Scope owner = separator == 0 ? root : namespace(name.substring(0, separator));
            return owner != null && owner.templates.contains(name.substring(separator + 2));
        }
        for (Scope current = scope; current != null; current = current.parent) if (current.templates.contains(name)) return true;
        return false;
    }
    boolean dependentName(String name) {
        String head = name.split("::")[0];
        for (Scope current = scope; current != null; current = current.parent) {
            if (current.templateTypes.contains(head) || current.templateValues.contains(head)) return true;
            if (current.names.containsKey(head)) return false;
        }
        return false;
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
        if (!className(nameAhead(0))) return false;
        int checkpoint = tokens.checkpoint();
        try { var type = writtenNamedType(); return className(type.name().orElseThrow()) && tokens.peek().equals("("); }
        catch (ParseException ignored) { return false; }
        finally { tokens.restore(checkpoint); }
    }
    boolean startsType() {
        String word = tokens.peek();
        return FUNDAMENTAL.contains(word) || word.equals("const") || word.equals("volatile") || word.equals("typedef")
                || word.equals("typename") || STORAGE.contains(word) || Set.of("struct", "class", "union", "enum").contains(word)
                || className(nameAhead(0));
    }
    boolean startsDeclaration() {
        if (Set.of("using", "class", "struct", "union", "enum").contains(tokens.peek())) return true;
        if (!startsType()) return false;
        int checkpoint = tokens.checkpoint();
        try {
            specifiers();
            if (!tokens.peek().equals("(")) return true;
            String inside = tokens.peek(1);
            return inside.equals("*") || inside.equals("&") || inside.equals("(") || memberPointerAhead(1)
                    || CppLexer.isIdentifier(inside) && tokens.peek(2).equals(")");
        } catch (ParseException ignored) { return true; }
        finally { tokens.restore(checkpoint); }
    }

    record Specifiers(Supplier<NodeBuilder<? extends TypeNode>> type, boolean typedef, List<String> modifiers) {}
    Specifiers specifiers() {
        var start = tokens.start();
        int implicitStart = implicitDeclarations.size();
        boolean typedef = false, constant = false, vol = false;
        List<String> modifiers = new ArrayList<>(), words = new ArrayList<>();
        String name = null, elaboratedKeyword = null;
        NamedType.Builder written = null;
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
            } else if (name == null && words.isEmpty() && (tokens.isIdentifier() || tokens.peek().equals("::") || tokens.peek().equals("typename"))) {
                written = writtenNamedType(); name = written.name().orElseThrow(); target = alias(name);
                if (written.dependent() && written.qualifier().isPresent() && !written.typenameKeyword())
                    throw tokens.error("A dependent qualified type requires typename");
            } else break;
        }
        if (name == null) {
            name = String.join(" ", words);
            if (FundamentalKind.fromSpelling(name).isEmpty()) throw tokens.error("Invalid C++03 type specifiers '" + name + "'");
        }
        var leaf = tokens.span(start, written == null ? NamedType.create().setName(name) : written);
        for (int index = implicitStart; index < implicitDeclarations.size(); index++) tokens.span(start, implicitDeclarations.get(index).declaration());
        if (target != null) leaf.setAlias(target);
        if (elaboratedKeyword != null) leaf.setElaboratedKeyword(elaboratedKeyword);
        final String spelling = name, keyword = elaboratedKeyword;
        final NamedType syntax = leaf.build();
        final TypedefDeclaration.Builder binding = target;
        final boolean c = constant, v = vol;
        // Every declarator owns fresh syntax; alias targets deliberately remain shared references.
        final var range = leaf.build().range();
        final var provenance = leaf.build().provenance();
        Supplier<NodeBuilder<? extends TypeNode>> supplier = () -> {
            var named = syntax.edit();
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
    NamedType.Builder writtenNamedType() {
        var start = tokens.start();
        boolean typename = tokens.take("typename");
        String name = tokens.take("::") ? "::" : "";
        name += tokens.identifier();
        var current = NamedType.create().setName(name).setDependent(dependentName(name));
        if (tokens.peek().equals("<")) {
            current.setTemplateArguments(templateArguments());
            current.setDependent(current.dependent() || dependentArguments(current));
        }
        while (tokens.peek().equals("::") && !tokens.peek(1).equals("*")) {
            tokens.advance(); boolean template = tokens.take("template");
            name += "::" + tokens.identifier();
            var next = NamedType.create().setName(name).setQualifier(current).setTemplateKeyword(template).setDependent(current.dependent());
            if (tokens.peek().equals("<")) {
                if (current.dependent() && !template) throw tokens.error("A dependent qualified template-id requires template");
                next.setTemplateArguments(templateArguments());
                next.setDependent(next.dependent() || dependentArguments(next));
            }
            else if (template) throw tokens.error("template disambiguator requires a template-id");
            current = next;
        }
        if (typename && current.qualifier().isEmpty()) throw tokens.error("typename requires a qualified type name");
        current.setTypenameKeyword(typename);
        return tokens.span(start, current);
    }
    private boolean dependentArguments(NamedType.Builder type) {
        boolean[] dependent = { false };
        for (var argument : type.templateArguments()) argument.build().walk(new NodeVisitor() {
            @Override public void visit(NamedType named) { dependent[0] |= named.dependent(); }
            @Override public void visit(NameExpression name) { dependent[0] |= dependentName(name.name()); }
        });
        return dependent[0];
    }
    List<NodeBuilder<? extends TemplateArgument>> templateArguments() {
        tokens.expect("<");
        var arguments = new ArrayList<NodeBuilder<? extends TemplateArgument>>();
        if (!tokens.take(">")) {
            do {
                var start = tokens.start();
                var argument = TemplateArgument.create();
                if (startsType()) {
                    var type = abstractType();
                    argument.setType(type);
                    if (type instanceof NamedType.Builder named && !named.hasTemplateArguments()
                            && templateName(named.name().orElseThrow())) argument.setKind(TemplateArgument.Kind.TEMPLATE);
                } else argument.setExpression(expressions.parseTemplateArgument());
                arguments.add(tokens.span(start, argument));
            } while (tokens.take(","));
            tokens.expect(">");
        }
        return arguments;
    }
    String qualifiedName() {
        StringBuilder name = new StringBuilder();
        if (tokens.take("::")) name.append("::");
        name.append(tokens.identifier());
        while (tokens.peek().equals("::") && !tokens.peek(1).equals("*")) { tokens.advance(); name.append("::").append(tokens.identifier()); }
        return name.toString();
    }
    record Declarator(String name, Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>> apply,
            List<NodeBuilder<? extends TemplateArgument>> templateArguments) {
        Declarator(String name, Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>> apply) { this(name, apply, null); }
    }
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
        List<NodeBuilder<? extends TemplateArgument>> arguments = null;
        Function<NodeBuilder<? extends TypeNode>, NodeBuilder<? extends TypeNode>> direct = Function.identity();
        if (!newType && tokens.peek().equals("(") && groupingAhead()) {
            tokens.advance(); var nested = declarator(abstractAllowed); tokens.expect(")"); name = nested.name(); direct = nested.apply(); arguments = nested.templateArguments();
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
        if (!name.isEmpty() && tokens.peek().equals("<")) arguments = templateArguments();
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
                boolean exceptionSpecification = tokens.peek().equals("throw");
                var exceptionTypes = exceptionTypes();
                var previous = direct;
                direct = base -> previous.apply(tokens.span(start, FunctionType.create().setReturnType(base).setParameters(parameters).setVariadic(varargs).setConst(constant).setVolatile(vol).setHasExceptionSpecification(exceptionSpecification).setExceptionTypes(exceptionTypes)));
            } else break;
        }
        var result = direct;
        return new Declarator(name, base -> {
            for (var prefix : prefixes) base = prefix.apply(base);
            return result.apply(base);
        }, arguments);
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
            var result = name.build().edit();
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
            result.setHasExceptionSpecification(function.hasExceptionSpecification());
            result.setExceptionTypes(function.exceptionTypes().stream().map(this::copyType).toList());
            for (var parameter : function.parameters()) {
                var original = (ParameterDeclaration.Builder) parameter;
                result.addParameter(ParameterDeclaration.create().setName(original.name().orElse(""))
                        .setModifiers(original.modifiers()).setType(copyType(original.type().orElseThrow())));
            }
            return result;
        }
        throw new IllegalArgumentException("Unsupported type syntax " + type.getClass().getName());
    }

    /** Dynamic exception specifications are syntax, not part of a C++03 function type identity. */
    List<NodeBuilder<? extends TypeNode>> exceptionTypes() {
        var result = new ArrayList<NodeBuilder<? extends TypeNode>>();
        if (tokens.take("throw")) {
            tokens.expect("(");
            if (!tokens.take(")")) {
                do { result.add(abstractType()); } while (tokens.take(","));
                tokens.expect(")");
            }
        }
        return result;
    }

    String operatorName() {
        String operation = tokens.peek();
        if (Set.of("new", "delete").contains(operation)) {
            tokens.advance();
            if (tokens.take("[")) { tokens.expect("]"); operation += "[]"; }
            return "operator " + operation;
        }
        if (tokens.take("(")) { tokens.expect(")"); return "operator()"; }
        if (tokens.take("[")) { tokens.expect("]"); return "operator[]"; }
        if (!Set.of("+", "-", "*", "/", "%", "^", "&", "|", "~", "!", "=", "<", ">",
                "+=", "-=", "*=", "/=", "%=", "^=", "&=", "|=", "<<", ">>", "<<=", ">>=",
                "==", "!=", "<=", ">=", "&&", "||", "++", "--", ",", "->*", "->").contains(operation))
            throw tokens.error("Unsupported operator function name");
        tokens.advance();
        return "operator" + operation;
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
