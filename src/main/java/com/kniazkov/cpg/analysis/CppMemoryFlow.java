/* Object-sensitive C++ reaching definitions and contextual call effects. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;

/**
 * Object storage is distinct from the pointer/reference that denotes it. Calls run
 * the callee CFG with actual storage bindings, not independent per-parameter
 * effects: two references to the same actual therefore preserve statement order.
 * Recursive contexts use finite may-effect approximations, never a speculative kill.
 */
final class CppMemoryFlow {
    private final Map<CallableDeclaration, FunctionFlow> flows = new IdentityHashMap<>();
    private final Map<Declaration,Declaration> externalObjects;
    private final Map<CallableDeclaration, PlatformProfile> profiles = new IdentityHashMap<>();
    private final Set<Node> normalized = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Declaration, CallableDeclaration> owners = new IdentityHashMap<>();
    // Offsets outside the finite source-constant domain widen to an unknown index.
    // Loops and recursive pointer arithmetic therefore cannot grow the lattice forever.
    private final Set<String> indices = new HashSet<>(List.of("-1","0","1"));
    private final Set<VariableDeclaration> globals = new LinkedHashSet<>();
    private final Map<Declaration,String> fieldKeys=new IdentityHashMap<>();
    private final Map<Declaration,ClassDeclaration> fieldOwners=new IdentityHashMap<>();
    private final List<VariableDeclaration> fieldDeclarations=new ArrayList<>();
    private final Set<Declaration> fields = Collections.newSetFromMap(new IdentityHashMap<>());

    private CppMemoryFlow(Project project) {
        externalObjects=LibraryExternalObjects.representatives(project);
        var contexts = SourceContexts.index(project);
        for (var flow : project.flows()) {
            if (SourceContexts.require(contexts, flow.entry()).language() != Language.CPP) continue;
            flows.put(flow.entry(), flow);
            profiles.put(flow.entry(), TypeSemantics.profile(SourceContexts.require(contexts, flow.entry())));
            flow.entry().walk(new NodeVisitor() {
                @Override public void visit(Declaration declaration) { owners.put(declaration, flow.entry()); }
            });
        }
        project.walk(new NodeVisitor() {
            @Override public void visit(IntegerLiteral literal) { indices.add(literal.bigValue().toString()); }
            @Override public void visit(ClassDeclaration cls) {
                for (Declaration member : cls.members()) if (member instanceof VariableDeclaration variable && !variable.modifiers().contains("static")) {
                    fields.add(member);
                    if(!fieldKeys.containsKey(member)) { fieldKeys.put(member,".field"+fieldKeys.size());fieldDeclarations.add(variable); }
                    fieldOwners.put(member,cls);
                }
            }
        });
        project.walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration variable) {
                if (!owners.containsKey(variable) && !fields.contains(variable)
                        && (!LibraryDeclarations.isLibrary(variable) || externalObjects.containsKey(variable)))
                    globals.add((VariableDeclaration)externalObjects.getOrDefault(variable,variable));
            }
        });
        project.dynamicArraySemantics().forEach(op -> normalized.add(op.syntax()));
        project.containerSemantics().forEach(op -> normalized.add(op.syntax()));
        project.sequenceSemantics().forEach(op -> normalized.add(op.syntax()));
    }

    static Project analyze(Project project) {
        var analysis = new CppMemoryFlow(project);
        return project.withFlows(project.flows().stream().map(analysis::analyze).toList());
    }

    private static List<CallableDeclaration> constructors(VariableDeclaration variable) {
        if(variable.constructorTarget().isPresent())return List.of(variable.constructorTarget().get());
        return variable.type().flatMap(ClassSemantics::objectClass).map(cls->cls.members().stream()
            .filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
            .filter(c->c.kind()==CallableDeclaration.Kind.CONSTRUCTOR&&!LibraryCallEffects.unknown(c)).toList()).orElse(List.of());
    }

    private Loc memberLocation(Loc object,Declaration field) {
        String key=fieldKeys.getOrDefault(field,".?");
        TypeNode type=(object.root() instanceof VariableDeclaration v?v.type():object.root() instanceof ParameterDeclaration p?p.type():Optional.<TypeNode>empty()).orElse(null);
        var path=java.util.regex.Pattern.compile("\\.field([0-9]+)").matcher(object.path());
        while(path.find())type=fieldDeclarations.get(Integer.parseInt(path.group(1))).type().orElse(null);
        if(type!=null) {
            type=TypeSemantics.withoutReference(type);
            if(type instanceof PointerType pointer)type=pointer.pointee();
            ClassDeclaration owner=fieldOwners.get(field);
            var actual=ClassSemantics.objectClass(type);
            if(owner!=null&&actual.isPresent()&&ownerPaths(actual.get(),owner,Collections.newSetFromMap(new IdentityHashMap<>()))!=1)key+="?";
        }
        return object.child(key);
    }
    /** Multiple inheritance views have no asserted ABI offset; duplicate owner paths are weak. */
    private static int ownerPaths(ClassDeclaration type,ClassDeclaration owner,Set<ClassDeclaration> active) {
        if(type==owner)return 1;
        if(!active.add(type))return 0;
        int count=0;
        for(BaseSpecifier base:type.bases()) {
            var parent=ClassSemantics.objectClass(base.type());
            if(parent.isPresent())count+=ownerPaths(parent.get(),owner,active);
            if(count>1)break;
        }
        active.remove(type);return count;
    }

    /** Value aggregates may contain pointer-bearing aggregates at any finite depth. */
    private void pointerFields(Loc object,ClassDeclaration type,Set<Loc> result,Set<ClassDeclaration> active) {
        if(!active.add(type))return;
        for(VariableDeclaration field:ClassSemantics.fields(type)) {
            TypeNode memberType=field.type().map(TypeSemantics::unqualified).orElse(null);
            Loc location=memberLocation(object,field);
            if(memberType instanceof PointerType || memberType instanceof ReferenceType)result.add(location);
            else if(memberType!=null)ClassSemantics.objectClass(memberType).ifPresent(nested->pointerFields(location,nested,result,active));
        }
        for(BaseSpecifier base:type.bases())ClassSemantics.objectClass(base.type()).ifPresent(parent->pointerFields(object,parent,result,active));
        active.remove(type);
    }

    private void referenceFields(Loc object,ClassDeclaration type,Set<Loc> result,Set<ClassDeclaration> active) {
        if(!active.add(type))return;
        for(VariableDeclaration field:ClassSemantics.fields(type)) {
            TypeNode memberType=field.type().map(TypeSemantics::unqualified).orElse(null);
            Loc location=memberLocation(object,field);
            if(memberType instanceof ReferenceType)result.add(location);
            else if(memberType!=null)ClassSemantics.objectClass(memberType).ifPresent(nested->referenceFields(location,nested,result,active));
        }
        for(BaseSpecifier base:type.bases())ClassSemantics.objectClass(base.type()).ifPresent(parent->referenceFields(object,parent,result,active));
        active.remove(type);
    }

    private FunctionFlow analyze(FunctionFlow flow) {
        if (!flows.containsKey(flow.entry()) || !flow.complete()) return flow;
        var session = new Session();
        Set<Node> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        var discover=new ArrayDeque<Node>();discover.add(flow.entry());
        while(!discover.isEmpty()) {
            Node node=discover.remove(); if(!visited.add(node))continue; discover.addAll(node.children());
            if(node instanceof NameExpression name)name.target().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                .map(variable -> (VariableDeclaration)externalObjects.getOrDefault(variable,variable))
                .filter(globals::contains).ifPresent(session.accessibleGlobals::add);
            if(node instanceof CallExpression call) { call.target().ifPresent(discover::add); discover.addAll(call.virtualTargets());call.defaultArguments().forEach(a->discover.add(a.expression())); }
            if(node instanceof Expression expression)expression.operatorTarget().ifPresent(discover::add);
            if(node instanceof VariableDeclaration variable) { constructors(variable).forEach(discover::add);variable.destructorTarget().ifPresent(discover::add); }
        }
        do {
            session.changed = false;
            session.edges.clear(); session.reads.clear();
            var initial = new State();
            for (VariableDeclaration global : session.accessibleGlobals) if (global.initializer().isPresent())
                initial.defs.put(new Loc(global,"",null),new LinkedHashSet<>(List.of(new Source(global,null))));
            for (var parameter : flow.entry().parameters()) {
                Loc location = new Loc(parameter, "", null);
                initial.defs.put(location, new LinkedHashSet<>(List.of(new Source(parameter, null))));
                TypeNode type = parameter.type().map(TypeSemantics::unqualified).orElse(null);
                if (type instanceof ReferenceType) initial.refs.put(parameter, Set.of(location));
                if (type instanceof PointerType) initial.pointers.put(location, Set.of(location.child("*")));
            }
            // At an unknown entry, distinct reference/pointer formals do not
            // prove distinct actual objects. Concrete calls replace these symbolic
            // bindings with their caller's exact locations below.
            var possibleObjects=new LinkedHashSet<Loc>();
            for(VariableDeclaration global:session.accessibleGlobals)possibleObjects.add(new Loc(global,"",null));
            for(ParameterDeclaration parameter:flow.entry().parameters()) {
                TypeNode type=parameter.type().map(TypeSemantics::unqualified).orElse(null);
                if(type instanceof ReferenceType)possibleObjects.add(new Loc(parameter,"",null));
                else if(type instanceof PointerType)possibleObjects.add(new Loc(parameter,"*",null));
            }
            flow.entry().walk(new NodeVisitor(){@Override public void visit(NameExpression name){
                name.target().filter(fields::contains).ifPresent(field->possibleObjects.add(new Loc(field,"",null)));
            }});
            for(ParameterDeclaration parameter:flow.entry().parameters()) {
                TypeNode type=parameter.type().map(TypeSemantics::unqualified).orElse(null);
                if(type instanceof ReferenceType)initial.refs.put(parameter,new LinkedHashSet<>(possibleObjects));
                else if(type instanceof PointerType)initial.pointers.put(new Loc(parameter,"",null),new LinkedHashSet<>(possibleObjects));
            }
            var pointerFields=new LinkedHashSet<Loc>();
            for(ParameterDeclaration parameter:flow.entry().parameters()) {
                parameter.type().map(TypeSemantics::withoutReference).flatMap(ClassSemantics::objectClass).ifPresent(cls->
                    pointerFields(new Loc(parameter,"",null),cls,pointerFields,Collections.newSetFromMap(new IdentityHashMap<>())));
            }
            var unknownPointees=new LinkedHashSet<Loc>(possibleObjects);
            for(Loc location:pointerFields)unknownPointees.add(location.child("*"));
            for(Loc location:pointerFields)initial.pointers.put(location,new LinkedHashSet<>(unknownPointees));
            session.run(flow, initial, null, null, Set.of());
        } while (session.recursive && session.changed);
        var edges = new LinkedHashSet<DataEdge>();
        for (var edge : flow.data())
            if (edge.kind() != Dependency.DEFINITION || !session.reads.contains(edge.to())) edges.add(edge);
        edges.addAll(session.edges);
        return new FunctionFlow(flow.entry(), flow.exit(), flow.control(), List.copyOf(edges), flow.complete());
    }

    /** A bounded access path: unknown indices and recursive indirection are weak. */
    private record Loc(Declaration root, String path, CallExpression frame) {
        Loc child(String part) {
            return new Loc(root, path.contains("?") || path.contains("*") ? path : path.length() > 100 ? "?" : path + part, frame);
        }
        boolean exact() { return !path.contains("?") && !path.contains("*"); }
    }
    // Analysis-only storage, never an AST declaration. Different opaque results may
    // denote the same inaccessible nonlocal cell. The wildcard path forces weak
    // updates; known address-exposed roots remain additional alias candidates.
    private static final Loc UNKNOWN_NONLOCAL = new Loc(null, "?", null);
    private record Source(Node node, CallExpression call) { }
    private static final class State {
        final Map<Loc, Set<Source>> defs = new LinkedHashMap<>();
        final Set<Loc> guaranteed = new LinkedHashSet<>();
        final Set<Loc> escaped = new LinkedHashSet<>();
        final Map<Loc, Set<Loc>> pointers = new LinkedHashMap<>();
        final Map<Declaration, Set<Loc>> refs = new LinkedHashMap<>();
        State copy() { var result = new State(); result.merge(this); result.guaranteed.addAll(guaranteed); return result; }
        boolean merge(State other) {
            if (other == null) return false;
            boolean changed = guaranteed.retainAll(other.guaranteed);
            changed |= escaped.addAll(other.escaped);
            changed |= mergeMap(defs, other.defs);
            changed |= mergeMap(pointers, other.pointers);
            changed |= mergeMap(refs, other.refs);
            return changed;
        }
        static <K,V> boolean mergeMap(Map<K,Set<V>> into, Map<K,Set<V>> from) {
            boolean changed = false;
            for (var entry : from.entrySet()) changed |= into.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
            return changed;
        }
        void replace(State other) {
            defs.clear(); pointers.clear(); refs.clear(); guaranteed.clear(); escaped.clear(); merge(other); guaranteed.addAll(other.guaranteed);
        }
        @Override public boolean equals(Object obj) {
            return obj instanceof State s && defs.equals(s.defs) && pointers.equals(s.pointers) && refs.equals(s.refs) && guaranteed.equals(s.guaranteed) && escaped.equals(s.escaped);
        }
        @Override public int hashCode() { return defs.hashCode(); }
    }
    private record Result(State normal, State exceptional, Set<Loc> returned, Set<ReturnStatement> returns) { }
    private record Key(CallExpression site, CallableDeclaration target, List<Set<Loc>> bindings, CallExpression origin) { }

    private final class Session {
        final Set<VariableDeclaration> accessibleGlobals=new LinkedHashSet<>();
        final Set<DataEdge> edges = new LinkedHashSet<>();
        final Set<Node> reads = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Key> active = new HashSet<>();
        final Map<Key, Result> summaries = new HashMap<>();
        boolean recursive, changed;

        Result run(FunctionFlow flow, State initial, CallExpression frame, CallExpression provenance, Set<Loc> receiver) {
            var context = new Context(flow, frame, provenance, receiver);
            Map<FlowVertex, State> inputs = new LinkedHashMap<>();
            inputs.put(flow.entry(), initial.copy());
            var pending = new ArrayDeque<FlowVertex>(); pending.add(flow.entry());
            while (!pending.isEmpty()) {
                FlowVertex vertex = pending.remove();
                State before = inputs.get(vertex), after = before.copy();
                context.exceptional = null; context.normal = true;
                context.transfer(vertex, after);
                for (ControlEdge edge : context.successors.getOrDefault(vertex, List.of())) {
                    if (!context.normal && edge.kind() != Branch.EXCEPTION && edge.kind() != Branch.THROW) continue;
                    State propagated = after;
                    if (edge.kind() == Branch.EXCEPTION) {
                        propagated = context.exceptional;
                        if (propagated == null) { propagated = after.copy(); propagated.merge(before); }
                    }
                    State old = inputs.get(edge.to());
                    if (old == null) { inputs.put(edge.to(), propagated.copy()); pending.add(edge.to()); }
                    else if (old.merge(propagated)) pending.add(edge.to());
                }
            }
            State exceptional = flow.exceptionalExit().map(inputs::get).orElse(null);
            return new Result(inputs.get(flow.exit()), exceptional, Set.copyOf(context.returned), Set.copyOf(context.returns));
        }

        final class Context {
            final FunctionFlow flow;
            final CallExpression frame;
            final CallExpression provenance;
            final Set<Loc> receiver;
            final PlatformProfile profile;
            final Map<FlowVertex,List<ControlEdge>> successors = new IdentityHashMap<>();
            final Map<Node,Expression> expanded = new IdentityHashMap<>();
            final Set<Expression> expandedInitializerArguments=Collections.newSetFromMap(new IdentityHashMap<>());
            final Map<Node,List<DataEdge>> inheritedEffects = new IdentityHashMap<>();
            final Map<CallExpression,Set<Loc>> callResults = new IdentityHashMap<>();
            final Set<Loc> returned = new LinkedHashSet<>();
            final Set<ReturnStatement> returns = new LinkedHashSet<>();
            State exceptional;
            boolean normal = true;
            Context(FunctionFlow flow, CallExpression frame, CallExpression provenance, Set<Loc> receiver) {
                this.flow = flow; this.frame = frame; this.provenance = provenance; this.receiver = receiver; profile = profiles.get(flow.entry());
                coveredReads(flow.entry());
                Set<Expression> instructions=Collections.newSetFromMap(new IdentityHashMap<>());
                for(ControlEdge edge:flow.control()) {
                    if(edge.from() instanceof Expression expression)instructions.add(expression);
                    if(edge.to() instanceof Expression expression)instructions.add(expression);
                }
                for(MemberInitializer initializer:flow.entry().initializers())for(Expression argument:initializer.arguments())
                    if(instructions.stream().anyMatch(instruction->contains(argument,instruction)))expandedInitializerArguments.add(argument);
                for(DataEdge edge:flow.data())if(edge.kind()==Dependency.DEFINITION && edge.to() instanceof Expression) {
                    Node source=edge.from(), event=source instanceof SequenceOperation op?op.syntax():source instanceof ContainerOperation op?op.syntax():source instanceof DynamicArrayOperation op?op.syntax():source;
                    boolean opaque=source!=event || source instanceof MemberInitializer
                        || source instanceof AssignmentExpression assignment&&!modeled(assignment.target())
                        || source instanceof CompoundAssignmentExpression assignment&&!modeled(assignment.target())
                        || source instanceof Expression e&&(LibraryCallEffects.unknown(e)||OperatorSemantics.mayInvokeUserCode(e))
                        || source instanceof VariableDeclaration v&&(v.constructorTarget().filter(LibraryCallEffects::unknown).isPresent()||v.destructorTarget().filter(LibraryCallEffects::unknown).isPresent());
                    if(opaque)inheritedEffects.computeIfAbsent(event,k->new ArrayList<>()).add(edge);
                }
                for (var edge : flow.control()) {
                    successors.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
                    if (edge.from() instanceof Expression completed && edge.to() instanceof Node consumer) {
                        Expression value = consumer instanceof VariableDeclaration v ? v.initializer().orElse(null)
                            : consumer instanceof ReturnStatement r ? r.value().orElse(null)
                            : consumer instanceof PrintStatement print ? print.value()
                            : consumer instanceof AssignmentExpression a && a.target() instanceof NameExpression ? a.value() : null;
                        if (value != null && contains(value, completed)) expanded.put(consumer, value);
                    }
                }
            }
            void coveredReads(Node node) {
                if(normalized.contains(node)||node instanceof SizeofExpression
                    ||node instanceof TypeidExpression t&&TypeSemantics.typeidOperandUnevaluated(t,profile))return;
                if(node instanceof Expression expression && (expression instanceof NameExpression || expression instanceof MemberExpression
                    ||expression instanceof DereferenceExpression ||expression instanceof IndexExpression
                    ||expression instanceof DotMemberPointerExpression||expression instanceof ArrowMemberPointerExpression)&&modeled(expression))reads.add(expression);
                for(Node child:node.children())coveredReads(child);
                if(node instanceof CallExpression call)call.defaultArguments().forEach(a->coveredReads(a.expression()));
            }
            Loc root(Declaration declaration) {
                declaration=externalObjects.getOrDefault(declaration,declaration);
                return new Loc(declaration, "", owners.containsKey(declaration) && !(declaration instanceof VariableDeclaration v && v.modifiers().contains("static")) ? frame : null);
            }
            boolean modeled(Expression expression) {
                if (normalized.contains(expression)) return false;
                if(expression instanceof MemberExpression member&&!modeled(member.receiver()))return false;
                if(expression instanceof DereferenceExpression dereference&&!modeled(dereference.operand()))return false;
                if(expression instanceof IndexExpression index&&!modeled(index.left()))return false;
                return TypeSemantics.expressionType(expression, profile).map(TypeSemantics::withoutReference)
                    .flatMap(ClassSemantics::objectClass).filter(LibraryDeclarations::isLibrary).isEmpty()
                    && TypeSemantics.expressionType(expression, profile).flatMap(LibraryIteratorSemantics::info).isEmpty();
            }
            TypeNode type(Declaration declaration) {
                return (declaration instanceof VariableDeclaration v ? v.type()
                    : declaration instanceof ParameterDeclaration p ? p.type() : Optional.<TypeNode>empty())
                    .map(TypeSemantics::unqualified).orElse(null);
            }
            Expression unwrap(Expression expression) {
                while (expression instanceof ConversionExpression c) expression = c.operand();
                return expression;
            }
            Set<Loc> view(Set<Loc> locations,ConversionExpression conversion) {
                if(!conversion.isExplicit())return locations;
                boolean weak=conversion.kind()==ConversionExpression.Kind.REINTERPRET_CAST||conversion.kind()==ConversionExpression.Kind.C_STYLE_CAST;
                TypeNode target=TypeSemantics.withoutReference(conversion.targetType());
                if(target instanceof PointerType pointer)target=pointer.pointee();
                TypeNode source=TypeSemantics.expressionType(conversion.operand(),profile).map(TypeSemantics::withoutReference).orElse(null);
                if(source instanceof PointerType pointer)source=pointer.pointee();
                if(source!=null&&!TypeSemantics.sizeof(source,profile).equals(TypeSemantics.sizeof(target,profile)))weak=true;
                if(!weak)return locations;
                var result=new LinkedHashSet<Loc>();for(Loc location:locations)result.add(location.child("?"));return result;
            }
            Set<Loc> locations(Expression expression, State state) {
                if(expression instanceof ConversionExpression conversion)return view(locations(conversion.operand(),state),conversion);
                if (expression instanceof NameExpression name && name.target().isPresent()) {
                    Declaration declaration = name.target().get();
                    if (fields.contains(declaration) && !receiver.isEmpty()) {
                        var result = new LinkedHashSet<Loc>();
                        for (Loc location : receiver) result.add(memberLocation(location,declaration));
                        return referenceMember(result,declaration,state);
                    }
                    if (!(declaration instanceof VariableDeclaration || declaration instanceof ParameterDeclaration)) return Set.of();
                    return state.refs.getOrDefault(declaration, Set.of(root(declaration)));
                }
                if (expression instanceof DereferenceExpression pointer) return points(pointer.operand(), state);
                if(expression instanceof DotMemberPointerExpression || expression instanceof ArrowMemberPointerExpression) {
                    var member=(BinaryExpression)expression;
                    Set<Loc> bases=expression instanceof ArrowMemberPointerExpression?points(member.left(),state):locations(member.left(),state);
                    var result=new LinkedHashSet<Loc>();for(Loc base:bases)result.add(base.child("?"));return result;
                }
                if (expression instanceof MemberExpression member) {
                    if(member.target().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                        .filter(v->v.modifiers().contains("static")).isPresent())return Set.of(root(member.target().get()));
                    var bases = member instanceof PointerMemberExpression ? points(member.receiver(),state) : locations(member.receiver(),state);
                    if(bases.isEmpty()&&member.target().filter(VariableDeclaration.class::isInstance).isPresent())return Set.of(root(member.target().get()));
                    var result = new LinkedHashSet<Loc>();
                    for (var base : bases) result.add(memberLocation(base,member.target().orElse(null)));
                    return referenceMember(result,member.target().orElse(null),state);
                }
                if (expression instanceof IndexExpression index) {
                    TypeNode type = TypeSemantics.expressionType(index.left(), profile).map(TypeSemantics::unqualified).orElse(null);
                    var bases = type instanceof PointerType ? points(index.left(),state) : locations(index.left(),state);
                    String key = ConstantEvaluator.evaluate(index.right(),profile).map(v -> v.number().toString()).orElse("?");
                    var result = new LinkedHashSet<Loc>();
                    for (var base : bases) {
                        if (type instanceof PointerType && key.equals("0")) result.add(base);
                        else if (type instanceof PointerType && base.path().matches(".*\\[-?[0-9]+\\]$") && !key.equals("?")) {
                            int start=base.path().lastIndexOf('[');
                            var offset=new java.math.BigInteger(base.path().substring(start+1,base.path().length()-1)).add(new java.math.BigInteger(key));
                            result.add(new Loc(base.root(),base.path().substring(0,start)+"["+(indices.contains(offset.toString())?offset:"?")+"]",base.frame()));
                        } else result.add(base.child("[" + key + "]"));
                    }
                    return result;
                }
                if (expression instanceof ConditionalExpression c) {
                    var result = new LinkedHashSet<>(locations(c.thenExpression(),state)); result.addAll(locations(c.elseExpression(),state)); return result;
                }
                if (expression instanceof CallExpression call) return callResults.getOrDefault(call,Set.of());
                if (expression instanceof AssignmentExpression a) return locations(a.target(),state);
                if(expression instanceof CompoundAssignmentExpression a)return locations(a.target(),state);
                if(expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression)return locations(((UnaryExpression)expression).operand(),state);
                if(expression instanceof CommaExpression comma)return locations(comma.right(),state);
                // An unfamiliar supported lvalue must not prove disjointness or
                // silently erase its possible operand storage. Widen these roots.
                var possible=new LinkedHashSet<Loc>();
                for(Node child:expression.children())if(child instanceof Expression operand)
                    for(Loc location:locations(operand,state))possible.add(location.child("?"));
                return possible;
            }
            Set<Loc> referenceMember(Set<Loc> slots,Declaration field,State state) {
                if(!(field instanceof VariableDeclaration variable)||variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isEmpty())return slots;
                var result=new LinkedHashSet<Loc>();
                for(Loc slot:slots)result.addAll(state.pointers.getOrDefault(slot,Set.of(slot.child("*"))));
                return result;
            }
            void bindReferenceFields(VariableDeclaration variable,State state) {
                var objectClass=variable.type().flatMap(ClassSemantics::objectClass);
                if(objectClass.isEmpty())return;
                var slots=new LinkedHashSet<Loc>();
                referenceFields(root(variable),objectClass.get(),slots,Collections.newSetFromMap(new IdentityHashMap<>()));
                if(slots.isEmpty())return;
                var possible=new LinkedHashSet<Loc>(state.escaped);
                for(VariableDeclaration global:accessibleGlobals)possible.add(new Loc(global,"",null));
                variable.initializer().ifPresent(initializer->{
                    Expression value=unwrap(initializer);
                    List<Expression> arguments=value instanceof InitializationExpression init?init.arguments():List.of(value);
                    for(Expression argument:arguments) { possible.addAll(locations(argument,state));possible.addAll(points(argument,state)); }
                });
                for(Loc slot:slots)if(!state.pointers.containsKey(slot)) {
                    var uncertain=new LinkedHashSet<Loc>();
                    for(Loc location:possible)uncertain.add(location.child("?"));
                    uncertain.add(slot.child("*"));
                    state.pointers.put(slot,uncertain);
                }
            }
            Set<Loc> points(Expression expression, State state) {
                if(expression instanceof ConversionExpression conversion)return view(points(conversion.operand(),state),conversion);
                if (expression instanceof NameExpression name && name.name().equals("this")) return receiver;
                if (expression instanceof AddressOfExpression address) return locations(address.operand(),state);
                if (expression instanceof ConditionalExpression conditional) {
                    var result = new LinkedHashSet<>(points(conditional.thenExpression(),state)); result.addAll(points(conditional.elseExpression(),state)); return result;
                }
                if (expression instanceof CallExpression call) return callResults.getOrDefault(call,Set.of());
                if (expression instanceof AddExpression || expression instanceof SubtractExpression) {
                    var binary=(BinaryExpression)expression;
                    boolean leftPointer=TypeSemantics.expressionType(binary.left(),profile).map(TypeSemantics::withoutReference).map(TypeSemantics::unqualified)
                        .filter(t->t instanceof PointerType || t instanceof ArrayType).isPresent();
                    Expression pointer=leftPointer?binary.left():binary.right(), distance=leftPointer?binary.right():binary.left();
                    String offset=ConstantEvaluator.evaluate(distance,profile).map(v->v.number().toString()).orElse("?");
                    if(expression instanceof SubtractExpression&&!offset.equals("?"))offset=new java.math.BigInteger(offset).negate().toString();
                    return offset(points(pointer,state),offset);
                }
                var result = new LinkedHashSet<Loc>();
                for (Loc location : locations(expression,state)) {
                    if (type(location.root()) instanceof ArrayType && location.path().isEmpty()) result.add(location.child("[0]"));
                    else {
                        boolean found=false;
                        for(var entry:state.pointers.entrySet()) {
                            Loc slot=entry.getKey();
                            if(slot.root()==location.root()&&slot.frame()==location.frame()&&(slot.equals(location)||!slot.exact()||!location.exact())) {
                                result.addAll(entry.getValue());found=true;
                            }
                        }
                        if(!found)result.add(location.child("*"));
                    }
                }
                return result;
            }
            Set<Loc> offset(Set<Loc> bases,String distance) {
                var result=new LinkedHashSet<Loc>();
                for(Loc base:bases) {
                    if(distance.equals("0"))result.add(base);
                    else if(base.path().matches(".*\\[-?[0-9]+\\]$")&&!distance.equals("?")) {
                        int start=base.path().lastIndexOf('[');
                        var index=new java.math.BigInteger(base.path().substring(start+1,base.path().length()-1)).add(new java.math.BigInteger(distance));
                        result.add(new Loc(base.root(),base.path().substring(0,start)+"["+(indices.contains(index.toString())?index:"?")+"]",base.frame()));
                    } else result.add(base.child("[?]"));
                }
                return result;
            }
            Set<Source> definitions(Loc location, State state) {
                var result = new LinkedHashSet<Source>();
                boolean exact = state.guaranteed.contains(location);
                for (var entry : state.defs.entrySet()) {
                    Loc candidate = entry.getKey();
                    if (candidate.root() != location.root() || candidate.frame() != location.frame()) continue;
                    if (candidate.equals(location) || candidate.path().startsWith(location.path()+".") || candidate.path().startsWith(location.path()+"[")
                        || !candidate.exact() || !location.exact()
                        || !exact && (location.path().startsWith(candidate.path()+".") || location.path().startsWith(candidate.path()+"[")))
                        result.addAll(entry.getValue());
                }
                return result;
            }
            void definition(Source source,Node consumer) {
                // Bridge sibling invocations through caller-owned call nodes. This
                // also covers opaque/recursive reads inside a subsequent callee.
                if(provenance != null && source.call() != null && source.call() != provenance) {
                    edges.add(new DataEdge(source.node(),source.call(),Dependency.DEFINITION,source.call()));
                    edges.add(new DataEdge(source.call(),consumer,Dependency.DEFINITION,provenance));
                } else edges.add(new DataEdge(source.node(),consumer,Dependency.DEFINITION,source.call() == null ? provenance : source.call()));
            }
            void read(Expression expression, State state) {
                if (!modeled(expression)) return;
                if (expression instanceof NameExpression name) readBinding(name,state);
                Set<Loc> locations = locations(expression,state);
                if (locations.isEmpty()) return;
                reads.add(expression);
                for (Loc location : locations) for (Source source : definitions(location,state))definition(source,expression);
            }
            /** Using a local reference observes its immutable binding, even when only
             * computing an address. Parameter roots instead represent incoming objects. */
            void readBinding(NameExpression name, State state) {
                Declaration declaration = name.target().orElse(null);
                if (!(declaration instanceof VariableDeclaration) || fields.contains(declaration)
                        || !(type(declaration) instanceof ReferenceType) || !state.refs.containsKey(declaration)) return;
                for (Source source : state.defs.getOrDefault(root(declaration),Set.of())) definition(source,name);
            }
            void write(Expression target, Node source, State state) {
                if (!modeled(target)) return;
                Set<Loc> locations = locations(target,state);
                boolean strong = locations.size() == 1 && locations.iterator().next().exact();
                for (Loc location : locations) {
                    if (strong) {
                        state.guaranteed.removeIf(l -> l.root()==location.root()&&l.frame()==location.frame()
                            &&(location.path().isEmpty()||l.path().startsWith(location.path()+".")||l.path().startsWith(location.path()+"[")));
                        state.guaranteed.add(location);
                        state.defs.keySet().removeIf(l -> l.root() == location.root() && l.frame() == location.frame()
                            && (l.path().startsWith(location.path()+".") || l.path().startsWith(location.path()+"[") || l.path().startsWith(location.path()+"?")));
                        state.defs.put(location,new LinkedHashSet<>());
                    }
                    state.defs.computeIfAbsent(location,k -> new LinkedHashSet<>()).add(new Source(source,provenance));
                }
            }
            void copyPointers(Loc destination,Set<Loc> sources,State state) {
                var copied=new LinkedHashMap<Loc,Set<Loc>>();
                for(Loc source:sources)for(var entry:state.pointers.entrySet()) {
                    Loc location=entry.getKey();
                    if(location.root()==source.root()&&location.frame()==source.frame()&&location.path().startsWith(source.path()+".")) {
                        Loc target=new Loc(destination.root(),destination.path()+location.path().substring(source.path().length()),destination.frame());
                        copied.computeIfAbsent(target,k->new LinkedHashSet<>()).addAll(entry.getValue());
                    }
                }
                copied.forEach(state.pointers::put);
            }
            void bind(Declaration declaration, Expression initializer, State state) {
                TypeNode type = type(declaration);
                if (type instanceof ReferenceType) state.refs.put(declaration,new LinkedHashSet<>(locations(initializer,state)));
                else if (type instanceof PointerType) state.pointers.put(root(declaration),new LinkedHashSet<>(points(initializer,state)));
                else if(type!=null&&ClassSemantics.objectClass(type).isPresent()) {
                    Expression value=unwrap(initializer);
                    if(value instanceof InitializationExpression init&&init.arguments().size()==1)value=init.arguments().getFirst();
                    copyPointers(root(declaration),locations(value,state),state);
                }
            }
            /** Replay trusted library/lifetime may-effects at their actual instruction.
             * Later exact ordinary writes can still kill these imported definitions. */
            void inherit(Node event,State state) {
                for(DataEdge edge:inheritedEffects.getOrDefault(event,List.of())) {
                    for(Loc location:locations((Expression)edge.to(),state))
                        state.defs.computeIfAbsent(location,k->new LinkedHashSet<>()).add(new Source(edge.from(),provenance));
                }
            }
            /** Implicit lifetime and operator calls retain conservative effects.
             * Only written CallExpression invocations publish precise body contexts. */
            void opaqueUserEffect(Node producer,Collection<Expression> arguments,State state) {
                var exposed=new LinkedHashSet<Loc>(state.escaped);
                exposed.add(UNKNOWN_NONLOCAL);
                for(VariableDeclaration global:accessibleGlobals)exposed.add(new Loc(global,"",null));
                if(producer instanceof VariableDeclaration variable)exposed.add(root(variable));
                for(Expression argument:arguments) {
                    Expression value=unwrap(argument);
                    List<Expression> values=value instanceof InitializationExpression init?init.arguments():List.of(value);
                    for(Expression actual:values) { exposed.addAll(locations(actual,state));exposed.addAll(points(actual,state)); }
                }
                var pending=new ArrayDeque<Loc>(exposed);
                while(!pending.isEmpty()) {
                    Loc object=pending.remove();
                    for(var entry:state.pointers.entrySet()) {
                        Loc slot=entry.getKey();
                        if(slot.equals(object)||slot.root()==object.root()&&slot.frame()==object.frame()&&slot.path().startsWith(object.path()+"."))
                            for(Loc location:entry.getValue())if(exposed.add(location))pending.add(location);
                    }
                }
                for(Loc location:exposed) {
                    for(Source source:definitions(location,state))definition(source,producer);
                    Loc affected=location.path().contains("[")||Optional.ofNullable(type(location.root())).flatMap(ClassSemantics::objectClass).isPresent()?location.child("?"):location;
                    state.defs.computeIfAbsent(affected,k->new LinkedHashSet<>()).add(new Source(producer,provenance));
                }
            }
            void transfer(FlowVertex vertex, State state) {
                if (vertex instanceof VariableDeclaration variable) {
                    if(variable.modifiers().contains("static")&&state.defs.containsKey(root(variable)))return;
                    variable.initializer().ifPresent(value -> {
                        if (!expanded.containsKey(variable)) evaluate(value,state);
                        state.defs.put(root(variable),new LinkedHashSet<>(List.of(new Source(variable,provenance))));
                        bind(variable,value,state);
                    });
                } else if (vertex instanceof ExpressionStatement statement) evaluate(statement.expression(),state);
                else if (vertex instanceof ReturnStatement statement) {
                    statement.value().ifPresent(value -> {
                        if (!expanded.containsKey(statement)) evaluate(value,state);
                        TypeNode result = flow.entry().returnType().map(TypeSemantics::unqualified).orElse(null);
                        if (result instanceof ReferenceType) returned.addAll(locations(value,state));
                        else if (result instanceof PointerType) returned.addAll(points(value,state));
                    });
                    returns.add(statement);
                } else if(vertex instanceof MemberInitializer initializer) {
                    arguments(initializer.arguments().stream().filter(argument->!expandedInitializerArguments.contains(argument)).toList(),state);
                } else if(vertex instanceof PrintStatement print) {
                    if(!expanded.containsKey(print))evaluate(print.value(),state);
                } else if (vertex instanceof Expression expression) evaluate(expression,state);
                else if (vertex instanceof CatchClause handler) handler.parameter().ifPresent(p -> state.defs.put(root(p),new LinkedHashSet<>(List.of(new Source(p,provenance)))));
                if(vertex instanceof VariableDeclaration variable)bindReferenceFields(variable,state);
                if(vertex instanceof Node node)inherit(node,state);
                if(vertex instanceof VariableDeclaration variable&&constructors(variable).stream().anyMatch(t->!LibraryCallEffects.unknown(t)))
                    opaqueUserEffect(variable,variable.initializer().map(List::of).orElse(List.of()),state);
                if(vertex instanceof AuxiliaryFlowVertex auxiliary)auxiliary.object().ifPresent(object->{
                    inherit(object,state);
                    if(auxiliary.destructor().or(auxiliary::constructor).filter(t->!LibraryCallEffects.unknown(t)).isPresent())opaqueUserEffect(object,List.of(),state);
                });
            }
            void address(Expression target, State state) {
                target = unwrap(target);
                if (target instanceof NameExpression name) readBinding(name,state);
                else if (target instanceof DereferenceExpression pointer) evaluate(pointer.operand(),state);
                else if(target instanceof DotMemberPointerExpression || target instanceof ArrowMemberPointerExpression) {
                    var member=(BinaryExpression)target;
                    if(target instanceof ArrowMemberPointerExpression)evaluate(member.left(),state);else address(member.left(),state);
                    evaluate(member.right(),state);
                } else if(target instanceof CommaExpression comma) { evaluate(comma.left(),state);address(comma.right(),state); }
                else if(target instanceof AssignmentExpression || target instanceof CompoundAssignmentExpression
                    ||target instanceof PrefixIncrementExpression||target instanceof PrefixDecrementExpression)evaluate(target,state);
                else if (target instanceof MemberExpression member) {
                    if (member instanceof PointerMemberExpression) evaluate(member.receiver(),state);
                    else address(member.receiver(),state);
                } else if (target instanceof IndexExpression index) {
                    if (TypeSemantics.expressionType(index.left(),profile).map(TypeSemantics::unqualified).filter(PointerType.class::isInstance).isPresent()) evaluate(index.left(),state);
                    else { address(index.left(),state); read(index.left(),state); }
                    evaluate(index.right(),state);
                }
            }
            void evaluate(Expression expression, State state) {
                if (normalized.contains(expression)) { inherit(expression,state); return; }
                if(expression instanceof SizeofExpression)return;
                if(expression.operatorTarget().filter(t->!LibraryCallEffects.unknown(t)).isPresent()) {
                    for(Expression operand:OperatorSemantics.operands(expression))evaluate(operand,state);
                    opaqueUserEffect(expression,OperatorSemantics.operands(expression),state);inherit(expression,state);return;
                }
                if (expression instanceof TypeidExpression t) {
                    if(!TypeSemantics.typeidOperandUnevaluated(t,profile))t.operand().ifPresent(operand->{State possible=state.copy();evaluate(operand,possible);state.merge(possible);});
                    return;
                }
                if(expression instanceof NewExpression allocation) {
                    for(Expression argument:allocation.placementArguments())evaluate(argument,state);
                    TypeNode type=allocation.type();while(type instanceof ArrayType array) { array.bound().ifPresent(bound->evaluate(bound,state));type=array.elementType(); }
                    for(Expression argument:allocation.initializerArguments())evaluate(argument,state);
                    return;
                }
                if (expression instanceof AssignmentExpression assignment && !OperatorSemantics.mayInvokeUserCode(expression)) {
                    if (!expanded.containsKey(assignment)) evaluate(assignment.value(),state);
                    address(assignment.target(),state);
                    write(assignment.target(),assignment,state);
                    TypeNode type = TypeSemantics.expressionType(assignment.target(),profile).map(TypeSemantics::withoutReference).map(TypeSemantics::unqualified).orElse(null);
                    if (type instanceof PointerType) {
                        Set<Loc> targets=locations(assignment.target(),state), values=points(assignment.value(),state);
                        boolean strong=targets.size()==1&&targets.iterator().next().exact();
                        for (Loc location : targets) {
                            if(strong) state.pointers.put(location,new LinkedHashSet<>(values));
                            else state.pointers.computeIfAbsent(location,k->new LinkedHashSet<>()).addAll(values);
                        }
                    }
                } else if (expression instanceof CompoundAssignmentExpression assignment) {
                    evaluate(assignment.target(),state); evaluate(assignment.value(),state); write(assignment.target(),assignment,state);
                    if(TypeSemantics.expressionType(assignment.target(),profile).map(TypeSemantics::withoutReference).map(TypeSemantics::unqualified).filter(PointerType.class::isInstance).isPresent()) {
                        String distance=ConstantEvaluator.evaluate(assignment.value(),profile).map(v->v.number().toString()).orElse("?");
                        if(assignment.operator().equals("-=")&&!distance.equals("?"))distance=new java.math.BigInteger(distance).negate().toString();
                        Set<Loc> targets=locations(assignment.target(),state);
                        for(Loc location:targets) {
                            Set<Loc> values=offset(state.pointers.getOrDefault(location,Set.of(location.child("*"))),distance);
                            if(targets.size()==1&&location.exact())state.pointers.put(location,values);
                            else state.pointers.computeIfAbsent(location,k->new LinkedHashSet<>()).addAll(values);
                        }
                    }
                } else if (expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression
                        || expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression) {
                    var update = (UnaryExpression)expression; evaluate(update.operand(),state); write(update.operand(),update,state);
                    if(TypeSemantics.expressionType(update.operand(),profile).map(TypeSemantics::withoutReference).map(TypeSemantics::unqualified).filter(PointerType.class::isInstance).isPresent()) {
                        String step=expression instanceof PrefixDecrementExpression||expression instanceof PostfixDecrementExpression?"-1":"1";
                        for(Loc location:locations(update.operand(),state))state.pointers.put(location,offset(state.pointers.getOrDefault(location,Set.of(location.child("*"))),step));
                    }
                } else if (expression instanceof AddressOfExpression address) address(address.operand(),state);
                else if (expression instanceof NameExpression) read(expression,state);
                else if (expression instanceof DereferenceExpression || expression instanceof IndexExpression || expression instanceof MemberExpression
                    ||expression instanceof DotMemberPointerExpression||expression instanceof ArrowMemberPointerExpression) {
                    address(expression,state); read(expression,state);
                } else if (expression instanceof ConditionalExpression conditional) {
                    evaluate(conditional.condition(),state);
                    var truth = ConstantEvaluator.evaluate(conditional.condition(),profile);
                    if (truth.isPresent()) evaluate(truth.get().number().signum()!=0 ? conditional.thenExpression() : conditional.elseExpression(),state);
                    else { State yes=state.copy(), no=state.copy(); evaluate(conditional.thenExpression(),yes); evaluate(conditional.elseExpression(),no); state.replace(yes); state.merge(no); }
                } else if (expression instanceof LogicalAndExpression || expression instanceof LogicalOrExpression) {
                    var logical = (BinaryExpression)expression; evaluate(logical.left(),state);
                    var truth = ConstantEvaluator.evaluate(logical.left(),profile);
                    if (truth.isPresent()) { if ((truth.get().number().signum()!=0) == (expression instanceof LogicalAndExpression)) evaluate(logical.right(),state); }
                    else { State possible=state.copy(); evaluate(logical.right(),possible); state.merge(possible); }
                } else if (expression instanceof CallExpression call) call(call,state);
                else if (expression instanceof ConditionDeclarationExpression condition) { transfer(condition.declaration(),state); }
                else for (Node child : expression.children()) if (child instanceof Expression operand) evaluate(operand,state);
                inherit(expression,state);
                if(expression.operatorTarget().filter(t->!LibraryCallEffects.unknown(t)).isPresent())
                    opaqueUserEffect(expression,OperatorSemantics.operands(expression),state);
            }
            Set<Loc> receiverLocations(CallExpression call,State state) {
                Expression callee=unwrap(call.callee());
                if(callee instanceof PointerMemberExpression member)return points(member.receiver(),state);
                if(callee instanceof MemberExpression member)return locations(member.receiver(),state);
                if(callee instanceof DotMemberPointerExpression member)return locations(member.left(),state);
                if(callee instanceof ArrowMemberPointerExpression member)return points(member.left(),state);
                if(TypeSemantics.expressionType(callee,profile).map(TypeSemantics::withoutReference).flatMap(ClassSemantics::objectClass).isPresent())return locations(callee,state);
                return Set.of();
            }
            void arguments(List<Expression> arguments,State state) {
                // Each argument can precede or follow sibling side effects in C++03.
                var effects = new ArrayList<State>();
                for (var argument : arguments) { State effect=state.copy(); evaluate(argument,effect); effects.add(effect); }
                for (int i=0;i<arguments.size();i++) {
                    State before=state.copy(); for (int j=0;j<effects.size();j++) if(i!=j) before.merge(effects.get(j)); evaluate(arguments.get(i),before);
                }
                State changes=new State();
                for (State effect : effects) {
                    effect.defs.entrySet().removeIf(e->Objects.equals(e.getValue(),state.defs.get(e.getKey())));
                    effect.pointers.entrySet().removeIf(e->Objects.equals(e.getValue(),state.pointers.get(e.getKey())));
                    effect.refs.entrySet().removeIf(e->Objects.equals(e.getValue(),state.refs.get(e.getKey())));
                    changes.merge(effect);
                }
                changes.defs.forEach(state.defs::put); changes.pointers.forEach(state.pointers::put); changes.refs.forEach(state.refs::put);
                state.escaped.addAll(changes.escaped);
            }
            void call(CallExpression call, State state) {
                CallExpression site = provenance == null ? call : provenance;
                evaluate(call.callee(),state);
                var arguments = new ArrayList<>(call.arguments()); call.defaultArguments().forEach(a -> arguments.add(a.expression()));
                arguments(arguments,state);
                List<CallableDeclaration> targets = call.isVirtualDispatch() ? call.virtualTargets() : call.target().map(List::of).orElse(List.of());
                State normal=null, thrown=null;
                Set<Loc> returnLocations = new LinkedHashSet<>();
                boolean opaqueReturn = targets.isEmpty() || call.isVirtualDispatch() && !call.virtualTargetsComplete();
                for (CallableDeclaration target : targets) {
                    FunctionFlow body=flows.get(target);
                    if (body == null || !body.complete() || LibraryCallEffects.unknown(target)) {
                        opaqueReturn = true;
                        State external=external(call,target,arguments,state); if(normal==null)normal=external;else normal.merge(external);
                        if(thrown==null)thrown=external.copy();else thrown.merge(external); continue;
                    }
                    State bound=state.copy(); var bindings=new ArrayList<Set<Loc>>();
                    Set<Loc> actualReceiver=receiverLocations(call,state);
                    if(actualReceiver.isEmpty())actualReceiver=receiver;
                    bindings.add(Set.copyOf(actualReceiver));
                    for(int i=0;i<target.parameters().size();i++) {
                        ParameterDeclaration parameter=target.parameters().get(i);
                        Expression argument=i<arguments.size()?arguments.get(i):null;
                        TypeNode type=parameter.type().map(TypeSemantics::unqualified).orElse(null);
                        Set<Loc> actual=argument==null?Set.of():type instanceof PointerType?points(argument,state):locations(argument,state);
                        bindings.add(Set.copyOf(actual));
                        if(type instanceof ReferenceType)bound.refs.put(parameter,new LinkedHashSet<>(actual));
                        else {
                            Loc parameterLocation=new Loc(parameter,"",call);
                            bound.defs.put(parameterLocation,new LinkedHashSet<>(List.of(new Source(parameter,site))));
                            if(type instanceof PointerType)bound.pointers.put(parameterLocation,new LinkedHashSet<>(actual));
                            else if(type!=null&&ClassSemantics.objectClass(type).isPresent())copyPointers(parameterLocation,actual,bound);
                        }
                        if(argument!=null)edges.add(new DataEdge(argument,parameter,Dependency.VALUE,site));
                    }
                    Key key=new Key(call,target,List.copyOf(bindings),site); Result result;
                    if(!active.add(key)) {
                        recursive=true;
                        Result old=summaries.get(key);
                        State conservative=external(call,target,arguments,state);
                        if(old!=null) { conservative.merge(old.normal()); conservative.merge(old.exceptional()); }
                        result=new Result(conservative,conservative.copy(),old==null?Set.of():old.returned(),old==null?Set.of():old.returns());
                    } else {
                        result=run(body,bound,call,site,actualReceiver); active.remove(key);
                        Result previous=summaries.put(key,result);
                        if(!Objects.equals(previous,result))changed=true;
                    }
                    if(result.normal()!=null) { State output=result.normal().copy(); restoreBindings(output,state); if(normal==null)normal=output;else normal.merge(output); }
                    if(result.exceptional()!=null) { State output=result.exceptional().copy(); restoreBindings(output,state); if(thrown==null)thrown=output;else thrown.merge(output); }
                    returnLocations.addAll(result.returned());
                    for(var ret:result.returns())edges.add(new DataEdge(ret,call,Dependency.VALUE,site));
                }
                if(targets.isEmpty() || call.isVirtualDispatch()&&!call.virtualTargetsComplete()) {
                    State external=external(call,call.target().orElse(null),arguments,state);
                    if(normal==null)normal=external;else normal.merge(external);
                    if(thrown==null)thrown=external.copy();else thrown.merge(external);
                }
                if(normal!=null)state.replace(normal); else this.normal=false;
                // Preserve throwing-only writes solely on exceptional successors.
                if(thrown!=null) { if(exceptional==null)exceptional=thrown;else exceptional.merge(thrown); }
                if (opaqueReturn) {
                    TypeNode resultType=call.target().flatMap(CallableDeclaration::returnType).or(()->TypeSemantics.callType(call,profile).map(FunctionType::returnType)).map(TypeSemantics::unqualified).orElse(null);
                    if(resultType instanceof PointerType || resultType instanceof ReferenceType) {
                        returnLocations.add(UNKNOWN_NONLOCAL);
                        state.escaped.add(UNKNOWN_NONLOCAL);
                        for(Set<Loc> locations:state.pointers.values())for(Loc location:locations)returnLocations.add(location.child("?"));
                        for(Expression argument:arguments) { for(Loc location:locations(argument,state))returnLocations.add(location.child("?")); for(Loc location:points(argument,state))returnLocations.add(location.child("?")); }
                        for(VariableDeclaration global:accessibleGlobals)returnLocations.add(new Loc(global,"?",null));
                    }
                }
                callResults.put(call,returnLocations);
            }
            void restoreBindings(State output, State caller) {
                output.refs.clear(); State.mergeMap(output.refs,caller.refs);
            }
            State external(CallExpression call, CallableDeclaration target,List<Expression> arguments,State input) {
                State output=input.copy();
                var accessible=new LinkedHashSet<Loc>(input.escaped);
                accessible.add(UNKNOWN_NONLOCAL);
                for(VariableDeclaration global:accessibleGlobals)accessible.add(new Loc(global,"",null));
                var exposedQueue=new ArrayDeque<Loc>(accessible);
                while(!exposedQueue.isEmpty())for(Loc location:input.pointers.getOrDefault(exposedQueue.remove(),Set.of()))if(accessible.add(location))exposedQueue.add(location);
                for(Loc location:accessible) {
                    for(Source source:definitions(location,input))definition(source,call);
                    Loc affected=location.path().contains("[")?new Loc(location.root(),location.path().substring(0,location.path().indexOf('['))+"[?]",location.frame()):location;
                    if(Optional.ofNullable(type(location.root())).flatMap(ClassSemantics::objectClass).isPresent())affected=affected.child("?");
                    output.defs.computeIfAbsent(affected,k->new LinkedHashSet<>()).add(new Source(call,provenance));
                }
                {
                    Set<Loc> receivers=receiverLocations(call,input);
                    for(Loc receiver:receivers) {
                        for(Source source:definitions(receiver,input))definition(source,call);
                        output.defs.computeIfAbsent(receiver.child("?"),k->new LinkedHashSet<>()).add(new Source(call,provenance));
                    }
                }
                for(VariableDeclaration global:accessibleGlobals) {
                    Loc location=new Loc(global,"",null);
                    for(Source source:definitions(location,input))definition(source,call);
                    output.defs.computeIfAbsent(location,k->new LinkedHashSet<>()).add(new Source(call,provenance));
                }
                for(int i=0;i<arguments.size();i++) {
                    Expression argument=arguments.get(i);
                    TypeNode type=target!=null&&i<target.parameters().size()?target.parameters().get(i).type().map(TypeSemantics::unqualified).orElse(null):null;
                    TypeNode exposed=type instanceof ReferenceType r?r.referredType():type instanceof PointerType p?p.pointee():null;
                    boolean mutable=exposed!=null&&(TypeSemantics.qualifiers(exposed)&1)==0;
                    Set<Loc> locations=type instanceof PointerType||unwrap(argument) instanceof AddressOfExpression?points(argument,input):locations(argument,input);
                    if(target==null)mutable=true;
                    if(exposed!=null || target==null || type instanceof ArrayType) {
                        var reachable=new LinkedHashSet<Loc>(locations); var pending=new ArrayDeque<Loc>(locations);
                        while(!pending.isEmpty()) {
                            Loc location=pending.remove();
                            for(Loc next:input.pointers.getOrDefault(location,Set.of()))if(reachable.add(next))pending.add(next);
                        }
                        output.escaped.addAll(reachable);
                        for(Loc location:reachable) {
                        for(Source source:definitions(location,input)) definition(source,call);
                        if(mutable) {
                            Loc written=Optional.ofNullable(type(location.root())).flatMap(ClassSemantics::objectClass).isPresent()?location.child("?"):location;
                            if(location.path().contains("["))written=new Loc(location.root(),location.path().substring(0,location.path().indexOf('['))+"[?]",location.frame());
                            output.defs.computeIfAbsent(written,k->new LinkedHashSet<>()).add(new Source(call,provenance));
                            if(input.pointers.containsKey(location)) {
                                var aliases=output.pointers.computeIfAbsent(location,k->new LinkedHashSet<>());
                                for(Loc possible:input.defs.keySet())aliases.add(possible.child("?"));
                                for(Set<Loc> possible:input.pointers.values())for(Loc candidate:possible)aliases.add(candidate.child("?"));
                            }
                        }
                        }
                    }
                }
                return output;
            }
        }
    }
    private static boolean contains(Node root,Node node) {
        if(root==node)return true; for(Node child:root.children())if(contains(child,node))return true; return false;
    }
}
