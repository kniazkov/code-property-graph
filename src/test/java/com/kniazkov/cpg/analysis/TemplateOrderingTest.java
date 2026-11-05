/* Function template partial ordering regressions. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateOrderingTest {
    private CallableDeclaration[] pair(String declarations) {
        var unit = Parser.parse("ordering.cpp", declarations, Language.CPP, "c++03").build();
        return unit.items().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).toArray(CallableDeclaration[]::new);
    }
    private void ordered(String declarations) {
        var f = pair(declarations);
        assertTrue(TemplateOrdering.moreSpecialized(f[1], f[0]));
        assertFalse(TemplateOrdering.moreSpecialized(f[0], f[1]));
    }
    private void unordered(String declarations) {
        var f = pair(declarations);
        assertFalse(TemplateOrdering.moreSpecialized(f[0], f[1]));
        assertFalse(TemplateOrdering.moreSpecialized(f[1], f[0]));
    }
    @Test void defaultedTrailingParametersDoNotParticipateInCallOrdering() {
        var f = pair("template<class T> int pick(T); template<class U> int pick(U*,int n=0);");
        assertTrue(TemplateOrdering.moreSpecialized(f[1], f[0], 1));
        assertFalse(TemplateOrdering.moreSpecialized(f[0], f[1], 1));
        var same = pair("template<class T> int pick(T,int n=0); template<class U> int pick(U,char n=0);");
        assertFalse(TemplateOrdering.moreSpecialized(same[0], same[1], 1));
        assertFalse(TemplateOrdering.moreSpecialized(same[1], same[0], 1));
        assertFalse(TemplateOrdering.moreSpecialized(f[1], f[0], 0));
    }
    @Test void repeatedParametersConstrainDeduction() {
        ordered("template<class T, class U> int pick(T,U); template<class V> int pick(V,V);");
    }
    @Test void pointerPatternOrdersAgainstBareType() {
        ordered("template<class T> int pick(T); template<class U> int pick(U*);");
    }
    @Test void pointeeConstIsSignificant() {
        ordered("template<class T> int pick(T*); template<class U> int pick(const U*);");
    }
    @Test void cvAndReferenceRulesKeepValueReferenceAmbiguity() {
        ordered("template<class T> int pick(T&); template<class U> int pick(const U&);");
        ordered("template<class T> int pick(const T*); template<class U> int pick(const volatile U*);");
        unordered("template<class T> int pick(T); template<class U> int pick(const U&);");
        unordered("template<class T> int pick(T); template<class U> int pick(const U);");
    }
    @Test void crossingConstraintsStayAmbiguous() {
        unordered("template<class T> int pick(T,int); template<class U> int pick(int,U);");
        unordered("template<class T, class U> int pick(T*,U); template<class T, class U> int pick(T,U*);");
    }
    @Test void AlphaEquivalentTemplatesAreUnordered() {
        unordered("template<class T> int pick(T,T); template<class V> int pick(V,V);");
    }
    @Test void nondependentStructureMustMatchExactly() {
        unordered("template<class T> int pick(T*,int); template<class U> int pick(U*,char);");
    }
    @Test void nestedTemplateArgumentsParticipate() {
        ordered("template<class A, class B> struct Pair {}; template<class T, class U> int pick(Pair<T,U>); template<class V> int pick(Pair<V,V>);");
    }
    @Test void nonTypeArgumentsAreRigidInTransformedTemplates() {
        ordered("template<int N, int M> struct Pair {}; template<int N,int M> int pick(Pair<N,M>); template<int K> int pick(Pair<K,K>);");
    }
}
