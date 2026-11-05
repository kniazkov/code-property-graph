/* Coherent class-template instance graph construction. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** Internal construction boundary used by class-template analysis. */
public final class ClassTemplateInstances {
    private ClassTemplateInstances() {}
    public record Request(ClassDeclaration origin, ClassDeclaration selected, Node cause, SourceFile source,
            List<TemplateArgument> arguments, Map<Node, Node> substitutions, String identity) {}
    /** A detached syntax probe; publication reconnects its external references by edit lineage. */
    public static TemplateArgument substituteArgument(TemplateArgument argument, Map<Node, Node> substitutions) {
        return new EditContext().fork(argument, substitutions).edge(argument, TemplateArgument.class).build();
    }
    public static Project apply(Project project, List<Request> requests) {
        if (requests.isEmpty()) return project;
        var originals = new IdentityHashMap<Object, Node>();
        project.walk(new NodeVisitor() { @Override public void visit(Node n) { originals.put(n.editIdentity, n); }});
        var reconnected = new IdentityHashMap<Node, Node>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        java.util.function.Consumer<Node> reconnect = new java.util.function.Consumer<>() {
            @Override public void accept(Node n) {
                if (!seen.add(n)) return;
                Node original = originals.get(n.editIdentity);
                if (n instanceof Declaration && original != null && original != n) reconnected.put(n, original);
                n.children().forEach(this); n.references().forEach(this);
            }
        };
        requests.forEach(r -> { r.arguments().forEach(reconnect); r.substitutions().values().forEach(reconnect); });
        var context = new EditContext(reconnected, Set.of());
        var root = context.edit(project, Project.Builder.class);
        var instances = new LinkedHashMap<String, ClassTemplateInstance.Builder>();
        project.classTemplateInstances().forEach(i -> instances.put(i.identity(), context.edit(i, ClassTemplateInstance.Builder.class)));
        for (Request request : requests) {
            var instance = instances.get(request.identity());
            if (instance == null) {
                var fork = context.fork(request.selected(), request.substitutions());
                var concrete = fork.edit(request.selected(), ClassDeclaration.Builder.class);
                concrete.setTemplateParameters(List.of()).setTemplateKind(TemplateKind.NONE);
                concrete.clearDefinition(); concrete.clearPreviousDeclaration(); concrete.setTypeIdentity(request.identity());
                request.selected().walk(new NodeVisitor() {
                    @Override public void visit(NamedType n) { fork.edit(n, NamedType.Builder.class).setDependent(false); }
                    @Override public void visit(Expression n) { fork.copy(n).setDependent(false); }
                    @Override public void visit(CallExpression n) { fork.edit(n, CallExpression.Builder.class).clearTarget().setDependent(false); }
                    @Override public void visit(MemberExpression n) { ((MemberExpression.Builder<?, ?>)fork.copy(n)).clearTarget().setDependent(false); }
                });
                instance = new ClassTemplateInstance.Builder();
                instance.origin = context.edge(request.origin(), ClassDeclaration.class);
                instance.selected = context.edge(request.selected(), ClassDeclaration.class);
                instance.declaration = concrete; instance.source = context.edge(request.source(), SourceFile.class);
                instance.identity = request.identity();
                var arguments = new ArrayList<NodeBuilder<? extends TemplateArgument>>();
                for (var argument : request.arguments()) arguments.add(context.fork(argument, Map.of()).edge(argument, TemplateArgument.class));
                instance.arguments = arguments;
                var declarationArguments = new ArrayList<NodeBuilder<? extends TemplateArgument>>();
                for (var argument : request.arguments()) declarationArguments.add(context.fork(argument, Map.of()).edge(argument, TemplateArgument.class));
                concrete.setTemplateArguments(declarationArguments);
                root.addClassTemplateInstance(instance); instances.put(request.identity(), instance);
            }
            var cause = context.copy(request.cause());
            if (!instance.requests.contains(cause)) instance.requests.add(cause);
            if (request.cause() instanceof NameExpression name) context.edit(name, NameExpression.Builder.class).setTarget(instance.declaration);
            if (request.cause() instanceof NamedType type)
                context.edit(type, NamedType.Builder.class).clearAlias().clearDeclaration().clearResolutionSteps()
                    .setDeclaration(instance.declaration).setCompleteAtUse(true).setDependent(false);
        }
        var build = new BuildContext();
        Project result = build.build(root);
        project.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (!node.diagnostics().isEmpty()) build.copy(context.copy(node)).findings = node.diagnostics();
        }});
        return result;
    }
}
