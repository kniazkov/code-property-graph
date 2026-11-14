/* Bounded nonlocal environment-save validation. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;

/** Validates syntax and opaque storage, not dynamic environment matching or lifetime safety.
 * Rejection of an environmental constraint is this implementation's diagnostic policy. */
public final class SetjmpSemantics {
    private SetjmpSemantics() {}

    public static Project analyze(Project project) {
        var operations = new ArrayList<SetjmpExpression>();
        var members = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        var parents = new IdentityHashMap<Node, Node>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) {
            members.add(node); node.children().forEach(child -> parents.put(child, node));
            if (node instanceof SetjmpExpression save) operations.add(save);
        } });
        var contexts = SourceContexts.index(project);
        var conflictingRuntimeDeclarations = members.stream().filter(node -> !LibraryDeclarations.isLibrary(node)
                && runtimeName(node)).toList();
        var editor = new ProjectEditor(project).retainDiagnostics().withoutRevalidation();
        var buffers = Collections.newSetFromMap(new IdentityHashMap<VariableDeclaration, Boolean>());
        for (var node : members) if (node instanceof VariableDeclaration buffer
                && buffer.type().filter(type -> jmpBuffer(type, members)).isPresent()
                && contexts.containsKey(buffer) && staticBuffers(TypeSemantics.profile(contexts.get(buffer)))) {
            buffers.add(buffer);
            if (buffer.modifiers().contains("extern") || buffer.previousDeclaration().isPresent() || !buffer.isDefinition() || members.stream().anyMatch(other -> other != buffer
                    && other instanceof VariableDeclaration declaration && declaration.name().equals(buffer.name())
                    && parents.get(other) == parents.get(buffer)))
                diagnose(editor, buffer, "unsupported", "buffer-redeclaration", "V2 does not normalize extern or repeated opaque-buffer declarations");
        }
        for (var save : operations) {
            var profile = TypeSemantics.profile(SourceContexts.require(contexts, save));
            if (conflictingRuntimeDeclarations.stream().anyMatch(node -> contexts.get(node) == contexts.get(save)
                    && (owner(node, parents) == null || owner(node, parents) == owner(save, parents)))) diagnose(editor, save, "unsupported", "runtime-declaration",
                    "A user or physical-header _setjmp declaration conflicts with the selected opaque runtime bridge");
            if (profile.setjmp().isEmpty()) diagnose(editor, save, "invalid", "capability", "Environment save requires the explicit glibc setjmp capability");
            if (!placement(save, parents, new IntegralConstants(project, profile)))
                diagnose(editor, save, "invalid", "context", "setjmp is restricted to the C90 controlling-expression or expression-statement contexts; this implementation rejects other placements");
            VariableDeclaration buffer = directBuffer(save.buffer(), staticBuffers(profile));
            if (buffer == null || !members.contains(buffer)) {
                diagnose(editor, save, "unsupported", "buffer-expression", "Only a direct name bound to local standard jmp_buf storage is supported");
                continue;
            }
            buffers.add(buffer);
            var type = buffer.type().orElse(null);
            if (type == null || !jmpBuffer(type, members)) {
                diagnose(editor, save, "invalid", "buffer-type", "The buffer must bind the standard-library jmp_buf type in this snapshot"); continue;
            }
            if (TypeSemantics.qualifiers(type) != 0)
                diagnose(editor, save, "unsupported", "qualified-buffer", "Const or volatile jmp_buf storage is outside this subset");
            CallableDeclaration owner = owner(save, parents);
            boolean automatic = owner != null && owner(buffer, parents) == owner && !isField(buffer, parents)
                    && !buffer.modifiers().contains("static") && !buffer.modifiers().contains("extern");
            boolean admittedStatic = staticBuffers(profile) && owner != null && staticStorage(buffer, parents)
                    && (owner(buffer, parents) == null || owner(buffer, parents) == owner)
                    && contexts.get(buffer) == contexts.get(save);
            if (!automatic && !admittedStatic)
                diagnose(editor, save, "unsupported", "buffer-storage", "The selected policy requires direct automatic storage or opt-in same-TU static storage");
            if (buffer.initializer().isPresent())
                diagnose(editor, buffer, "unsupported", "buffer-initialization", "Initialization or copying of opaque jmp_buf storage is outside this subset");
        }
        for (var root : SourceContexts.roots(project)) {
            var profile = TypeSemantics.profile(root.source());
            if (staticBuffers(profile)) validateRestores(root.node(), profile, members, parents, contexts, buffers, editor, false);
            uses(root.node(), buffers, profile, editor, false, false);
        }
        return editor.build();
    }

    private static VariableDeclaration directBuffer(Expression expression, boolean qualified) {
        Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                : qualified && expression instanceof ScopeMemberExpression scope && namespaceQualifier(scope.receiver())
                    ? scope.target().orElse(null) : null;
        return target instanceof VariableDeclaration variable ? variable : null;
    }
    private static boolean namespaceQualifier(Expression expression) {
        Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                : expression instanceof ScopeMemberExpression scope && namespaceQualifier(scope.receiver()) ? scope.target().orElse(null) : null;
        return target instanceof NamespaceDeclaration || target instanceof NamespaceAliasDeclaration;
    }
    private static boolean staticBuffers(PlatformProfile profile) {
        return profile.setjmp().map(com.kniazkov.cpg.build.SetjmpModel::staticBuffers).orElse(false);
    }
    private static boolean staticStorage(VariableDeclaration buffer, Map<Node, Node> parents) {
        return !isField(buffer, parents) && !buffer.modifiers().contains("extern")
                && (owner(buffer, parents) == null || buffer.modifiers().contains("static"));
    }
    /** Checks structural admission only, not whether the saving activation is still alive. */
    private static void validateRestores(Node node, PlatformProfile profile, Set<Node> members,
            Map<Node, Node> parents, Map<Node, SourceFile> contexts, Set<VariableDeclaration> buffers,
            ProjectEditor editor, boolean unevaluated) {
        unevaluated |= node instanceof SizeofExpression || node instanceof OffsetofExpression
                || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile);
        if (!unevaluated && node instanceof CallExpression call && call.target().filter(target ->
                LibraryDeclarations.isLibrary(target) && target.name().equals("longjmp")).isPresent()
                && !call.arguments().isEmpty()) {
            Expression argument = call.arguments().getFirst();
            while (argument instanceof ConversionExpression conversion && !conversion.isExplicit()) argument = conversion.operand();
            VariableDeclaration buffer = directBuffer(argument, true);
            if (buffer == null || !members.contains(buffer) || buffer.type().filter(type -> jmpBuffer(type, members)
                            && TypeSemantics.qualifiers(type) == 0).isEmpty()
                    || isField(buffer, parents) || buffer.modifiers().contains("extern")
                    || buffer.initializer().isPresent() || contexts.get(buffer) != contexts.get(call)) {
                diagnose(editor, call, "unsupported", "restore-buffer", "V2 requires a direct same-TU standard buffer definition; dynamic matching and saving-frame lifetime remain unproved");
            }
        }
        for (Node child : node.children()) validateRestores(child, profile, members, parents, contexts, buffers, editor, unevaluated);
    }

    private static CallableDeclaration owner(Node node, Map<Node, Node> parents) {
        for (Node parent = parents.get(node); parent != null; parent = parents.get(parent))
            if (parent instanceof CallableDeclaration callable) return callable;
        return null;
    }
    private static boolean runtimeName(Node node) {
        String name = switch (node) {
            case CallableDeclaration declaration -> declaration.name();
            case VariableDeclaration declaration -> declaration.name();
            case ParameterDeclaration declaration -> declaration.name();
            case TypedefDeclaration declaration -> declaration.name();
            case ClassDeclaration declaration -> declaration.name();
            case EnumDeclaration declaration -> declaration.name();
            case EnumeratorDeclaration declaration -> declaration.name();
            case NamespaceDeclaration declaration -> declaration.name();
            case NamespaceAliasDeclaration declaration -> declaration.name();
            case TemplateParameterDeclaration declaration -> declaration.name();
            case UsingDeclaration declaration -> declaration.qualifiedName();
            default -> "";
        };
        return name.equals("_setjmp") || name.endsWith("::_setjmp");
    }
    private static boolean isField(Node node, Map<Node, Node> parents) {
        if (node instanceof Declaration declaration && (declaration.access().isPresent()
                || declaration.previousDeclaration().filter(previous -> previous != node && previous.access().isPresent()).isPresent())) return true;
        for (Node parent = parents.get(node); parent != null; parent = parents.get(parent)) {
            if (parent instanceof ClassDeclaration) return true;
            if (parent instanceof CallableDeclaration) return false;
        }
        return false;
    }
    private static boolean jmpBuffer(TypeNode type, Set<Node> members) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<TypeNode, Boolean>());
        while (seen.add(type)) {
            if (type instanceof QualifiedType qualified) type = qualified.baseType();
            else if (type instanceof NamedType name && name.alias().filter(members::contains).isPresent()) type = name.alias().orElseThrow().type();
            else return type instanceof OpaqueLibraryType opaque && members.contains(opaque)
                    && opaque.category() == OpaqueLibraryType.Category.OBJECT
                    && opaque.identity().equals("library::jmp_buf") && LibraryDeclarations.isLibrary(opaque);
        }
        return false;
    }
    private static Expression implicitEnvelope(Expression expression, Map<Node, Node> parents) {
        while (parents.get(expression) instanceof ConversionExpression cast && !cast.isExplicit()
                && cast.operand() == expression) expression = cast;
        return expression;
    }
    private static boolean controlling(Expression expression, Map<Node, Node> parents) {
        expression = implicitEnvelope(expression, parents);
        return control(parents.get(expression), expression);
    }
    private static boolean placement(SetjmpExpression save, Map<Node, Node> parents, IntegralConstants constants) {
        Expression subject = implicitEnvelope(save, parents);
        Node parent = parents.get(subject);
        if (parent instanceof ExpressionStatement statement && statement.expression() == subject) return true;
        if (parent instanceof ConversionExpression cast && cast.operand() == subject
                && cast.isExplicit() && TypeSemantics.unqualified(cast.targetType()) instanceof NamedType type
                && type.fundamentalKind().orElse(null) == FundamentalKind.VOID)
            return parents.get(cast) instanceof ExpressionStatement statement && statement.expression() == cast;
        if (controlling(save, parents)) return true;
        if (parent instanceof LogicalNotExpression unary && unary.operand() == subject)
            return controlling(unary, parents);
        if (parent instanceof BinaryExpression binary && comparison(binary) && binary.operatorTarget().isEmpty()) {
            Expression other = binary.left() == subject ? binary.right() : binary.left();
            return constants.evaluate(other).isPresent() && controlling(binary, parents);
        }
        return false;
    }
    private static boolean comparison(BinaryExpression expression) {
        return expression instanceof EqualExpression || expression instanceof NotEqualExpression
                || expression instanceof LessExpression || expression instanceof LessOrEqualExpression
                || expression instanceof GreaterExpression || expression instanceof GreaterOrEqualExpression;
    }
    private static boolean control(Node parent, Expression expression) {
        return parent instanceof IfStatement s && s.condition() == expression
                || parent instanceof WhileStatement w && w.condition() == expression
                || parent instanceof DoWhileStatement d && d.condition() == expression
                || parent instanceof ForStatement f && f.condition().orElse(null) == expression
                || parent instanceof SwitchStatement sw && sw.condition() == expression;
    }
    private static void uses(Node node, Set<VariableDeclaration> buffers, PlatformProfile profile,
            ProjectEditor editor, boolean unevaluated, boolean allowed) {
        unevaluated |= node instanceof SizeofExpression || node instanceof OffsetofExpression
                || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile);
        Declaration referenced = node instanceof NameExpression name ? name.target().orElse(null)
                : staticBuffers(profile) && node instanceof MemberExpression member ? member.target().orElse(null) : null;
        if (!unevaluated && buffers.contains(referenced) && !allowed)
            diagnose(editor, node, "unsupported", "escaped-buffer", "Copying, escaping or indirectly accessing saved jmp_buf storage is outside this subset");
        for (Node child : node.children()) {
            boolean direct = node instanceof SetjmpExpression save && child == save.buffer()
                    || staticBuffers(profile) && allowed && node instanceof ConversionExpression conversion && !conversion.isExplicit();
            if (node instanceof CallExpression call && call.target().filter(target -> LibraryDeclarations.isLibrary(target)
                    && target.name().equals("longjmp")).isPresent() && !call.arguments().isEmpty())
                direct |= child == call.arguments().getFirst();
            uses(child, buffers, profile, editor, unevaluated, direct);
        }
    }
    private static void diagnose(ProjectEditor editor, Node node, String category, String suffix, String text) {
        editor.diagnose(node, new Diagnostic(category + "-setjmp-" + suffix, Diagnostic.Severity.ERROR, text));
    }
}
