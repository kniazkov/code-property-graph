/* Conservative effects when a library declaration has no behavioral model. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

final class LibraryCallEffects {
    private LibraryCallEffects() {}
    static boolean unknown(Expression expression) {
        var selected = expression.operatorTarget();
        if (selected.isEmpty() && expression instanceof CallExpression call) selected = call.target();
        return selected.map(LibraryCallEffects::unknown).orElse(false);
    }
    static boolean unknown(CallableDeclaration target) {
        return LibraryDeclarations.isLibrary(target) || target.templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent();
    }
    /** Receiver and reference/pointer arguments expose object state to opaque code. */
    static List<Expression> exposed(CallExpression call) {
        var result = new ArrayList<Expression>();
        if (call.callee() instanceof MemberExpression member) result.add(member.receiver());
        var target = call.target().orElse(null);
        if (target == null) return List.copyOf(result);
        var arguments = new ArrayList<Expression>(call.arguments());
        call.defaultArguments().forEach(argument -> arguments.add(argument.expression()));
        for (int i = 0; i < arguments.size(); i++) {
            TypeNode type = i < target.parameters().size() ? target.parameters().get(i).type().orElse(null) : null;
            if (type != null) type = TypeSemantics.unqualified(type);
            if (type instanceof ReferenceType || type instanceof PointerType || type instanceof ArrayType
                    || arguments.get(i) instanceof AddressOfExpression) result.add(arguments.get(i));
        }
        return List.copyOf(result);
    }
    static Set<Declaration> roots(Expression expression) {
        var roots = Collections.newSetFromMap(new IdentityHashMap<Declaration,Boolean>());
        roots(expression, roots); return roots;
    }
    private static void roots(Expression expression, Set<Declaration> out) {
        if (expression instanceof NameExpression name) name.target().filter(d -> d instanceof VariableDeclaration || d instanceof ParameterDeclaration).ifPresent(out::add);
        else if (expression instanceof ConversionExpression conversion) roots(conversion.operand(),out);
        else if (expression instanceof MemberExpression member) roots(member.receiver(),out);
        else if (expression instanceof AddressOfExpression address) roots(address.operand(),out);
        else if (expression instanceof DereferenceExpression dereference) roots(dereference.operand(),out);
        else if (expression instanceof IndexExpression index) roots(index.left(),out);
        else if (expression instanceof AssignmentExpression assignment) roots(assignment.target(),out);
    }
    /** Conservative aliases: address-exposed storage and referenced nonlocal data may remain reachable. */
    static Set<Declaration> accessible(CallableDeclaration function, com.kniazkov.cpg.build.PlatformProfile profile) {
        var local = Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        function.walk(new NodeVisitor() { @Override public void visit(Node node) { local.add(node); } });
        var result = Collections.newSetFromMap(new IdentityHashMap<Declaration,Boolean>());
        var unevaluated = Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        function.walk(new NodeVisitor() {
            private void mark(Node root) { root.walk(new NodeVisitor() { @Override public void visit(Node node) { unevaluated.add(node); } }); }
            @Override public void visit(SizeofExpression size) { mark(size); }
            @Override public void visit(TypeidExpression typeid) { if (TypeSemantics.typeidOperandUnevaluated(typeid, profile)) mark(typeid); }
        });
        function.walk(new NodeVisitor() {
            @Override public void visit(AddressOfExpression address) { if (!unevaluated.contains(address)) result.addAll(roots(address.operand())); }
            @Override public void visit(NameExpression name) {
                if (unevaluated.contains(name)) return;
                name.target().filter(VariableDeclaration.class::isInstance).filter(d -> !local.contains(d)).ifPresent(result::add);
            }
            @Override public void visit(VariableDeclaration declaration) {
                if (unevaluated.contains(declaration)) return;
                if (declaration.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent())
                    declaration.initializer().ifPresent(value -> result.addAll(roots(value)));
            }
        });
        return result;
    }
}
