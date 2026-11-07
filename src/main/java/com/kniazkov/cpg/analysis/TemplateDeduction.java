/* C++03 callable template deduction and immediate-context substitution. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;

final class TemplateDeduction {
    record Result(List<TemplateArgument> structuredArguments, Map<Node,Node> substitutions, CallableDeclaration signature) {
        List<TypeNode> arguments(){return structuredArguments.stream().flatMap(a->a.type().stream()).toList();}
    }
    static Optional<Result> deduce(CallableDeclaration origin, List<TemplateArgument> explicit,
            List<Expression> actual, PlatformProfile profile,Map<ClassDeclaration,ClassDeclaration> classOrigins) {
        if (explicit.size() > origin.templateParameters().size() || actual.size() < OverloadResolution.requiredArguments(origin)
                || !origin.variadic() && actual.size() > origin.parameters().size()) return Optional.empty();
        var values = new LinkedHashMap<TemplateParameterDeclaration,TypeNode>();
        var fixed = new HashSet<TemplateParameterDeclaration>();
        var constants=new LinkedHashMap<TemplateParameterDeclaration,Expression>();
        for (int i=0;i<explicit.size();i++) {
            var parameter=origin.templateParameters().get(i);
            if(parameter.kind()==TemplateParameterDeclaration.Kind.VALUE) {
                if(explicit.get(i).expression().isEmpty())return Optional.empty();
                var constant=ConstantEvaluator.evaluate(explicit.get(i).expression().get(),profile);
                if(constant.isEmpty())return Optional.empty();
                constants.put(parameter,IntegerLiteral.create().setValue(constant.get().number()).build());
            } else {
                TypeNode type = explicit.get(i).type().orElse(null);
                if (type == null || TypeSemantics.dependent(type)) return Optional.empty();
                if(parameter.kind()==TemplateParameterDeclaration.Kind.TEMPLATE && !compatibleTemplate(parameter,type,classOrigins))return Optional.empty();
                values.put(parameter,type); fixed.add(parameter);
            }
        }
        for (int i=0;i<Math.min(actual.size(),origin.parameters().size());i++) {
            TypeNode formal = origin.parameters().get(i).type().orElse(null);
            TypeNode type = TypeSemantics.expressionType(actual.get(i),profile).orElse(null);
            if (formal == null || type == null || TypeSemantics.dependent(type)) return Optional.empty();
            if(!deduceValues(TypeSemantics.withoutReference(formal),TypeSemantics.withoutReference(type),constants,profile))return Optional.empty();
            if (!(formal instanceof ReferenceType)) type=TypeSemantics.decay(type);
            if (!deduceType(TypeSemantics.unqualified(TypeSemantics.withoutReference(formal)),
                    TypeSemantics.unqualified(TypeSemantics.withoutReference(type)), values, fixed,classOrigins)) return Optional.empty();
        }
        if (origin.templateParameters().stream().anyMatch(p -> !values.containsKey(p) && !constants.containsKey(p))) return Optional.empty();
        // An unbound nested alias is not yet a concrete template argument. Wait
        // for a later binding round instead of publishing a malformed instance.
        if (values.values().stream().anyMatch(type -> type instanceof NamedType name
                && name.fundamentalKind().isEmpty() && name.declaration().isEmpty() && name.alias().isEmpty())) return Optional.empty();
        var substitutions = new IdentityHashMap<Node,Node>();
        for(int i=0;i<Math.min(actual.size(),origin.parameters().size());i++) {
            TypeNode formal=origin.parameters().get(i).type().orElseThrow();
            TypeNode type=TypeSemantics.expressionType(actual.get(i),profile).orElseThrow();
            rememberTemplateTypes(TypeSemantics.withoutReference(formal),TypeSemantics.withoutReference(type),substitutions);
        }
        var failed = new boolean[1];
        // Only signature syntax participates in SFINAE. Body substitution errors are diagnosed after selection.
        var signatureNodes = new ArrayList<Node>(origin.parameters()); origin.returnType().ifPresent(signatureNodes::add);
        for (Node node : signatureNodes) node.walk(new NodeVisitor() { @Override public void visit(NamedType type) {
            if(substitutions.containsKey(type))return;
            if (type.declaration().orElse(null) instanceof TemplateParameterDeclaration p && values.containsKey(p) && !type.hasTemplateArguments()) substitutions.put(type,values.get(p));
            else if (type.dependent() && !type.hasTemplateArguments()) {
                TypeNode replacement = dependentType(type,values);
                if (replacement == null) failed[0]=true; else substitutions.put(type,replacement);
            }
        }});
        if (failed[0]) return Optional.empty();
        origin.walk(new NodeVisitor() { @Override public void visit(NamedType type) {
            if(substitutions.containsKey(type))return;
            if (type.declaration().orElse(null) instanceof TemplateParameterDeclaration p && values.containsKey(p) && !type.hasTemplateArguments()) substitutions.put(type,values.get(p));
            else if (type.dependent() && !type.hasTemplateArguments()) { TypeNode replacement=dependentType(type,values); if(replacement!=null) substitutions.put(type,replacement); }
        }});
        origin.walk(new NodeVisitor(){@Override public void visit(NamedType type){
            if(!substitutions.containsKey(type) && type.hasTemplateArguments()
                    && type.declaration().orElse(null) instanceof TemplateParameterDeclaration p && p.kind()==TemplateParameterDeclaration.Kind.TEMPLATE
                    && values.get(p) instanceof NamedType template)
                substitutions.put(type,TemplateSubstitution.templateType(type,template.name(),substitutions));
        }});
        origin.walk(new NodeVisitor(){@Override public void visit(NameExpression name){
            if(name.target().orElse(null) instanceof TemplateParameterDeclaration p && constants.containsKey(p))substitutions.put(name,constants.get(p));
        }});
        // setfill's unspecified result retains the character parameter of its
        // specified output operation without exposing any representation.
        if (LibraryDeclarations.isLibrary(origin)) origin.walk(new NodeVisitor() {
            @Override public void visit(OpaqueLibraryType type) {
                if (!type.name().equals("setfill_result")) return;
                for (var entry : values.entrySet()) if (entry.getKey().name().equals("CharT")) {
                    substitutions.put(type, type.edit().setIdentity("library::setfill_result<" + entry.getValue().canonicalIdentity() + ">").build());
                    break;
                }
            }
        });
        CallableDeclaration signature = TemplateSubstitution.signature(origin,substitutions);
        // C++03 13.5: a non-member overloaded operator requires a class or
        // enumeration operand. This is a signature rule, not a library body
        // prerequisite (e.g. EqualityComparable), and also applies to explicit calls.
        if (origin.name().startsWith("operator") && origin.name().length() > 8
                && !Character.isJavaIdentifierPart(origin.name().charAt(8)) && origin.name().charAt(8) != ' '
                && (!(origin instanceof MethodDeclaration) || origin.isFriend())
                && signature.parameters().stream().flatMap(p -> p.type().stream()).noneMatch(TemplateDeduction::operatorOperand))
            return Optional.empty();
        if (OverloadResolution.candidate(signature,actual,profile,OverloadResolution.requiredArguments(origin)).isEmpty()) return Optional.empty();
        var arguments=origin.templateParameters().stream().map(p -> values.containsKey(p)
                ? TemplateSubstitution.typeArgument(values.get(p),p.kind()==TemplateParameterDeclaration.Kind.TEMPLATE?TemplateArgument.Kind.TEMPLATE:TemplateArgument.Kind.TYPE)
                : TemplateArgument.create().setExpression(((IntegerLiteral)constants.get(p)).edit()).build()).toList();
        return Optional.of(new Result(arguments,substitutions,signature));
    }
    private static boolean operatorOperand(TypeNode type) {
        type = TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
        return !(type instanceof ArrayType) && ClassSemantics.objectClass(type).isPresent()
                || type instanceof NamedType named && named.declaration().orElse(null) instanceof EnumDeclaration
                // An implementation-defined library type may be a class. Do not
                // reject its portable interface based on a guessed representation.
                || type instanceof OpaqueLibraryType opaque
                    && (opaque.category() == OpaqueLibraryType.Category.OBJECT || opaque.category() == OpaqueLibraryType.Category.UNSPECIFIED);
    }
    static Optional<Result> explicit(CallableDeclaration origin,CallableDeclaration request,PlatformProfile profile,Map<ClassDeclaration,ClassDeclaration> classOrigins) {
        var actual=request.parameters().stream().map(parameter -> (Expression)NameExpression.create().setName(parameter.name().isEmpty()?"__argument":parameter.name()).setTarget(parameter.edit()).build()).toList();
        return deduce(origin,request.templateArguments(),actual,profile,classOrigins);
    }
    static boolean compatibleTemplate(TemplateParameterDeclaration parameter,TypeNode argument,Map<ClassDeclaration,ClassDeclaration> origins) {
        if(!(argument instanceof NamedType named) || !(named.declaration().orElse(null) instanceof ClassDeclaration declaration))return false;
        return compatibleParameters(parameter.templateParameters(),origins.getOrDefault(declaration,declaration).templateParameters());
    }
    static boolean compatibleParameters(List<TemplateParameterDeclaration> expected,List<TemplateParameterDeclaration> actual) {
        if(expected.size()!=actual.size())return false;
        for(int i=0;i<expected.size();i++) {
            var a=expected.get(i);var b=actual.get(i);if(a.kind()!=b.kind())return false;
            if(a.kind()==TemplateParameterDeclaration.Kind.TEMPLATE && !compatibleParameters(a.templateParameters(),b.templateParameters()))return false;
            if(a.kind()==TemplateParameterDeclaration.Kind.VALUE && !a.type().map(TypeNode::canonicalIdentity).equals(b.type().map(TypeNode::canonicalIdentity)))return false;
        }
        return true;
    }
    static void rememberTemplateTypes(TypeNode formal,TypeNode actual,Map<Node,Node> substitutions) {
        formal=TypeSemantics.unqualified(formal);actual=TypeSemantics.unqualified(actual);
        if(formal instanceof NamedType name && name.hasTemplateArguments() && actual instanceof NamedType concrete) {
            NamedType matched = name.declaration().orElse(null) instanceof TemplateParameterDeclaration parameter
                    && parameter.kind() == TemplateParameterDeclaration.Kind.TEMPLATE ? concrete : templateMatch(name,concrete,new HashSet<>());
            if (matched != null) substitutions.put(formal,matched);
        }
        else if(formal instanceof PointerType p && actual instanceof PointerType q)rememberTemplateTypes(p.pointee(),q.pointee(),substitutions);
        else if(formal instanceof ArrayType p && actual instanceof ArrayType q)rememberTemplateTypes(p.elementType(),q.elementType(),substitutions);
    }
    static boolean deduceValues(TypeNode formal,TypeNode actual,Map<TemplateParameterDeclaration,Expression> values,PlatformProfile profile) {
        formal=TypeSemantics.unqualified(formal);actual=TypeSemantics.unqualified(actual);
        if(formal instanceof ArrayType array && actual instanceof ArrayType other) {
            if(array.bound().orElse(null) instanceof NameExpression name && name.target().orElse(null) instanceof TemplateParameterDeclaration p) {
                var constant=other.bound().flatMap(bound->ConstantEvaluator.evaluate(bound,profile));
                if(constant.isEmpty())return false;
                Expression value=IntegerLiteral.create().setValue(constant.get().number()).build();
                Expression prior=values.putIfAbsent(p,value);
                if(prior!=null && !ConstantEvaluator.evaluate(prior,profile).map(v->v.number().equals(constant.get().number())).orElse(false))return false;
            }
            return deduceValues(array.elementType(),other.elementType(),values,profile);
        }
        if(formal instanceof PointerType pointer && actual instanceof PointerType other)return deduceValues(pointer.pointee(),other.pointee(),values,profile);
        if (formal instanceof NamedType pattern && pattern.hasTemplateArguments() && actual instanceof NamedType concrete) {
            NamedType matched = pattern.declaration().orElse(null) instanceof TemplateParameterDeclaration parameter
                    && parameter.kind() == TemplateParameterDeclaration.Kind.TEMPLATE ? concrete : templateMatch(pattern,concrete,new HashSet<>());
            if (matched == null || pattern.templateArguments().size()!=concreteArguments(matched).size()) return false;
            for (int i=0;i<pattern.templateArguments().size();i++) {
                TemplateArgument a=pattern.templateArguments().get(i), b=concreteArguments(matched).get(i);
                if (a.type().isPresent() && b.type().isPresent()) {
                    if (!deduceValues(a.type().get(),b.type().get(),values,profile)) return false;
                } else if (a.expression().isPresent() && b.expression().isPresent()) {
                    var value=ConstantEvaluator.evaluate(b.expression().get(),profile);
                    if (value.isEmpty()) return false;
                    Expression supplied=IntegerLiteral.create().setValue(value.get().number()).build();
                    if (a.expression().get() instanceof NameExpression name && name.target().orElse(null) instanceof TemplateParameterDeclaration parameter) {
                        Expression prior=values.putIfAbsent(parameter,supplied);
                        if (prior!=null && !ConstantEvaluator.evaluate(prior,profile).map(v->v.number().equals(value.get().number())).orElse(false)) return false;
                    } else if (!ConstantEvaluator.evaluate(a.expression().get(),profile).map(v->v.number().equals(value.get().number())).orElse(false)) return false;
                } else return false;
            }
        }
        return true;
    }
    static boolean deduceType(TypeNode formal, TypeNode actual, Map<TemplateParameterDeclaration,TypeNode> values, Set<TemplateParameterDeclaration> fixed,Map<ClassDeclaration,ClassDeclaration> classOrigins) {
        if(formal instanceof QualifiedType qualified) {
            int remaining=TypeSemantics.qualifiers(actual)&~TypeSemantics.qualifiers(formal);
            TypeNode base=TypeSemantics.unqualified(actual);
            if(remaining!=0)base=QualifiedType.create().setBaseType(base.edit()).setConst((remaining&1)!=0).setVolatile((remaining&2)!=0).build();
            return deduceType(qualified.baseType(),base,values,fixed,classOrigins);
        }
        if (formal instanceof NamedType named && named.declaration().orElse(null) instanceof TemplateParameterDeclaration p) {
            if(p.kind()==TemplateParameterDeclaration.Kind.TEMPLATE && named.hasTemplateArguments()) {
                if(!(actual instanceof NamedType other) || named.templateArguments().size()!=other.templateArguments().size())return false;
                if(!compatibleTemplate(p,other,classOrigins))return false;
                ClassDeclaration actualTemplate=classOrigins.getOrDefault((ClassDeclaration)other.declaration().orElseThrow(),(ClassDeclaration)other.declaration().orElseThrow());
                TypeNode template=NamedType.create().setName(other.name()).setDeclaration(actualTemplate.edit()).build();
                TypeNode prior=values.putIfAbsent(p,template);if(prior!=null&&!prior.canonicalIdentity().equals(template.canonicalIdentity()))return false;
                for(int i=0;i<named.templateArguments().size();i++) {
                    var a=named.templateArguments().get(i).type();var b=other.templateArguments().get(i).type();
                    if(a.isEmpty()||b.isEmpty()||!deduceType(a.get(),b.get(),values,fixed,classOrigins))return false;
                }
                return true;
            }
            if (fixed.contains(p)) return true;
            TypeNode prior=values.putIfAbsent(p,actual); return prior==null || prior.canonicalIdentity().equals(actual.canonicalIdentity());
        }
        if(formal instanceof ArrayType array)return actual instanceof ArrayType other && deduceType(array.elementType(),other.elementType(),values,fixed,classOrigins);
        if (formal instanceof PointerType pointer) return actual instanceof PointerType other && deduceType(pointer.pointee(),other.pointee(),values,fixed,classOrigins);
        if (formal instanceof ReferenceType ref) return deduceType(ref.referredType(),TypeSemantics.withoutReference(actual),values,fixed,classOrigins);
        if (formal instanceof MemberPointerType pointer)
            return actual instanceof MemberPointerType other
                    && deduceType(pointer.ownerType(),other.ownerType(),values,fixed,classOrigins)
                    && deduceType(pointer.pointee(),other.pointee(),values,fixed,classOrigins);
        if (formal instanceof FunctionType function) {
            if (!(actual instanceof FunctionType other) || function.parameters().size()!=other.parameters().size()
                    || function.variadic()!=other.variadic() || function.isConst()!=other.isConst()
                    || function.isVolatile()!=other.isVolatile()) return false;
            if (!deduceType(function.returnType(),other.returnType(),values,fixed,classOrigins)) return false;
            for (int i=0;i<function.parameters().size();i++) {
                var expected=function.parameters().get(i).type(); var supplied=other.parameters().get(i).type();
                if(expected.isEmpty() || supplied.isEmpty() || !deduceType(expected.get(),supplied.get(),values,fixed,classOrigins)) return false;
            }
            return true;
        }
        if (formal instanceof NamedType named && actual instanceof NamedType other && named.hasTemplateArguments()) {
            NamedType matched = templateMatch(named,other,new HashSet<>());
            if (matched == null || named.templateArguments().size()!=concreteArguments(matched).size()) return false;
            for (int i=0;i<named.templateArguments().size();i++) {
                var a=named.templateArguments().get(i).type(); var b=concreteArguments(matched).get(i).type();
                if(a.isEmpty() && b.isEmpty()) continue; // Value arguments were checked by deduceValues.
                if(a.isEmpty() || b.isEmpty() || !deduceType(a.get(),b.get(),values,fixed,classOrigins)) return false;
            }
        }
        return true; // Nondependent portions are checked by standard conversion ranking.
    }
    private static List<TemplateArgument> concreteArguments(NamedType type) {
        if (type.declaration().orElse(null) instanceof ClassDeclaration owner
                && owner.templateParameters().isEmpty() && !owner.templateArguments().isEmpty()) return owner.templateArguments();
        return type.templateArguments();
    }
    private record MatchedBase(NamedType type, String subobject) {}
    private static NamedType templateMatch(NamedType formal, NamedType actual, Set<ClassDeclaration> visited) {
        MatchedBase match = templateBase(formal,actual,visited,"root");
        return match == null ? null : match.type();
    }
    private static MatchedBase templateBase(NamedType formal, NamedType actual, Set<ClassDeclaration> visited, String path) {
        ClassDeclaration owner = actual.declaration().filter(ClassDeclaration.class::isInstance).map(ClassDeclaration.class::cast).orElse(null);
        ClassDeclaration pattern = formal.declaration().filter(ClassDeclaration.class::isInstance).map(ClassDeclaration.class::cast).orElse(null);
        boolean sameTemplate = pattern != null && owner != null && pattern.typeIdentity().isPresent() && owner.typeIdentity().isPresent()
                ? pattern.typeIdentity().map(i -> i.split("<",2)[0]).equals(owner.typeIdentity().map(i -> i.split("<",2)[0]))
                : formal.name().equals(actual.name());
        if (sameTemplate) {
            return new MatchedBase(actual,path);
        }
        if (owner == null || !visited.add(owner)) return null;
        MatchedBase found = null;
        for (int i=0;i<owner.bases().size();i++) {
            BaseSpecifier base=owner.bases().get(i);
            TypeNode type = TypeSemantics.unqualified(base.type());
            if (type instanceof NamedType named) {
                String subobject = base.isVirtual() ? "virtual:"+type.canonicalIdentity() : path+"/"+i;
                MatchedBase candidate = templateBase(formal,named,new HashSet<>(visited),subobject);
                if (candidate != null) {
                    if (found != null && (!found.subobject().equals(candidate.subobject())
                            || !found.type().canonicalIdentity().equals(candidate.type().canonicalIdentity()))) return null;
                    found = candidate;
                }
            }
        }
        return found;
    }
    private static TypeNode iteratorDifference(TypeNode iterator,PlatformProfile profile) {
        if(iterator==null)return null;
        iterator=TypeSemantics.unqualified(TypeSemantics.withoutReference(iterator));
        // Match the catalogue ptrdiff_t profile policy, not a host ABI assumption.
        if(iterator instanceof PointerType) return NamedType.create().setName(profile.sizeType().replace("unsigned ","")).build();
        var info=LibraryIteratorSemantics.info(iterator);
        if(info.isPresent())return info.get().iterator().iteratorDifferenceType().orElse(null);
        if(iterator instanceof NamedType named && named.declaration().orElse(null) instanceof ClassDeclaration owner
                && LibraryDeclarations.isLibrary(owner) && owner.name().equals("reverse_iterator")) {
            var arguments=named.templateArguments().isEmpty()?owner.templateArguments():named.templateArguments();
            if(!arguments.isEmpty())return iteratorDifference(arguments.getFirst().type().orElse(null),profile);
        }
        return inheritedAlias(iterator,"difference_type",new HashSet<>());
    }
    private static TypeNode inheritedAlias(TypeNode type, String member, Set<ClassDeclaration> visited) {
        TypedefDeclaration alias=inheritedAliasDeclaration(type,member,visited);
        return alias==null?null:alias.type();
    }
    private static TypedefDeclaration inheritedAliasDeclaration(TypeNode type, String member, Set<ClassDeclaration> visited) {
        type=TypeSemantics.unqualified(type);
        if (!(type instanceof NamedType named) || !(named.declaration().orElse(null) instanceof ClassDeclaration owner)
                || !LibraryDeclarations.isLibrary(owner) || !owner.templateParameters().isEmpty() || !visited.add(owner)) return null;
        // This is a catalogue iterator contract, not a replacement for C++ name
        // and access lookup on arbitrary user classes. Concrete catalogue instances
        // have already substituted their aliases and bases.
        // Re-entering substitution here can recurse through iterator_traits back
        // into this same query before a dependent iterator has been instantiated.
        for (Node node : owner.members()) if (node instanceof TypedefDeclaration alias && alias.name().equals(member))
            return TypeSemantics.dependent(alias.type()) ? null : alias;
        TypedefDeclaration found=null;
        for (BaseSpecifier base : owner.bases()) {
            TypedefDeclaration candidate=inheritedAliasDeclaration(base.type(),member,new HashSet<>(visited));
            if (candidate!=null) {
                if (found!=null && found!=candidate) return null;
                found=candidate;
            }
        }
        return found;
    }
    static TypeNode dependentType(NamedType named, Map<TemplateParameterDeclaration,TypeNode> values) {
        if (named.qualifier().orElse(null) instanceof NamedType traits
                && traits.declaration().orElse(null) instanceof ClassDeclaration owner
                && owner.name().equals("iterator_traits") && LibraryDeclarations.isLibrary(owner)
                && owner.templateKind()!=TemplateKind.FULL_SPECIALIZATION && traits.templateArguments().size()==1
                && named.name().endsWith("::difference_type")) {
            TypeNode iterator=traits.templateArguments().getFirst().type().orElse(null);
            if(iterator instanceof NamedType argument && argument.declaration().orElse(null) instanceof TemplateParameterDeclaration parameter)
                iterator=values.getOrDefault(parameter,iterator);
            var profile=named.provenance().map(p->p.unit().configuration().platform()).orElseGet(PlatformProfile::defaults);
            TypeNode difference=iteratorDifference(iterator,profile);
            if(difference!=null)return difference;
        }
        String[] parts=named.name().split("::"); if(parts.length<2)return null;
        TypeNode current=null;
        for(var entry:values.entrySet()) if(entry.getKey().name().equals(parts[0])) current=entry.getValue();
        for(int i=1;i<parts.length && current!=null;i++) {
            current=TypeSemantics.unqualified(current);
            if(!(current instanceof NamedType type) || !(type.declaration().orElse(null) instanceof ClassDeclaration klass)) return null;
            // Member aliases from a primary template must be substituted with the
            // qualifier's actual arguments, not leaked as the primary's T.
            var bindings=new LinkedHashMap<TemplateParameterDeclaration,TemplateArgument>();
            if(!klass.templateParameters().isEmpty()) {
                for(int j=0;j<Math.min(klass.templateParameters().size(),type.templateArguments().size());j++)
                    bindings.put(klass.templateParameters().get(j),type.templateArguments().get(j));
            }
            String member=parts[i]; current=null;
            for(Node node:klass.members()) if(node instanceof TypedefDeclaration alias && alias.name().equals(member)) {
                // Abstract iterator identity belongs to the concrete container. Keep
                // the qualified member until that instance is available for binding.
                if(!klass.templateParameters().isEmpty() && alias.type().canonicalType() instanceof OpaqueLibraryType opaque
                        && Set.of("iterator","const_iterator").contains(opaque.name()))return null;
                current=ClassTemplateSemantics.substituteType(alias.type(),bindings); break;
            }
        }
        return current;
    }
}
