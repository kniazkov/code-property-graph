/* Direct member address types and generic callable deduction. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MemberAddressTemplateDeductionTest {
    private static Project resolve(String code) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("members.cpp",code,Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Project project,Class<T> kind) {
        var result=new ArrayList<T>();
        project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node n){if(kind.isInstance(n))result.add(kind.cast(n));}});
        return result;
    }
    @Test void directMemberAddressesDeduceOwnerReturnAndArguments() {
        var project=resolve("template<class R,class C,class A> R adapt(R(C::*p)(A)); template<class R,class C,class A> R adapt(R(C::*p)(A)const); struct C {int f(long); int g(long)const;}; void use(){adapt(&C::f);adapt(&C::g);}");
        assertTrue(project.semanticDiagnostics().isEmpty(),()->project.semanticDiagnostics().toString());
        assertEquals(2,nodes(project,CallExpression.class).size());
        for(var call:nodes(project,CallExpression.class)) assertTrue(call.target().isPresent());
        for(var address:nodes(project,AddressOfExpression.class)) {
            var type=TypeSemantics.expressionType(address,TypeSemantics.profile(project.files().getFirst())).orElseThrow();
            assertInstanceOf(MemberPointerType.class,type);
            assertInstanceOf(FunctionType.class,((MemberPointerType)type).pointee());
        }
    }
    @Test void staticMembersRemainOrdinaryPointersAndFieldsAreMemberPointers() {
        var project=resolve("struct C {int value; static int field; static int f(long);}; void use(){&C::value;&C::field;&C::f;}");
        assertTrue(project.semanticDiagnostics().isEmpty(),()->project.semanticDiagnostics().toString());
        var addresses=nodes(project,AddressOfExpression.class);
        assertEquals(3,addresses.size());
        var profile=TypeSemantics.profile(project.files().getFirst());
        assertInstanceOf(MemberPointerType.class,TypeSemantics.expressionType(addresses.get(0),profile).orElseThrow());
        assertInstanceOf(PointerType.class,TypeSemantics.expressionType(addresses.get(1),profile).orElseThrow());
        assertInstanceOf(PointerType.class,TypeSemantics.expressionType(addresses.get(2),profile).orElseThrow());
    }
    @Test void inheritedMemberOwnerIsNotGuessedFromQualifier() {
        var project=resolve("struct Base {int value;}; struct Derived:Base {}; void use(){&Derived::value;}");
        var address=nodes(project,AddressOfExpression.class).getFirst();
        assertTrue(TypeSemantics.expressionType(address,TypeSemantics.profile(project.files().getFirst())).isEmpty());
    }
    @Test void constMemberCannotMatchNonConstFunctionPattern() {
        var project=resolve("template<class R,class C> R adapt(R(C::*p)()); struct C {int f()const;}; void use(){adapt(&C::f);}");
        assertTrue(project.semanticDiagnostics().stream().anyMatch(d->d.code().equals("no-viable-template")));
        assertTrue(nodes(project,CallExpression.class).getFirst().target().isEmpty());
    }
}
