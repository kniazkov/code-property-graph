/* Code Property Graph: typed graph analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Evaluates immutable integer expressions using the owning C++ translation unit's profile.
 * Java retains its existing conservative signed-32-bit behavior. */
public final class ConstantFolder {
    private ConstantFolder() {}

    /** Visits a reversed preorder so operands are available before their parent.
     * The identity map contains only proven integer constants. A missing operand,
     * division by zero or an out-of-range intermediate prevents folding its parent.
     * All replacements are published together through the project editor. */
    public static Project fold(Project project) {
        // Replacing even an unevaluated branch can erase the syntax that explains an
        // error. Preserve the complete erroneous snapshot rather than conceal it.
        if (project.hasErrors() || ConstantEvaluator.hasSemanticErrors(project)) return project.recordPass("ConstantFolder");
        var editor = new ProjectEditor(project).setPassId("ConstantFolder").withoutRevalidation();
        var order = new ArrayList<Node>();
        for (var root : SourceContexts.roots(project)) {
            SourceFile file = root.source();
            if (file.language() == Language.CPP) {
                foldCpp(project, root.node(), file, editor);
            } else root.node().walk(new NodeVisitor() {
                @Override public void visit(Node node) { order.add(node); }
            });
        }
        Map<Node, Integer> constants = new IdentityHashMap<>();
        for (Node node : order.reversed()) {
            if (node instanceof IntegerLiteral literal) {
                if (literal.bigValue().bitLength() < 32) constants.put(node, literal.bigValue().intValue());
                continue;
            }
            long result;
            if (node instanceof UnaryExpression unary && constants.containsKey(unary.operand())) {
                long operand = constants.get(unary.operand());
                if (unary instanceof NegateExpression) result = -operand;
                else if (unary instanceof UnaryPlusExpression) result = operand;
                else continue;
            } else if (node instanceof BinaryExpression binary
                    && constants.containsKey(binary.left()) && constants.containsKey(binary.right())) {
                long a = constants.get(binary.left()), b = constants.get(binary.right());
                if (binary instanceof AddExpression) result = a + b;
                else if (binary instanceof SubtractExpression) result = a - b;
                else if (binary instanceof MultiplyExpression) result = a * b;
                else if (binary instanceof DivideExpression && b != 0) result = a / b;
                else continue;
            } else continue;
            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                constants.put(node, (int) result);
                var literal = IntegerLiteral.create().setValue((int) result);
                node.range().ifPresent(literal::setRange);
                editor.replace(node, literal.build());
            }
        }
        Project folded = editor.build();
        // Recompute green array facts if a symbolic bound was replaced by its value.
        boolean greenTypes = project.files().stream().anyMatch(file -> file.language() == Language.CPP
                && hasGreenTypes(file));
        if (greenTypes) folded = TypeNormalizer.green(folded);
        // A changed operand invalidates sequence metadata and its concrete template
        // declarations. Re-resolve before publishing fresh green sequence references.
        if (!project.dynamicArrayTypes().isEmpty() || !project.dynamicArraySemantics().isEmpty()
                || !project.containerTypes().isEmpty() || !project.containerSemantics().isEmpty()
                || !project.characterSequenceTypes().isEmpty() || !project.sequenceSemantics().isEmpty())
            folded = SequenceSemantics.green(ContainerSemantics.green(DynamicArraySemantics.green(NameResolver.resolve(folded))));
        return folded.recordPass("ConstantFolder");
    }

    private static boolean hasGreenTypes(SourceFile file) {
        boolean[] result = {false};
        file.walk(new NodeVisitor() {
            @Override public void visit(TypeNode type) { result[0] |= type.semanticType().isPresent(); }
        });
        return result[0];
    }

    /** A literal cannot retain a layout dependency. Preserve the intrinsic, its operands,
     * and values depending on it (including sizeof of an offsetof-sized array) so later
     * declaration edits still recompute the same syntax. This is not a numeric cache. */
    private static Set<Node> offsetofDependencies(Project project, Node root, com.kniazkov.cpg.build.PlatformProfile profile) {
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Node, List<Node>> users = new IdentityHashMap<>();
        var layout = new LayoutDependencies(project, profile, retained);
        var pending = new ArrayDeque<Node>(); pending.add(root);
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (!seen.add(node)) continue;
            if (node instanceof OffsetofExpression offset) layout.constant(offset);
            for (Node dependency : java.util.stream.Stream.concat(node.children().stream(), node.references().stream()).toList()) {
                users.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(node);
                pending.addLast(dependency);
            }
        }
        pending.addAll(retained);
        while (!pending.isEmpty()) for (Node user : users.getOrDefault(pending.removeFirst(), List.of()))
            if (retained.add(user)) pending.addLast(user);
        // Retain the complete expression syntax, not just its dependent branch.
        // For example, folding the sibling conversion in offsetof(S,x)+1 to 1UL
        // would edit an array declarator enclosing the preserved macro invocation.
        retained.stream().filter(Expression.class::isInstance).forEach(pending::addLast);
        seen.clear();
        while (!pending.isEmpty()) {
            Node node = pending.removeFirst();
            if (!seen.add(node)) continue;
            retained.add(node); pending.addAll(node.children());
        }
        return retained;
    }

    /** The query's representation dependencies, not arbitrary reachable method bodies.
     * Pointers require only their own representation. A const/enum initializer and every
     * sizeof dependency retain syntax even if their numeric value was previously known. */
    private static final class LayoutDependencies {
        private final com.kniazkov.cpg.build.PlatformProfile profile;
        private final Set<Node> retained;
        private final Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<EnumeratorDeclaration, EnumDeclaration> enums = new IdentityHashMap<>();
        private final Map<ClassDeclaration, ClassTemplateInstance> templates = new IdentityHashMap<>();
        private final Map<ClassDeclaration, List<ClassDeclaration>> selectionHeaders;
        private final Set<Node> argumentSyntaxSeen = Collections.newSetFromMap(new IdentityHashMap<>());
        LayoutDependencies(Project project, com.kniazkov.cpg.build.PlatformProfile profile, Set<Node> retained) {
            this.profile = profile; this.retained = retained;
            selectionHeaders = ClassTemplateSemantics.layoutSelectionHeaders(project);
            project.classTemplateInstances().forEach(instance -> templates.put(instance.declaration(), instance));
            project.walk(new NodeVisitor() { @Override public void visit(EnumDeclaration owner) {
                owner.enumerators().forEach(enumerator -> enums.put(enumerator, owner));
            } });
        }
        void constant(Expression expression) {
            if (!visited.add(expression)) return;
            expression.walk(new NodeVisitor() { @Override public void visit(Node node) { retained.add(node); } });
            if (expression instanceof OffsetofExpression offset) {
                type(offset.type());
                offset.steps().stream().filter(OffsetofExpression.Index.class::isInstance)
                        .map(OffsetofExpression.Index.class::cast).forEach(index -> constant(index.expression()));
            }
            if (expression instanceof SizeofExpression size)
                size.type().or(() -> size.operand().flatMap(value -> TypeSemantics.expressionType(value, profile))).ifPresent(this::type);
            Declaration declaration = expression instanceof NameExpression name ? name.target().orElse(null)
                    : expression instanceof ScopeMemberExpression member ? member.target().orElse(null) : null;
            if (declaration instanceof VariableDeclaration variable && variable.type().isPresent()
                    && TypeSemantics.qualifiers(variable.type().orElseThrow()) == 1) {
                type(variable.type().orElseThrow());
                variable.initializer().ifPresent(this::constant);
            }
            if (profile.offsetof().map(model -> model.enumStorage() != null).orElse(false))
                EnumSemantics.objectType(expression).flatMap(EnumSemantics::declaration).ifPresent(this::enumeration);
            if (declaration instanceof EnumeratorDeclaration enumerator) enumeration(enums.get(enumerator));
            if (expression instanceof ConversionExpression conversion)
                EnumSemantics.declaration(conversion.targetType()).ifPresent(this::enumeration);
            if (expression instanceof InitializationExpression initialization)
                initialization.type().flatMap(EnumSemantics::declaration).ifPresent(this::enumeration);
            expression.children().stream().filter(Expression.class::isInstance).map(Expression.class::cast).forEach(this::constant);
        }
        private void enumeration(EnumDeclaration owner) {
            if (owner == null || !visited.add(owner)) return;
            owner.enumerators().forEach(value -> value.value().ifPresent(this::constant));
        }
        /** Preserve constant syntax inside type arguments without treating the
         * argument's class or pointer pointee as an object-layout dependency. */
        private void argumentSyntax(Node node) {
            if (!argumentSyntaxSeen.add(node)) return;
            if (node instanceof Expression expression) { constant(expression); return; }
            if (node instanceof NamedType named) {
                named.alias().ifPresent(alias -> argumentSyntax(alias.type()));
                // Actual types and dependent partial headers may both omit
                // arguments completed without changing syntax. Retain defaults'
                // original const/sizeof/enum chains, including unselected headers,
                // before folding any literals.
                if (named.hasTemplateArguments()) {
                    Declaration bound = named.declaration().orElse(null);
                    ClassTemplateInstance instance = templates.get(bound);
                    ClassDeclaration primary = instance == null
                            ? bound instanceof ClassDeclaration declaration
                                && declaration.templateKind() == TemplateKind.PRIMARY ? declaration : null
                            : instance.origin();
                    if (primary != null)
                        for (int i = named.templateArguments().size(); i < primary.templateParameters().size(); i++)
                            primary.templateParameters().get(i).defaultArgument().ifPresent(this::argumentSyntax);
                }
            }
            node.children().forEach(this::argumentSyntax);
        }
        private void fields(ClassDeclaration owner) {
            if (visited.add(owner)) for (Declaration member : owner.members())
                if (member instanceof VariableDeclaration field && !field.modifiers().contains("static"))
                    field.type().ifPresent(this::type);
        }
        private void type(TypeNode type) {
            if (!visited.add(type)) return;
            retained.add(type);
            if (type instanceof QualifiedType qualified) type(qualified.baseType());
            else if (type instanceof ArrayType array) { type(array.elementType()); array.bound().ifPresent(this::constant); }
            else if (type instanceof ReferenceType reference) type(reference.referredType());
            else if (type instanceof MemberPointerType pointer) {
                // Preserve category aliases and written constant dependencies, but
                // neither owner bodies nor by-value layouts affect this storage.
                argumentSyntax(pointer.ownerType()); argumentSyntax(pointer.pointee());
            }
            else if (type instanceof NamedType named) {
                if (named.alias().isPresent()) type(named.alias().orElseThrow().type());
                else if (named.declaration().orElse(null) instanceof EnumDeclaration owner) enumeration(owner);
                else if (named.declaration().orElse(null) instanceof ClassDeclaration owner) {
                    ClassDeclaration definition = owner.definition().orElse(owner) instanceof ClassDeclaration value ? value : owner;
                    fields(definition);
                    ClassTemplateInstance instance = templates.get(definition);
                    if (instance != null) {
                        // Re-instantiation uses written selected fields and argument syntax,
                        // not just the current substituted copy. Preserve both dependencies.
                        fields(instance.selected());
                        selectionHeaders.getOrDefault(instance.declaration(), List.of(instance.selected()))
                                .forEach(header -> header.templateArguments().forEach(this::argumentSyntax));
                        instance.arguments().forEach(this::argumentSyntax);
                        instance.origin().templateParameters().forEach(parameter -> parameter.defaultArgument().ifPresent(this::argumentSyntax));
                        instance.requests().stream().filter(NamedType.class::isInstance).map(NamedType.class::cast)
                                .forEach(request -> request.templateArguments().forEach(this::argumentSyntax));
                        named.templateArguments().forEach(this::argumentSyntax);
                    }
                }
            }
            // Object and function pointer pointees do not determine pointer storage.
        }
    }

    private static void foldCpp(Project project, Node root, SourceFile file, ProjectEditor editor) {
        var profile = TypeSemantics.profile(file);
        var retained = offsetofDependencies(project, root, profile);
        root.walk(new NodeVisitor() {
            @Override public void visit(Expression expression) {
                if (expression instanceof IntegerLiteral || expression instanceof BooleanLiteral || retained.contains(expression)) return;
                ConstantEvaluator.evaluate(expression, profile).ifPresent(value -> {
                    if (value.type().spelling().equals("bool")) {
                        var literal = BooleanLiteral.create().setValue(value.number().signum() != 0);
                        expression.range().ifPresent(literal::setRange);
                        editor.replace(expression, literal.build());
                        return;
                    }
                    // A char/short value has no equivalent ordinary integer-literal spelling.
                    if (!Set.of("int", "unsigned int", "long", "unsigned long").contains(value.type().spelling())) return;
                    var literal = IntegerLiteral.create().setValue(value.number());
                    if (!value.type().spelling().equals("int")) literal.setCppType(value.type().spelling());
                    expression.range().ifPresent(literal::setRange);
                    editor.replace(expression, literal.build());
                });
            }
        });
    }
}
