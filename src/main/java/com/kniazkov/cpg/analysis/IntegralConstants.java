/* Snapshot-owned C++03 integral-constant facts, not general expression folding. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.Function;

/** Certifies declaration ownership, rejects nonstatic member values, and shares
 * successful values across repeated enum/const dependencies within one profile. */
final class IntegralConstants implements ConstantEvaluator.Context {
    private final PlatformProfile profile;
    private final Project project;
    private final Set<OffsetofExpression> activeOffsets = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<OffsetofExpression, PlatformProfile> offsetProfiles = new IdentityHashMap<>();
    private final Map<Node, SourceFile> layoutSources = new IdentityHashMap<>();
    final PodLayout.Guards layoutGuards = new PodLayout.Guards();
    private PodLayout.Query offsetQuery;
    private PodLayout.Query boundedQuery;
    private final Set<Declaration> activeLocals = Collections.newSetFromMap(new IdentityHashMap<>());
    boolean boundedLayoutActive() { return boundedQuery != null; }
    Optional<java.math.BigInteger> boundedSizeof(SizeofExpression expression,
            Function<Declaration, Optional<ConstantEvaluator.Value>> values) {
        return boundedQuery.sizeof(expression, values);
    }
    /** Temporarily shares this exact query for representation initializer dependencies. */
    <T> T inLayout(PodLayout.Query query, java.util.function.Supplier<T> action) {
        PodLayout.Query previous = boundedQuery; boundedQuery = query;
        try { return action.get(); } finally { boundedQuery = previous; }
    }
    /** Lexical enum overlays may predate entry into a bounded query. Recompute their
     * initializer type/value here; never reuse an unbounded cached numeric result. */
    Optional<ConstantEvaluator.Value> boundedLocal(Declaration declaration, ConstantEvaluator.Value value) {
        return !boundedLayoutActive() ? Optional.of(value)
                : declaration instanceof EnumeratorDeclaration enumerator ? lexicalEnumerator(enumerator) : Optional.empty();
    }
    /** Within an enum, only an actually used predecessor is a dependency. In
     * particular, an explicit initializer does not evaluate unrelated earlier values. */
    private Optional<ConstantEvaluator.Value> lexicalEnumerator(EnumeratorDeclaration enumerator) {
        if (!activeLocals.add(enumerator)) return Optional.empty();
        try {
            EnumDeclaration owner = owners.get(enumerator);
            if (owner == null) return Optional.empty();
            int index = owner.enumerators().indexOf(enumerator);
            if (enumerator.value().isPresent()) {
                var lexical = ConstantEvaluator.overlay(this, declaration -> declaration instanceof EnumeratorDeclaration earlier
                        && owners.get(earlier) == owner && owner.enumerators().indexOf(earlier) < index
                        ? lexicalEnumerator(earlier) : Optional.empty(), profile);
                return boundedLayoutActive() && profile.offsetof().map(model -> model.enumStorage() != null).orElse(false)
                        ? boundedQuery.lexicalValue(enumerator.value().orElseThrow(), lexical)
                        : ConstantEvaluator.integralConstant(enumerator.value().orElseThrow(), profile, lexical);
            }
            var previous = index == 0 ? Optional.<ConstantEvaluator.Value>empty()
                    : lexicalEnumerator(owner.enumerators().get(index - 1));
            if (index != 0 && previous.isEmpty()) return Optional.empty();
            var number = previous.map(value -> value.number().add(java.math.BigInteger.ONE)).orElse(java.math.BigInteger.ZERO);
            if (previous.isPresent() && previous.orElseThrow().type().contains(number))
                return Optional.of(new ConstantEvaluator.Value(number, previous.orElseThrow().type()));
            for (String spelling : List.of("int", "unsigned int", "long", "unsigned long")) {
                var type = ConstantEvaluator.integerType(spelling, profile);
                if (type.isPresent() && type.orElseThrow().contains(number))
                    return Optional.of(new ConstantEvaluator.Value(number, type.orElseThrow()));
            }
            return Optional.empty();
        } finally { activeLocals.remove(enumerator); }
    }
    @Override public IntegralConstants integralContext() { return this; }

    private final Map<EnumeratorDeclaration, EnumDeclaration> owners = new IdentityHashMap<>();
    private final Map<OffsetofExpression, EnumeratorDeclaration> offsetEnumerators = new IdentityHashMap<>();
    private final Set<Declaration> declarations = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<VariableDeclaration> nonstaticMembers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Declaration> active = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Declaration, ConstantEvaluator.Value> known = new IdentityHashMap<>();
    IntegralConstants(Project project, PlatformProfile profile) {
        this.profile = profile; this.project = project;
        if (project == null) return;
        for (var root : SourceContexts.roots(project)) root.node().walk(new NodeVisitor() {
            @Override public void visit(OffsetofExpression expression) {
                offsetProfiles.put(expression, TypeSemantics.profile(root.source())); layoutSources.put(expression, root.source());
            }
            @Override public void visit(ClassDeclaration owner) { layoutSources.put(owner, root.source()); }
        });
        project.walk(new NodeVisitor() {
            @Override public void visit(Declaration declaration) { declarations.add(declaration); }
            @Override public void visit(EnumDeclaration enumeration) {
                visit((Declaration) enumeration);
                enumeration.enumerators().forEach(value -> {
                    owners.put(value, enumeration);
                    value.walk(new NodeVisitor() { @Override public void visit(OffsetofExpression expression) { offsetEnumerators.put(expression, value); } });
                });
            }
            @Override public void visit(ClassDeclaration owner) {
                visit((Declaration) owner);
                owner.members().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                        .filter(field -> !field.modifiers().contains("static")).forEach(nonstaticMembers::add);
            }
        });
    }
    Optional<PodLayout.Failure> pragmaFailure(Node node) {
        SourceFile source = layoutSources.get(node);
        var violation = source == null ? node.provenance().map(TokenProvenance::unit)
                .flatMap(com.kniazkov.cpg.build.OffsetofSourcePolicy::violation)
                .or(() -> com.kniazkov.cpg.build.OffsetofSourcePolicy.syntaxViolation(node))
                : com.kniazkov.cpg.build.OffsetofSourcePolicy.violation(source);
        return violation.map(detail -> new PodLayout.Failure(PodLayout.Reason.UNSUPPORTED_PRAGMA,
                "Selected offsetof layout does not certify active pragmas other than #pragma once: " + detail));
    }

    PodLayout.Result<PodLayout.Offset> offsetofResult(OffsetofExpression expression) {
        return offsetofResult(expression, this);
    }
    PodLayout.Result<PodLayout.Offset> offsetofResult(OffsetofExpression expression,
            Function<Declaration, Optional<ConstantEvaluator.Value>> values) {
        if (profile.offsetof().isEmpty() || project != null && !profile.equals(offsetProfiles.get(expression)))
            return PodLayout.failure(PodLayout.Reason.UNKNOWN_LAYOUT, "offsetof requires the enclosing translation unit's selected offsetof capability");
        var pragma = pragmaFailure(expression);
        if (pragma.isPresent()) return new PodLayout.Result<>(Optional.empty(), pragma);
        if (!activeOffsets.add(expression))
            return PodLayout.failure(PodLayout.Reason.RECURSIVE_TYPE, "Recursive offsetof constant/layout dependency");
        if (offsetQuery == null) offsetQuery = new PodLayout.Query(project, profile, this,
                profile.offsetof().orElseThrow().supportedObjectBytes());
        PodLayout.Query previousQuery = boundedQuery;
        boundedQuery = offsetQuery;
        try {
            EnumeratorDeclaration enclosing = offsetEnumerators.get(expression);
            if (values == this && enclosing != null) {
                EnumDeclaration owner = owners.get(enclosing);
                int index = owner.enumerators().indexOf(enclosing);
                values = ConstantEvaluator.overlay(this, declaration -> declaration instanceof EnumeratorDeclaration earlier
                        && owners.get(earlier) == owner && owner.enumerators().indexOf(earlier) < index
                        ? lexicalEnumerator(earlier) : Optional.empty(), profile);
            }
            List<PodLayout.Step> path = expression.steps().stream().map(step -> step instanceof OffsetofExpression.Member member
                    ? (PodLayout.Step) new PodLayout.Member(member.name())
                    : new PodLayout.Index(((OffsetofExpression.Index) step).expression())).toList();
            return offsetQuery.offset(expression.type(), path, values);
        } finally { boundedQuery = previousQuery; activeOffsets.remove(expression); }
    }
    Optional<ConstantEvaluator.Value> offsetof(OffsetofExpression expression,
            Function<Declaration, Optional<ConstantEvaluator.Value>> values) {
        return offsetofResult(expression, values).value().flatMap(result -> ConstantEvaluator.integerType(profile.sizeType(), profile)
                .filter(type -> type.contains(result.offsetBytes())).map(type -> new ConstantEvaluator.Value(result.offsetBytes(), type)));
    }

    Optional<ConstantEvaluator.Value> evaluate(Expression expression) {
        return ConstantEvaluator.integralConstant(expression, profile, this);
    }
    @Override public Optional<ConstantEvaluator.Value> apply(Declaration declaration) {
        if (!declarations.contains(declaration)) return Optional.empty();
        if (!boundedLayoutActive() && known.containsKey(declaration)) return Optional.of(known.get(declaration));
        if (!active.add(declaration)) return Optional.empty();
        try {
            var value = compute(declaration);
            if (!boundedLayoutActive()) value.ifPresent(result -> known.put(declaration, result));
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
