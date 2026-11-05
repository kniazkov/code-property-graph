/* C++03 conservative pairwise overload selection. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.function.Function;

/** Selects a unique best conversion vector, never a sum of conversion costs.
 * Unsupported/dependent conversions remain unknown rather than selecting by accident. */
public final class OverloadResolution {
    private OverloadResolution() {}
    public enum Rank { EXACT, PROMOTION, CONVERSION, USER_DEFINED, ELLIPSIS, UNKNOWN, AMBIGUOUS }
    public record Sequence(Rank rank, int qualification, CallableDeclaration userDefined, boolean referenceBinding, boolean knownViable) {
        public Sequence(Rank rank, int qualification, CallableDeclaration userDefined, boolean referenceBinding) { this(rank, qualification, userDefined, referenceBinding, rank != Rank.UNKNOWN); }
        public Sequence(Rank rank, int qualification, CallableDeclaration userDefined) { this(rank, qualification, userDefined, false); }
    }
    public record Candidate(CallableDeclaration declaration, List<Sequence> sequences) {
        public Candidate { sequences = List.copyOf(sequences); }
    }
    public record Result(CallExpression.Resolution status, Optional<CallableDeclaration> selected,
            List<CallableDeclaration> viable) {
        public Result { viable = List.copyOf(viable); }
    }
    public static Result select(List<CallableDeclaration> declarations, List<Expression> arguments, PlatformProfile profile) {
        return select(declarations, arguments, profile, OverloadResolution::requiredArguments);
    }
    public static Result select(List<CallableDeclaration> declarations, List<Expression> arguments,
            PlatformProfile profile, ToIntFunction<CallableDeclaration> required) {
        var candidates = new ArrayList<Candidate>();
        for (var declaration : declarations) candidate(declaration, arguments, profile, required.applyAsInt(declaration)).ifPresent(candidates::add);
        return selectCandidates(candidates);
    }
    public static int requiredArguments(CallableDeclaration declaration) {
        int count = declaration.parameters().size();
        while (count > 0 && declaration.parameters().get(count - 1).defaultValue().isPresent()) count--;
        return count;
    }
    public static Optional<Candidate> candidate(CallableDeclaration declaration, List<Expression> arguments,
            PlatformProfile profile, int requiredArguments) {
        return candidate(declaration, arguments, profile, requiredArguments, expression -> TypeSemantics.expressionType(expression, profile));
    }
    /** Supplies contextual argument types, e.g. the owning enum of an enumerator. */
    public static Optional<Candidate> candidate(CallableDeclaration declaration, List<Expression> arguments,
            PlatformProfile profile, int requiredArguments, Function<Expression, Optional<TypeNode>> argumentTypes) {
        if (arguments.size() < requiredArguments || !declaration.variadic() && arguments.size() > declaration.parameters().size()) return Optional.empty();
        var sequences = new ArrayList<Sequence>();
        for (int i = 0; i < arguments.size(); i++) {
            if (i >= declaration.parameters().size()) sequences.add(new Sequence(Rank.ELLIPSIS, 0, null));
            else {
                var type = declaration.parameters().get(i).type();
                var sequence = type.isEmpty() ? Optional.of(new Sequence(Rank.UNKNOWN, 0, null)) : conversion(arguments.get(i), type.get(), profile, true, argumentTypes.apply(arguments.get(i)));
                if (sequence.isEmpty()) return Optional.empty();
                sequences.add(sequence.get());
            }
        }
        return Optional.of(new Candidate(declaration, sequences));
    }
    public static Result selectCandidates(List<Candidate> candidates) {
        var viable = candidates.stream().map(Candidate::declaration).distinct().toList();
        if (candidates.isEmpty()) return new Result(CallExpression.Resolution.UNRESOLVED, Optional.empty(), viable);
        if (candidates.stream().anyMatch(c -> c.sequences().stream().anyMatch(s -> s.rank() == Rank.UNKNOWN))) {
            // An implementation-defined arithmetic type has a guaranteed standard
            // conversion but no known exact/promotion/conversion rank. One candidate
            // needs no ranking. Identical explicit conversions can also be ranked
            // by the implicit object cv conversion alone.
            var cvBest=candidates.stream().filter(a->a.sequences().stream().allMatch(s->s.knownViable() && s.rank()!=Rank.AMBIGUOUS)
                    && candidates.stream().allMatch(b->a==b || sameExplicitParameters(a.declaration(),b.declaration()) && sameUnknownSequences(a,b) && better(a,b))).toList();
            if(cvBest.size()==1)return new Result(CallExpression.Resolution.RESOLVED,Optional.of(cvBest.getFirst().declaration()),viable);
            if(candidates.size()==1 && candidates.getFirst().sequences().stream().allMatch(s->s.knownViable() && s.rank()!=Rank.AMBIGUOUS))
                return new Result(CallExpression.Resolution.RESOLVED,Optional.of(candidates.getFirst().declaration()),viable);
            return new Result(CallExpression.Resolution.UNRESOLVED, Optional.empty(), viable);
        }
        var best = candidates.stream().filter(a -> candidates.stream().allMatch(b -> a == b || better(a, b))).toList();
        if (best.size() == 1 && best.getFirst().sequences().stream().noneMatch(sequence -> sequence.rank() == Rank.AMBIGUOUS))
            return new Result(CallExpression.Resolution.RESOLVED, Optional.of(best.getFirst().declaration()), viable);
        return new Result(CallExpression.Resolution.AMBIGUOUS, Optional.empty(), viable);
    }
    private static boolean sameUnknownSequences(Candidate a, Candidate b) {
        if(a.sequences().size()!=b.sequences().size())return false;
        for(int i=0;i<a.sequences().size();i++) {
            var x=a.sequences().get(i);var y=b.sequences().get(i);
            if((x.rank()==Rank.UNKNOWN || y.rank()==Rank.UNKNOWN) && !x.equals(y))return false;
        }
        return true;
    }
    private static boolean sameExplicitParameters(CallableDeclaration a, CallableDeclaration b) {
        return a instanceof MethodDeclaration && b instanceof MethodDeclaration
                && a.parameters().stream().map(p->p.type().map(TypeNode::canonicalIdentity).orElse("?")).toList()
                .equals(b.parameters().stream().map(p->p.type().map(TypeNode::canonicalIdentity).orElse("?")).toList());
    }
    public static boolean better(Candidate a, Candidate b) {
        if (a.sequences().size() != b.sequences().size()) return false;
        boolean strict = false;
        for (int i = 0; i < a.sequences().size(); i++) {
            Sequence x = a.sequences().get(i), y = b.sequences().get(i);
            if (x.qualification() < 0 || y.qualification() < 0) continue; // A static member has no implicit object conversion.
            Rank xr = x.rank() == Rank.AMBIGUOUS ? Rank.USER_DEFINED : x.rank(), yr = y.rank() == Rank.AMBIGUOUS ? Rank.USER_DEFINED : y.rank();
            if (xr == Rank.USER_DEFINED && yr == Rank.USER_DEFINED && x.userDefined() != y.userDefined()) return false;
            int comparison = Integer.compare(xr.ordinal(), yr.ordinal());
            if (comparison == 0 && x.referenceBinding() == y.referenceBinding()) comparison = Integer.compare(x.qualification(), y.qualification());
            if (comparison > 0) return false;
            strict |= comparison < 0;
        }
        return strict;
    }
    public static Optional<Sequence> conversion(Expression expression, TypeNode target, PlatformProfile profile, boolean allowUserDefined) {
        return conversion(expression, target, profile, allowUserDefined, TypeSemantics.expressionType(expression, profile));
    }
    private static Optional<Sequence> conversion(Expression expression, TypeNode target, PlatformProfile profile,
            boolean allowUserDefined, Optional<TypeNode> source) {
        if (source.isEmpty()) return Optional.of(new Sequence(Rank.UNKNOWN, 0, null));
        var standard = standard(source.get(), target, TypeSemantics.valueCategory(expression, profile), expression, profile);
        if (standard.isPresent() || !allowUserDefined) return standard;
        var conversions = new ArrayList<Sequence>();
        var conversionFunctions = new ArrayList<Candidate>();
        ClassSemantics.objectClass(source.get()).ifPresent(owner -> {
            for (var member : owner.members()) if (member instanceof CallableDeclaration callable && callable.conversionType().isPresent()) {
                int sourceCv = TypeSemantics.qualifiers(source.get());
                if ((sourceCv & 1) != 0 && !callable.isConst() || (sourceCv & 2) != 0 && !callable.isVolatile()) continue;
                var converted = callable.conversionType().orElseThrow();
                boolean reference = TypeSemantics.unqualified(converted) instanceof ReferenceType;
                standard(TypeSemantics.withoutReference(converted), target, reference ? ValueCategory.LVALUE : ValueCategory.RVALUE, null, profile)
                        .ifPresent(sequence -> conversionFunctions.add(new Candidate(callable, List.of(sequence))));
            }
        });
        if (!conversionFunctions.isEmpty()) {
            Result selected = selectCandidates(conversionFunctions);
            if (selected.selected().isPresent()) {
                Candidate path = conversionFunctions.stream().filter(c -> c.declaration() == selected.selected().get()).findFirst().orElseThrow();
                Sequence tail = path.sequences().getFirst();
                conversions.add(new Sequence(Rank.USER_DEFINED, tail.rank().ordinal() * 10 + tail.qualification(), path.declaration()));
            } else if (selected.status() == CallExpression.Resolution.AMBIGUOUS) return Optional.of(new Sequence(Rank.AMBIGUOUS, 0, null));
            else return Optional.of(new Sequence(Rank.UNKNOWN, 0, null));
        }
        TypeNode targetObject = TypeSemantics.withoutReference(target);
        boolean canConstruct = !(TypeSemantics.unqualified(target) instanceof ReferenceType reference) || TypeSemantics.qualifiers(reference.referredType()) == 1;
        var constructors = new ArrayList<Candidate>();
        if (canConstruct) ClassSemantics.objectClass(targetObject).ifPresent(owner -> {
            for (var member : owner.members()) if (member instanceof CallableDeclaration callable
                    && callable.kind() == CallableDeclaration.Kind.CONSTRUCTOR && !callable.modifiers().contains("explicit")
                    && !callable.parameters().isEmpty() && requiredArguments(callable) <= 1) {
                callable.parameters().getFirst().type().flatMap(type -> conversion(expression, type, profile, false))
                        .filter(Sequence::knownViable)
                        .ifPresent(sequence -> constructors.add(new Candidate(callable, List.of(sequence))));
            }
        });
        if (!constructors.isEmpty()) {
            Result selected = selectCandidates(constructors);
            if (selected.selected().isPresent()) conversions.add(new Sequence(Rank.USER_DEFINED, 0, selected.selected().get()));
            else if (selected.status() == CallExpression.Resolution.AMBIGUOUS) return Optional.of(new Sequence(Rank.AMBIGUOUS, 0, null));
            else return Optional.of(new Sequence(Rank.UNKNOWN, 0, null));
        }
        // Competing user-defined paths are not a justified implicit conversion.
        if (conversions.size() == 1) return Optional.of(conversions.getFirst());
        if (conversions.size() > 1) return Optional.of(new Sequence(Rank.AMBIGUOUS, 0, null));
        return Optional.empty();
    }
    private static boolean opaqueArithmetic(TypeNode type) {
        return type instanceof OpaqueLibraryType opaque && (opaque.category()==OpaqueLibraryType.Category.ARITHMETIC || opaque.category()==OpaqueLibraryType.Category.INTEGER)
                || type instanceof NamedType named && named.fundamentalKind().filter(kind->kind!=FundamentalKind.VOID).isPresent();
    }
    private static Optional<Sequence> standard(TypeNode source, TypeNode target, ValueCategory category,
            Expression expression, PlatformProfile profile) {
        source = source.canonicalType(); target = target.canonicalType();
        TypeNode sourceBase=TypeSemantics.unqualified(TypeSemantics.withoutReference(source));
        TypeNode targetBase=TypeSemantics.unqualified(TypeSemantics.withoutReference(target));
        var fromIterator=LibraryIteratorSemantics.info(sourceBase);
        var toIterator=LibraryIteratorSemantics.info(targetBase);
        if(fromIterator.isPresent() && toIterator.isPresent() && !sourceBase.canonicalIdentity().equals(targetBase.canonicalIdentity())) {
            if(!fromIterator.get().compatible(toIterator.get()) || fromIterator.get().iterator().name().equals("const_iterator") || !toIterator.get().iterator().name().equals("const_iterator"))return Optional.empty();
            if(TypeSemantics.unqualified(target) instanceof ReferenceType reference && TypeSemantics.qualifiers(reference.referredType())!=1)return Optional.empty();
            // Conversion is required by the iterator contract, but its underlying
            // implementation may be a qualification or a converting constructor.
            return Optional.of(new Sequence(Rank.UNKNOWN,0,null,target instanceof ReferenceType,true));
        }
        // No basic_string-to-stream-bitmask conversion is specified. Do not
        // invent one and let it obscure the guaranteed string-stream constructor.
        // Unspecified conversions for arbitrary user classes remain conservative.
        if (targetBase instanceof OpaqueLibraryType opaque
                && Set.of("fmtflags", "iostate", "openmode", "seekdir", "ctype_mask").contains(opaque.name())
                && ClassSemantics.objectClass(sourceBase).filter(LibraryDeclarations::isLibrary)
                    .filter(owner -> owner.name().equals("basic_string")).isPresent()) return Optional.empty();
        // Parameterized manipulators expose only their specified stream operation.
        // Their unspecified representation supplies no numeric conversion contract.
        if (sourceBase instanceof OpaqueLibraryType opaque
                && Set.of("setw_result", "setprecision_result", "setbase_result", "setfill_result", "setiosflags_result", "resetiosflags_result").contains(opaque.name())
                && !sourceBase.canonicalIdentity().equals(targetBase.canonicalIdentity())) return Optional.empty();
        if (targetBase instanceof OpaqueLibraryType opaque
                && Set.of("setw_result", "setprecision_result", "setbase_result", "setfill_result", "setiosflags_result", "resetiosflags_result").contains(opaque.name())
                && !sourceBase.canonicalIdentity().equals(targetBase.canonicalIdentity())) return Optional.empty();
        if((sourceBase instanceof OpaqueLibraryType || targetBase instanceof OpaqueLibraryType)
                && !sourceBase.canonicalIdentity().equals(targetBase.canonicalIdentity()))
            return Optional.of(new Sequence(Rank.UNKNOWN,0,null,target instanceof ReferenceType,
                    opaqueArithmetic(sourceBase) && opaqueArithmetic(targetBase)
                    && (!(TypeSemantics.unqualified(target) instanceof ReferenceType reference) || TypeSemantics.qualifiers(reference.referredType())==1)));
        if (unknown(TypeSemantics.unqualified(source)) && unknown(TypeSemantics.unqualified(TypeSemantics.withoutReference(target))))
            return Optional.of(new Sequence(Rank.UNKNOWN, 0, null));
        if (TypeSemantics.unqualified(target) instanceof ReferenceType reference) {
            TypeNode referred = reference.referredType();
            int cv = referenceQualifiers(referred), original = referenceQualifiers(source);
            boolean same = referenceSame(source, referred);
            if ((original & cv) != original) return Optional.empty();
            if (same && (category == ValueCategory.LVALUE || cv == 1))
                return Optional.of(new Sequence(Rank.EXACT, Integer.bitCount(cv & ~original), null, true));
            if (category == ValueCategory.LVALUE && derives(source, referred)) return Optional.of(new Sequence(Rank.CONVERSION, 0, null));
            if (cv != 1) return Optional.empty();
            return standard(source, TypeSemantics.unqualified(referred), category, expression, profile);
        }
        source = TypeSemantics.decay(source); target = TypeSemantics.decay(target);
        source = TypeSemantics.unqualified(source); target = TypeSemantics.unqualified(target);
        if (same(source, target)) return Optional.of(new Sequence(Rank.EXACT, 0, null));
        if (source instanceof NamedType named && named.declaration().orElse(null) instanceof EnumDeclaration enumeration && arithmetic(target, profile)) {
            var promotion = enumPromotion(enumeration, profile);
            if (promotion.isEmpty()) return Optional.of(new Sequence(Rank.UNKNOWN, 0, null));
            return Optional.of(new Sequence(target.canonicalIdentity().equals(promotion.get()) ? Rank.PROMOTION : Rank.CONVERSION, 0, null));
        }
        if (arithmetic(source, profile) && arithmetic(target, profile)) {
            String identity = target.canonicalIdentity();
            boolean promotion = TypeSemantics.promoted(source, profile).map(type -> type.canonicalIdentity().equals(identity)).orElse(false)
                    || TypeSemantics.floatingRank(source) == 1 && TypeSemantics.floatingRank(target) == 2;
            return Optional.of(new Sequence(promotion ? Rank.PROMOTION : Rank.CONVERSION, 0, null));
        }
        if (target instanceof PointerType to) {
            if (source instanceof PointerType from) {
                int a = TypeSemantics.qualifiers(from.pointee()), b = TypeSemantics.qualifiers(to.pointee());
                if ((a & b) != a) return Optional.empty();
                var qualification = pointerQualification(from, to);
                if (qualification.isPresent()) return Optional.of(new Sequence(Rank.EXACT, qualification.getAsInt(), null));
                if (derives(from.pointee(), to.pointee())) return Optional.of(new Sequence(Rank.CONVERSION, 0, null));
                if (TypeSemantics.unqualified(to.pointee()).canonicalIdentity().equals("void") && !(TypeSemantics.unqualified(from.pointee()) instanceof FunctionType))
                    return Optional.of(new Sequence(Rank.CONVERSION, 1, null));
            }
            if (expression != null && TypeSemantics.integerType(source, profile).isPresent()
                    && ConstantEvaluator.evaluate(expression, profile).map(value -> value.number().signum() == 0).orElse(false))
                return Optional.of(new Sequence(Rank.CONVERSION, 0, null));
        }
        if (target.canonicalIdentity().equals("bool") && (source instanceof PointerType || source instanceof MemberPointerType))
            return Optional.of(new Sequence(Rank.CONVERSION, 1, null));
        if (derives(source, target)) return Optional.of(new Sequence(Rank.CONVERSION, 0, null));
        if (unknown(source) || unknown(target)) return Optional.of(new Sequence(Rank.UNKNOWN, 0, null));
        return Optional.empty();
    }
    private static int referenceQualifiers(TypeNode type) {
        int result=TypeSemantics.qualifiers(type);type=TypeSemantics.unqualified(type);
        return type instanceof ArrayType array?result|referenceQualifiers(array.elementType()):result;
    }
    private static boolean referenceSame(TypeNode a,TypeNode b) {
        a=TypeSemantics.unqualified(a);b=TypeSemantics.unqualified(b);
        if(a instanceof ArrayType x && b instanceof ArrayType y)
            return x.bound().map(bound->bound.source(Language.CPP)).equals(y.bound().map(bound->bound.source(Language.CPP)))
                    && referenceSame(x.elementType(),y.elementType());
        return same(a,b);
    }
    private static OptionalInt pointerQualification(PointerType source, PointerType target) {
        var from = new ArrayList<Integer>(); var to = new ArrayList<Integer>();
        TypeNode a = source, b = target;
        while (TypeSemantics.unqualified(a) instanceof PointerType ap && TypeSemantics.unqualified(b) instanceof PointerType bp) {
            a = ap.pointee(); b = bp.pointee();
            from.add(TypeSemantics.qualifiers(a)); to.add(TypeSemantics.qualifiers(b));
        }
        if (!same(a, b)) return OptionalInt.empty();
        int added = 0;
        for (int i = 0; i < from.size(); i++) {
            if ((from.get(i) & to.get(i)) != from.get(i)) return OptionalInt.empty();
            if (!from.get(i).equals(to.get(i))) {
                for (int j = 0; j < i; j++) if ((to.get(j) & 1) == 0) return OptionalInt.empty();
                added += Integer.bitCount(to.get(i) & ~from.get(i));
            }
        }
        return OptionalInt.of(added);
    }
    private static boolean unknown(TypeNode type) {
        return type instanceof NamedType name && name.fundamentalKind().isEmpty() && name.declaration().isEmpty();
    }
    private static boolean arithmetic(TypeNode type, PlatformProfile profile) {
        return TypeSemantics.integerType(type, profile).isPresent() || TypeSemantics.floatingRank(type) > 0;
    }
    private static boolean same(TypeNode a, TypeNode b) {
        return TypeSemantics.unqualified(a).canonicalIdentity().equals(TypeSemantics.unqualified(b).canonicalIdentity());
    }
    private static boolean derives(TypeNode a, TypeNode b) {
        var from = ClassSemantics.objectClass(a); var to = ClassSemantics.objectClass(b);
        return from.isPresent() && to.isPresent() && derives(from.get(), to.get(), new HashSet<>());
    }
    private static boolean derives(ClassDeclaration from, ClassDeclaration to, Set<ClassDeclaration> seen) {
        if (!seen.add(from)) return false;
        for (BaseSpecifier base : from.bases()) {
            var parent = ClassSemantics.objectClass(base.type());
            if (parent.isPresent() && (ClassSemantics.sameClass(parent.get(), to) || derives(parent.get(), to, seen))) return true;
        }
        return false;
    }
    private static Optional<String> enumPromotion(EnumDeclaration enumeration, PlatformProfile profile) {
        java.math.BigInteger next = java.math.BigInteger.ZERO, min = next, max = next;
        for (EnumeratorDeclaration value : enumeration.enumerators()) {
            if (value.value().isPresent()) {
                var evaluated = ConstantEvaluator.evaluate(value.value().get(), profile);
                if (evaluated.isEmpty()) return Optional.empty();
                next = evaluated.get().number();
            }
            min = min.min(next); max = max.max(next); next = next.add(java.math.BigInteger.ONE);
        }
        for (String type : List.of("int", "unsigned int", "long", "unsigned long")) {
            var range = profile.integerRange(type);
            if (range.isPresent() && range.get().min().compareTo(min) <= 0 && range.get().max().compareTo(max) >= 0) return Optional.of(type);
        }
        return Optional.empty();
    }
    public static List<ClassDeclaration> associatedClasses(List<Expression> arguments, PlatformProfile profile) {
        Set<ClassDeclaration> result = new LinkedHashSet<>();
        for (var argument : arguments) TypeSemantics.expressionType(argument, profile).ifPresent(type -> associated(type, result));
        return List.copyOf(result);
    }
    private static void associated(TypeNode type, Set<ClassDeclaration> result) {
        type = TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
        if (type instanceof PointerType pointer) associated(pointer.pointee(), result);
        else if (type instanceof ArrayType array) associated(array.elementType(), result);
        else ClassSemantics.objectClass(type).ifPresent(owner -> {
            if (result.add(owner)) for (var base : owner.bases()) associated(base.type(), result);
        });
    }
}
