/* C++03 casts, RTTI, allocation and unevaluated operand grammar. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CppObjectExpressionTest {
    private static Expression expression(String source) {
        var file=Parser.parse("object.cpp", "struct X {}; int f() { return "+source+"; }", Language.CPP).build();
        var function=(FunctionDeclaration)file.items().getLast();
        return ((ReturnStatement)function.body().orElseThrow().items().getFirst()).value().orElseThrow();
    }
    @Test void namedCastsAndCStyleCastsHaveExplicitKindsAndBindingPower() {
        for (String kind : new String[]{"static_cast", "dynamic_cast", "const_cast", "reinterpret_cast"}) {
            var cast=assertInstanceOf(ConversionExpression.class, expression(kind+"<X*>(p)"));
            assertEquals(kind.toUpperCase(), cast.kind().name());
            assertTrue(cast.isExplicit()); assertInstanceOf(PointerType.class, cast.targetType());
            assertEquals(cast.kind(),cast.edit().build().kind());
        }
        var sum=assertInstanceOf(AddExpression.class,expression("(int)*p + 1"));
        var cast=assertInstanceOf(ConversionExpression.class,sum.left());
        assertEquals(ConversionExpression.Kind.C_STYLE_CAST,cast.kind());
        assertInstanceOf(DereferenceExpression.class,cast.operand());
        assertInstanceOf(ConversionExpression.class, ((ConversionExpression)expression("(int)(long)x")).operand());
        assertInstanceOf(AddExpression.class,expression("(x)+1"));
        assertInstanceOf(InitializationExpression.class,expression("int(1)"));
        assertInstanceOf(InitializationExpression.class,expression("X()"));
    }
    @Test void sizeAndTypeidDistinguishTypesFromExpressions() {
        assertTrue(((SizeofExpression)expression("sizeof(int*)")).type().isPresent());
        assertInstanceOf(InitializationExpression.class,((SizeofExpression)expression("sizeof(int(1))")).operand().orElseThrow());
        assertInstanceOf(IndexExpression.class,((SizeofExpression)expression("sizeof(p)[0]")).operand().orElseThrow());
        assertTrue(((TypeidExpression)expression("typeid(X)")).type().isPresent());
        assertInstanceOf(DereferenceExpression.class,((TypeidExpression)expression("typeid(*p)")).operand().orElseThrow());
        assertInstanceOf(FunctionType.class,((TypeidExpression)expression("typeid(X())")).type().orElseThrow());
        var member=assertInstanceOf(DotMemberExpression.class,expression("typeid(x).name"));
        assertInstanceOf(TypeidExpression.class,member.receiver());
    }
    @Test void newExpressionsPreservePlacementInitializerAndTypeParentheses() {
        var scalar=assertInstanceOf(NewExpression.class,expression("new int"));
        assertFalse(scalar.hasInitializer());
        var initialized=(NewExpression)expression("new int()");
        assertTrue(initialized.hasInitializer()); assertTrue(initialized.initializerArguments().isEmpty());
        var array=(NewExpression)expression("new int[n][3]");
        assertInstanceOf(ArrayType.class,((ArrayType)array.type()).elementType());
        var placement=(NewExpression)expression("::new (arena, tag) X(1, 2)");
        assertTrue(placement.isGlobal()); assertEquals(2,placement.placementArguments().size()); assertEquals(2,placement.initializerArguments().size());
        var nested=(NewExpression)expression("new (arena) (int[3])()");
        assertTrue(nested.isParenthesizedType()); assertTrue(nested.hasInitializer());
        assertInstanceOf(ArrayType.class,nested.type());
        var copied=placement.edit().build();
        assertTrue(copied.isGlobal()); assertEquals(placement.children().size(),copied.children().size());
        assertNotSame(placement.type(),copied.type());
        assertNotSame(placement.placementArguments().getFirst(),copied.placementArguments().getFirst());
    }
    @Test void deleteExpressionsPreserveArrayAndScopeAndUnaryBinding() {
        var scalar=assertInstanceOf(DeleteExpression.class,expression("delete p"));
        assertFalse(scalar.isArray());
        var array=(DeleteExpression)expression("::delete [] p++");
        assertTrue(array.isGlobal()); assertTrue(array.isArray());
        assertInstanceOf(PostfixIncrementExpression.class,array.operand());
        assertTrue(array.edit().build().isArray());
        assertInstanceOf(CommaExpression.class,expression("delete p, 0"));
    }
    @Test void allocationOperatorDeclarationsHaveTheirWrittenNames() {
        var file=Parser.parse("operators.cpp", "void* operator new(unsigned long size, void* p) { return p; } void operator delete[](void* p);", Language.CPP).build();
        assertEquals("operator new",((FunctionDeclaration)file.items().getFirst()).name());
        assertEquals("operator delete[]",((FunctionDeclaration)file.items().getLast()).name());
    }
    @Test void malformedSyntaxAndCpp11FormsAreRejected() {
        for(String text:new String[]{"typeid x","typeid()","sizeof()","static_cast<int>()","dynamic_cast<int>(x,y", "new () int", "new int{1}", "delete [1] p", "(static int)x"})
            assertThrows(ParseException.class,()->expression(text),text);
    }
}
