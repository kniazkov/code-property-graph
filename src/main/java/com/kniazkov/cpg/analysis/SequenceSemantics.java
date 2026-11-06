/* Conservative C++03 character, iterator and algorithm semantics. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;
import static com.kniazkov.cpg.model.SequenceOperation.Kind.*;

/** Publishes references to written operations, never substitutes an implementation of the STL. */
public final class SequenceSemantics {
    private SequenceSemantics() { }
    public static Project green(Project project) {
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var types = new LinkedHashMap<ClassDeclaration, CharacterSequenceType.Builder>();
        var sites = new IdentityHashMap<ClassDeclaration,List<NodeBuilder<? extends TypeNode>>>();
        for (var instance : project.classTemplateInstances()) {
            if (!LibraryDeclarations.isLibrary(instance.origin()) || instance.selected()!=instance.origin()
                    || !instance.origin().name().equals("basic_string")) continue;
            var args=instance.arguments();
            if(args.size()!=3 || args.stream().anyMatch(a->a.type().isEmpty()))continue;
            if(!Set.of("char","wchar_t").contains(args.getFirst().type().orElseThrow().canonicalIdentity()))continue;
            // Custom traits and allocators can change observable behavior; leave them raw.
            if(!standard(project,args.get(1).type().orElseThrow(),"char_traits")
                    || !standard(project,args.get(2).type().orElseThrow(),"allocator"))continue;
            var cls=instance.declaration();
            types.put(cls,CharacterSequenceType.create().setDeclaration(context.copy(cls))
                    .setCharacterType(context.copy(args.getFirst().type().orElseThrow()))
                    .setTraitsType(context.copy(args.get(1).type().orElseThrow()))
                    .setAllocatorType(context.copy(args.get(2).type().orElseThrow())));
            types.get(cls).addTransformationProvenance("SequenceSemantics", "source-construct", cls);
            sites.put(cls,new ArrayList<>());
        }
        var operations=new ArrayList<NodeBuilder<? extends SequenceOperation>>();
        for(var source:SourceContexts.roots(project)) {
            if(source.source().language()!=Language.CPP || LibraryDeclarations.isLibrary(source.node()))continue;
            var profile=TypeSemantics.profile(source.source());
            walk(source.node(),new NodeVisitor() {
                boolean aliasesKnown=true;
                final Set<Node> reassigned=Collections.newSetFromMap(new IdentityHashMap<>());
                final Set<Node> locals=Collections.newSetFromMap(new IdentityHashMap<>());
                @Override public void visit(CallableDeclaration callable) {
                    aliasesKnown=true;reassigned.clear();locals.clear();
                    callable.walk(new NodeVisitor() {
                        @Override public void visit(VariableDeclaration v) {locals.add(v);}
                        @Override public void visit(AssignmentExpression a) {if(written(a.target()) instanceof NameExpression n)n.target().ifPresent(reassigned::add);}
                        @Override public void visit(CallExpression call) {
                            var target=call.target().orElse(null);
                            if(target==null || !library(target) || Set.of("find_if","transform").contains(target.name())
                                    || target.name().equals("sort") && call.arguments().size()==3
                                    || target.name().equals("accumulate") && call.arguments().size()==4)aliasesKnown=false;
                        }
                    });
                }
                Optional<Node> output(Expression expression) {return output(expression,Collections.newSetFromMap(new IdentityHashMap<>()));}
                Optional<Node> output(Expression expression,Set<Node> seen) {
                    var direct=outputDestination(expression);if(direct.isPresent())return direct;
                    var e=written(expression);
                    if(!aliasesKnown || !(e instanceof NameExpression name) || !(name.target().orElse(null) instanceof VariableDeclaration v)
                            || !locals.contains(v) || reassigned.contains(v) || !seen.add(v) || !outputAdapter(expression,profile))return Optional.empty();
                    var init=v.initializer().map(SequenceSemantics::written).orElse(null);
                    int expected=ClassSemantics.objectClass(v.type().orElseThrow()).map(c->c.name().equals("insert_iterator")?2:1).orElse(1);
                    if(init instanceof InitializationExpression i && i.form()==InitializationExpression.Form.DIRECT && i.arguments().size()==expected
                            && v.constructorTarget().filter(SequenceSemantics::library).isPresent())return Optional.of(i.arguments().getFirst());
                    return init==null?Optional.empty():output(init,seen);
                }

                ClassDeclaration owner(TypeNode type) {var cls=ClassSemantics.objectClass(TypeSemantics.withoutReference(type)).orElse(null);return types.containsKey(cls)?cls:null;}
                ClassDeclaration owner(Expression e) {return TypeSemantics.expressionType(e,profile).map(this::owner).orElse(null);}
                @Override public void visit(TypeNode type) {var cls=owner(type);if(cls!=null)sites.get(cls).add(context.copy(type));}
                @Override public void visit(VariableDeclaration v) {
                    if(!v.isDefinition() || v.type().isEmpty())return;
                    var type=TypeSemantics.unqualified(v.type().orElseThrow());
                    if(type instanceof ReferenceType || type instanceof ArrayType)return;
                    var cls=owner(type);
                    if(cls==null) {
                        var info=LibraryIteratorSemantics.info(type);
                        var init=v.initializer().map(SequenceSemantics::written).orElse(null);
                        var target=v.constructorTarget().orElse(null);
                        if(info.filter(LibraryIteratorSemantics.Info::reverse).isPresent() && init instanceof InitializationExpression i && i.arguments().size()==1 && target!=null && library(target))
                            operations.add(base(REVERSE_ITERATOR,v,target,i.arguments()).setDestination(context.copy(v)).setFirst(context.copy(i.arguments().getFirst())).setDirection(SequenceOperation.Direction.REVERSE).setResultKind(SequenceOperation.ResultKind.ITERATOR).setWritable(!info.orElseThrow().readOnly()));
                        return;
                    }
                    var init=v.initializer().map(SequenceSemantics::written).orElse(null);
                    List<Expression> args=init==null?List.of():init instanceof InitializationExpression i && i.form()==InitializationExpression.Form.DIRECT?i.arguments():List.of(init);
                    var kind=args.isEmpty()?STRING_EMPTY:args.size()==1?(owner(args.getFirst())==cls?STRING_COPY:STRING_C_STRING):args.size()==2?(integer(args.getFirst(),profile)?STRING_FILL:integer(args.get(1),profile)?STRING_BUFFER:STRING_RANGE):null;
                    if(args.size()>1 && owner(args.getFirst())==cls)return; // substring-copy overload is not a buffer
                    if(kind==STRING_RANGE && !compatible(args.getFirst(),args.get(1),profile))return;
                    if(kind!=null)string(kind,v,v,cls,v.constructorTarget().orElse(null),args);
                }
                @Override public void visit(IndexExpression index) {var cls=owner(index.left());if(cls!=null)string(STRING_SUBSCRIPT,index,index.left(),cls,index.operatorTarget().orElse(null),List.of(index.right()));}
                @Override public void visit(AssignmentExpression a) {if(written(a.target()) instanceof NameExpression name)name.target().ifPresent(reassigned::add);var cls=owner(a.target());if(cls!=null)string(STRING_ASSIGN,a,a.target(),cls,a.operatorTarget().orElse(null),List.of(a.value()));}
                @Override public void visit(CallExpression call) {
                    var target=call.target().orElse(null);if(target==null || !library(target)){aliasesKnown=false;return;}
                    if(call.callee() instanceof MemberExpression m && !(m instanceof ScopeMemberExpression)) {
                        var cls=owner(m.receiver());if(cls==null)return;
                        var kind=switch(target.name()) {
                            case "append"->STRING_APPEND;case "assign","operator="->STRING_ASSIGN;
                            case "find"->STRING_FIND;case "substr"->STRING_SUBSTR;case "size","length"->STRING_SIZE;
                            case "at"->STRING_AT;case "operator[]"->STRING_SUBSCRIPT;
                            case "begin"->STRING_BEGIN;case "end"->STRING_END;case "rbegin"->STRING_RBEGIN;case "rend"->STRING_REND;default->null;
                        };
                        if(kind!=null)string(kind,call,m.receiver(),cls,target,call.arguments());
                    } else algorithm(call,target);
                }
                SequenceOperation.Builder base(SequenceOperation.Kind kind,Node syntax,CallableDeclaration target,List<? extends Expression> args) {
                    var b=SequenceOperation.create().setKind(kind).setSyntax(context.copy(syntax)).setSelectedTarget(context.copy(target));
                    b.addTransformationProvenance("SequenceSemantics", "source-construct", syntax);
                    var inputs=new ArrayList<NodeBuilder<? extends Node>>();args.forEach(a->inputs.add(context.copy(a)));b.setSources(inputs);
                    target.returnType().ifPresent(t->b.setResultType(context.copy(t)));
                    return b;
                }
                void string(SequenceOperation.Kind kind,Node syntax,Node destination,ClassDeclaration cls,CallableDeclaration target,List<? extends Expression> args) {
                    if(target==null || !library(target))return;
                    if((kind==STRING_APPEND || kind==STRING_ASSIGN) && args.size()>1 && owner(args.getFirst())==cls)return;
                    var b=base(kind,syntax,target,args).setCharacterType(types.get(cls)).setDestination(context.copy(destination));
                    if((kind==STRING_SUBSTR || kind==STRING_AT || kind==STRING_SUBSCRIPT) && !args.isEmpty())b.setPosition(context.copy(args.getFirst()));
                    if(kind==STRING_SUBSTR && args.size()>1)b.setCount(context.copy(args.get(1)));
                    if(kind==STRING_FIND && args.size()>1)b.setPosition(context.copy(args.get(1)));
                    if(Set.of(STRING_EMPTY,STRING_COPY,STRING_C_STRING,STRING_BUFFER,STRING_FILL,STRING_RANGE,STRING_ASSIGN,STRING_APPEND).contains(kind))b.setMutatedObjects(List.of(context.copy(destination)));
                    if(!args.isEmpty() && Set.of(STRING_COPY,STRING_C_STRING,STRING_BUFFER,STRING_FILL,STRING_RANGE,STRING_ASSIGN,STRING_APPEND,STRING_FIND).contains(kind)) {
                        if(kind==STRING_RANGE) b.setLengthKind(SequenceOperation.LengthKind.RANGE).setFirst(context.copy(args.getFirst())).setLast(context.copy(args.get(1)));
                        else if(kind==STRING_FILL) b.setLengthKind(SequenceOperation.LengthKind.EXPLICIT_COUNT).setCount(context.copy(args.getFirst()));
                        else if(owner(args.getFirst())==cls) b.setLengthKind(SequenceOperation.LengthKind.SEQUENCE).setInputSequence(context.copy(args.getFirst()));
                        else if(kind==STRING_BUFFER || (kind==STRING_APPEND || kind==STRING_ASSIGN) && args.size()==2 && !integer(args.getFirst(),profile) && integer(args.get(1),profile)) b.setLengthKind(SequenceOperation.LengthKind.EXPLICIT_COUNT).setCount(context.copy(args.get(1)));
                        else if(kind==STRING_FIND && args.size()==3) b.setLengthKind(SequenceOperation.LengthKind.EXPLICIT_COUNT).setCount(context.copy(args.get(2)));
                        else if((kind==STRING_APPEND || kind==STRING_ASSIGN) && args.size()==2 && integer(args.getFirst(),profile))b.setLengthKind(SequenceOperation.LengthKind.EXPLICIT_COUNT).setCount(context.copy(args.getFirst()));
                        else if((kind==STRING_APPEND || kind==STRING_ASSIGN) && args.size()==2) {
                            if(!compatible(args.getFirst(),args.get(1),profile))return;
                            b.setLengthKind(SequenceOperation.LengthKind.RANGE).setFirst(context.copy(args.getFirst())).setLast(context.copy(args.get(1)));
                        }
                        else if(!integer(args.getFirst(),profile))b.setLengthKind(SequenceOperation.LengthKind.NULL_TERMINATED);
                    }
                    var result=switch(kind) {
                        case STRING_AT,STRING_SUBSCRIPT -> SequenceOperation.ResultKind.REFERENCE;
                        case STRING_BEGIN,STRING_END,STRING_RBEGIN,STRING_REND -> SequenceOperation.ResultKind.ITERATOR;
                        case STRING_SUBSTR -> SequenceOperation.ResultKind.SEQUENCE;
                        case STRING_APPEND,STRING_ASSIGN -> SequenceOperation.ResultKind.SEQUENCE_REFERENCE;
                        case STRING_SIZE,STRING_FIND -> SequenceOperation.ResultKind.VALUE;
                        default -> SequenceOperation.ResultKind.NONE;
                    };
                    b.setResultKind(result);
                    if(Set.of(STRING_AT,STRING_SUBSCRIPT,STRING_BEGIN,STRING_END,STRING_RBEGIN,STRING_REND,STRING_SIZE,STRING_SUBSTR,STRING_FIND).contains(kind))b.setInputSequence(context.copy(destination));
                    b.setSizeEffect(Set.of(STRING_EMPTY,STRING_COPY,STRING_C_STRING,STRING_BUFFER,STRING_FILL,STRING_RANGE,STRING_ASSIGN,STRING_APPEND).contains(kind)?SequenceOperation.SizeEffect.MAY_CHANGE:SequenceOperation.SizeEffect.UNCHANGED);
                    b.setDirection(kind==STRING_RBEGIN || kind==STRING_REND?SequenceOperation.Direction.REVERSE:SequenceOperation.Direction.FORWARD);
                    if((result==SequenceOperation.ResultKind.REFERENCE || result==SequenceOperation.ResultKind.ITERATOR || result==SequenceOperation.ResultKind.SEQUENCE_REFERENCE) && destination instanceof Expression e)b.setWritable(TypeSemantics.expressionType(e,profile).map(TypeSemantics::withoutReference).map(t->(TypeSemantics.qualifiers(t)&1)==0).orElse(false));
                    operations.add(b);
                }
                void algorithm(CallExpression call,CallableDeclaration target) {
                    SequenceOperation.Kind kind;try{kind=SequenceOperation.Kind.valueOf(target.name().toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return;}
                    var args=call.arguments();int n=args.size();
                    if(kind==BACK_INSERTER || kind==FRONT_INSERTER || kind==INSERTER) {
                        if(n!=(kind==INSERTER?2:1))return;
                        var b=base(kind,call,target,args).setOutputDestination(context.copy(args.getFirst())).setResultKind(SequenceOperation.ResultKind.OUTPUT_ADAPTER).setWritable(true);
                        operations.add(b);return;
                    }
                    if(kind==ADVANCE) {if(n==2 && element(args.getFirst(),profile).isPresent() && integer(args.get(1),profile))operations.add(base(kind,call,target,args).setDestination(context.copy(args.getFirst())).setCount(context.copy(args.get(1))).setMutatedObjects(List.of(context.copy(args.getFirst()))));return;}
                    if(!Set.of(DISTANCE,COPY,FIND_IF,TRANSFORM,SORT,REMOVE,ACCUMULATE).contains(kind) || n<2)return;
                    if(kind==DISTANCE && n!=2 || kind==COPY && n!=3 || kind==FIND_IF && n!=3 || kind==TRANSFORM && n!=4 && n!=5 || kind==SORT && n!=2 && n!=3 || kind==REMOVE && n!=3 || kind==ACCUMULATE && n!=3 && n!=4)return;
                    var first=args.getFirst();var last=args.get(1);var element=element(first,profile);
                    if(element.isEmpty() || !compatible(first,last,profile))return;
                    if(kind==SORT && !randomAccess(first,profile))return;
                    if((kind==SORT || kind==REMOVE) && !writable(first,profile))return;
                    var b=base(kind,call,target,args).setFirst(context.copy(first)).setLast(context.copy(last)).setLengthKind(SequenceOperation.LengthKind.RANGE);
                    sequence(first).ifPresent(s->b.setInputSequence(context.copy(s)));
                    b.setSizeEffect(SequenceOperation.SizeEffect.UNCHANGED);
                    TypeSemantics.expressionType(first,profile).flatMap(LibraryIteratorSemantics::info).ifPresent(i->b.setDirection(i.reverse()?SequenceOperation.Direction.REVERSE:SequenceOperation.Direction.FORWARD));
                    if(kind==COPY || kind==TRANSFORM) {
                        int index=kind==COPY?2:n==4?2:3;var output=args.get(index);
                        if(!writable(output,profile))return;
                        b.setDestination(context.copy(output)).setResultKind(SequenceOperation.ResultKind.ITERATOR);
                        Node out=output(output).orElseGet(()->sequence(output).orElse(output));
                        if(outputAdapter(output,profile)) {
                            b.setSizeEffect(SequenceOperation.SizeEffect.MAY_CHANGE);
                            if(!plainOutputAdapter(project,output,profile))b.setCallableEffect(SequenceOperation.CallableEffect.UNKNOWN).setCallableMayThrow(true);
                        }
                        b.setOutputDestination(context.copy(out)).setMutatedObjects(List.of(context.copy(out)));
                        if(n==5)b.setSecondFirst(context.copy(args.get(2)));
                    } else if(kind==SORT || kind==REMOVE) b.setMutatedObjects(List.of(context.copy(sequence(first).orElse(first)))).setResultKind(kind==REMOVE?SequenceOperation.ResultKind.ITERATOR:SequenceOperation.ResultKind.NONE);
                    else b.setResultKind(kind==FIND_IF?SequenceOperation.ResultKind.ITERATOR:SequenceOperation.ResultKind.VALUE);
                    if(Set.of(COPY,TRANSFORM,FIND_IF,REMOVE).contains(kind))b.setWritable(writable(kind==COPY?args.get(2):kind==TRANSFORM?args.get(n==4?2:3):first,profile));
                    if(kind==ACCUMULATE) {b.setInitialValue(context.copy(args.get(2)));target.returnType().ifPresent(t->b.setAccumulatorType(context.copy(t)));}
                    int callback=kind==FIND_IF?2:kind==TRANSFORM?n-1:kind==SORT && n==3?2:kind==ACCUMULATE && n==4?3:-1;
                    if(callback>=0) {
                        var callable=args.get(callback);var parameterTypes=new ArrayList<TypeNode>();
                        if(kind==ACCUMULATE) {var acc=TypeSemantics.expressionType(args.get(2),profile);if(acc.isEmpty())return;parameterTypes.add(acc.get());}
                        parameterTypes.add(element.get());
                        if(kind==SORT)parameterTypes.add(element.get());
                        if(kind==TRANSFORM && n==5){var second=element(args.get(2),profile);if(second.isEmpty())return;parameterTypes.add(second.get());}
                        b.setCallable(context.copy(callable)).setCallableEffect(SequenceOperation.CallableEffect.UNKNOWN).setCallableMayThrow(true);
                        callback(callable,parameterTypes,profile).ifPresent(t->b.setCallableTarget(context.copy(t)));
                        aliasesKnown=false;
                    }
                    if(callback<0 && ClassSemantics.objectClass(element.get()).isPresent())
                        b.setCallableEffect(SequenceOperation.CallableEffect.UNKNOWN).setCallableMayThrow(true);
                    operations.add(b);
                }
            });
        }
        types.forEach((cls,type)->type.setWrittenTypes(sites.get(cls)));
        root.setCharacterSequenceTypes(new ArrayList<>(types.values())).setSequenceSemantics(operations);
        return root.build().recordPass("SequenceSemantics");
    }
    private static boolean standard(Project p,TypeNode type,String name) {
        var cls=ClassSemantics.objectClass(type).orElse(null);
        return cls!=null && cls.name().equals(name) && LibraryDeclarations.isLibrary(cls)
                && p.classTemplateInstances().stream().filter(i->i.declaration()==cls).allMatch(i->i.origin()==i.selected() && LibraryDeclarations.isLibrary(i.origin()));
    }
    private static boolean library(CallableDeclaration target) {return LibraryDeclarations.isLibrary(target)||target.templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent();}
    private static Expression written(Expression e) {while(e instanceof ConversionExpression c && !c.isExplicit())e=c.operand();return e;}
    private static boolean integer(Expression e,PlatformProfile p) {return TypeSemantics.expressionType(e,p).flatMap(t->TypeSemantics.integerType(t,p)).isPresent();}
    private static Optional<TypeNode> element(Expression e,PlatformProfile p) {
        return TypeSemantics.expressionType(e,p).map(TypeSemantics::decay).flatMap(t->t instanceof PointerType ptr?Optional.of(ptr.pointee()):LibraryIteratorSemantics.info(t).map(LibraryIteratorSemantics.Info::referenceType));
    }
    private static boolean plainOutputAdapter(Project project,Expression e,PlatformProfile p) {
        var adapter=TypeSemantics.expressionType(e,p).flatMap(ClassSemantics::objectClass).orElse(null);
        if(adapter==null || adapter.templateArguments().isEmpty())return false;
        var container=adapter.templateArguments().getFirst().type().flatMap(ClassSemantics::objectClass).orElse(null);
        if(container==null || !LibraryDeclarations.isLibrary(container) || !Set.of("vector","deque","list","basic_string").contains(container.name()) || container.templateArguments().size()<2)return false;
        var args=container.templateArguments();
        return args.getFirst().type().filter(t->ClassSemantics.objectClass(t).isEmpty()).isPresent()
                && args.getLast().type().filter(t->standard(project,t,"allocator")).isPresent();
    }
    private static boolean outputAdapter(Expression e,PlatformProfile p) {
        return TypeSemantics.expressionType(e,p).flatMap(ClassSemantics::objectClass).filter(LibraryDeclarations::isLibrary)
                .filter(c->Set.of("back_insert_iterator","front_insert_iterator","insert_iterator").contains(c.name())).isPresent();
    }
    private static boolean writable(Expression e,PlatformProfile p) {
        if(outputDestination(e).isPresent())return true;
        return TypeSemantics.expressionType(e,p).map(TypeSemantics::decay).map(t->{
            if(TypeSemantics.unqualified(t) instanceof PointerType ptr)return (TypeSemantics.qualifiers(ptr.pointee())&1)==0;
            if(LibraryIteratorSemantics.info(t).filter(i->!i.readOnly()).isPresent())return true;
            return ClassSemantics.objectClass(t).filter(LibraryDeclarations::isLibrary)
                    .filter(c->Set.of("back_insert_iterator","front_insert_iterator","insert_iterator").contains(c.name())).isPresent();
        }).orElse(false);
    }
    private static boolean randomAccess(Expression e,PlatformProfile p) {return TypeSemantics.expressionType(e,p).map(TypeSemantics::decay).map(t->t instanceof PointerType || LibraryIteratorSemantics.info(t).filter(LibraryIteratorSemantics.Info::randomAccess).isPresent()).orElse(false);}
    private static boolean compatible(Expression a,Expression b,PlatformProfile p) {
        var x=TypeSemantics.expressionType(a,p).map(TypeSemantics::decay);var y=TypeSemantics.expressionType(b,p).map(TypeSemantics::decay);
        return x.isPresent() && y.isPresent() && x.get().canonicalIdentity().equals(y.get().canonicalIdentity());
    }
    private static Optional<Node> sequence(Expression expression) {
        var e=written(expression);
        if(e instanceof CallExpression call && call.callee() instanceof MemberExpression m && call.target().filter(SequenceSemantics::library).filter(t->Set.of("begin","end","rbegin","rend").contains(t.name())).isPresent())return Optional.of(m.receiver());
        return Optional.empty();
    }
    private static Optional<Node> outputDestination(Expression expression) {
        var e=written(expression);
        if(e instanceof CallExpression call && call.target().filter(SequenceSemantics::library).filter(t->Set.of("back_inserter","front_inserter","inserter").contains(t.name())).isPresent() && !call.arguments().isEmpty())return Optional.of(call.arguments().getFirst());
        return Optional.empty();
    }
    private static Optional<CallableDeclaration> callback(Expression expression,List<TypeNode> types,PlatformProfile profile) {
        var e=written(expression);if(e instanceof AddressOfExpression address)e=written(address.operand());
        List<CallableDeclaration> declarations;
        if(e instanceof NameExpression name && name.target().orElse(null) instanceof CallableDeclaration fn)declarations=List.of(fn);
        else {var cls=TypeSemantics.expressionType(e,profile).flatMap(ClassSemantics::objectClass).orElse(null);if(cls==null)return Optional.empty();declarations=callOperators(cls,Collections.newSetFromMap(new IdentityHashMap<>()));}
        var arguments=new ArrayList<Expression>();var argumentTypes=new IdentityHashMap<Expression,TypeNode>();
        for(var type:types){var arg=NameExpression.create().setName("__algorithm_argument"+arguments.size()).build();arguments.add(arg);argumentTypes.put(arg,type);}
        var candidates=new ArrayList<OverloadResolution.Candidate>();
        for(var fn:declarations)OverloadResolution.candidate(fn,arguments,profile,arguments.size(),arg->Optional.ofNullable(argumentTypes.get(arg))).ifPresent(candidates::add);
        return OverloadResolution.selectCandidates(candidates).selected();
    }
    private static List<CallableDeclaration> callOperators(ClassDeclaration cls,Set<ClassDeclaration> seen) {
        if(!seen.add(cls))return List.of();
        var direct=cls.members().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).filter(c->c.name().equals("operator()")).toList();
        if(!direct.isEmpty())return direct.stream().filter(c->c.access().orElse(cls.memberAccess()).equals("public")).toList();
        List<CallableDeclaration> found=List.of();
        for(var base:cls.bases()) {if(!base.access().equals("public"))return List.of();var parent=InheritanceSemantics.baseClass(base).orElse(null);if(parent==null)continue;
            var branch=Collections.newSetFromMap(new IdentityHashMap<ClassDeclaration,Boolean>());branch.addAll(seen);var candidates=callOperators(parent,branch);
            if(!candidates.isEmpty()){if(!found.isEmpty())return List.of();found=candidates;}}
        return found;
    }
    private static void walk(Node node,NodeVisitor visitor) {
        if(node instanceof SizeofExpression || node instanceof TypeidExpression)node.accept(visitor);
        else if(node instanceof Expression || node instanceof VariableDeclaration){for(var child:node.children())walk(child,visitor);node.accept(visitor);}
        else {node.accept(visitor);for(var child:node.children())walk(child,visitor);}
    }
}
