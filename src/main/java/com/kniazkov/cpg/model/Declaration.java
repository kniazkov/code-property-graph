/* Declaration identities and linkage metadata. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** A source declaration. Relations are non-owning and preserve individual occurrences. */
public abstract class Declaration extends Node {
    public enum Linkage { NONE, INTERNAL, EXTERNAL }
    Linkage linkage = Linkage.NONE;
    String languageLinkage = "C++";
    boolean friend;
    public boolean isFriend() { return friend; }
    String access;
    /** Written class-member access, absent outside class scope. */
    public Optional<String> access() { return Optional.ofNullable(access); }
    boolean definitionOccurrence = true;
    Declaration previousDeclaration, definition;
    String typeIdentity;
    Declaration() {}
    public Optional<String> typeIdentity() { return Optional.ofNullable(typeIdentity); }
    public Linkage linkage() { return linkage; }
    public String languageLinkage() { return languageLinkage; }
    public boolean isDefinition() { return definitionOccurrence; }
    public Optional<Declaration> previousDeclaration() { return Optional.ofNullable(previousDeclaration); }
    public Optional<Declaration> definition() { return Optional.ofNullable(definition); }
    @Override public List<Node> references() {
        var result = new ArrayList<Node>();
        if (previousDeclaration != null) result.add(previousDeclaration);
        if (definition != null && definition != previousDeclaration) result.add(definition);
        return List.copyOf(result);
    }
}
