/* C++03 function template partial ordering. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Orders signatures by deduction against distinct invented template arguments, never by syntax depth. */
final class TemplateOrdering {
    private TemplateOrdering() {}
    private record Term(String tag, List<Term> children, String variable) {
        static Term atom(String tag) { return new Term(tag, List.of(), null); }
        static Term of(String tag, Term... children) { return new Term(tag, List.of(children), null); }
    }

    /** True only when b accepts a's transformed form and the reverse deduction fails. */
    static boolean moreSpecialized(CallableDeclaration a, CallableDeclaration b) {
        if (a.parameters().size() != b.parameters().size()) return false;
        return moreSpecialized(a, b, a.parameters().size());
    }

    /** Call-context ordering ignores parameters for which the call supplies no argument. */
    static boolean moreSpecialized(CallableDeclaration a, CallableDeclaration b, int argumentCount) {
        if (argumentCount < 0) throw new IllegalArgumentException("Negative argument count");
        if (a.templateParameters().isEmpty() || b.templateParameters().isEmpty()) return false;
        int participating = Math.min(argumentCount, Math.min(a.parameters().size(), b.parameters().size()));
        return accepts(b, a, participating) && !accepts(a, b, participating);
    }

    private static boolean accepts(CallableDeclaration pattern, CallableDeclaration transformed, int participating) {
        var names = names(pattern);
        var invented = names(transformed);
        var deductions = new HashMap<String, Term>();
        for (int i = 0; i < participating; i++) {
            TypeNode p = pattern.parameters().get(i).type().orElse(null);
            TypeNode a = transformed.parameters().get(i).type().orElse(null);
            if (p == null || a == null) return false;
            // Function parameter adjustment precedes deduction. References are removed,
            // top-level cv is irrelevant, but pointee cv remains significant.
            boolean references = p instanceof ReferenceType && a instanceof ReferenceType;
            Term formal = parameterTerm(p, references, names, false);
            Term actual = parameterTerm(a, references, invented, true);
            if (!match(formal, actual, deductions)) return false;
        }
        return true;
    }

    private static Term parameterTerm(TypeNode written, boolean references, Map<String, String> parameters, boolean rigid) {
        if (references) return term(((ReferenceType)written).referredType(), parameters, rigid);
        TypeNode type = adjust(written);
        if (!(written instanceof ReferenceType)) {
            if (type instanceof ArrayType array) return Term.of("pointer", term(array.elementType(), parameters, rigid));
            if (type instanceof FunctionType) return Term.of("pointer", term(type, parameters, rigid));
        }
        return term(type, parameters, rigid);
    }

    private static Map<String, String> names(CallableDeclaration function) {
        var result = new HashMap<String, String>();
        for (int i = 0; i < function.templateParameters().size(); i++) {
            var parameter = function.templateParameters().get(i);
            if (!parameter.name().isEmpty()) result.put(parameter.name(), parameter.kind() + ":" + i);
        }
        return result;
    }

    private static TypeNode adjust(TypeNode type) {
        return TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
    }

    private static Term term(TypeNode type, Map<String, String> parameters, boolean rigid) {
        if (type instanceof NamedType named) {
            String variable = named.qualifier().isEmpty() && !named.hasTemplateArguments() ? parameters.get(named.name()) : null;
            if (variable != null) return rigid ? Term.atom("invented:" + variable) : new Term("", List.of(), variable);
            if (named.alias().isPresent()) return term(named.alias().get().type(), parameters, rigid);
            var children = new ArrayList<Term>();
            named.qualifier().ifPresent(q -> children.add(term(q, parameters, rigid)));
            for (var argument : named.templateArguments()) {
                if (argument.type().isPresent()) children.add(term(argument.type().get(), parameters, rigid));
                else children.add(value(argument.expression().orElseThrow(), parameters, rigid));
            }
            return new Term("named:" + named.name(), List.copyOf(children), null);
        }
        if (type instanceof QualifiedType qualified)
            return Term.of("cv:" + TypeSemantics.qualifiers(qualified), term(qualified.baseType(), parameters, rigid));
        if (type instanceof PointerType pointer) return Term.of("pointer", term(pointer.pointee(), parameters, rigid));
        if (type instanceof ReferenceType reference) return Term.of("reference", term(reference.referredType(), parameters, rigid));
        if (type instanceof ArrayType array)
            return Term.of("array", term(array.elementType(), parameters, rigid),
                    array.bound().map(b -> value(b, parameters, rigid)).orElse(Term.atom("unbounded")));
        if (type instanceof MemberPointerType pointer)
            return Term.of("member-pointer", term(pointer.ownerType(), parameters, rigid), term(pointer.pointee(), parameters, rigid));
        if (type instanceof FunctionType function) {
            var children = new ArrayList<Term>();
            children.add(term(function.returnType(), parameters, rigid));
            for (var parameter : function.parameters()) children.add(term(adjust(parameter.type().orElseThrow()), parameters, rigid));
            return new Term("function:" + function.variadic() + ":" + function.isConst() + ":" + function.isVolatile(), List.copyOf(children), null);
        }
        return Term.atom(type.canonicalIdentity());
    }

    private static Term value(Expression expression, Map<String, String> parameters, boolean rigid) {
        if (expression instanceof NameExpression name && parameters.containsKey(name.name())) {
            String variable = parameters.get(name.name());
            return rigid ? Term.atom("invented:" + variable) : new Term("", List.of(), variable);
        }
        return Term.atom("value:" + expression.source(Language.CPP));
    }

    private static boolean match(Term formal, Term actual, Map<String, Term> deductions) {
        if (formal.variable != null) {
            Term prior = deductions.putIfAbsent(formal.variable, actual);
            return prior == null || prior.equals(actual);
        }
        if (formal.tag.startsWith("cv:")) {
            int required = Integer.parseInt(formal.tag.substring(3));
            int provided = actual.tag.startsWith("cv:") ? Integer.parseInt(actual.tag.substring(3)) : 0;
            if ((required & provided) != required) return false;
            Term base = provided == 0 ? actual : actual.children.getFirst();
            int remaining = provided & ~required;
            return match(formal.children.getFirst(), remaining == 0 ? base : Term.of("cv:" + remaining, base), deductions);
        }
        if (!formal.tag.equals(actual.tag) || formal.children.size() != actual.children.size()) return false;
        for (int i = 0; i < formal.children.size(); i++)
            if (!match(formal.children.get(i), actual.children.get(i), deductions)) return false;
        return true;
    }
}
