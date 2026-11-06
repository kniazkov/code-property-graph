/* C++03 type facts evaluated against the translation unit's target. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/** Conservative scalar, pointer and array layout. No host ABI is consulted.
 * Function, void, incomplete, member-pointer and unmodelled class layouts remain unknown. */
public final class TypeSemantics {
    /** Dependency is structural; unknown concrete names are not assumed to be dependent. */
    public static boolean dependent(Node node) {
        if (node instanceof NamedType type && (type.dependent() || type.declaration().orElse(null) instanceof TemplateParameterDeclaration)) return true;
        if (node instanceof NameExpression name && name.target().isPresent()) {
            Declaration target = name.target().get();
            if (target instanceof TemplateParameterDeclaration) return true;
            if (target instanceof ParameterDeclaration parameter && parameter.type().map(TypeSemantics::dependent).orElse(false)) return true;
            if (target instanceof VariableDeclaration variable && variable.type().map(TypeSemantics::dependent).orElse(false)) return true;
        }
        return node.children().stream().anyMatch(TypeSemantics::dependent);
    }

    private TypeSemantics() {}

    /** The effective profile belongs to the TU occurrence, not to the physical header path. */
    public static PlatformProfile profile(SourceFile file) {
        return file.provenance().map(TokenProvenance::unit).map(Preprocessing::configuration)
                .map(com.kniazkov.cpg.build.Build.TranslationUnit::platform).orElseGet(PlatformProfile::defaults);
    }

    public static TypeRepresentation describe(TypeNode type, PlatformProfile profile) {
        Objects.requireNonNull(type); Objects.requireNonNull(profile);
        var facts = facts(type, profile, Collections.newSetFromMap(new IdentityHashMap<>()));
        return new TypeRepresentation(type.canonicalIdentity(), profile.stableIdentity(),
                facts.size.map(value -> value.multiply(BigInteger.valueOf(profile.bitsPerByte()))),
                facts.size, facts.alignment, facts.range);
    }

    /** Result is measured in target bytes. Empty means no justified sizeof value. */
    public static Optional<BigInteger> sizeof(TypeNode type, PlatformProfile profile) {
        return describe(type, profile).sizeBytes();
    }

    public static Optional<BigInteger> sizeof(SizeofExpression expression, PlatformProfile profile) {
        // Allocation result signatures intentionally detach class bindings. The written
        // allocated type is still available here, without evaluating or copying new.
        if (expression.operand().orElse(null) instanceof DereferenceExpression dereference
                && dereference.operand() instanceof NewExpression allocation) {
            TypeNode allocated = unqualified(allocation.type());
            return sizeof(allocated instanceof ArrayType array ? array.elementType() : allocation.type(), profile);
        }
        return expression.type().or(() -> expression.operand().flatMap(value -> expressionType(value, profile)))
                .flatMap(type -> sizeof(type, profile));
    }

    private record Facts(Optional<BigInteger> size, OptionalInt alignment,
            Optional<PlatformProfile.IntegerRange> range) {}
    private static final Facts UNKNOWN = new Facts(Optional.empty(), OptionalInt.empty(), Optional.empty());

    private static Facts facts(TypeNode type, PlatformProfile profile, Set<TypeNode> active) {
        if (!active.add(type)) return UNKNOWN;
        try {
            if (type instanceof QualifiedType qualified) return facts(qualified.baseType(), profile, active);
            if (type instanceof ReferenceType reference) return facts(reference.referredType(), profile, active);
            if (type instanceof NamedType name) {
                if (name.alias().isPresent()) return facts(name.alias().orElseThrow().type(), profile, active);
                if (name.fundamentalKind().isEmpty()) {
                    var owner = ClassSemantics.objectClass(name);
                    return owner.flatMap(value -> layout(value, profile, active))
                            .map(value -> new Facts(Optional.of(value.sizeBytes()), OptionalInt.of(value.alignmentBits()), Optional.empty()))
                            .orElse(UNKNOWN);
                }
                if (name.fundamentalKind().orElseThrow() == FundamentalKind.VOID) return UNKNOWN;
                String spelling = name.fundamentalKind().orElseThrow().spelling();
                return scalar(spelling, profile);
            }
            if (type instanceof PointerType) return scalar("pointer", profile);
            if (type instanceof ArrayType array) {
                var element = facts(array.elementType(), profile, active);
                var count = array.bound().flatMap(bound -> ConstantEvaluator.evaluate(bound, profile))
                        .map(ConstantEvaluator.Value::number).filter(n -> n.signum() > 0);
                return new Facts(element.size.flatMap(size -> count.map(size::multiply)), element.alignment, Optional.empty());
            }
            return UNKNOWN;
        } finally { active.remove(type); }
    }

    private static Facts scalar(String type, PlatformProfile profile) {
        OptionalInt size = profile.sizeBytes(type);
        return new Facts(size.isPresent() ? Optional.of(BigInteger.valueOf(size.getAsInt())) : Optional.empty(),
                profile.alignmentBits(type), profile.integerRange(type));
    }

    /** Explicit sequential plain-aggregate rule. This is not a C++ class/vtable/base-class ABI.
     * Callers must supply only ordinary non-static object fields, in declaration order. */
    public record AggregateLayout(List<BigInteger> offsets, BigInteger sizeBytes, int alignmentBits) {
        public AggregateLayout { offsets = List.copyOf(offsets); }
    }
    public static Optional<AggregateLayout> layout(List<? extends TypeNode> fields, PlatformProfile profile) {
        return layout(fields, profile, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    /** Only complete plain aggregates have a representation under this policy. Unions,
     * bases, bit-fields, references and non-aggregates need ABI rules we do not model. */
    public static Optional<AggregateLayout> layout(ClassDeclaration owner, PlatformProfile profile) {
        return layout(owner, profile, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static Optional<AggregateLayout> layout(ClassDeclaration owner, PlatformProfile profile, Set<TypeNode> active) {
        if (LibraryDeclarations.isLibrary(owner) || !owner.templateParameters().isEmpty()) return Optional.empty();
        if (!owner.isDefinition() || owner.keyword().equals("union") || !ClassSemantics.aggregate(owner)) return Optional.empty();
        var fields = owner.members().stream().filter(VariableDeclaration.class::isInstance)
                .map(VariableDeclaration.class::cast).filter(field -> !field.modifiers().contains("static")).toList();
        if (fields.stream().anyMatch(field -> field.bitWidth().isPresent() || field.type().isEmpty())) return Optional.empty();
        return layout(fields.stream().map(field -> field.type().orElseThrow()).toList(), profile, active);
    }
    private static Optional<AggregateLayout> layout(List<? extends TypeNode> fields, PlatformProfile profile, Set<TypeNode> active) {
        if (!profile.aggregateLayout().equals("sequential") || profile.fieldAlignment().equals("unknown")
                || !profile.aggregateAlignment().equals("maximum-field") || !profile.tailPadding().equals("alignment")) return Optional.empty();
        BigInteger offset = BigInteger.ZERO;
        int maximumAlignment = profile.bitsPerByte();
        var offsets = new ArrayList<BigInteger>();
        for (TypeNode field : fields) {
            // References require an ABI representation, unlike sizeof(reference).
            if (unqualified(field) instanceof ReferenceType) return Optional.empty();
            var facts = facts(field, profile, active);
            if (facts.size.isEmpty() || !profile.fieldAlignment().equals("packed1") && facts.alignment.isEmpty()) return Optional.empty();
            int alignment = profile.fieldAlignment().equals("packed1") ? profile.bitsPerByte() : facts.alignment.orElseThrow();
            if (alignment % profile.bitsPerByte() != 0) return Optional.empty();
            offset = align(offset, BigInteger.valueOf(alignment / profile.bitsPerByte()));
            offsets.add(offset);
            offset = offset.add(facts.size.orElseThrow());
            maximumAlignment = Math.max(maximumAlignment, alignment);
        }
        return Optional.of(new AggregateLayout(offsets,
                align(offset.max(BigInteger.ONE), BigInteger.valueOf(maximumAlignment / profile.bitsPerByte())), maximumAlignment));
    }
    private static BigInteger align(BigInteger size, BigInteger alignment) {
        return size.add(alignment).subtract(BigInteger.ONE).divide(alignment).multiply(alignment);
    }

    /** Explicit unsigned size_t policy; pointer widths do not imply an integer rank. */
    public static Optional<String> sizeType(PlatformProfile profile) {
        return profile.integerRange(profile.sizeType()).isPresent() ? Optional.of(profile.sizeType()) : Optional.empty();
    }

    static TypeNode unqualified(TypeNode type) {
        Set<TypeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (seen.add(type)) {
            if (type instanceof QualifiedType qualifier) type = qualifier.baseType();
            else if (type instanceof NamedType name && name.alias().isPresent()) type = name.alias().orElseThrow().type();
            else return type;
        }
        return type;
    }

    /** Semantic result type before context-dependent decay. References denote the referred object. */
    public static Optional<TypeNode> expressionType(Expression expression, PlatformProfile profile) {
        return expressionType(expression, profile, ignored -> Optional.empty());
    }
    static Optional<TypeNode> expressionType(Expression expression, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> constants) {
        if (expression instanceof InitializationExpression initialization) return initialization.type();
        return rawExpressionType(expression, profile, constants).map(TypeSemantics::withoutReference);
    }
    static TypeNode withoutReference(TypeNode type) {
        var plain = unqualified(type);
        return plain instanceof ReferenceType reference ? reference.referredType() : type;
    }
    private static Optional<TypeNode> rawExpressionType(Expression expression, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> constants) {
        if (expression instanceof ConversionExpression conversion) return Optional.of(conversion.targetType());
        if(expression instanceof DereferenceExpression dereference) {
            var iterator=expressionType(dereference.operand(),profile).flatMap(LibraryIteratorSemantics::info);
            if(iterator.isPresent())return Optional.of(iterator.get().referenceType());
        }
        if (expression.operatorTarget().isPresent()) return expression.operatorTarget().orElseThrow().returnType();
        if (!expression.operatorCandidates().isEmpty()) return Optional.empty();
        if (expression instanceof IntegerLiteral literal)
            return ConstantEvaluator.literal(literal, profile).map(value -> named(value.type().spelling()));
        if (expression instanceof BooleanLiteral) return Optional.of(named("bool"));
        if (expression instanceof CharacterLiteral literal) return Optional.of(named(literal.cppType()));
        if (expression instanceof FloatingLiteral literal) return Optional.of(named(literal.cppType()));
        if (expression instanceof StringLiteral literal) return Optional.of(ArrayType.create()
                .setElementType(QualifiedType.create().setBaseType(NamedType.create().setName(literal.wide() ? "wchar_t" : "char")).setConst(true))
                .setBound(IntegerLiteral.create().setValue(literal.codeUnits().size() + 1)).build());
        if (expression instanceof ConversionExpression conversion) return Optional.of(conversion.targetType());
        if (expression instanceof SizeofExpression) return sizeType(profile).map(TypeSemantics::named);
        if (expression instanceof NewExpression allocation) {
            TypeNode allocated = unqualified(allocation.type());
            TypeNode pointee = allocated instanceof ArrayType array ? array.elementType() : allocation.type();
            return Optional.of(PointerType.create().setPointee(signatureType(pointee)).build());
        }
        if (expression instanceof DeleteExpression) return Optional.of(named("void"));
        if (expression instanceof TypeidExpression) return Optional.of(QualifiedType.create()
                .setBaseType(NamedType.create().setName("std::type_info")).setConst(true).build());
        if (expression instanceof NameExpression name) return declarationType(name.target());
        if (expression instanceof MemberExpression member) {
            var type=declarationType(member.target());
            // Abstract iterator arrow access carries the element's constness without
            // inventing an operator-> implementation or a physical pointer type.
            if(member instanceof PointerMemberExpression && member.target().orElse(null) instanceof VariableDeclaration field && !field.modifiers().contains("mutable")
                    && !field.modifiers().contains("static") && type.filter(t->unqualified(t) instanceof ReferenceType).isEmpty()
                    && expressionType(member.receiver(),profile).flatMap(LibraryIteratorSemantics::info)
                        .filter(LibraryIteratorSemantics.Info::readOnly).isPresent())
                return type.map(t->QualifiedType.create().setBaseType(t.edit()).setConst(true).build());
            return type;
        }
        if (expression instanceof CallExpression call) return call.target().flatMap(CallableDeclaration::returnType)
                .or(() -> callType(call, profile).map(FunctionType::returnType));
        if (expression instanceof AssignmentExpression assignment) return expressionType(assignment.target(), profile, constants);
        if (expression instanceof CompoundAssignmentExpression assignment) {
            var type=expressionType(assignment.target(),profile);
            var iterator=type.flatMap(LibraryIteratorSemantics::info);
            if(iterator.isPresent() && (!iterator.get().randomAccess() || !Set.of("+=","-=").contains(assignment.operator())
                    || expressionType(assignment.value(),profile).flatMap(t->integerType(t,profile)).isEmpty()))return Optional.empty();
            return type;
        }
        if (expression instanceof ConditionalExpression conditional) {
            var a = expressionType(conditional.thenExpression(), profile, constants);
            var b = expressionType(conditional.elseExpression(), profile, constants);
            if (a.isEmpty() || b.isEmpty()) return Optional.empty();
            if (a.get().canonicalIdentity().equals(b.get().canonicalIdentity())) return a;
            var left = integralOrEnum(a.get(), profile) ? promoted(conditional.thenExpression(), profile, constants) : a;
            var right = integralOrEnum(b.get(), profile) ? promoted(conditional.elseExpression(), profile, constants) : b;
            return left.isPresent() && right.isPresent() ? commonArithmeticType(left.get(), right.get(), profile) : Optional.empty();
        }
        if (expression instanceof AddressOfExpression address) {
            // A directly declared qualified non-static member has a member-pointer type.
            // Keep inherited/unknown owners conservative; no ABI layout is inferred.
            if (address.operand() instanceof ScopeMemberExpression member
                    && member.target().filter(target -> target instanceof VariableDeclaration variable
                            && !variable.modifiers().contains("static") || target instanceof MethodDeclaration method
                            && !method.modifiers().contains("static")).isPresent()) {
                Declaration owner = member.receiver() instanceof NameExpression name ? name.target().orElse(null)
                        : member.receiver() instanceof ScopeMemberExpression scope ? scope.target().orElse(null) : null;
                if (!(owner instanceof ClassDeclaration klass) || !klass.members().contains(member.target().orElseThrow())) return Optional.empty();
                return declarationType(member.target()).map(type -> MemberPointerType.create()
                        .setOwnerType(NamedType.create().setName(klass.name()).setDeclaration(klass.edit()))
                        .setPointee(signatureType(type)).build());
            }
            return expressionType(address.operand(), profile, constants).map(type -> PointerType.create().setPointee(type.edit()).build());
        }
        if (expression instanceof DereferenceExpression dereference)
            return expressionType(dereference.operand(), profile, constants).flatMap(type -> LibraryIteratorSemantics.info(type)
                    .map(LibraryIteratorSemantics.Info::referenceType).or(() -> pointee(decay(type))));
        if (expression instanceof LogicalNotExpression logical) return expressionType(logical.operand(), profile, constants)
                .map(TypeSemantics::decay).filter(type -> scalar(type, profile)).map(type -> named("bool"));
        if (expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression)
            return expressionType(((UnaryExpression) expression).operand(), profile, constants);
        if (expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression)
            return expressionType(((UnaryExpression) expression).operand(), profile, constants).map(TypeSemantics::unqualified);
        if (expression instanceof BitwiseNotExpression bitwise)
            return promoted(bitwise.operand(), profile, constants).filter(type -> integerType(type, profile).isPresent());
        if (expression instanceof UnaryPlusExpression || expression instanceof NegateExpression)
            return promoted(((UnaryExpression) expression).operand(), profile, constants);
        if (expression instanceof BinaryExpression binary) {
            if (binary instanceof CommaExpression) return expressionType(binary.right(), profile, constants);
            var left = expressionType(binary.left(), profile, constants).map(TypeSemantics::decay);
            var right = expressionType(binary.right(), profile, constants).map(TypeSemantics::decay);
            if (left.isEmpty() || right.isEmpty()) return Optional.empty();
            TypeNode a = left.get(), b = right.get();
            var iteratorA=LibraryIteratorSemantics.info(a);
            var iteratorB=LibraryIteratorSemantics.info(b);
            if (binary instanceof IndexExpression) {
                if(iteratorA.isPresent() && iteratorA.get().randomAccess() && integerType(b,profile).isPresent())return Optional.of(iteratorA.get().referenceType());
                if (integerType(b, profile).isPresent()) return pointee(a);
                if (integerType(a, profile).isPresent()) return pointee(b);
                return Optional.empty();
            }
            if (binary instanceof DotMemberPointerExpression || binary instanceof ArrowMemberPointerExpression)
                return unqualified(b) instanceof MemberPointerType member ? Optional.of(member.pointee()) : Optional.empty();
            if(isComparison(binary) && (binary instanceof EqualExpression || binary instanceof NotEqualExpression || iteratorA.filter(LibraryIteratorSemantics.Info::randomAccess).isPresent()) && iteratorA.isPresent() && iteratorB.isPresent() && iteratorA.get().compatible(iteratorB.get()))
                return Optional.of(named("bool"));
            if (binary instanceof LogicalAndExpression || binary instanceof LogicalOrExpression || isComparison(binary))
                return scalar(a, profile) && scalar(b, profile) ? Optional.of(named("bool")) : Optional.empty();
            if (binary instanceof ShiftLeftExpression || binary instanceof ShiftRightExpression)
                return integralOrEnum(a, profile) && integralOrEnum(b, profile) ? promoted(binary.left(), profile, constants) : Optional.empty();
            if (binary instanceof AddExpression || binary instanceof SubtractExpression) {
                if(iteratorA.isPresent() && iteratorA.get().randomAccess() && integerType(b,profile).isPresent())return Optional.of(a);
                if(binary instanceof AddExpression && iteratorB.isPresent() && iteratorB.get().randomAccess() && integerType(a,profile).isPresent())return Optional.of(b);
                if(binary instanceof SubtractExpression && iteratorA.isPresent() && iteratorA.get().randomAccess() && iteratorB.isPresent() && iteratorA.get().compatible(iteratorB.get()))
                    return iteratorA.get().iterator().iteratorDifferenceType();
                if (pointee(a).isPresent() && integerType(b, profile).isPresent()) return Optional.of(a);
                if (binary instanceof AddExpression && integerType(a, profile).isPresent() && pointee(b).isPresent()) return Optional.of(b);
                // ptrdiff_t is not specified by the profile: do not guess its integer type.
            }
            if (binary instanceof RemainderExpression || binary instanceof BitwiseAndExpression
                    || binary instanceof BitwiseOrExpression || binary instanceof BitwiseXorExpression) {
                if (!integralOrEnum(a, profile) || !integralOrEnum(b, profile)) return Optional.empty();
            }
            var promotedLeft = integralOrEnum(a, profile) ? promoted(binary.left(), profile, constants) : Optional.of(a);
            var promotedRight = integralOrEnum(b, profile) ? promoted(binary.right(), profile, constants) : Optional.of(b);
            return promotedLeft.isPresent() && promotedRight.isPresent()
                    ? commonArithmeticType(promotedLeft.get(), promotedRight.get(), profile) : Optional.empty();
        }
        return Optional.empty();
    }
    /** Without a complete polymorphic-class analysis, class operands remain potentially
     * evaluated. Known non-class operands and written type operands are unevaluated. */
    public static boolean typeidOperandUnevaluated(TypeidExpression expression, PlatformProfile profile) {
        if (expression.type().isPresent()) return true;
        return expression.operand().flatMap(operand -> expressionType(operand, profile)).map(TypeSemantics::unqualified)
                .map(type -> type instanceof PointerType || type instanceof MemberPointerType || type instanceof ArrayType
                        || type instanceof FunctionType || type instanceof NamedType name && name.fundamentalKind().isPresent())
                .orElse(false);
    }
    public static ValueCategory valueCategory(Expression expression, PlatformProfile profile) {
        if (expression instanceof ConversionExpression conversion)
            return unqualified(conversion.targetType()) instanceof ReferenceType ? ValueCategory.LVALUE : ValueCategory.RVALUE;
        if (expression.operatorTarget().isPresent()) return expression.operatorTarget().orElseThrow().returnType()
                .map(type -> unqualified(type) instanceof ReferenceType ? ValueCategory.LVALUE : ValueCategory.RVALUE)
                .orElse(ValueCategory.UNKNOWN);
        if (!expression.operatorCandidates().isEmpty()) return ValueCategory.UNKNOWN;
        if (expression instanceof TypeidExpression) return ValueCategory.LVALUE;
        if (expression instanceof StringLiteral || expression instanceof DereferenceExpression || expression instanceof IndexExpression
                || expression instanceof AssignmentExpression || expression instanceof CompoundAssignmentExpression
                || expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression) return ValueCategory.LVALUE;
        if (expression instanceof NameExpression name)
            return name.target().map(target -> target instanceof EnumeratorDeclaration ? ValueCategory.RVALUE
                    : target instanceof VariableDeclaration || target instanceof ParameterDeclaration || target instanceof CallableDeclaration
                    ? ValueCategory.LVALUE : ValueCategory.UNKNOWN).orElse(ValueCategory.UNKNOWN);
        if (expression instanceof MemberExpression member) {
            if (member.target().isEmpty()) return ValueCategory.UNKNOWN;
            if (member instanceof PointerMemberExpression || member instanceof ScopeMemberExpression) return ValueCategory.LVALUE;
            return valueCategory(member.receiver(), profile);
        }
        if (expression instanceof CallExpression call) return callType(call, profile).map(FunctionType::returnType)
                .map(type -> unqualified(type) instanceof ReferenceType ? ValueCategory.LVALUE : ValueCategory.RVALUE).orElse(ValueCategory.UNKNOWN);
        if (expression instanceof ConversionExpression conversion)
            return unqualified(conversion.targetType()) instanceof ReferenceType ? ValueCategory.LVALUE : ValueCategory.RVALUE;
        if (expression instanceof CommaExpression comma) return valueCategory(comma.right(), profile);
        if (expression instanceof ConditionalExpression conditional) {
            var a = expressionType(conditional.thenExpression(), profile); var b = expressionType(conditional.elseExpression(), profile);
            if (a.isPresent() && b.isPresent() && a.get().canonicalIdentity().equals(b.get().canonicalIdentity())
                    && valueCategory(conditional.thenExpression(), profile) == ValueCategory.LVALUE
                    && valueCategory(conditional.elseExpression(), profile) == ValueCategory.LVALUE) return ValueCategory.LVALUE;
        }
        if (expression instanceof DotMemberPointerExpression member) return valueCategory(member.left(), profile);
        if (expression instanceof ArrowMemberPointerExpression) return ValueCategory.LVALUE;
        return expressionType(expression, profile).isPresent() ? ValueCategory.RVALUE : ValueCategory.UNKNOWN;
    }
    static boolean isComparison(BinaryExpression binary) {
        return binary instanceof EqualExpression || binary instanceof NotEqualExpression || binary instanceof LessExpression
                || binary instanceof LessOrEqualExpression || binary instanceof GreaterExpression || binary instanceof GreaterOrEqualExpression;
    }
    static boolean scalar(TypeNode type, PlatformProfile profile) {
        return integralOrEnum(type, profile) || floatingRank(type) > 0 || unqualified(type) instanceof PointerType
                || unqualified(type) instanceof OpaqueLibraryType opaque && (opaque.category()==OpaqueLibraryType.Category.INTEGER || opaque.category()==OpaqueLibraryType.Category.ARITHMETIC);
    }
    static int floatingRank(TypeNode type) {
        type = unqualified(type);
        return type instanceof NamedType name ? switch (name.fundamentalKind().map(FundamentalKind::spelling).orElse("")) {
            case "float" -> 1; case "double" -> 2; case "long double" -> 3; default -> 0;
        } : 0;
    }
    static boolean integralOrEnum(TypeNode type, PlatformProfile profile) {
        return integerType(type, profile).isPresent() || EnumSemantics.declaration(type).isPresent();
    }
    /** Expression-aware promotions preserve direct bit-field ranges without importing later DRs. */
    public static Optional<TypeNode> promoted(Expression expression, PlatformProfile profile) {
        return promoted(expression, profile, ignored -> Optional.empty());
    }
    static Optional<TypeNode> promoted(Expression expression, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> constants) {
        if (BitFieldSemantics.uncertainResult(expression)) return Optional.empty();
        var field = BitFieldSemantics.field(expression);
        if (field.isPresent()) {
            var enumeration = field.get().type().flatMap(EnumSemantics::declaration);
            if (enumeration.isPresent()) return EnumSemantics.underlying(enumeration.get(), profile, constants)
                    .flatMap(type -> EnumSemantics.promoteUnderlying(type, profile)).map(value -> named(value.spelling()));
            return BitFieldSemantics.promoted(field.get(), profile,
                    value -> ConstantEvaluator.integralConstant(value, profile, constants)).map(value -> named(value.spelling()));
        }
        return expressionType(expression, profile, constants).flatMap(type -> promoted(type, profile, constants));
    }
    public static Optional<TypeNode> promoted(TypeNode type, PlatformProfile profile) {
        return promoted(type, profile, ignored -> Optional.empty());
    }
    static Optional<TypeNode> promoted(TypeNode type, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> constants) {
        var enumeration = EnumSemantics.declaration(type);
        if (enumeration.isPresent()) return EnumSemantics.underlying(enumeration.get(), profile, constants)
                .flatMap(value -> EnumSemantics.promoteUnderlying(value, profile)).map(value -> named(value.spelling()));
        if (floatingRank(type) > 0) return Optional.of(unqualified(type));
        return integerType(type, profile).flatMap(value -> ConstantEvaluator.promote(value, profile)).map(value -> named(value.spelling()));
    }
    public static Optional<TypeNode> commonArithmeticType(TypeNode a, TypeNode b, PlatformProfile profile) {
        int floating = Math.max(floatingRank(a), floatingRank(b));
        if (floating > 0 && (floatingRank(a) > 0 || integerType(a, profile).isPresent())
                && (floatingRank(b) > 0 || integerType(b, profile).isPresent()))
            return Optional.of(named(List.of("", "float", "double", "long double").get(floating)));
        var ia = integerType(a, profile); var ib = integerType(b, profile);
        return ia.isPresent() && ib.isPresent() ? ConstantEvaluator.commonType(ia.get(), ib.get(), profile)
                .map(value -> named(value.spelling())) : Optional.empty();
    }
    static Optional<TypeNode> pointee(TypeNode type) {
        return unqualified(type) instanceof PointerType pointer ? Optional.of(pointer.pointee()) : Optional.empty();
    }
    static int qualifiers(TypeNode type) {
        int cv = 0;
        Set<TypeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (seen.add(type)) {
            type = type.canonicalType();
            if (!(type instanceof QualifiedType q)) break;
            if (q.isConst()) cv |= 1;
            if (q.isVolatile()) cv |= 2;
            type = q.baseType();
        }
        return cv;
    }
    static TypeNode decay(TypeNode type) {
        var plain = unqualified(type);
        if (plain instanceof ArrayType array) {
            TypeNode element = array.elementType();
            int cv = qualifiers(type);
            if (cv != 0) element = QualifiedType.create().setBaseType(element.edit())
                    .setConst((cv & 1) != 0).setVolatile((cv & 2) != 0).build();
            return PointerType.create().setPointee(element.edit()).build();
        }
        if (plain instanceof FunctionType) return PointerType.create().setPointee(type.edit()).build();
        return type;
    }
    /** A unique callable signature, including indirect calls, without inventing a target. */
    public static Optional<FunctionType> callType(CallExpression call, PlatformProfile profile) {
        Optional<TypeNode> type = call.target().flatMap(target -> declarationType(Optional.of(target)));
        if (type.isEmpty()) type = expressionType(call.callee(), profile);
        return type.flatMap(value -> {
            TypeNode plain = unqualified(withoutReference(value));
            if (plain instanceof PointerType pointer) plain = unqualified(pointer.pointee());
            return plain instanceof FunctionType function ? Optional.of(function) : Optional.empty();
        });
    }
    private static Optional<TypeNode> declarationType(Optional<Declaration> declaration) {
        return declaration.flatMap(target -> {
            if (target instanceof TemplateParameterDeclaration parameter) return parameter.type();
            if (target instanceof VariableDeclaration variable) return variable.type();
            if (target instanceof ParameterDeclaration parameter) return parameter.type().map(TypeSemantics::decay);
            if (target instanceof CallableDeclaration callable) {
                if (callable.writtenType().isPresent()) return callable.writtenType();
                if (callable.returnType().isEmpty()) return Optional.empty();
                var function = FunctionType.create().setReturnType(signatureType(callable.returnType().get())).setVariadic(callable.variadic())
                        .setConst(callable.isConst()).setVolatile(callable.isVolatile());
                for (var parameter : callable.parameters()) function.addParameter(ParameterDeclaration.create().setName(parameter.name())
                        .setType(signatureType(parameter.type().orElseThrow())));
                return Optional.of(function.build());
            }
            return Optional.empty();
        });
    }

    /** A detached signature must not copy the class owning one of its own parameters. */
    @SuppressWarnings("unchecked")
    private static NodeBuilder<? extends TypeNode> signatureType(TypeNode input) {
        TypeNode type = input.canonicalType();
        if (type instanceof OpaqueLibraryType opaque) return OpaqueLibraryType.create().setName(opaque.name()).setIdentity(opaque.identity()).setCategory(opaque.category());
        if (type instanceof NamedType named) return NamedType.create().setName(named.fundamentalKind().map(FundamentalKind::spelling)
                .orElseGet(() -> named.declaration().flatMap(Declaration::typeIdentity).orElse(named.name())));
        if (type instanceof QualifiedType qualified) return QualifiedType.create().setBaseType(signatureType(qualified.baseType())).setConst(qualified.isConst()).setVolatile(qualified.isVolatile());
        if (type instanceof ReferenceType reference) return ReferenceType.create().setReferredType(signatureType(reference.referredType()));
        if (type instanceof PointerType pointer) return PointerType.create().setPointee(signatureType(pointer.pointee()));
        if (type instanceof MemberPointerType pointer) return MemberPointerType.create().setOwnerType(signatureType(pointer.ownerType())).setPointee(signatureType(pointer.pointee()));
        if (type instanceof ArrayType array) {
            var result = ArrayType.create().setElementType(signatureType(array.elementType()));
            array.bound().ifPresent(bound -> result.setBound((NodeBuilder<? extends Expression>) bound.edit())); return result;
        }
        if (type instanceof FunctionType function) {
            var result = FunctionType.create().setReturnType(signatureType(function.returnType())).setVariadic(function.variadic()).setConst(function.isConst()).setVolatile(function.isVolatile());
            function.parameters().forEach(parameter -> result.addParameter(ParameterDeclaration.create().setName(parameter.name()).setType(signatureType(parameter.type().orElseThrow()))));
            return result;
        }
        throw new IllegalArgumentException("Unsupported signature type");
    }

    static Optional<ConstantEvaluator.IntegerType> integerType(TypeNode type, PlatformProfile profile) {
        type = unqualified(type);
        if (type instanceof ReferenceType reference) return integerType(reference.referredType(), profile);
        return type instanceof NamedType name ? name.fundamentalKind()
                .flatMap(kind -> ConstantEvaluator.integerType(kind.spelling(), profile)) : Optional.empty();
    }
    private static TypeNode named(String name) { return NamedType.create().setName(name).build(); }
}
