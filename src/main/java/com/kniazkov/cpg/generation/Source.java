/* Code Property Graph: Java and C++ source generation. MIT License. */
package com.kniazkov.cpg.generation;

import com.kniazkov.cpg.model.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Prints typed syntax and adapts the supported demonstration entry points. */
public final class Source {
    /** Languages currently supported for direct source generation. */
    public static final Set<Language> SUPPORTED_TARGETS = Set.of(Language.CPP, Language.JAVA);

    private final Language target;
    private int blockDepth;

    private Source(Language target) {
        if (!SUPPORTED_TARGETS.contains(target)) throw new IllegalArgumentException("Supported targets: JAVA, CPP");
        this.target = target;
    }

    public static String render(Node node, Language target) { return renderFragment(node, target, false); }

    /** Retains lexical block context when a source-preserving edit renders one local declaration. */
    static String renderFragment(Node node, Language target, boolean local) {
        requireComplete(node);
        if (node instanceof Project project) requireVariantSelection(project);
        if (!languages(node).contains(target)) throw new IllegalArgumentException("Unsupported direct emission to " + target);
        var source = new Source(target);
        source.blockDepth = local ? 1 : 0;
        return source.emit(node, 0);
    }

    /** Intersects the language restrictions of owned syntax nodes without following semantic references. */
    public static Set<Language> languages(Node node) {
        EnumSet<Language> targets = EnumSet.copyOf(SUPPORTED_TARGETS);
        if (!implicitIntroductionsMatch(node)) targets.clear();
        node.walk(new NodeVisitor() {
            @Override public void visit(Node n) {
                if (n instanceof SourceFile file && file.hasErrors() || n instanceof ErrorExpression || hasSemanticError(n) || unrepresentableDeclaration(n))
                    targets.clear();
                if (n instanceof IncludeDirective || n instanceof FunctionDeclaration
                        || n instanceof ScopeMemberExpression || n instanceof PointerMemberExpression
                        || n instanceof NameExpression name && name.name().contains("::")
                        || n instanceof AddressOfExpression || n instanceof DereferenceExpression
                        || n instanceof TypeNode && !(n instanceof NamedType)
                        || n instanceof TypedefDeclaration || n instanceof SizeofExpression
                        || n instanceof IntegerLiteral literal && literal.cppType().isPresent()
                        || n instanceof Declaration declaration && cppOnlyDeclaration(declaration))
                    targets.remove(Language.JAVA);
                if (n instanceof NamedType type) {
                    if (type.name().equals("String[]")) targets.remove(Language.CPP);
                    if (type.name().contains("::") || type.elaboratedKeyword().isPresent() || type.alias().isPresent()
                            || type.declaration().orElse(null) instanceof EnumDeclaration
                            || type.fundamentalKind().isPresent()
                                && !Set.of("void", "char", "short", "int", "long", "float", "double").contains(type.name()))
                        targets.remove(Language.JAVA);
                }
                if (n instanceof MethodDeclaration method && method.modifiers().contains("public"))
                    targets.remove(Language.CPP);
            }
        });
        return Collections.unmodifiableSet(targets);
    }

    /** Alternative configurations cannot be flattened into one physical file. */
    private static void requireVariantSelection(Project project) {
        var paths = new HashSet<String>();
        for (var file : project.files()) {
            String path = file.range().map(SourceRange::file).orElse(file.path());
            if (!paths.add(Path.of(path).toAbsolutePath().normalize().toString()))
                throw new IllegalArgumentException("Multiple build variants for " + path + "; select an explicit build composition before source generation");
        }
    }

    /** Generates named files, adapting supported program entry points when changing languages. */
    public static Map<String,String> files(Project project, Language target) {
        requireComplete(project);
        if (target == Language.CPP && PreservedSource.applicable(project)) return PreservedSource.files(project);
        requireVariantSelection(project);
        Source out = new Source(target);
        Map<String,String> files = new LinkedHashMap<>();
        for (SourceFile file : project.files()) {
            boolean translate = target != file.language();
            String name = file.path();
            String content;
            if (!translate) {
                if (!languages(file).contains(target)) throw out.unsupported(file);
                content = out.emit(file, 0);
            }
            else {
                out.checkPortable(file);
                List<ClassDeclaration> classes = file.items().stream().filter(ClassDeclaration.class::isInstance)
                        .map(ClassDeclaration.class::cast).toList();
                List<FunctionDeclaration> functions = file.items().stream().filter(FunctionDeclaration.class::isInstance)
                        .map(FunctionDeclaration.class::cast).toList();
                if (target == Language.JAVA) {
                    for (var function : functions) checkWrapper(function, project);
                    if (classes.isEmpty()) continue; // The C++ dispatch wrapper has no Java source file.
                    if (classes.size() != 1) throw new IllegalArgumentException("Expected one class per translated Java file");
                    var cls = classes.getFirst();
                    name = cls.name() + ".java";
                    content = out.translatedClass(cls, file.language());
                } else {
                    if (classes.size() != 1 || project.files().size() != 1)
                        throw new IllegalArgumentException("Java to C++ entry-point adaptation requires one file with one class");
                    var cls = classes.getFirst();
                    name = cls.name() + ".cpp";
                    content = "#include <iostream>\n\n" + out.translatedClass(cls, file.language())
                            + "\nint main() {\n    " + cls.name() + "::main();\n    return 0;\n}\n";
                }
            }
            if (!Path.of(name).getFileName().toString().equals(name) || files.putIfAbsent(name, content) != null)
                throw new IllegalArgumentException("Unsafe or duplicate output name: " + name);
        }
        return Collections.unmodifiableMap(files);
    }

    /** Invalid snapshots must never reach either replay-based or generated-source emission. */
    private static void requireComplete(Node node) {
        if (!implicitIntroductionsMatch(node))
            throw new IllegalArgumentException("Source generation cannot change an implicit class declaration independently of its written elaborated type");
        node.walk(new NodeVisitor() {
            @Override public void visit(Node n) {
                if (n instanceof SourceFile file && file.hasErrors())
                    throw new IllegalArgumentException("Source generation is blocked by parse errors in " + file.path());
                if (n instanceof ErrorExpression error)
                    throw new IllegalArgumentException("Source generation is blocked by parse errors: " + error.problem().message());
                if (unrepresentableDeclaration(n))
                    throw new IllegalArgumentException("Source generation cannot discard the body or members of " + n.getClass().getSimpleName());
                for (Diagnostic diagnostic : n.diagnostics())
                    if (diagnostic.severity() == Diagnostic.Severity.ERROR)
                        throw new IllegalArgumentException("Source generation is blocked by " + diagnostic.code() + ": " + diagnostic.message());
            }
        });
    }

    private static boolean unrepresentableDeclaration(Node node) {
        return node instanceof ClassDeclaration cls && (!cls.isDefinition() && !cls.members().isEmpty() || cls.implicit() && cls.isDefinition())
                || node instanceof CallableDeclaration callable && callable.writtenType().isPresent() && callable.body().isPresent();
    }

    /** Synthetic tag introductions overlap written type syntax and cannot be edited independently. */
    private static boolean implicitIntroductionsMatch(Node root) {
        var introductions = new ArrayList<ClassDeclaration>();
        var written = new ArrayList<NamedType>();
        root.walk(new NodeVisitor() {
            @Override public void visit(ClassDeclaration declaration) {
                if (declaration.implicit() && declaration != root) introductions.add(declaration);
            }
            @Override public void visit(NamedType type) { if (type.elaboratedKeyword().isPresent()) written.add(type); }
        });
        return introductions.stream().allMatch(declaration -> {
            var uses = written.stream().filter(type -> sameIntroduction(declaration, type)).toList();
            return !uses.isEmpty() && uses.stream().allMatch(type -> declaration.name().equals(type.name())
                    && type.elaboratedKeyword().orElseThrow().equals(declaration.keyword()));
        });
    }

    private static boolean sameIntroduction(ClassDeclaration declaration, NamedType type) {
        if (declaration.provenance().isPresent()) {
            var introduced = declaration.provenance().orElseThrow();
            var written = type.provenance().orElse(null);
            return written != null && introduced.unit() == written.unit()
                    && introduced.startToken() == written.startToken() && introduced.endToken() == written.endToken();
        }
        if (declaration.range().isPresent()) return declaration.range().equals(type.range());
        return type.declaration().orElse(null) == declaration;
    }

    private static boolean hasSemanticError(Node node) {
        return node.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

    /** These source constructs have no supported Java translation. */
    private static boolean cppOnlyDeclaration(Declaration declaration) {
        return declaration instanceof NamespaceDeclaration || declaration instanceof NamespaceAliasDeclaration
                || declaration instanceof UsingDirective || declaration instanceof UsingDeclaration
                || declaration instanceof EnumDeclaration || declaration instanceof EnumeratorDeclaration
                || declaration instanceof ClassDeclaration cls && (!cls.isDefinition() || !cls.keyword().equals("class"))
                || !declaration.languageLinkage().equals("C++")
                || declaration instanceof VariableDeclaration variable && !variable.modifiers().isEmpty()
                || declaration instanceof ParameterDeclaration parameter && !parameter.modifiers().isEmpty()
                || declaration instanceof CallableDeclaration callable && callable.modifiers().stream()
                        .anyMatch(m -> Set.of("extern", "inline", "virtual", "friend").contains(m));
    }

    /** Validates a relative output path before any filesystem write. */
    static void requireSafeName(String name) {
        Path path = Path.of(name);
        if (name.isBlank() || path.isAbsolute() || !path.normalize().equals(path)
                || name.contains("\\") || path.startsWith(".."))
            throw new IllegalArgumentException("Unsafe output name: " + name);
    }

    private static void checkWrapper(FunctionDeclaration fn, Project project) {
        if (fn.body().isEmpty()) throw new IllegalArgumentException("Cannot translate this C++ declaration to Java");
        var items = fn.body().orElseThrow().items();
        if (!fn.name().equals("main") || items.size() != 2 || !(items.getFirst() instanceof ExpressionStatement stmt)
                || !(stmt.expression() instanceof CallExpression call) || !call.arguments().isEmpty()
                || !(call.callee() instanceof ScopeMemberExpression member) || !member.member().equals("main")
                || !(member.receiver() instanceof NameExpression owner)
                || !(items.getLast() instanceof ReturnStatement ret) || !(ret.value().orElse(null) instanceof IntegerLiteral zero)
                || zero.value() != 0 || project.files().stream().flatMap(f -> f.items().stream())
                    .noneMatch(n -> n instanceof ClassDeclaration c && c.name().equals(owner.name())))
            throw new IllegalArgumentException("Cannot translate this C++ entry point to Java");
    }

    private void checkPortable(SourceFile file) {
        if (target == Language.JAVA && file.items().stream().anyMatch(item -> item instanceof VariableDeclaration || item instanceof TypedefDeclaration))
            throw new IllegalArgumentException("Cannot translate C++ global variable or typedef declarations to Java");
        file.walk(new NodeVisitor() {
            @Override public void visit(Declaration n) {
                if (target == Language.JAVA && cppOnlyDeclaration(n)) throw unsupported(n);
            }
            @Override public void visit(TypeNode n) {
                if (target != Language.JAVA) return;
                if (!languages(n).contains(target)) throw unsupported(n);
                if (n instanceof NamedType named && named.fundamentalKind().isPresent()) {
                    if (!Set.of(FundamentalKind.INT, FundamentalKind.VOID).contains(named.fundamentalKind().orElseThrow())) throw unsupported(n);
                    var profile = n.provenance().map(p -> p.unit().configuration()).map(c -> c.platform());
                    if (named.fundamentalKind().orElseThrow() == FundamentalKind.INT && profile.isPresent()
                            && profile.orElseThrow().sizeBits("int").orElse(0) != 32) throw unsupported(n);
                }
            }
            @Override public void visit(IntegerLiteral n) { if (target == Language.JAVA && n.cppType().isPresent()) throw unsupported(n); }
            @Override public void visit(TypedefDeclaration n) { if (target == Language.JAVA) throw unsupported(n); }
            @Override public void visit(SizeofExpression n) { if (target == Language.JAVA) throw unsupported(n); }
            @Override public void visit(AddressOfExpression n) { if (target == Language.JAVA) throw unsupported(n); }
            @Override public void visit(DereferenceExpression n) { if (target == Language.JAVA) throw unsupported(n); }
            @Override public void visit(PointerMemberExpression n) { if (target == Language.JAVA) throw unsupported(n); }
            @Override public void visit(IncludeDirective n) {
                if (!n.system() || !n.header().equals("iostream")) throw unsupported(n);
            }
            @Override public void visit(NameExpression n) {
                if (target == Language.JAVA && n.name().contains("::")) throw unsupported(n);
                if (n.target().isEmpty() && Set.of("std", "System").contains(n.name()))
                    throw new IllegalArgumentException("Translate console output at GREEN or FOLDED stage");
            }
        });
    }

    private String translatedClass(ClassDeclaration cls, Language from) {
        if (cls.members().size() != 1 || !(cls.members().getFirst() instanceof MethodDeclaration method)
                || !method.name().equals("main") || method.body().isEmpty())
            throw new IllegalArgumentException("Expected a class with one main method for translation");
        var body = method.body().orElseThrow();
        Set<String> names = new HashSet<>();
        body.walk(new NodeVisitor() {
            @Override public void visit(NameExpression n) { names.add(n.name()); }
            @Override public void visit(VariableDeclaration n) { names.add(n.name()); }
        });
        if (from == Language.JAVA && method.parameters().stream().anyMatch(p -> names.contains(p.name())))
            throw new IllegalArgumentException("Translation of command-line arguments to C++ is unsupported");
        String parameter = "args";
        while (names.contains(parameter)) parameter += "_";
        String signature = target == Language.JAVA ? "public static void main(String[] " + parameter + ")" : "static void main()";
        return classStart(cls) + "    " + signature + " " + emit(body, 1) + classEnd();
    }

    private String classStart(ClassDeclaration cls) {
        String access = target == Language.JAVA && cls.modifiers().contains("public") ? "public " : "";
        return access + (target == Language.CPP ? cls.keyword() : "class") + " " + cls.name() + " {\n" + (target == Language.CPP ? cls.memberAccess() + ":\n" : "");
    }
    private String classEnd() { return target == Language.CPP ? "};\n" : "}\n"; }

    private IllegalArgumentException unsupported(Node node) {
        return new IllegalArgumentException("Cannot generate " + node.getClass().getSimpleName() + " in " + target);
    }

    private String emit(Node n, int level) {
        String pad = "    ".repeat(level);
        return switch (n) {
            case SourceFile f -> f.items().stream().map(item -> emit(item, level)).collect(Collectors.joining("\n"));
            case IncludeDirective i when target == Language.CPP -> "#include " + (i.system() ? "<" + i.header() + ">" : "\"" + i.header() + "\"") + "\n";
            case NamespaceDeclaration namespace -> pad + "namespace" + (namespace.anonymous() ? "" : " " + namespace.name()) + " {\n"
                    + namespace.members().stream().map(member -> emit(member, level + 1)).collect(Collectors.joining("\n")) + pad + "}\n";
            case NamespaceAliasDeclaration alias -> pad + "namespace " + alias.name() + " = " + alias.targetName() + ";\n";
            case UsingDirective using -> pad + "using namespace " + using.namespaceName() + ";\n";
            case UsingDeclaration using -> pad + "using " + using.qualifiedName() + ";\n";
            case EnumDeclaration enumeration -> pad + "enum" + (enumeration.name().isEmpty() ? "" : " " + enumeration.name()) + " { "
                    + enumeration.enumerators().stream().map(value -> emit(value, 0)).collect(Collectors.joining(", ")) + " };\n";
            case EnumeratorDeclaration value -> value.name() + value.value().map(expression -> " = " + emit(expression, 0)).orElse("");
            case ClassDeclaration c -> c.implicit() ? "" : !c.isDefinition() ? pad + c.keyword() + " " + c.name() + ";\n"
                    : pad + classStart(c) + c.members().stream().map(m -> emit(m, level + 1)).collect(Collectors.joining("\n")) + pad + classEnd();
            case CallableDeclaration f -> linkage(f, pad + String.join("", f.modifiers().stream().map(m -> m + " ").toList())
                    + (f.writtenType().isPresent() ? declaration(f.writtenType().orElseThrow(), f.name()) + ";\n"
                        : declaration(f.returnType().orElseThrow(), f.name() + "(" + parameters(f.parameters(), f.variadic()) + ")"
                            + (f.isConst() ? " const" : "") + (f.isVolatile() ? " volatile" : ""))
                            + f.body().map(b -> " " + emit(b, level)).orElse(";\n")), level);
            case ParameterDeclaration p -> String.join("", p.modifiers().stream().map(m -> m + " ").toList())
                    + declaration(p.type().orElseThrow(), p.name());
            case TypedefDeclaration d -> pad + "typedef " + declaration(d.type(), d.name()) + ";\n";
            case TypeNode t -> declaration(t, "");
            case BlockStatement b -> block(b, level);
            case VariableDeclaration v -> linkage(v, pad + String.join("", v.modifiers().stream().map(m -> m + " ").toList())
                    + (!v.isDefinition() && v.languageLinkage().equals("C") && !v.modifiers().contains("extern") ? "extern " : "")
                    + declaration(v.type().orElseThrow(), v.name())
                    + v.initializer().map(i -> " = " + emit(i, 0)).orElse("") + ";\n", level);
            case ReturnStatement r -> pad + "return" + r.value().map(e -> " " + emit(e, 0)).orElse("") + ";\n";
            case ExpressionStatement s -> pad + (s.expression() instanceof AssignmentExpression a ? assignment(a) : emit(s.expression(), 0)) + ";\n";
            case IfStatement i -> pad + "if (" + emit(i.condition(), 0) + ") " + body(i.thenBranch(), level)
                    + i.elseBranch().map(branch -> pad + "else " + body(branch, level)).orElse("");
            case WhileStatement w -> pad + "while (" + emit(w.condition(), 0) + ") " + body(w.body(), level);
            case PrintStatement s -> pad + (target == Language.JAVA ? "System.out.println(" + emit(s.value(), 0) + ");"
                    : "std::cout << " + emit(s.value(), 0) + " << std::endl;") + "\n";
            case AssignmentExpression a -> "(" + assignment(a) + ")";
            case Exit ignored -> "";
            case IntegerLiteral i -> integerLiteral(i);
            case SizeofExpression size -> "sizeof(" + size.type().map(t -> declaration(t, "")).orElseGet(() -> emit(size.operand().orElseThrow(), 0)) + ")";
            case NameExpression name -> name.name();
            case MemberExpression m -> emit(m.receiver(), 0) + separator(m) + m.member();
            case CallExpression c -> emit(c.callee(), 0) + "(" + c.arguments().stream().map(e -> emit(e, 0)).collect(Collectors.joining(", ")) + ")";
            case UnaryExpression u -> "(" + symbol(u) + "(" + emit(u.operand(), 0) + "))";
            case BinaryExpression b -> "(" + emit(b.left(), 0) + " " + symbol(b) + " " + emit(b.right(), 0) + ")";
            default -> throw unsupported(n);
        };
    }

    private String block(BlockStatement block, int level) {
        blockDepth++;
        try {
            return "{\n" + block.items().stream().map(item -> emit(item, level + 1)).collect(Collectors.joining())
                    + "    ".repeat(level) + "}\n";
        } finally { blockDepth--; }
    }

    /** A linkage block preserves variable declaration/definition semantics and storage specifiers. */
    private String linkage(Declaration declaration, String text, int level) {
        if (!declaration.languageLinkage().equals("C") || blockDepth > 0) return text;
        String pad = "    ".repeat(level);
        return pad + "extern \"C\" {\n" + text + pad + "}\n";
    }

    private String integerLiteral(IntegerLiteral literal) {
        if (target != Language.CPP) return Integer.toString(literal.value());
        var profile = literal.provenance().map(p -> p.unit().configuration()).map(c -> c.platform())
                .orElseGet(com.kniazkov.cpg.build.PlatformProfile::defaults);
        var number = java.math.BigInteger.valueOf(literal.value());
        String type = literal.cppType().orElseGet(() -> List.of("int", "long", "unsigned long").stream()
                .filter(name -> profile.integerRange(name).map(range -> number.compareTo(range.min()) >= 0 && number.compareTo(range.max()) <= 0).orElse(false))
                .findFirst().orElse("int"));
        String suffix = switch (type) { case "unsigned int" -> "U"; case "long" -> "L"; case "unsigned long" -> "UL"; default -> ""; };
        var range = profile.integerRange(type);
        // The positive token in -MIN may require a wider (or unsigned) type.
        // Express signed minima using two representable operands of the intended type.
        if (number.signum() < 0 && range.isPresent() && number.equals(range.orElseThrow().min()))
            return "((-(" + range.orElseThrow().max() + suffix + ")) - 1" + suffix + ")";
        return literal.value() + (literal.cppType().isPresent() ? suffix : "");
    }

    /** Prints the declarator around its name; grouping follows the type tree's binding. */
    private String declaration(TypeNode type, String name) { return declaration(type, name, ""); }
    private String declaration(TypeNode type, String name, String qualifiers) {
        return switch (type) {
            case NamedType n -> n.elaboratedKeyword().map(keyword -> keyword + " ").orElse("") + n.name() + qualifiers + (name.isEmpty() ? "" : " " + name);
            case QualifiedType q -> declaration(q.baseType(), name, qualifiers + (q.isConst() ? " const" : "") + (q.isVolatile() ? " volatile" : ""));
            case PointerType p -> declaration(p.pointee(), grouped(p.pointee(), "*" + qualifiers + (qualifiers.isEmpty() ? "" : " ") + name));
            case ReferenceType r -> declaration(r.referredType(), grouped(r.referredType(), "&" + name));
            case MemberPointerType p -> declaration(p.pointee(), grouped(p.pointee(), declaration(p.ownerType(), "") + "::*" + qualifiers + (qualifiers.isEmpty() ? "" : " ") + name));
            case ArrayType a -> declaration(a.elementType(), name + "[" + a.bound().map(e -> emit(e, 0)).orElse("") + "]", qualifiers);
            case FunctionType f -> declaration(f.returnType(), name + "(" + parameters(f.parameters(), f.variadic()) + ")"
                    + (f.isConst() ? " const" : "") + (f.isVolatile() ? " volatile" : ""));
            default -> throw unsupported(type);
        };
    }
    private String grouped(TypeNode target, String declarator) {
        while (target instanceof QualifiedType qualified) target = qualified.baseType();
        return target instanceof ArrayType || target instanceof FunctionType ? "(" + declarator + ")" : declarator;
    }
    private String parameters(List<ParameterDeclaration> parameters, boolean variadic) {
        String written = parameters.stream().map(p -> emit(p, 0)).collect(Collectors.joining(", "));
        return written + (variadic ? (written.isEmpty() ? "" : ", ") + "..." : "");
    }

    private String body(Statement statement, int level) {
        if (statement instanceof BlockStatement block) return emit(block, level);
        return "{\n" + emit(statement, level + 1) + "    ".repeat(level) + "}\n";
    }

    private String separator(MemberExpression m) {
        if (m instanceof ScopeMemberExpression) return target == Language.CPP ? "::" : ".";
        if (m instanceof DotMemberExpression) return ".";
        if (target == Language.CPP) return "->";
        throw unsupported(m);
    }

    private String assignment(AssignmentExpression a) { return emit(a.target(), 0) + " = " + emit(a.value(), 0); }

    private String symbol(Expression e) {
        return switch (e) {
            case AddExpression a -> "+"; case SubtractExpression a -> "-"; case MultiplyExpression a -> "*";
            case DivideExpression a -> "/"; case RemainderExpression a -> "%"; case ShiftLeftExpression a -> "<<";
            case ShiftRightExpression a -> ">>"; case EqualExpression a -> "=="; case NotEqualExpression a -> "!=";
            case LessExpression a -> "<"; case GreaterExpression a -> ">"; case LessOrEqualExpression a -> "<=";
            case GreaterOrEqualExpression a -> ">="; case LogicalNotExpression a -> "!";
            case UnaryPlusExpression a -> "+"; case NegateExpression a -> "-";
            case AddressOfExpression a when target == Language.CPP -> "&";
            case DereferenceExpression a when target == Language.CPP -> "*";
            default -> throw unsupported(e);
        };
    }
}
