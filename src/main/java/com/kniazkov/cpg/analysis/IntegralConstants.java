/* Snapshot-owned C++03 integral-constant facts, not general expression folding. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.Function;

/** Certifies declaration ownership, rejects nonstatic member values, and shares
 * successful values across repeated enum/const dependencies within one profile. */
final class IntegralConstants implements Function<Declaration, Optional<ConstantEvaluator.Value>> {
    private final PlatformProfile profile;
    private final Map<EnumeratorDeclaration, EnumDeclaration> owners = new IdentityHashMap<>();
    private final Set<Declaration> declarations = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<VariableDeclaration> nonstaticMembers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Declaration> active = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Declaration, ConstantEvaluator.Value> known = new IdentityHashMap<>();
    IntegralConstants(Project project, PlatformProfile profile) {
        this.profile = profile;
        project.walk(new NodeVisitor() {
            @Override public void visit(Declaration declaration) { declarations.add(declaration); }
            @Override public void visit(EnumDeclaration enumeration) {
                visit((Declaration) enumeration);
                enumeration.enumerators().forEach(value -> owners.put(value, enumeration));
            }
            @Override public void visit(ClassDeclaration owner) {
                visit((Declaration) owner);
                owner.members().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                        .filter(field -> !field.modifiers().contains("static")).forEach(nonstaticMembers::add);
            }
        });
    }
    Optional<ConstantEvaluator.Value> evaluate(Expression expression) {
        return ConstantEvaluator.integralConstant(expression, profile, this);
    }
    @Override public Optional<ConstantEvaluator.Value> apply(Declaration declaration) {
        if (!declarations.contains(declaration)) return Optional.empty();
        if (known.containsKey(declaration)) return Optional.of(known.get(declaration));
        if (!active.add(declaration)) return Optional.empty();
        try {
            var value = compute(declaration);
            value.ifPresent(result -> known.put(declaration, result));
            return value;
        } finally { active.remove(declaration); }
    }
    private Optional<ConstantEvaluator.Value> compute(Declaration declaration) {
        if (declaration instanceof EnumeratorDeclaration enumerator) {
            EnumDeclaration owner = owners.get(enumerator);
            if (owner == null || !active.add(owner)) return Optional.empty();
            try {
                var values = EnumSemantics.values(owner, profile, this);
                var type = EnumSemantics.underlying(owner, profile, this).flatMap(value -> EnumSemantics.promoteUnderlying(value, profile));
                return values.flatMap(map -> Optional.ofNullable(map.get(enumerator)))
                        .flatMap(value -> type.map(target -> new ConstantEvaluator.Value(value.number(), target)));
            } finally { active.remove(owner); }
        }
        if (!(declaration instanceof VariableDeclaration variable) || nonstaticMembers.contains(variable)
                || variable.type().isEmpty() || variable.initializer().isEmpty()
                || TypeSemantics.qualifiers(variable.type().get()) != 1
                || TypeSemantics.unqualified(variable.type().get()) instanceof ReferenceType) return Optional.empty();
        var type = integerType(variable.type().get(), false);
        return type.flatMap(target -> evaluate(variable.initializer().get())
                .flatMap(value -> ConstantEvaluator.cast(value.number(), target, profile)));
    }
    Optional<ConstantEvaluator.IntegerType> integerType(TypeNode type, boolean promote) {
        var enumeration = EnumSemantics.declaration(type);
        if (enumeration.isPresent()) return EnumSemantics.underlying(enumeration.get(), profile, this)
                .flatMap(value -> EnumSemantics.promoteUnderlying(value, profile));
        var integer = TypeSemantics.integerType(type, profile);
        return promote ? integer.flatMap(value -> ConstantEvaluator.promote(value, profile)) : integer;
    }
}
