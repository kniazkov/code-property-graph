/* Conservative C++03 container-family semantics. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;
import static com.kniazkov.cpg.model.ContainerOperation.Kind.*;
import static com.kniazkov.cpg.model.ContainerOperation.Handle.Validity.*;

/** Selected library operations retain their written syntax and unspecified implementation effects.
 * No node count, layout, comparison order, or internal copy multiplicity is invented. */
public final class ContainerSemantics {
    private ContainerSemantics() { }
    private record Family(ContainerType.Kind kind, TypeNode value, TypeNode key, TypeNode comparator) {
        boolean associative() { return key != null; }
        boolean nodeStable() { return kind == ContainerType.Kind.LIST || associative(); }
    }
    public static Project green(Project project) {
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var types = new LinkedHashMap<ClassDeclaration, ContainerType.Builder>();
        var families = new IdentityHashMap<ClassDeclaration, Family>();
        var sites = new IdentityHashMap<ClassDeclaration,List<NodeBuilder<? extends TypeNode>>>();
        for (var instance : project.classTemplateInstances()) {
            if (!LibraryDeclarations.isLibrary(instance.origin()) || instance.selected() != instance.origin()) continue;
            ContainerType.Kind kind;
            try { kind = ContainerType.Kind.valueOf(instance.origin().name().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { continue; }
            var args = instance.arguments();
            boolean mapped = kind == ContainerType.Kind.MAP || kind == ContainerType.Kind.MULTIMAP;
            boolean assoc = mapped || kind == ContainerType.Kind.SET || kind == ContainerType.Kind.MULTISET;
            boolean adapter = kind == ContainerType.Kind.STACK || kind == ContainerType.Kind.QUEUE || kind == ContainerType.Kind.PRIORITY_QUEUE;
            int expected = assoc ? (mapped ? 4 : 3) : kind == ContainerType.Kind.PRIORITY_QUEUE ? 3 : 2;
            if (args.size() != expected || args.stream().anyMatch(a -> a.type().isEmpty())) continue;
            var cls = instance.declaration();
            TypeNode first = args.getFirst().type().orElseThrow();
            TypeNode value = mapped ? cls.members().stream().filter(TypedefDeclaration.class::isInstance)
                    .map(TypedefDeclaration.class::cast).filter(t -> t.name().equals("value_type"))
                    .map(TypedefDeclaration::type).findFirst().orElse(null) : first;
            if (value == null) continue;
            if (!adapter && !standardAllocator(project,args.getLast().type().orElseThrow())) continue;
            if (adapter && !standardUnderlying(project,args.get(1).type().orElseThrow(), kind, first)) continue;
            TypeNode comparator = assoc ? args.get(mapped ? 2 : 1).type().orElseThrow()
                    : kind == ContainerType.Kind.PRIORITY_QUEUE ? args.get(2).type().orElseThrow() : null;
            var type = ContainerType.create().setKind(kind).setConcreteClass(context.copy(cls)).setValueType(context.copy(value));
            type.addTransformationProvenance("ContainerSemantics", "source-construct", cls);
            if (assoc) type.setKeyType(context.copy(first));
            if (mapped) type.setMappedType(context.copy(args.get(1).type().orElseThrow()));
            if (comparator != null) type.setComparatorType(context.copy(comparator));
            if (adapter) type.setUnderlyingType(context.copy(args.get(1).type().orElseThrow()));
            types.put(cls,type); families.put(cls,new Family(kind,value,assoc ? first : null,comparator)); sites.put(cls,new ArrayList<>());
        }
        var operations = new ArrayList<NodeBuilder<? extends ContainerOperation>>();
        for (var source : SourceContexts.roots(project)) {
            if (source.source().language() != Language.CPP || LibraryDeclarations.isLibrary(source.node())) continue;
            var profile = TypeSemantics.profile(source.source());
            var conditional = Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
            conditionals(source.node(),false,conditional);
            walk(source.node(),new NodeVisitor() {
                final List<Tracked> handles = new ArrayList<>();
                boolean aliasesKnown = true;
                final Map<Node,Boolean> nonempty = new IdentityHashMap<>();
                final Set<Node> changedAliases = Collections.newSetFromMap(new IdentityHashMap<>());
                @Override public void visit(CallableDeclaration callable) { handles.clear(); changedAliases.clear(); nonempty.clear(); aliasesKnown = true; }
                @Override public void visit(Node node) {
                    if (node instanceof TypeidExpression || node instanceof GotoStatement) uncertain();
                    if (node instanceof PrefixIncrementExpression || node instanceof PostfixIncrementExpression
                            || node instanceof PrefixDecrementExpression || node instanceof PostfixDecrementExpression)
                        for(var child:node.children()) if(child instanceof Expression e) changedAliases.add(identity(e));
                }
                void uncertain() { aliasesKnown = false; nonempty.clear(); handles.forEach(h -> { h.validity = UNKNOWN; h.position = null; }); }
                boolean safeAlias(Expression expression) {
                    var node=written(expression);
                    if(node instanceof NameExpression n && n.target().orElse(null) instanceof VariableDeclaration v) {
                        if(!aliasesKnown || changedAliases.contains(v)) return false;
                        return v.initializer().isEmpty() || safeAlias(v.initializer().orElseThrow());
                    }
                    return true;
                }
                ClassDeclaration owner(TypeNode type) {
                    var cls = ClassSemantics.objectClass(TypeSemantics.withoutReference(type)).orElse(null);
                    return types.containsKey(cls) ? cls : null;
                }
                ClassDeclaration owner(Expression expression) { return TypeSemantics.expressionType(expression,profile).map(this::owner).orElse(null); }
                @Override public void visit(TypeNode type) { var cls=owner(type); if(cls!=null) sites.get(cls).add(context.copy(type)); }
                @Override public void visit(VariableDeclaration variable) {
                    if (!variable.isDefinition() || variable.type().isEmpty()) return;
                    var written = TypeSemantics.unqualified(variable.type().orElseThrow());
                    if(written instanceof ReferenceType || written instanceof ArrayType) return;
                    var cls=owner(written); if(cls==null) return;
                    var init=variable.initializer().map(ContainerSemantics::written).orElse(null);
                    List<Expression> args=init==null ? List.of() : init instanceof InitializationExpression i && i.form()==InitializationExpression.Form.DIRECT ? i.arguments() : List.of(init);
                    ContainerOperation.Kind kind = args.isEmpty() ? EMPTY : args.size()==1 && owner(args.getFirst())==cls ? COPY : null;
                    if (!families.get(cls).associative() && (families.get(cls).kind()==ContainerType.Kind.LIST || families.get(cls).kind()==ContainerType.Kind.DEQUE)) {
                        if(args.size()==1 && count(args.getFirst(),profile)) kind=COUNT;
                        if(args.size()==2) kind=count(args.getFirst(),profile)?FILL:RANGE;
                    }
                    if(kind!=null) add(kind,variable,variable,cls,variable.constructorTarget().orElse(null),args);
                }
                @Override public void visit(CallExpression call) {
                    if(!(call.callee() instanceof MemberExpression member)) { uncertain(); return; }
                    var cls=owner(member.receiver()); var target=call.target().orElse(null);
                    if(cls==null || target==null || !libraryTarget(target)) { uncertain(); return; }
                    var kind=memberKind(target,call.arguments(),families.get(cls),profile);
                    if(kind==SPLICE && (call.arguments().size()<2 || owner(call.arguments().get(1))!=cls)) kind=null;
                    if(kind==SWAP && (call.arguments().size()!=1 || owner(call.arguments().getFirst())!=cls)) kind=null;
                    if(kind==null || !add(kind,call,member.receiver(),cls,target,call.arguments())) uncertain();
                }
                @Override public void visit(IndexExpression index) {
                    var cls=owner(index.left());
                    if(cls!=null && (families.get(cls).kind()==ContainerType.Kind.DEQUE || families.get(cls).kind()==ContainerType.Kind.MAP))
                        if(!add(SUBSCRIPT,index,index.left(),cls,index.operatorTarget().orElse(null),List.of(index.right()))) uncertain();
                }
                @Override public void visit(AssignmentExpression assignment) {
                    changedAliases.add(identity(assignment.target()));
                    var cls=owner(assignment.target()); if(cls==null) return;
                    if(owner(assignment.value())!=cls || !add(ASSIGN_COPY,assignment,assignment.target(),cls,assignment.operatorTarget().orElse(null),List.of(assignment.value()))) uncertain();
                }
                boolean add(ContainerOperation.Kind kind, Node syntax, Node destination, ClassDeclaration cls,
                        CallableDeclaration target,List<? extends Expression> args) {
                    if(target==null || !libraryTarget(target)) return false;
                    var family=families.get(cls);
                    var operation=ContainerOperation.create().setKind(kind).setType(types.get(cls)).setSyntax(context.copy(syntax))
                            .setDestination(context.copy(destination)).setSelectedTarget(context.copy(target));
                    operation.addTransformationProvenance("ContainerSemantics", "source-construct", syntax);
                    var inputs=new ArrayList<NodeBuilder<? extends Node>>(); args.forEach(a->inputs.add(context.copy(a))); operation.setSources(inputs);
                    if(!effects(operation,kind,family,profile,context)) return false;
                    if(usesComparator(kind,family)) comparator(family,profile).ifPresent(c->operation.setComparator(context.copy(c)));
                    boolean mutation=mutates(kind,family);
                    var result=result(kind,family,target,args.size()); operation.setResultKind(result);
                    boolean writable=family.kind()!=ContainerType.Kind.SET && family.kind()!=ContainerType.Kind.MULTISET && family.kind()!=ContainerType.Kind.PRIORITY_QUEUE
                            && destination instanceof Expression e && TypeSemantics.expressionType(e,profile).map(TypeSemantics::withoutReference)
                            .map(t->!(t instanceof QualifiedType q)||!q.isConst()).orElse(false);
                    operation.setWritable(writable && (result==ContainerOperation.ResultKind.REFERENCE || result==ContainerOperation.ResultKind.ITERATOR));
                    Node sequence=identity(destination), other=kind==SWAP?identity(args.getFirst()):kind==SPLICE?identity(args.get(1)):null;
                    if(mutation) operation.setMutatedObjects(other==null?List.of(context.copy(destination)):List.of(context.copy(destination),context.copy(kind==SWAP?args.getFirst():args.get(1))));
                    if(kind==SPLICE) operation.setSourceContainer(context.copy(args.get(1))).setTransferKind(args.size()==2?ContainerOperation.TransferKind.ALL:args.size()==3?ContainerOperation.TransferKind.ONE:ContainerOperation.TransferKind.RANGE);
                    var changes=new ArrayList<ContainerOperation.HandleEffect.Builder>();
                    for(var handle:handles) {
                        if(!mutation || !handle.owners.contains(sequence) && (other==null || !handle.owners.contains(other))) continue;
                        boolean conditionalSite=conditional.contains(syntax);
                        if(kind==SWAP) {
                            if(conditionalSite) { handle.owners.add(sequence); handle.owners.add(other); }
                            else { boolean a=handle.owners.remove(sequence),b=handle.owners.remove(other); if(a)handle.owners.add(other); if(b)handle.owners.add(sequence); }
                            if(handle.boundary) handle.validity=UNKNOWN;
                        } else if(kind==SPLICE) {
                            if(handle.owners.contains(other) && handle.boundary && !handle.endBase) handle.owners.add(sequence);
                            if(handle.owners.contains(other) && !handle.boundary) {
                                boolean all=args.size()==2 || args.size()==4 && safeAlias(args.get(2)) && safeAlias(args.get(3)) && at(args.get(2),other,"begin") && at(args.get(3),other,"end");
                                boolean selected=args.size()==3 && safeAlias(args.get(2)) && (origin(args.get(2))==handle.syntax || at(args.get(2),other,"begin") && "begin".equals(handle.position));
                                if(all || selected) {
                                    if(!conditionalSite && handle.owners.size()==1 && (!all || !handle.mayBeEnd)) handle.owners.clear();
                                    handle.owners.add(sequence);
                                } else if(args.size()>2) handle.owners.add(sequence);
                            }
                        } else if(handle.validity!=INVALID) {
                            var after=UNKNOWN;
                            if(kind==CLEAR && family.nodeStable() && handle.endBase) after=handle.validity;
                            else if(kind==CLEAR && family.nodeStable() && handle.mayBeEnd) after=UNKNOWN;
                            else if(kind==CLEAR || kind==ASSIGN_COPY || kind==ASSIGN_FILL || kind==ASSIGN_RANGE) after=INVALID;
                            else if(family.nodeStable() && Set.of(INSERT,INSERT_FILL,INSERT_RANGE,PUSH_BACK,PUSH_FRONT).contains(kind)) after=handle.validity;
                            else if(family.kind()==ContainerType.Kind.MAP && kind==SUBSCRIPT) after=handle.validity;
                            else if(family.kind()==ContainerType.Kind.DEQUE && Set.of(PUSH_BACK,PUSH_FRONT).contains(kind) && !handle.iterator) after=handle.validity;
                            if(conditionalSite && after!=handle.validity || handle.owners.size()>1) after=UNKNOWN;
                            handle.validity=after;
                        }
                        var change=ContainerOperation.HandleEffect.create().setProducer(handle.producer).setValidityAfter(handle.validity);
                        if(handle.owners.size()==1) change.setSequenceAfter(context.copy(handle.owners.iterator().next()));
                        changes.add(change);
                        handle.position=null;
                    }
                    operation.setHandleEffects(changes);
                    if(kind==EMPTY) nonempty.put(sequence,false);
                    else if(kind==COUNT || kind==FILL) {
                        var count=ConstantEvaluator.evaluate(args.getFirst(),profile);
                        if(count.isPresent()) nonempty.put(sequence,count.get().number().signum()>0); else nonempty.remove(sequence);
                    } else if(mutation) {
                        Boolean sourceNonempty=other==null?null:nonempty.get(other), destinationNonempty=nonempty.get(sequence);
                        if(conditional.contains(syntax)) { nonempty.remove(sequence); if(other!=null)nonempty.remove(other); }
                        else if(kind==CLEAR) nonempty.put(sequence,false);
                        else if(Set.of(PUSH_BACK,PUSH_FRONT,PUSH).contains(kind) || kind==INSERT && !family.associative()) nonempty.put(sequence,true);
                        else if(kind==SPLICE) {
                            if(args.size()==2) {nonempty.put(other,false);if(Boolean.TRUE.equals(sourceNonempty)||Boolean.TRUE.equals(destinationNonempty))nonempty.put(sequence,true);else nonempty.remove(sequence);}
                            else {nonempty.remove(other);if(args.size()==3)nonempty.put(sequence,true);else nonempty.remove(sequence);}
                        } else if(kind==SWAP) {
                            if(sourceNonempty==null)nonempty.remove(sequence);else nonempty.put(sequence,sourceNonempty);
                            if(destinationNonempty==null)nonempty.remove(other);else nonempty.put(other,destinationNonempty);
                        } else nonempty.remove(sequence);
                    }
                    if(result==ContainerOperation.ResultKind.REFERENCE || result==ContainerOperation.ResultKind.ITERATOR) {
                        boolean iterator=result==ContainerOperation.ResultKind.ITERATOR;
                        boolean emptyBegin=(kind==BEGIN || kind==REND) && Boolean.FALSE.equals(nonempty.get(sequence));
                        boolean endBase=kind==END || kind==RBEGIN || emptyBegin;
                        boolean boundary=kind==END || kind==REND || kind==RBEGIN || emptyBegin;
                        var position=kind==FRONT || kind==BEGIN?ContainerOperation.Handle.Position.BEGIN:kind==BACK || kind==RBEGIN || kind==TOP?ContainerOperation.Handle.Position.LAST:kind==END || kind==REND?ContainerOperation.Handle.Position.END:kind==SUBSCRIPT || kind==AT?ContainerOperation.Handle.Position.INDEX:ContainerOperation.Handle.Position.UNKNOWN;
                        var handle=ContainerOperation.Handle.create().setKind(iterator?ContainerOperation.Handle.Kind.ITERATOR:ContainerOperation.Handle.Kind.REFERENCE)
                                .setSequence(context.copy(sequence)).setPositionKind(position).setWritable(writable).setValidity(iterator?VALID:UNKNOWN)
                                .setDirection(kind==RBEGIN || kind==REND?ContainerOperation.Handle.Direction.REVERSE:ContainerOperation.Handle.Direction.FORWARD);
                        if(position==ContainerOperation.Handle.Position.INDEX) { handle.setPosition(context.copy(args.getFirst())); operation.setIndex(context.copy(args.getFirst())); }
                        operation.setHandle(handle);
                        handles.add(new Tracked(operation,syntax,sequence,iterator,boundary,endBase,Set.of(ERASE,ERASE_RANGE,FIND,LOWER_BOUND,UPPER_BOUND).contains(kind) || (kind==BEGIN || kind==REND) && !Boolean.TRUE.equals(nonempty.get(sequence)),position==ContainerOperation.Handle.Position.BEGIN?"begin":null,iterator?VALID:UNKNOWN));
                    }
                    operations.add(operation); return true;
                }
            });
        }
        types.forEach((cls,type)->type.setWrittenTypes(sites.get(cls)));
        root.setContainerTypes(new ArrayList<>(types.values())).setContainerSemantics(operations);
        return root.build();
    }
    private static final class Tracked {
        final ContainerOperation.Builder producer; final Node syntax; final boolean iterator,boundary,endBase,mayBeEnd;
        final Set<Node> owners=Collections.newSetFromMap(new IdentityHashMap<>());
        String position; ContainerOperation.Handle.Validity validity;
        Tracked(ContainerOperation.Builder p,Node s,Node owner,boolean i,boolean b,boolean e,boolean m,String pos,ContainerOperation.Handle.Validity v) {producer=p;syntax=s;owners.add(owner);iterator=i;boundary=b;endBase=e;mayBeEnd=m;position=pos;validity=v;}
    }
    private static boolean standardAllocator(Project project,TypeNode type) {
        var cls=ClassSemantics.objectClass(type).orElse(null);
        return cls!=null && LibraryDeclarations.isLibrary(cls) && cls.name().equals("allocator") && trustedSelection(project,cls);
    }
    private static boolean standardUnderlying(Project project,TypeNode type,ContainerType.Kind kind,TypeNode element) {
        var cls=ClassSemantics.objectClass(type).orElse(null);
        if(cls==null || !LibraryDeclarations.isLibrary(cls)) return false;
        var allowed=kind==ContainerType.Kind.PRIORITY_QUEUE?Set.of("vector","deque"):kind==ContainerType.Kind.QUEUE?Set.of("deque","list"):Set.of("vector","deque","list");
        if(!allowed.contains(cls.name()) || !trustedSelection(project,cls)) return false;
        var plain=TypeSemantics.unqualified(type);
        var args=plain instanceof NamedType n && !n.templateArguments().isEmpty()?n.templateArguments():cls.templateArguments();
        if(args.isEmpty() || args.getFirst().type().filter(t->t.canonicalIdentity().equals(element.canonicalIdentity())).isEmpty()) return false;
        if(args.size()>1 && !standardAllocator(project,args.get(1).type().orElseThrow())) return false;
        return !(TypeSemantics.unqualified(element) instanceof NamedType n && n.fundamentalKind().orElse(null)==FundamentalKind.BOOL && cls.name().equals("vector"));
    }
    private static boolean trustedSelection(Project project,ClassDeclaration cls) {
        return project.classTemplateInstances().stream().filter(i->i.declaration()==cls)
                .allMatch(i->i.origin()==i.selected() && LibraryDeclarations.isLibrary(i.origin()));
    }
    private static boolean libraryTarget(CallableDeclaration target) {return LibraryDeclarations.isLibrary(target)||target.templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent();}
    private static Expression written(Expression expression) {while(expression instanceof ConversionExpression c && !c.isExplicit()) expression=c.operand();return expression;}
    private static Node identity(Node node) {
        if(node instanceof Expression e) node=written(e);
        if(node instanceof NameExpression n && n.target().isPresent()) {
            var target=n.target().orElseThrow();
            if(target instanceof VariableDeclaration v && v.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent() && v.initializer().isPresent()) return identity(v.initializer().orElseThrow());
            return target;
        }
        return node;
    }
    private static Node origin(Expression expression) {
        expression=written(expression);
        if(expression instanceof NameExpression n && n.target().orElse(null) instanceof VariableDeclaration v && v.initializer().isPresent()) return origin(v.initializer().orElseThrow());
        return expression;
    }
    private static boolean at(Expression expression,Node owner,String name) {
        Node node=origin(expression);
        return node instanceof CallExpression call && call.target().filter(ContainerSemantics::libraryTarget).filter(t->t.name().equals(name)).isPresent()
                && call.callee() instanceof MemberExpression m && identity(m.receiver())==owner;
    }
    private static boolean count(Expression expression,PlatformProfile profile) {return TypeSemantics.expressionType(expression,profile).flatMap(t->TypeSemantics.integerType(t,profile)).isPresent();}
    private static ContainerOperation.Kind memberKind(CallableDeclaration target,List<Expression> args,Family f,PlatformProfile p) {
        String name=target.name(); int n=args.size();
        if(name.equals("splice") && f.kind()==ContainerType.Kind.LIST && n>=2 && n<=4) return SPLICE;
        if(n==0) return switch(name) {case "front"->FRONT;case "back"->BACK;case "top"->TOP;case "begin"->BEGIN;case "end"->END;case "rbegin"->RBEGIN;case "rend"->REND;case "size"->SIZE;case "empty"->IS_EMPTY;case "max_size"->MAX_SIZE;case "clear"->CLEAR;case "pop_back"->POP_BACK;case "pop_front"->POP_FRONT;case "pop"->POP;default->null;};
        if(name.equals("insert")) {
            if(f.associative()) return n==2 && (!target.templateParameters().isEmpty() || target.templateOrigin().filter(t->t instanceof CallableDeclaration c && !c.templateParameters().isEmpty()).isPresent())?INSERT_RANGE:INSERT;
            if(n==2)return INSERT; if(n==3)return count(args.get(1),p)?INSERT_FILL:INSERT_RANGE;
        }
        if(name.equals("erase")) return n==1?ERASE:n==2?ERASE_RANGE:null;
        if(name.equals("resize") && n<=2)return RESIZE;
        if(name.equals("assign") && n==2)return count(args.getFirst(),p)?ASSIGN_FILL:ASSIGN_RANGE;
        if(n==1)return switch(name) {case "push_back"->PUSH_BACK;case "push_front"->PUSH_FRONT;case "push"->PUSH;case "at"->AT;case "operator[]"->SUBSCRIPT;case "operator="->ASSIGN_COPY;case "swap"->SWAP;case "find"->FIND;case "count"->COUNT_KEY;case "lower_bound"->LOWER_BOUND;case "upper_bound"->UPPER_BOUND;case "equal_range"->EQUAL_RANGE;default->null;};
        return null;
    }
    private static boolean mutates(ContainerOperation.Kind k,Family f) {
        return Set.of(ASSIGN_COPY,ASSIGN_FILL,ASSIGN_RANGE,INSERT,INSERT_FILL,INSERT_RANGE,ERASE,ERASE_RANGE,CLEAR,SPLICE,PUSH_BACK,PUSH_FRONT,POP_BACK,POP_FRONT,PUSH,POP,RESIZE,SWAP).contains(k)||k==SUBSCRIPT && f.kind()==ContainerType.Kind.MAP;
    }
    private static ContainerOperation.ResultKind result(ContainerOperation.Kind k,Family f,CallableDeclaration target,int count) {
        if(Set.of(FRONT,BACK,TOP,AT,SUBSCRIPT).contains(k))return ContainerOperation.ResultKind.REFERENCE;
        if(Set.of(BEGIN,END,RBEGIN,REND,FIND,LOWER_BOUND,UPPER_BOUND).contains(k))return ContainerOperation.ResultKind.ITERATOR;
        if(Set.of(SIZE,IS_EMPTY,MAX_SIZE,COUNT_KEY,EQUAL_RANGE).contains(k))return ContainerOperation.ResultKind.VALUE;
        if(k==INSERT) return f.associative() && (f.kind()==ContainerType.Kind.SET || f.kind()==ContainerType.Kind.MAP) && count==1?ContainerOperation.ResultKind.VALUE:ContainerOperation.ResultKind.ITERATOR;
        if((k==ERASE || k==ERASE_RANGE) && !f.associative())return ContainerOperation.ResultKind.ITERATOR;
        if(k==ERASE && target.parameters().getFirst().type().map(TypeSemantics::withoutReference).map(TypeSemantics::unqualified).filter(OpaqueLibraryType.class::isInstance).isEmpty() && f.associative())return ContainerOperation.ResultKind.VALUE;
        return ContainerOperation.ResultKind.NONE;
    }
    private static boolean usesComparator(ContainerOperation.Kind k,Family f) {
        return f.comparator()!=null && (f.associative() && Set.of(FIND,COUNT_KEY,LOWER_BOUND,UPPER_BOUND,EQUAL_RANGE,INSERT,INSERT_RANGE,ERASE,SUBSCRIPT).contains(k) || f.kind()==ContainerType.Kind.PRIORITY_QUEUE && (k==PUSH || k==POP));
    }
    /** Select against const key/value lvalues, not just a method spelling. Ambiguous custom
     * comparators remain unlinked, and the raw call still carries unknown effects. */
    private static Optional<CallableDeclaration> comparator(Family f,PlatformProfile profile) {
        var cls=ClassSemantics.objectClass(f.comparator()).orElse(null); if(cls==null)return Optional.empty();
        TypeNode value=QualifiedType.create().setBaseType((f.key()==null?f.value():f.key()).edit()).setConst(true).build();
        var left=NameExpression.create().setName("__key_left").build(); var right=NameExpression.create().setName("__key_right").build();
        var candidates=new ArrayList<OverloadResolution.Candidate>();
        for(var c:comparatorMembers(cls,Collections.newSetFromMap(new IdentityHashMap<>())))
            OverloadResolution.candidate(c,List.of(left,right),profile,2,ignored->Optional.of(value)).ifPresent(candidates::add);
        return OverloadResolution.selectCandidates(candidates).selected();
    }
    /** Name hiding and access precede overload selection. Multiple supplying base
     * subobjects remain ambiguous rather than choosing a convenient body. */
    private static List<CallableDeclaration> comparatorMembers(ClassDeclaration cls,Set<ClassDeclaration> seen) {
        if(!seen.add(cls)) return List.of();
        var direct=cls.members().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                .filter(c->c.name().equals("operator()")).toList();
        if(!direct.isEmpty()) return direct.stream().filter(c->c.access().orElse(cls.memberAccess()).equals("public")).toList();
        List<CallableDeclaration> found=List.of();
        for(var base:cls.bases()) {
            if(!base.access().equals("public")) return List.of();
            var parent=InheritanceSemantics.baseClass(base).orElse(null); if(parent==null)continue;
            var branch=Collections.newSetFromMap(new IdentityHashMap<ClassDeclaration,Boolean>()); branch.addAll(seen);
            var candidates=comparatorMembers(parent,branch);
            if(!candidates.isEmpty()) {if(!found.isEmpty())return List.of();found=candidates;}
        }
        return found;
    }
    private static boolean effects(ContainerOperation.Builder operation,ContainerOperation.Kind kind,Family f,PlatformProfile profile,EditContext context) {
        if(kind==SPLICE || kind==SWAP)return true;
        var cls=ClassSemantics.objectClass(f.value()).orElse(null); if(cls==null)return true;
        var members=ClassSemantics.specialMembers(cls);
        if(kind==COUNT || kind==RESIZE) return false; // Default construction is not represented as a copy.
        if((f.kind()==ContainerType.Kind.DEQUE || f.kind()==ContainerType.Kind.PRIORITY_QUEUE
                || Set.of(ASSIGN_COPY,ASSIGN_FILL,ASSIGN_RANGE).contains(kind)) && mutates(kind,f)
                && members.stream().anyMatch(m->m.kind()==ClassSemantics.SpecialKind.COPY_ASSIGNMENT && m.origin()!=ClassSemantics.Origin.IMPLICIT)) return false;
        boolean copy=Set.of(COPY,FILL,RANGE,ASSIGN_COPY,ASSIGN_FILL,ASSIGN_RANGE,INSERT,INSERT_FILL,INSERT_RANGE,PUSH_BACK,PUSH_FRONT,PUSH,RESIZE).contains(kind);
        if(copy) {
            var selected=ClassSemantics.selectedElementCopyConstructor(f.value(),null,profile);
            if(selected.isEmpty() && !scalarLibraryPair(cls) && members.stream().anyMatch(m->m.kind()==ClassSemantics.SpecialKind.COPY_CONSTRUCTOR && m.origin()!=ClassSemantics.Origin.IMPLICIT)) return false;
            if(selected.isPresent()) {if(!selected.get().access().orElse("public").equals("public"))return false;operation.setElementCopyConstructor(context.copy(selected.get()));}
        }
        if(mutates(kind,f)) {
            var destructors=ClassSemantics.specialMembers(cls).stream().filter(m->m.kind()==ClassSemantics.SpecialKind.DESTRUCTOR).flatMap(m->m.declarations().stream()).toList();
            if(destructors.size()>1 || destructors.stream().anyMatch(d->!d.access().orElse("public").equals("public")))return false;
            if(!destructors.isEmpty())operation.setElementDestructor(context.copy(destructors.getFirst()));
        }
        return true;
    }
    private static boolean scalarLibraryPair(ClassDeclaration cls) {
        return LibraryDeclarations.isLibrary(cls) && cls.name().equals("pair") && cls.templateArguments().size()==2
                && cls.templateArguments().stream().allMatch(a->a.type().isPresent() && ClassSemantics.objectClass(a.type().orElseThrow()).isEmpty());
    }
    private static void conditionals(Node node,boolean conditional,Set<Node> result) {
        if(conditional)result.add(node);
        boolean next=conditional || node instanceof IfStatement || node instanceof WhileStatement || node instanceof ForStatement || node instanceof DoWhileStatement || node instanceof SwitchStatement || node instanceof TryStatement || node instanceof ConditionalExpression || node instanceof LogicalAndExpression || node instanceof LogicalOrExpression;
        for(var child:node.children())conditionals(child,next,result);
    }
    private static void walk(Node node,NodeVisitor visitor) {
        if(node instanceof SizeofExpression || node instanceof TypeidExpression) node.accept(visitor);
        else if(node instanceof Expression || node instanceof VariableDeclaration) {for(var child:node.children())walk(child,visitor);node.accept(visitor);}
        else {node.accept(visitor);for(var child:node.children())walk(child,visitor);}
    }
}
