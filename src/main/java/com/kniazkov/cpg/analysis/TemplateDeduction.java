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
        CallableDeclaration signature = TemplateSubstitution.signature(origin,substitutions);
        if (OverloadResolution.candidate(signature,actual,profile,OverloadResolution.requiredArguments(origin)).isEmpty()) return Optional.empty();
        var arguments=origin.templateParameters().stream().map(p -> values.containsKey(p)
                ? TemplateArgument.create().setType(values.get(p).edit()).setKind(p.kind()==TemplateParameterDeclaration.Kind.TEMPLATE?TemplateArgument.Kind.TEMPLATE:TemplateArgument.Kind.TYPE).build()
                : TemplateArgument.create().setExpression(((IntegerLiteral)constants.get(p)).edit()).build()).toList();
        return Optional.of(new Result(arguments,substitutions,signature));
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
        if(formal instanceof NamedType name && name.hasTemplateArguments() && actual instanceof NamedType concrete)substitutions.put(formal,actual);
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
        if (formal instanceof NamedType named && actual instanceof NamedType other && named.hasTemplateArguments()) {
            if (!named.name().equals(other.name()) || named.templateArguments().size()!=other.templateArguments().size()) return false;
            for (int i=0;i<named.templateArguments().size();i++) {
                var a=named.templateArguments().get(i).type(); var b=other.templateArguments().get(i).type();
                if(a.isEmpty() || b.isEmpty() || !deduceType(a.get(),b.get(),values,fixed,classOrigins)) return false;
            }
        }
        return true; // Nondependent portions are checked by standard conversion ranking.
    }
    static TypeNode dependentType(NamedType named, Map<TemplateParameterDeclaration,TypeNode> values) {
        String[] parts=named.name().split("::"); if(parts.length<2)return null;
        TypeNode current=null;
        for(var entry:values.entrySet()) if(entry.getKey().name().equals(parts[0])) current=entry.getValue();
        for(int i=1;i<parts.length && current!=null;i++) {
            current=TypeSemantics.unqualified(current);
            if(!(current instanceof NamedType type) || !(type.declaration().orElse(null) instanceof ClassDeclaration klass)) return null;
            String member=parts[i]; current=null;
            for(Node node:klass.members()) if(node instanceof TypedefDeclaration alias && alias.name().equals(member)) { current=alias.type(); break; }
        }
        return current;
    }
}
