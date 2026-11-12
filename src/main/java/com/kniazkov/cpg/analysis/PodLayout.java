/* Conservative C++03 POD layout preparation, not an offsetof implementation. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

/**
 * A deliberately bounded, snapshot-local query under an explicit target layout policy.
 * This neither changes generic sizeof/aggregate semantics nor certifies a compiler ABI.
 * Failure means this query cannot justify a result, not necessarily invalid C++03.
 * Enums and member pointers require separate explicitly selected storage contracts.
 * Unresolved templates remain unsupported. Bit-fields require a separate narrow
 * allocation contract; they never become addressable fields. Member pointers require
 * their own explicitly selected storage contract. Ordinary methods
 * and static data do not affect the layout; user special members and non-POD subobjects do.
 * No source nodes, implicit declarations, persistent caches or host facts are created.
 * Bounds and indices use the existing ICE evaluator, with declaration values available
 * only when a Project is supplied. A selected offsetof context evaluates sizeof through
 * this bounded query; ordinary/context-free sizeof semantics remain unchanged.
 */
final class PodLayout {
    private PodLayout() {}

    enum Reason {
        NOT_POD, INCOMPLETE_TYPE, RECURSIVE_TYPE, UNSUPPORTED_TYPE, UNSUPPORTED_TEMPLATE,
        UNKNOWN_LAYOUT, BIT_FIELD, STATIC_MEMBER, FUNCTION_MEMBER, MISSING_MEMBER,
        INVALID_PATH, NON_CONSTANT_INDEX, INDEX_OUT_OF_BOUNDS, UNKNOWN_ARRAY_BOUND,
        INVALID_ARRAY_BOUND, SIZE_NOT_REPRESENTABLE, SUPPORTED_OBJECT_LIMIT, UNSUPPORTED_PRAGMA, UNSUPPORTED_ENUM_SEMANTICS
    }
    record Failure(Reason reason, String detail) {
        Failure { Objects.requireNonNull(reason); Objects.requireNonNull(detail); }
    }
    record Result<T>(Optional<T> value, Optional<Failure> failure) {
        Result {
            Objects.requireNonNull(value); Objects.requireNonNull(failure);
            if (value.isPresent() == failure.isPresent())
                throw new IllegalArgumentException("A layout query has either a value or a failure");
        }
        boolean supported() { return value.isPresent(); }
    }
    /** Offsets and sizes are target bytes, whereas alignment follows profile bit units. */
    record Field(VariableDeclaration declaration, BigInteger offsetBytes) {}
    record Layout(BigInteger sizeBytes, int alignmentBits, List<Field> fields) {
        Layout { fields = List.copyOf(fields); }
    }
    sealed interface Step permits Member, Index {}
    record Member(String name) implements Step {
        Member { Objects.requireNonNull(name); }
    }
    /** The expression is queried as an ICE, never executed or attached to a fabricated object. */
    record Index(Expression expression) implements Step {
        Index { Objects.requireNonNull(expression); }
    }
    /** Type and member declarations retain the exact supplied snapshot's nodes. */
    record Offset(BigInteger offsetBytes, TypeNode type, List<VariableDeclaration> members) {
        Offset { members = List.copyOf(members); }
    }

    static Result<Layout> layout(TypeNode type, PlatformProfile profile) {
        return new Query(null, profile).type(Objects.requireNonNull(type), true);
    }
    /** A declaration query inspects this definition; it has no source use point. */
    static Result<Layout> layout(ClassDeclaration owner, PlatformProfile profile) {
        return new Query(null, profile).owner(Objects.requireNonNull(owner));
    }
    static Result<Layout> layout(Project project, TypeNode type, PlatformProfile profile) {
        return new Query(Objects.requireNonNull(project), profile).type(Objects.requireNonNull(type), true);
    }
    static Result<Layout> layout(Project project, ClassDeclaration owner, PlatformProfile profile) {
        return new Query(Objects.requireNonNull(project), profile).owner(Objects.requireNonNull(owner));
    }
    static Result<Offset> offset(TypeNode type, List<Step> path, PlatformProfile profile) {
        return new Query(null, profile).offset(Objects.requireNonNull(type), List.copyOf(path));
    }
    static Result<Offset> offset(Project project, TypeNode type, List<Step> path, PlatformProfile profile) {
        return new Query(Objects.requireNonNull(project), profile).offset(Objects.requireNonNull(type), List.copyOf(path));
    }

    private static <T> Result<T> success(T value) {
        return new Result<>(Optional.of(value), Optional.empty());
    }
    static <T> Result<T> failure(Reason reason, String detail) {
        return new Result<>(Optional.empty(), Optional.of(new Failure(reason, detail)));
    }
    private static <T> Result<T> propagate(Result<?> result) {
        return new Result<>(Optional.empty(), result.failure());
    }
    private static BigInteger align(BigInteger size, int alignmentBits, PlatformProfile profile) {
        BigInteger alignment = BigInteger.valueOf(alignmentBits / profile.bitsPerByte());
        return size.add(alignment).subtract(BigInteger.ONE).divide(alignment).multiply(alignment);
    }

    /** Shared across ordinary and bounded queries in the same constant context. */
    static final class Guards {
        final Set<TypeNode> activeTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<ClassDeclaration> activeClasses = new ArrayList<>();
        final Set<EnumDeclaration> activeEnums = Collections.newSetFromMap(new IdentityHashMap<>());
    }
    static final class Query {
        private final PlatformProfile profile;
        private Function<Declaration, Optional<ConstantEvaluator.Value>> constants;
        private final BigInteger ceiling;
        private final IntegralConstants context;
        private Failure dependencyFailure;
        private final Set<Declaration> declarations;
        private final Map<ClassDeclaration, ClassTemplateInstance> concreteTemplates = new IdentityHashMap<>();
        private final Map<ClassDeclaration, List<ClassDeclaration>> selectionHeaders;
        private final Set<ClassDeclaration> activeTemplateArguments = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<EnumeratorDeclaration, EnumDeclaration> enumOwners = new IdentityHashMap<>();
        private final Set<TypeNode> activeTypes;
        private final List<ClassDeclaration> activeClasses;

        Query(Project project, PlatformProfile profile) {
            this(project, profile, new IntegralConstants(project, profile), null);
        }
        Query(Project project, PlatformProfile profile, IntegralConstants constants, BigInteger ceiling) {
            this.profile = Objects.requireNonNull(profile); this.ceiling = ceiling;
            this.constants = constants; this.context = constants;
            selectionHeaders = project == null ? Map.of() : ClassTemplateSemantics.layoutSelectionHeaders(project);
            activeTypes = constants.layoutGuards.activeTypes; activeClasses = constants.layoutGuards.activeClasses;
            declarations = project == null ? null : Collections.newSetFromMap(new IdentityHashMap<>());
            if (project != null) project.classTemplateInstances().forEach(instance -> {
                if (instance.declaration().isDefinition() && instance.declaration().templateParameters().isEmpty()
                        && TypeSemantics.profile(instance.source()).equals(profile))
                    concreteTemplates.put(instance.declaration(), instance);
            });
            if (project != null) project.walk(new NodeVisitor() {
                @Override public void visit(Declaration declaration) { declarations.add(declaration); }
                @Override public void visit(EnumDeclaration enumeration) {
                    visit((Declaration) enumeration);
                    enumeration.enumerators().forEach(value -> enumOwners.put(value, enumeration));
                }
            });
        }
        // A necessary target representability bound, not a promise that a compiler
        // supports objects this large. Never substitute Java's numeric limits.
        private boolean representable(BigInteger value) {
            return value.signum() >= 0 && profile.integerRange(profile.sizeType())
                    .map(range -> value.compareTo(range.max()) <= 0).orElse(false);
        }
        private <T> Result<T> oversized() {
            return failure(Reason.SIZE_NOT_REPRESENTABLE,
                    "Object size or offset is not representable in target " + profile.sizeType());
        }
        private <T> Result<T> unsupportedSize(BigInteger size) {
            if (!representable(size)) return oversized();
            return failure(Reason.SUPPORTED_OBJECT_LIMIT, "Layout dependency exceeds offsetof supportedObjectBytes ceiling " + ceiling);
        }
        private boolean supportedSize(BigInteger size) { return representable(size) && (ceiling == null || size.compareTo(ceiling) <= 0); }
        private Result<Layout> layoutValue(BigInteger size, int alignment, List<Field> fields) {
            return supportedSize(size) ? success(new Layout(size, alignment, fields)) : unsupportedSize(size);
        }
        private <T> Result<T> constantFailure(Reason reason, String detail) {
            return dependencyFailure == null ? failure(reason, detail) : new Result<>(Optional.empty(), Optional.of(dependencyFailure));
        }
        private boolean owned(Declaration declaration) {
            return declarations == null || declarations.contains(declaration);
        }
        private boolean active(ClassDeclaration owner) {
            return activeClasses.stream().anyMatch(previous -> ClassSemantics.sameClass(previous, owner));
        }
        private ClassDeclaration definition(ClassDeclaration owner) {
            return owner.definition().orElse(owner) instanceof ClassDeclaration value ? value : owner;
        }

        private Result<Layout> type(TypeNode type, boolean checkCompleteness) {
            if (!activeTypes.add(type)) return failure(Reason.RECURSIVE_TYPE, "Cyclic type or typedef dependency");
            try {
                if (type instanceof QualifiedType qualified) return type(qualified.baseType(), checkCompleteness);
                if (type instanceof ReferenceType) return failure(Reason.NOT_POD, "A reference is not a POD object subobject");
                if (type instanceof MemberPointerType pointer) return memberPointer(pointer);
                // A pointer's representation does not require its pointee's layout or completeness.
                if (type instanceof PointerType) return scalar("pointer");
                if (type instanceof ArrayType array) {
                    var count = array.bound().flatMap(this::integer);
                    if (count.isEmpty()) return constantFailure(Reason.UNKNOWN_ARRAY_BOUND,
                            "A fixed array bound is not proven constant under the supplied snapshot context");
                    if (count.get().signum() <= 0) return failure(Reason.INVALID_ARRAY_BOUND, "A fixed array bound must be positive");
                    var element = type(array.elementType(), checkCompleteness);
                    if (!element.supported()) return element;
                    Layout value = element.value().orElseThrow();
                    return layoutValue(value.sizeBytes().multiply(count.get()), value.alignmentBits(), List.of());
                }
                if (!(type instanceof NamedType named))
                    return failure(Reason.UNSUPPORTED_TYPE, "No POD object representation for " + type.getClass().getSimpleName());
                if (templateName(named)) return failure(Reason.UNSUPPORTED_TEMPLATE, "Template-dependent or specialized type layout is unsupported");
                Declaration target = named.declaration().orElse(null);
                var namedOwner = byValueClass(named);
                if (namedOwner.filter(this::active).isPresent())
                    return failure(Reason.RECURSIVE_TYPE, "Recursive by-value class " + namedOwner.orElseThrow().name());
                if (checkCompleteness && !named.completeAtUse())
                    return failure(Reason.INCOMPLETE_TYPE, "Type " + named.name() + " is incomplete at this use");
                if (named.alias().isPresent()) {
                    TypedefDeclaration alias = named.alias().orElseThrow();
                    if (!owned(alias)) return failure(Reason.UNSUPPORTED_TYPE, "Typedef is outside the supplied snapshot");
                    // The alias use has its own completeness fact. Its declaration may predate
                    // the definition, so do not substitute the typedef's earlier use point.
                    return type(alias.type(), false);
                }
                if (named.fundamentalKind().isPresent()) {
                    FundamentalKind kind = named.fundamentalKind().orElseThrow();
                    return kind == FundamentalKind.VOID
                            ? failure(Reason.UNSUPPORTED_TYPE, "void has no POD object representation") : scalar(kind.spelling());
                }
                if (target instanceof ClassDeclaration owner) {
                    ClassTemplateInstance instance = concreteTemplates.get(owner);
                    if (instance != null) {
                        var arguments = templateArguments(named, instance);
                        if (arguments.isPresent()) return new Result<>(Optional.empty(), arguments);
                    }
                    return owner(definition(owner));
                }
                if (target instanceof EnumDeclaration enumeration) return enumLayout(enumeration);
                return failure(Reason.UNSUPPORTED_TYPE, "No resolved supported object type for " + named.name());
            } finally { activeTypes.remove(type); }
        }
        /** Instantiation normalizes non-type arguments to literals. Their written
         * dependencies still belong to the bounded layout and cannot be skipped. */
        private Optional<Failure> templateArguments(NamedType use, ClassTemplateInstance instance) {
            if (!activeTemplateArguments.add(instance.declaration()))
                return Optional.of(new Failure(Reason.RECURSIVE_TYPE, "Recursive concrete template argument dependency"));
            try {
                for (ClassDeclaration header : selectionHeaders.getOrDefault(instance.declaration(), List.of(instance.selected())))
                    for (TemplateArgument argument : header.templateArguments())
                        if (!headerSyntax(argument, Collections.newSetFromMap(new IdentityHashMap<>())))
                            return Optional.ofNullable(dependencyFailure).or(() -> Optional.of(new Failure(Reason.UNSUPPORTED_TEMPLATE,
                                    "Specialization header dependency is not certified by the bounded context")));
                if (instance.arguments().stream().anyMatch(argument -> !resolvedArgument(argument)))
                    return Optional.of(new Failure(Reason.UNSUPPORTED_TEMPLATE, "Template arguments are not fully resolved"));
                List<NamedType> requests = instance.requests().stream().filter(NamedType.class::isInstance)
                        .map(NamedType.class::cast).toList();
                if (requests.isEmpty()) return Optional.of(new Failure(Reason.UNSUPPORTED_TEMPLATE, "Concrete template has no written type request"));
                if (requests.contains(use)) requests = List.of(use);
                for (NamedType request : requests) {
                    for (TemplateArgument argument : request.templateArguments()) {
                        if (!argumentSyntax(argument, Collections.newSetFromMap(new IdentityHashMap<>())))
                            return Optional.ofNullable(dependencyFailure).or(() -> Optional.of(new Failure(
                                    Reason.UNSUPPORTED_TEMPLATE, "Template argument dependency is not certified by the bounded context")));
                    }
                    for (int i = request.templateArguments().size(); i < instance.arguments().size(); i++) {
                        if (i >= instance.origin().templateParameters().size())
                            return Optional.of(new Failure(Reason.UNSUPPORTED_TEMPLATE, "No written default template argument"));
                        var parameter = instance.origin().templateParameters().get(i);
                        var argument = parameter.defaultArgument();
                        if (argument.isEmpty()) return Optional.of(new Failure(Reason.UNSUPPORTED_TEMPLATE,
                                "Default template argument provenance is not available on the selected primary"));
                        if (!argumentSyntax(argument.orElseThrow(), Collections.newSetFromMap(new IdentityHashMap<>())))
                            return Optional.ofNullable(dependencyFailure).or(() -> Optional.of(new Failure(
                                    Reason.UNSUPPORTED_TEMPLATE, "Default template argument dependency is not certified by the bounded context")));
                    }
                }
                return Optional.empty();
            } finally { activeTemplateArguments.remove(instance.declaration()); }
        }
        /** Type arguments can contain constant syntax too (for example an array
         * bound selecting a specialization). Follow syntax and typedefs, not object
         * declarations or pointer-pointee layouts. */
        private boolean argumentSyntax(Node node, Set<Node> active) {
            if (!active.add(node)) return false;
            try {
                if (node instanceof Expression expression)
                    return safeConstant(expression, Collections.newSetFromMap(new IdentityHashMap<>()));
                if (node instanceof NamedType named) {
                    if (named.alias().isPresent()) {
                        TypedefDeclaration alias = named.alias().orElseThrow();
                        if (!owned(alias) || !argumentSyntax(alias.type(), active)) return false;
                    }
                    // A nested actual template-id retains its written (possibly
                    // shortened) argument list. Selection's read-only completion
                    // must not erase the original default's sizeof/enum/const
                    // dependencies from this bounded layout query.
                    if (named.hasTemplateArguments()) {
                        Declaration bound = named.declaration().orElse(null);
                        ClassTemplateInstance instance = concreteTemplates.get(bound);
                        ClassDeclaration primary = instance == null
                                ? bound instanceof ClassDeclaration declaration
                                    && declaration.templateKind() == TemplateKind.PRIMARY ? declaration : null
                                : instance.origin();
                        if (primary != null) {
                            if (!owned(primary)) return false;
                            for (int i = named.templateArguments().size(); i < primary.templateParameters().size(); i++) {
                                var argument = primary.templateParameters().get(i).defaultArgument();
                                if (argument.isEmpty() || !argumentSyntax(argument.orElseThrow(), active)) return false;
                            }
                        }
                    }
                }
                return node.children().stream().allMatch(child -> argumentSyntax(child, active));
            } finally { active.remove(node); }
        }

        private boolean headerSyntax(Node node, Set<Node> active) {
            // A nondependent full-specialization type can hide omitted defaults.
            // Validate their written dependencies just as for actual arguments,
            // even when this visible specialization is not the selected one.
            if ((node instanceof Expression || node instanceof NamedType) && !TypeSemantics.dependent(node))
                return argumentSyntax(node, active);
            if (!active.add(node)) return false;
            try {
                if (node instanceof NamedType named) {
                    if (named.alias().isPresent()
                            && !headerSyntax(named.alias().orElseThrow().type(), active)) return false;
                    // A dependent partial pattern can omit independent defaults.
                    // Check their original expressions even for unselected
                    // candidates; normalized bool/unsigned values hide size and
                    // enum-policy dependencies that remain layout obligations.
                    if (named.hasTemplateArguments()) {
                        Declaration bound = named.declaration().orElse(null);
                        ClassTemplateInstance instance = concreteTemplates.get(bound);
                        ClassDeclaration primary = instance == null
                                ? bound instanceof ClassDeclaration declaration
                                    && declaration.templateKind() == TemplateKind.PRIMARY ? declaration : null
                                : instance.origin();
                        if (primary != null) {
                            if (!owned(primary)) return false;
                            for (int i = named.templateArguments().size(); i < primary.templateParameters().size(); i++) {
                                var argument = primary.templateParameters().get(i).defaultArgument();
                                if (argument.isEmpty() || !argumentSyntax(argument.orElseThrow(), active)) return false;
                            }
                        }
                    }
                }
                return node.children().stream().allMatch(child -> headerSyntax(child, active));
            } finally { active.remove(node); }
        }

        private boolean resolvedArgument(TemplateArgument argument) {
            return resolvedArgumentSyntax(argument, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        /** A materialized argument may still hide a dependent typedef target.
         * Inspect type syntax and exact alias edges, never class/pointee layout. */
        private boolean resolvedArgumentSyntax(Node node, Set<Node> active) {
            if (!active.add(node)) return false;
            try {
                if (TypeSemantics.dependent(node)) return false;
                if (node instanceof NamedType named) {
                    if (named.fundamentalKind().isEmpty() && named.alias().isEmpty() && named.declaration().isEmpty()) return false;
                    if (named.alias().isPresent()) {
                        TypedefDeclaration alias = named.alias().orElseThrow();
                        if (!owned(alias) || !resolvedArgumentSyntax(alias.type(), active)) return false;
                        // A namespace qualifier is not an object type. The exact owned
                        // alias binding establishes its meaning; still check any written
                        // argument syntax rather than demanding a layout for the qualifier.
                        return named.templateArguments().stream().allMatch(argument -> resolvedArgumentSyntax(argument, active));
                    }
                    if (named.declaration().isPresent() && !owned(named.declaration().orElseThrow())) return false;
                    // A bound concrete class establishes its head identity even
                    // when its qualifier is a namespace rather than an object
                    // type. Dependent syntax was rejected above; still validate
                    // every written argument instead of traversing that qualifier.
                    if (named.declaration().orElse(null) instanceof ClassDeclaration)
                        return named.templateArguments().stream().allMatch(argument -> resolvedArgumentSyntax(argument, active));
                }
                return node.children().stream().allMatch(child -> resolvedArgumentSyntax(child, active));
            } finally { active.remove(node); }
        }

        private boolean templateName(NamedType type) {
            return type.dependent() || (type.hasTemplateArguments() || type.templateKeyword())
                    && !concreteTemplates.containsKey(type.declaration().orElse(null))
                    || type.declaration().orElse(null) instanceof TemplateParameterDeclaration
                    || type.qualifier().map(this::templateName).orElse(false);
        }
        private Optional<ClassDeclaration> byValueClass(TypeNode type) {
            Set<TypeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            while (seen.add(type)) {
                if (type instanceof QualifiedType qualified) type = qualified.baseType();
                else if (type instanceof ArrayType array) type = array.elementType();
                else if (type instanceof NamedType named && named.alias().isPresent()) type = named.alias().orElseThrow().type();
                else return type instanceof NamedType named && named.declaration().orElse(null) instanceof ClassDeclaration owner
                        ? Optional.of(definition(owner)) : Optional.empty();
            }
            return Optional.empty();
        }
        private Result<Layout> scalar(String spelling) {
            var size = profile.sizeBytes(spelling);
            var alignment = profile.alignmentBits(spelling);
            if (size.isEmpty() || alignment.isEmpty())
                return failure(Reason.UNKNOWN_LAYOUT, "Explicit size and alignment are required for " + spelling);
            return layoutValue(BigInteger.valueOf(size.getAsInt()), alignment.getAsInt(), List.of());
        }

        /** Storage depends on the resolved category, not an owner's object layout,
         * pointee size, function body or ordinary pointer representation. */
        private Result<Layout> memberPointer(MemberPointerType pointer) {
            if (profile.offsetof().map(model -> model.memberPointerStorage() != null).orElse(false) == false)
                return failure(Reason.UNSUPPORTED_TYPE, "Member-pointer storage requires an explicit offsetof.memberPointerStorage contract");
            var owner = memberShape(pointer.ownerType());
            if (!owner.supported()) return propagate(owner);
            if (!(owner.value().orElseThrow() instanceof NamedType name)
                    || !(name.declaration().orElse(null) instanceof ClassDeclaration))
                return failure(Reason.UNSUPPORTED_TYPE, "A member pointer needs a resolved class owner");
            var pointee = memberShape(pointer.pointee());
            if (!pointee.supported()) return propagate(pointee);
            TypeNode category = pointee.value().orElseThrow();
            if (category instanceof ReferenceType || category instanceof NamedType named
                    && named.fundamentalKind().orElse(null) == FundamentalKind.VOID)
                return failure(Reason.UNSUPPORTED_TYPE, "A member pointer needs an object or function member type");
            if (!resolvedArgumentSyntax(pointer, Collections.newSetFromMap(new IdentityHashMap<>())))
                return failure(Reason.UNSUPPORTED_TYPE, "Member-pointer owner and member type syntax must be resolved in this snapshot");
            // Written bounds and template arguments can contain sizeof dependencies.
            // Check those with the enclosing guards, without traversing owner bodies.
            if (!argumentSyntax(pointer, Collections.newSetFromMap(new IdentityHashMap<>())))
                return constantFailure(Reason.UNSUPPORTED_TYPE, "Member-pointer type dependencies are outside the bounded context");
            if (!(category instanceof FunctionType || category instanceof NamedType || category instanceof PointerType
                    || category instanceof MemberPointerType || category instanceof ArrayType))
                return failure(Reason.UNSUPPORTED_TYPE, "No resolved member-pointer storage category");
            return layoutValue(BigInteger.valueOf(category instanceof FunctionType ? 16 : 8), 64, List.of());
        }
        /** Follow only cv/typedef edges, retaining cycle and snapshot guards. Identity
         * lookup is not by-value layout recursion: the owner can be the enclosing type,
         * even when a graph reuses that exact TypeNode. */
        private Result<TypeNode> memberShape(TypeNode type) {
            Set<TypeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            while (seen.add(type)) {
                if (type instanceof QualifiedType qualified) { type = qualified.baseType(); continue; }
                if (type instanceof NamedType named) {
                    if (templateName(named)) return failure(Reason.UNSUPPORTED_TEMPLATE, "Member-pointer type is not fully resolved");
                    if (named.alias().isPresent()) {
                        TypedefDeclaration alias = named.alias().orElseThrow();
                        if (!owned(alias)) return failure(Reason.UNSUPPORTED_TYPE, "Member-pointer typedef is outside the supplied snapshot");
                        type = alias.type(); continue;
                    }
                    if (named.declaration().isPresent() && !owned(named.declaration().orElseThrow()))
                        return failure(Reason.UNSUPPORTED_TYPE, "Member-pointer declaration is outside the supplied snapshot");
                }
                return success(type);
            }
            return failure(Reason.RECURSIVE_TYPE, "Cyclic member-pointer type or typedef dependency");
        }

        private Result<Layout> owner(ClassDeclaration owner) {
            if (ceiling != null) {
                var pragma = context.pragmaFailure(owner);
                if (pragma.isPresent()) return new Result<>(Optional.empty(), pragma);
            }
            if (!owned(owner)) return failure(Reason.UNSUPPORTED_TYPE, "Class is outside the supplied snapshot");
            if (active(owner)) return failure(Reason.RECURSIVE_TYPE, "Recursive by-value class " + owner.name());
            if (!owner.isDefinition()) return failure(Reason.INCOMPLETE_TYPE, "Class " + owner.name() + " has no definition");
            if ((owner.templateKind() != TemplateKind.NONE || owner.hasTemplateArguments()
                    || !owner.templateParameters().isEmpty() || owner.templateOrigin().isPresent())
                    && !concreteTemplates.containsKey(owner))
                return failure(Reason.UNSUPPORTED_TEMPLATE, "Template class layout is outside this query's supported subset");
            if (LibraryDeclarations.isLibrary(owner)) return failure(Reason.UNSUPPORTED_TYPE, "Library declarations do not certify object layout");
            if (!owner.bases().isEmpty()) return failure(Reason.NOT_POD, "A C++03 POD class has no base classes");
            for (Declaration member : owner.members()) {
                if (member instanceof CallableDeclaration function && !function.isFriend()) {
                    if (function.kind() == CallableDeclaration.Kind.CONSTRUCTOR || function.modifiers().contains("virtual"))
                        return failure(Reason.NOT_POD, "Class has a user constructor or a virtual function");
                }
            }
            for (var special : ClassSemantics.specialMembers(owner)) {
                if (special.origin() != ClassSemantics.Origin.USER_DECLARED) continue;
                if (special.kind() == ClassSemantics.SpecialKind.DESTRUCTOR)
                    return failure(Reason.NOT_POD, "Class has a user-declared destructor");
                if (special.kind() == ClassSemantics.SpecialKind.COPY_ASSIGNMENT
                        && special.declarations().stream().filter(this::ordinaryAssignment)
                            .anyMatch(function -> copyAssignment(owner, function)))
                    return failure(Reason.NOT_POD, "Class has a user-declared copy assignment operator");
            }
            // An unresolved assignment parameter must not be guessed to be a harmless overload.
            for (Declaration member : owner.members()) {
                if (member instanceof CallableDeclaration function && ordinaryAssignment(function)) {
                    TypeNode parameter = function.parameters().getFirst().type().map(TypeSemantics::unqualified).orElse(null);
                    if (parameter instanceof ReferenceType reference) parameter = TypeSemantics.unqualified(reference.referredType());
                    if (parameter == null || parameter instanceof NamedType named && named.fundamentalKind().isEmpty()
                            && named.declaration().isEmpty())
                        return failure(Reason.UNSUPPORTED_TYPE, "Assignment parameter needs a resolved type to establish POD status");
                }
            }
            if (!profile.aggregateLayout().equals("sequential")
                    || !Set.of("natural", "packed1").contains(profile.fieldAlignment())
                    || !profile.aggregateAlignment().equals("maximum-field") || !profile.tailPadding().equals("alignment"))
                return failure(Reason.UNKNOWN_LAYOUT, "Explicit sequential, field-alignment, maximum-field and tail-padding policies are required");
            activeClasses.add(owner);
            try {
                boolean union = owner.keyword().equals("union");
                BigInteger size = BigInteger.ZERO;
                BigInteger nextBit = BigInteger.ZERO;
                int alignment = profile.bitsPerByte();
                var fields = new ArrayList<Field>();
                for (Declaration member : owner.members()) {
                    if (!(member instanceof VariableDeclaration field) || field.modifiers().contains("static")) continue;
                    // C++03 9.6/2: an unnamed bit-field is not a member. Its
                    // access label must not turn an unsupported layout into NOT_POD.
                    if (field.bitWidth().isPresent() && field.name().isEmpty())
                        return failure(Reason.BIT_FIELD, "Unnamed bit-field allocation is outside the selected contract");
                    if (!field.access().orElse(owner.memberAccess()).equals("public"))
                        return failure(Reason.NOT_POD, "A C++03 POD class has only public nonstatic data members");
                    if (field.bitWidth().isPresent()) {
                        var bitField = bitField(field);
                        if (!bitField.supported()) return propagate(bitField);
                        BitAllocation value = bitField.value().orElseThrow();
                        BigInteger unit = BigInteger.valueOf(value.storageBits());
                        BigInteger width = BigInteger.valueOf(value.width());
                        BigInteger start = union ? BigInteger.ZERO : nextBit;
                        // SysV permits sharing the current storage unit, including bytes
                        // after ordinary fields, but never crossing the declared-type unit.
                        if (start.mod(unit).add(width).compareTo(unit) > 0)
                            start = start.add(unit).subtract(BigInteger.ONE).divide(unit).multiply(unit);
                        BigInteger end = start.add(width);
                        BigInteger occupied = end.add(BigInteger.valueOf(7)).divide(BigInteger.valueOf(8));
                        size = union ? size.max(occupied) : occupied;
                        if (!supportedSize(size)) return unsupportedSize(size);
                        if (!union) nextBit = end;
                        alignment = Math.max(alignment, value.storageBits());
                        // No Field entry: a bit-field has no addressable byte offset.
                        continue;
                    }
                    if (field.name().isEmpty() || field.type().isEmpty())
                        return failure(Reason.UNSUPPORTED_TYPE, "Every object field needs a name and resolved type");
                    var result = type(field.type().orElseThrow(), true);
                    if (!result.supported()) return result;
                    Layout value = result.value().orElseThrow();
                    int fieldAlignment = profile.fieldAlignment().equals("packed1") ? profile.bitsPerByte() : value.alignmentBits();
                    alignment = Math.max(alignment, fieldAlignment);
                    BigInteger offset = union ? BigInteger.ZERO : align(size, fieldAlignment, profile);
                    if (!supportedSize(offset)) return unsupportedSize(offset);
                    fields.add(new Field(field, offset));
                    size = union ? size.max(value.sizeBytes()) : offset.add(value.sizeBytes());
                    if (!union) nextBit = size.multiply(BigInteger.valueOf(8));
                    if (!supportedSize(size)) return unsupportedSize(size);
                }
                return layoutValue(align(size.max(BigInteger.ONE), alignment, profile), alignment, fields);
            } finally { activeClasses.removeLast(); }
        }
        /** Positive named unsigned fields only; no allocation facts are host-derived. */
        private record BitAllocation(int storageBits, int width) {}
        private Result<BitAllocation> bitField(VariableDeclaration field) {
            if (profile.offsetof().map(model -> model.bitFieldStorage() == null).orElse(true))
                return failure(Reason.BIT_FIELD, "Bit-field allocation requires explicit offsetof.bitFieldStorage");
            if (field.name().isEmpty() || field.type().isEmpty())
                return failure(Reason.BIT_FIELD, "Selected bit-field allocation requires a named field with a resolved type");
            var shape = memberShape(field.type().orElseThrow());
            if (!shape.supported()) return propagate(shape);
            TypeNode plain = shape.value().orElseThrow();
            String spelling = plain instanceof NamedType named
                    ? named.fundamentalKind().map(FundamentalKind::spelling).orElse("") : "";
            int bits = switch (spelling) { case "unsigned char" -> 8; case "unsigned int" -> 32; default -> 0; };
            if (bits == 0)
                return failure(Reason.BIT_FIELD, "Selected bit-field allocation supports only unsigned char and unsigned int");
            var width = integer(field.bitWidth().orElseThrow());
            if (width.isEmpty()) return constantFailure(Reason.BIT_FIELD, "Bit-field width is not a certified integral constant");
            if (width.orElseThrow().signum() <= 0 || width.orElseThrow().compareTo(BigInteger.valueOf(bits)) > 0)
                return failure(Reason.BIT_FIELD, "Selected bit-field width must be positive and no greater than its declared storage width");
            return success(new BitAllocation(bits, width.orElseThrow().intValueExact()));
        }

        private boolean ordinaryAssignment(CallableDeclaration function) {
            return function.name().equals("operator=") && function.parameters().size() == 1
                    && !function.isFriend() && !function.modifiers().contains("static")
                    && function.templateKind() == TemplateKind.NONE && function.templateParameters().isEmpty()
                    && function.templateOrigin().isEmpty();
        }
        private boolean copyAssignment(ClassDeclaration owner, CallableDeclaration function) {
            TypeNode parameter = function.parameters().getFirst().type().map(TypeSemantics::unqualified).orElse(null);
            if (parameter instanceof ReferenceType reference) parameter = TypeSemantics.unqualified(reference.referredType());
            return parameter instanceof NamedType named && byValueClass(named)
                    .filter(value -> ClassSemantics.sameClass(owner, value)).isPresent();
        }

        Result<Offset> offset(TypeNode type, List<Step> path, Function<Declaration, Optional<ConstantEvaluator.Value>> values) {
            var previous = constants;
            var previousFailure = dependencyFailure;
            constants = values; dependencyFailure = null;
            try { return offset(type, path); }
            finally { constants = previous; dependencyFailure = previousFailure; }
        }

        private Result<Offset> offset(TypeNode type, List<Step> path) {
            if (path.isEmpty() || !(path.getFirst() instanceof Member))
                return failure(Reason.INVALID_PATH, "A member path must begin with a named class member");
            var root = type(type, true);
            if (!root.supported()) return propagate(root);
            TypeNode current = type;
            boolean checkCompleteness = true;
            Layout layout = root.value().orElseThrow();
            BigInteger offset = BigInteger.ZERO;
            var members = new ArrayList<VariableDeclaration>();
            for (Step step : path) {
                TypeNode plain = TypeSemantics.unqualified(current);
                if (step instanceof Member member) {
                    if (!(plain instanceof NamedType named) || !(named.declaration().orElse(null) instanceof ClassDeclaration declaration))
                        return failure(Reason.INVALID_PATH, "Named member access requires a POD class or union subobject");
                    ClassDeclaration owner = definition(declaration);
                    if (owner.members().stream().anyMatch(value -> value instanceof VariableDeclaration field
                            && field.name().equals(member.name()) && field.bitWidth().isPresent()))
                        return failure(Reason.BIT_FIELD, "A bit-field cannot be designated by offsetof");
                    var selected = layout.fields().stream().filter(field -> field.declaration().name().equals(member.name())).toList();
                    if (selected.size() != 1) {
                        if (owner.members().stream().anyMatch(value -> value instanceof VariableDeclaration field
                                && field.name().equals(member.name()) && field.modifiers().contains("static")))
                            return failure(Reason.STATIC_MEMBER, "A static data member has no object offset");
                        if (owner.members().stream().anyMatch(value -> value instanceof CallableDeclaration function
                                && !function.isFriend() && function.name().equals(member.name())))
                            return failure(Reason.FUNCTION_MEMBER, "A function member has no object offset");
                        return failure(Reason.MISSING_MEMBER, "No unique nonstatic object member named " + member.name());
                    }
                    Field field = selected.getFirst();
                    offset = offset.add(field.offsetBytes());
                    members.add(field.declaration());
                    current = field.declaration().type().orElseThrow();
                    checkCompleteness = true;
                } else if (step instanceof Index index) {
                    if (!(plain instanceof ArrayType array)) return failure(Reason.INVALID_PATH, "A constant subscript requires an array subobject");
                    var value = integer(index.expression());
                    if (value.isEmpty()) return constantFailure(Reason.NON_CONSTANT_INDEX,
                            "Index is not proven to be an integral constant under the supplied snapshot context");
                    var count = array.bound().flatMap(this::integer);
                    if (count.isEmpty()) return constantFailure(Reason.UNKNOWN_ARRAY_BOUND, "Array bound is not proven constant under the supplied snapshot context");
                    if (value.get().signum() < 0 || value.get().compareTo(count.get()) >= 0)
                        return failure(Reason.INDEX_OUT_OF_BOUNDS, "Array index must be nonnegative and less than its bound");
                    checkCompleteness &= !throughAlias(current);
                    var element = type(array.elementType(), checkCompleteness);
                    if (!element.supported()) return propagate(element);
                    offset = offset.add(element.value().orElseThrow().sizeBytes().multiply(value.get()));
                    current = array.elementType();
                }
                if (!supportedSize(offset)) return unsupportedSize(offset);
                var next = type(current, checkCompleteness);
                if (!next.supported()) return propagate(next);
                layout = next.value().orElseThrow();
            }
            return success(new Offset(offset, current, members));
        }
        private boolean throughAlias(TypeNode type) {
            while (type instanceof QualifiedType qualified) type = qualified.baseType();
            return type instanceof NamedType named && named.alias().isPresent();
        }

        private boolean enumSelected() {
            return profile.offsetof().map(model -> model.enumStorage() != null).orElse(false);
        }
        private record EnumFacts(ConstantEvaluator.IntegerType storage,
                ConstantEvaluator.IntegerType nativePromotion, List<BigInteger> numbers) {}
        private <T> Result<T> enumFailure(String detail) {
            return failure(Reason.UNSUPPORTED_ENUM_SEMANTICS, detail);
        }
        private Result<EnumFacts> enumFacts(EnumDeclaration owner) {
            if (!enumSelected()) return failure(Reason.UNSUPPORTED_TYPE, "Enum storage requires explicit offsetof.enumStorage");
            if (declarations == null || !owned(owner)) return enumFailure("Enum declaration is outside this snapshot");
            if (!context.layoutGuards.activeEnums.add(owner)) return enumFailure("Recursive enum representation dependency");
            try {
                var values = context.inLayout(this, () -> EnumSemantics.valuesBefore(owner, null, profile, constants, this::lexicalValue));
                if (values.isEmpty()) return constantFailure(Reason.UNSUPPORTED_ENUM_SEMANTICS, "Enum initializer values are not certified in the bounded context");
                List<BigInteger> numbers = values.orElseThrow().values().stream().map(ConstantEvaluator.Value::number).toList();
                if (numbers.isEmpty()) numbers = List.of(BigInteger.ZERO);
                var signed = ConstantEvaluator.integerType("int", profile).orElseThrow();
                var unsigned = ConstantEvaluator.integerType("unsigned int", profile).orElseThrow();
                boolean wider = numbers.stream().anyMatch(n -> !signed.contains(n) && !unsigned.contains(n));
                boolean negative = numbers.stream().anyMatch(n -> n.signum() < 0);
                ConstantEvaluator.IntegerType storage = null, promotion = null;
                for (String name : negative ? List.of("int", "long") : List.of("unsigned int", "unsigned long")) {
                    var candidate = ConstantEvaluator.integerType(name, profile).orElseThrow();
                    if ((!wider && candidate.bits() > signed.bits()) || !numbers.stream().allMatch(candidate::contains)) continue;
                    storage = candidate; break;
                }
                if (storage == null) return enumFailure("Enum range has no selected representation consistent with original C++03 7.2");
                for (String name : List.of("int", "unsigned int", "long", "unsigned long")) {
                    var candidate = ConstantEvaluator.integerType(name, profile).orElseThrow();
                    if (numbers.stream().allMatch(candidate::contains)) { promotion = candidate; break; }
                }
                if (promotion == null) return enumFailure("Enum promotion is outside selected integer types");
                return success(new EnumFacts(storage, promotion, numbers));
            } finally { context.layoutGuards.activeEnums.remove(owner); }
        }
        private Result<Layout> enumLayout(EnumDeclaration owner) {
            var facts = enumFacts(owner);
            return facts.supported() ? scalar(facts.value().orElseThrow().storage().spelling()) : propagate(facts);
        }
        private boolean compatibleEnum(EnumDeclaration owner) {
            var facts = enumFacts(owner);
            if (!facts.supported()) { dependencyFailure = facts.failure().orElseThrow(); return false; }
            var value = facts.value().orElseThrow();
            var original = EnumSemantics.underlyingValues(value.numbers(), profile)
                    .flatMap(type -> EnumSemantics.promoteUnderlying(type, profile));
            if (original.isPresent() && original.orElseThrow().equals(value.nativePromotion())) return true;
            dependencyFailure = new Failure(Reason.UNSUPPORTED_ENUM_SEMANTICS,
                    "Original C++03 enum promotion is unknown or differs from selected native emission");
            return false;
        }
        private boolean compatibleType(TypeNode type) {
            return EnumSemantics.declaration(type).map(this::compatibleEnum).orElse(true);
        }
        private Declaration target(Expression expression) {
            return expression instanceof NameExpression name ? name.target().orElse(null)
                    : expression instanceof ScopeMemberExpression member ? member.target().orElse(null) : null;
        }
        /** A known scalar condition or a proven lexical integer fallback. Class
         * conversion operators are outside this bounded proof; values are not run. */
        private boolean booleanCondition(Expression expression) {
            return EnumSemantics.objectType(expression).or(() -> TypeSemantics.expressionType(expression, profile, constants))
                    .map(type -> TypeSemantics.scalar(type, profile)).orElse(false)
                    || boundedIntegerType(expression).isPresent();
        }
        /** A narrow lexical-initializer fallback, not a replacement type engine. */
        private Optional<ConstantEvaluator.IntegerType> boundedIntegerType(Expression expression) {
            Declaration target = target(expression);
            if (target instanceof EnumeratorDeclaration enumerator) {
                var local = ((ConstantEvaluator.Context) constants).local(enumerator);
                if (local.isPresent()) return constants.apply(enumerator).map(ConstantEvaluator.Value::type);
            }
            var enumeration = expressionEnum(expression);
            if (enumeration.isPresent()) {
                var facts = enumFacts(enumeration.orElseThrow());
                return facts.value().flatMap(value -> EnumSemantics.underlyingValues(value.numbers(), profile)
                        .flatMap(type -> EnumSemantics.promoteUnderlying(type, profile)));
            }
            if (expression.operatorTarget().isPresent())
                return EnumSemantics.objectType(expression).flatMap(type -> TypeSemantics.integerType(type, profile));
            if (expression instanceof ConditionalExpression conditional) {
                if (!booleanCondition(conditional.condition())) return Optional.empty();
                var a = boundedIntegerType(conditional.thenExpression());
                var b = boundedIntegerType(conditional.elseExpression());
                if (a.isEmpty() || b.isEmpty()) return Optional.empty();
                return a.equals(b) ? a : ConstantEvaluator.commonType(a.orElseThrow(), b.orElseThrow(), profile);
            }
            if (expression instanceof UnaryExpression unary) {
                if (expression instanceof LogicalNotExpression) return booleanCondition(unary.operand())
                        ? ConstantEvaluator.integerType("bool", profile) : Optional.empty();
                if (!(expression instanceof UnaryPlusExpression || expression instanceof NegateExpression || expression instanceof BitwiseNotExpression)) return Optional.empty();
                return boundedIntegerType(unary.operand()).flatMap(type -> ConstantEvaluator.promote(type, profile));
            }
            var known = EnumSemantics.objectType(expression).or(() -> TypeSemantics.expressionType(expression, profile, constants))
                    .flatMap(type -> TypeSemantics.integerType(type, profile));
            if (known.isPresent()) return known;
            if (expression instanceof BinaryExpression binary) {
                var a = boundedIntegerType(binary.left()); var b = boundedIntegerType(binary.right());
                if (a.isEmpty() || b.isEmpty()) return Optional.empty();
                if (binary instanceof ShiftLeftExpression || binary instanceof ShiftRightExpression)
                    return ConstantEvaluator.promote(a.orElseThrow(), profile);
                if (binary instanceof EqualExpression || binary instanceof NotEqualExpression || binary instanceof LessExpression
                        || binary instanceof GreaterExpression || binary instanceof LessOrEqualExpression || binary instanceof GreaterOrEqualExpression
                        || binary instanceof LogicalAndExpression || binary instanceof LogicalOrExpression)
                    return ConstantEvaluator.integerType("bool", profile);
                if (!(binary instanceof AddExpression || binary instanceof SubtractExpression || binary instanceof MultiplyExpression
                        || binary instanceof DivideExpression || binary instanceof RemainderExpression || binary instanceof BitwiseAndExpression
                        || binary instanceof BitwiseOrExpression || binary instanceof BitwiseXorExpression)) return Optional.empty();
                return ConstantEvaluator.commonType(a.orElseThrow(), b.orElseThrow(), profile);
            }
            return Optional.empty();
        }
        private Optional<TypeNode> boundedExpressionType(Expression expression) {
            if (!enumSelected()) return TypeSemantics.expressionType(expression, profile, constants);
            if (expression instanceof LogicalNotExpression && expression.operatorTarget().isEmpty())
                return boundedIntegerType(expression).map(type -> NamedType.create().setName(type.spelling()).build());
            if (target(expression) instanceof EnumeratorDeclaration enumerator) {
                var local = ((ConstantEvaluator.Context) constants).local(enumerator);
                if (local.isPresent()) return constants.apply(enumerator).map(value -> NamedType.create().setName(value.type().spelling()).build());
            }
            if (expression instanceof ConditionalExpression && expressionEnum(expression).isEmpty()) {
                var integer = boundedIntegerType(expression);
                if (integer.isPresent()) return integer.map(value -> NamedType.create().setName(value.spelling()).build());
            }
            if (expression instanceof UnaryPlusExpression || expression instanceof NegateExpression || expression instanceof BitwiseNotExpression) {
                var integer = boundedIntegerType(expression);
                if (integer.isPresent()) return integer.map(value -> NamedType.create().setName(value.spelling()).build());
            }
            var exact = EnumSemantics.objectType(expression);
            if (exact.isPresent()) return exact;
            var known = TypeSemantics.expressionType(expression, profile, constants);
            if (known.isPresent()) return known;
            return boundedIntegerType(expression).map(type -> NamedType.create().setName(type.spelling()).build());
        }
        Optional<ConstantEvaluator.Value> lexicalValue(Expression expression,
                Function<Declaration, Optional<ConstantEvaluator.Value>> values) {
            var previous = constants; constants = values;
            try {
                if (!safeConstant(expression, Collections.newSetFromMap(new IdentityHashMap<>()))) return Optional.empty();
                var result = context.inLayout(this, () -> ConstantEvaluator.integralConstant(expression, profile, values));
                if (expression instanceof ConditionalExpression && result.isPresent()) {
                    var type = boundedIntegerType(expression);
                    if (type.isPresent() && type.orElseThrow().contains(result.orElseThrow().number()))
                        return Optional.of(new ConstantEvaluator.Value(result.orElseThrow().number(), type.orElseThrow()));
                }
                return result;
            } finally { constants = previous; }
        }
        /** Operand values are not evaluated, but promoted/type-combining syntax must
         * still agree with emission. A direct enum object has no promotion. */
        private boolean safeUnevaluated(Expression expression, Set<Declaration> active) {
            if (!enumSelected()) return true;
            if (expression instanceof SizeofExpression nested) return safeConstant(nested, active);
            if (expression instanceof OffsetofExpression offset) return safeConstant(offset, active);
            for (Node child : expression.children())
                if (child instanceof Expression operand && !safeUnevaluated(operand, active)) return false;
            if (expression instanceof LogicalNotExpression logical && expression.operatorTarget().isEmpty()
                    && !booleanCondition(logical.operand())) return false;
            // Unevaluated overload resolution can change the selected return type.
            List<Expression> arguments = expression instanceof CallExpression call ? call.arguments()
                    : expression.operatorTarget().isPresent() ? expression.children().stream()
                            .filter(Expression.class::isInstance).map(Expression.class::cast).toList() : List.of();
            for (Expression argument : arguments) if (!compatibleOperand(argument)) return false;
            if (expression instanceof NameExpression || expression instanceof ScopeMemberExpression
                    || expression instanceof AddressOfExpression || expression instanceof DereferenceExpression
                    || expression instanceof IndexExpression || expression instanceof CallExpression
                    || expression instanceof MemberExpression || expression instanceof ConversionExpression
                    || expression instanceof InitializationExpression) return true;
            if (expression instanceof ConditionalExpression conditional) {
                if (!booleanCondition(conditional.condition())) return false;
                if (expressionEnum(conditional).isPresent()) return true;
            }
            for (Node child : expression.children())
                if (child instanceof Expression operand && !compatibleOperand(operand)) return false;
            return true;
        }
        /** Preserve only proven enum identity through same-enum conditionals.
         * Lexical earlier enumerators instead have their initializer integer types. */
        private Optional<EnumDeclaration> expressionEnum(Expression expression) {
            Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                    : expression instanceof ScopeMemberExpression member ? member.target().orElse(null) : null;
            if (target instanceof EnumeratorDeclaration enumerator)
                return ((ConstantEvaluator.Context) constants).local(enumerator).isPresent() ? Optional.empty()
                        : Optional.ofNullable(enumOwners.get(enumerator));
            var exact = EnumSemantics.objectType(expression).flatMap(EnumSemantics::declaration);
            if (exact.isPresent()) return exact;
            if (expression instanceof ConditionalExpression conditional) {
                var left = expressionEnum(conditional.thenExpression());
                var right = expressionEnum(conditional.elseExpression());
                if (left.isPresent() && right.isPresent() && left.orElseThrow() == right.orElseThrow()) return left;
            }
            return Optional.empty();
        }
        private boolean compatibleOperand(Expression operand) {
            var enumeration = expressionEnum(operand);
            if (enumeration.isPresent()) return compatibleEnum(enumeration.orElseThrow());
            Declaration target = operand instanceof NameExpression name ? name.target().orElse(null)
                    : operand instanceof ScopeMemberExpression member ? member.target().orElse(null) : null;
            if (target instanceof EnumeratorDeclaration enumerator
                    && ((ConstantEvaluator.Context) constants).local(enumerator).isPresent()) return true;
            var type = boundedExpressionType(operand);
            if (type.isPresent() && !compatibleType(type.orElseThrow())) return false;
            return !(target instanceof EnumeratorDeclaration enumerator) || compatibleEnum(enumOwners.get(enumerator));
        }

        /** Evaluated only by the active bounded constant context, with the same guards,
         * object ceiling and lexical values as the enclosing offsetof. */
        Optional<BigInteger> sizeof(SizeofExpression expression,
                Function<Declaration, Optional<ConstantEvaluator.Value>> values) {
            var previous = constants;
            constants = values;
            try {
                if (expression.operand().isPresent() && !safeUnevaluated(expression.operand().orElseThrow(), Collections.newSetFromMap(new IdentityHashMap<>()))) return Optional.empty();
                var enumeration = expression.operand().flatMap(this::expressionEnum);
                var operand = expression.type().or(() -> expression.operand().flatMap(this::boundedExpressionType));
                if (operand.isEmpty() && enumeration.isEmpty()) return Optional.empty();
                var result = enumeration.isPresent() ? enumLayout(enumeration.orElseThrow()) : type(operand.orElseThrow(), true);
                if (!result.supported()) {
                    Failure failure = result.failure().orElseThrow();
                    dependencyFailure = failure.reason() == Reason.NOT_POD
                            ? new Failure(Reason.UNSUPPORTED_TYPE, "sizeof dependency is outside the supported POD representation subset: " + failure.detail())
                            : failure;
                }
                return result.value().map(Layout::sizeBytes);
            } finally { constants = previous; }
        }

        private Optional<BigInteger> integer(Expression expression) {
            dependencyFailure = null;
            // Preflight sizeof dependencies through this query's recursion guards before
            // invoking the shared evaluator, whose generic sizeof starts a fresh traversal.
            if (!safeConstant(expression, Collections.newSetFromMap(new IdentityHashMap<>()))) return Optional.empty();
            return ConstantEvaluator.integralConstant(expression, profile, constants).map(ConstantEvaluator.Value::number);
        }
        private boolean safeConstant(Expression expression, Set<Declaration> active) {
            if (expression instanceof SizeofExpression size) {
                return sizeof(size, constants).isPresent();
            }
            if (expression instanceof OffsetofExpression offset) {
                var context = ((ConstantEvaluator.Context) constants).integralContext();
                var result = context.offsetofResult(offset, constants);
                if (!result.supported()) dependencyFailure = result.failure().orElseThrow();
                return result.supported();
            }
            Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                    : expression instanceof ScopeMemberExpression member ? member.target().orElse(null) : null;
            if (target != null) {
                if (declarations == null || !owned(target)) return false;
                if (target instanceof EnumeratorDeclaration enumerator) {
                    EnumDeclaration owner = enumOwners.get(enumerator);
                    if (owner == null) return false;
                    if (((ConstantEvaluator.Context) constants).local(enumerator).isPresent()) {
                        // Initializer types inside the enum depend only on used values;
                        // an implicit value additionally depends on its predecessor.
                        return safeLexicalEnumerator(enumerator, owner, active);
                    }
                    if (enumSelected()) return compatibleEnum(owner);
                    if (!active.add(owner)) return safeEnumerator(enumerator, active);
                    try {
                        // Underlying-type evaluation can inspect every enumerator, but
                        // only an initializer currently being checked is a dependency cycle.
                        return owner.enumerators().stream().allMatch(value -> safeEnumerator(value, active));
                    } finally { active.remove(owner); }
                }
                if (!active.add(target)) return false;
                try {
                    if (target instanceof VariableDeclaration variable) {
                        if (enumSelected() && variable.type().isPresent() && !compatibleType(variable.type().orElseThrow())) return false;
                        return variable.initializer().map(value -> safeConstant(value, active)).orElse(false);
                    }
                    return false;
                } finally { active.remove(target); }
            }
            if (enumSelected() && expression instanceof ConversionExpression conversion && !compatibleType(conversion.targetType())) return false;
            if (enumSelected() && expression instanceof InitializationExpression initialization
                    && initialization.type().isPresent() && !compatibleType(initialization.type().orElseThrow())) return false;
            return expression.children().stream().filter(Expression.class::isInstance).map(Expression.class::cast)
                    .allMatch(child -> safeConstant(child, active));
        }
        private boolean safeLexicalEnumerator(EnumeratorDeclaration enumerator, EnumDeclaration owner, Set<Declaration> active) {
            if (!active.add(enumerator)) return false;
            try {
                if (enumerator.value().isPresent()) return safeConstant(enumerator.value().orElseThrow(), active);
                int index = owner.enumerators().indexOf(enumerator);
                return index == 0 || safeLexicalEnumerator(owner.enumerators().get(index - 1), owner, active);
            } finally { active.remove(enumerator); }
        }
        private boolean safeEnumerator(EnumeratorDeclaration enumerator, Set<Declaration> active) {
            if (!active.add(enumerator)) return false;
            try {
                return enumerator.value().map(value -> safeConstant(value, active)).orElse(true);
            } finally { active.remove(enumerator); }
        }
    }
}
