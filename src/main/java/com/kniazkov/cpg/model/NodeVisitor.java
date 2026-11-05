/* Typed CPG model: immutable nodes and nested mutable builders. MIT License. */
package com.kniazkov.cpg.model;

/**
 * Receives typed callbacks for syntax nodes. Implement the overload for a leaf
 * to select exactly that construct, or a family overload to select its variants.
 * Each default overload delegates to the immediate superclass overload, ending
 * at {@link #visit(Node)}. An override can explicitly invoke the default method
 * when it also needs the family behavior.
 * <p>One call to Node.accept dispatches once. Use Node.walk for a whole subtree.
 */
public interface NodeVisitor {
    default void visit(NamespaceDeclaration node) { visit((Declaration) node); }
    default void visit(NamespaceAliasDeclaration node) { visit((Declaration) node); }
    default void visit(UsingDirective node) { visit((Declaration) node); }
    default void visit(UsingDeclaration node) { visit((Declaration) node); }
    default void visit(EnumDeclaration node) { visit((Declaration) node); }
    default void visit(EnumeratorDeclaration node) { visit((Declaration) node); }

    default void visit(PointerType node) { visit((TypeNode) node); }
    default void visit(ReferenceType node) { visit((TypeNode) node); }
    default void visit(QualifiedType node) { visit((TypeNode) node); }
    default void visit(ArrayType node) { visit((TypeNode) node); }
    default void visit(FunctionType node) { visit((TypeNode) node); }
    default void visit(MemberPointerType node) { visit((TypeNode) node); }
    default void visit(TypedefDeclaration node) { visit((Declaration) node); }
    default void visit(SizeofExpression node) { visit((Expression) node); }

    /** Visits a recovery placeholder through the ordinary expression family. */
    default void visit(ErrorExpression node) { visit((Expression) node); }
    /** Visits an owned external configuration input. */
    default void visit(ExternalInput node) { visit((Node) node); }
    /** Visits the synthetic terminal as an ordinary owned leaf. */
    default void visit(Exit node) { visit((Node) node); }

    /** Fallback for all nodes whose more specific overloads delegate here. */
    default void visit(Node node) {
    }

    /** Receives a AddExpression and delegates to the BinaryExpression overload by default. */
    default void visit(AddExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a AddressOfExpression and delegates to the UnaryExpression overload by default. */
    default void visit(AddressOfExpression node) {
        visit((UnaryExpression) node);
    }

    /** Receives a AssignmentExpression and delegates to the Expression overload by default. */
    default void visit(AssignmentExpression node) {
        visit((Expression) node);
    }

    /** Receives a BinaryExpression and delegates to the Expression overload by default. */
    default void visit(BinaryExpression node) {
        visit((Expression) node);
    }

    /** Receives a BlockStatement and delegates to the Statement overload by default. */
    default void visit(BlockStatement node) {
        visit((Statement) node);
    }

    /** Receives a CallExpression and delegates to the Expression overload by default. */
    default void visit(CallExpression node) {
        visit((Expression) node);
    }

    /** Receives a CallableDeclaration and delegates to the Declaration overload by default. */
    default void visit(CallableDeclaration node) {
        visit((Declaration) node);
    }

    /** Receives a ClassDeclaration and delegates to the Declaration overload by default. */
    default void visit(ClassDeclaration node) {
        visit((Declaration) node);
    }

    /** Receives a Declaration and delegates to the Node overload by default. */
    default void visit(Declaration node) {
        visit((Node) node);
    }

    /** Receives a DereferenceExpression and delegates to the UnaryExpression overload by default. */
    default void visit(DereferenceExpression node) {
        visit((UnaryExpression) node);
    }

    /** Receives a DivideExpression and delegates to the BinaryExpression overload by default. */
    default void visit(DivideExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a DotMemberExpression and delegates to the MemberExpression overload by default. */
    default void visit(DotMemberExpression node) {
        visit((MemberExpression) node);
    }

    /** Receives a EqualExpression and delegates to the BinaryExpression overload by default. */
    default void visit(EqualExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a Expression and delegates to the Node overload by default. */
    default void visit(Expression node) {
        visit((Node) node);
    }

    /** Receives a ExpressionStatement and delegates to the Statement overload by default. */
    default void visit(ExpressionStatement node) {
        visit((Statement) node);
    }

    /** Receives a FunctionDeclaration and delegates to the CallableDeclaration overload by default. */
    default void visit(FunctionDeclaration node) {
        visit((CallableDeclaration) node);
    }

    /** Receives a GreaterExpression and delegates to the BinaryExpression overload by default. */
    default void visit(GreaterExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a GreaterOrEqualExpression and delegates to the BinaryExpression overload by default. */
    default void visit(GreaterOrEqualExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a IfStatement and delegates to the Statement overload by default. */
    default void visit(IfStatement node) {
        visit((Statement) node);
    }

    /** Receives a IncludeDirective and delegates to the Node overload by default. */
    default void visit(IncludeDirective node) {
        visit((Node) node);
    }

    /** Receives a IntegerLiteral and delegates to the Expression overload by default. */
    default void visit(IntegerLiteral node) {
        visit((Expression) node);
    }

    /** Receives a LessExpression and delegates to the BinaryExpression overload by default. */
    default void visit(LessExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a LessOrEqualExpression and delegates to the BinaryExpression overload by default. */
    default void visit(LessOrEqualExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a LogicalNotExpression and delegates to the UnaryExpression overload by default. */
    default void visit(LogicalNotExpression node) {
        visit((UnaryExpression) node);
    }

    /** Receives a MemberExpression and delegates to the Expression overload by default. */
    default void visit(MemberExpression node) {
        visit((Expression) node);
    }

    /** Receives a MethodDeclaration and delegates to the CallableDeclaration overload by default. */
    default void visit(MethodDeclaration node) {
        visit((CallableDeclaration) node);
    }

    /** Receives a MultiplyExpression and delegates to the BinaryExpression overload by default. */
    default void visit(MultiplyExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a NameExpression and delegates to the Expression overload by default. */
    default void visit(NameExpression node) {
        visit((Expression) node);
    }

    /** Receives a NamedType and delegates to the TypeNode overload by default. */
    default void visit(NamedType node) {
        visit((TypeNode) node);
    }

    /** Receives a NegateExpression and delegates to the UnaryExpression overload by default. */
    default void visit(NegateExpression node) {
        visit((UnaryExpression) node);
    }

    /** Receives a NotEqualExpression and delegates to the BinaryExpression overload by default. */
    default void visit(NotEqualExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a ParameterDeclaration and delegates to the Declaration overload by default. */
    default void visit(ParameterDeclaration node) {
        visit((Declaration) node);
    }

    /** Receives a PointerMemberExpression and delegates to the MemberExpression overload by default. */
    default void visit(PointerMemberExpression node) {
        visit((MemberExpression) node);
    }

    /** Receives a PrintStatement and delegates to the Statement overload by default. */
    default void visit(PrintStatement node) {
        visit((Statement) node);
    }

    /** Receives a Project and delegates to the Node overload by default. */
    default void visit(Project node) {
        visit((Node) node);
    }

    /** Receives a RemainderExpression and delegates to the BinaryExpression overload by default. */
    default void visit(RemainderExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a ReturnStatement and delegates to the Statement overload by default. */
    default void visit(ReturnStatement node) {
        visit((Statement) node);
    }

    /** Receives a ScopeMemberExpression and delegates to the MemberExpression overload by default. */
    default void visit(ScopeMemberExpression node) {
        visit((MemberExpression) node);
    }

    /** Receives a ShiftLeftExpression and delegates to the BinaryExpression overload by default. */
    default void visit(ShiftLeftExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a ShiftRightExpression and delegates to the BinaryExpression overload by default. */
    default void visit(ShiftRightExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a SourceFile and delegates to the Node overload by default. */
    default void visit(SourceFile node) {
        visit((Node) node);
    }

    /** Receives a Statement and delegates to the Node overload by default. */
    default void visit(Statement node) {
        visit((Node) node);
    }

    /** Receives a SubtractExpression and delegates to the BinaryExpression overload by default. */
    default void visit(SubtractExpression node) {
        visit((BinaryExpression) node);
    }

    /** Receives a TypeNode and delegates to the Node overload by default. */
    default void visit(TypeNode node) {
        visit((Node) node);
    }

    /** Receives a UnaryExpression and delegates to the Expression overload by default. */
    default void visit(UnaryExpression node) {
        visit((Expression) node);
    }

    /** Receives a UnaryPlusExpression and delegates to the UnaryExpression overload by default. */
    default void visit(UnaryPlusExpression node) {
        visit((UnaryExpression) node);
    }

    /** Receives a VariableDeclaration and delegates to the Declaration overload by default. */
    default void visit(VariableDeclaration node) {
        visit((Declaration) node);
    }

    /** Receives a WhileStatement and delegates to the Statement overload by default. */
    default void visit(WhileStatement node) {
        visit((Statement) node);
    }
}
