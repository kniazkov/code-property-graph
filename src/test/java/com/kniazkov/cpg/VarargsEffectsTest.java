/* Bounded varargs have opaque state effects even when their values are discarded. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VarargsEffectsTest {
    @TempDir Path directory;
    private Project read(String body) throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "#include <cstdarg>\ntypedef void (*Mutator)(int*); typedef int (*Callback)(int); typedef int (*Row)[3]; enum E{one}; int global; int f(int n,...){" + body + "}");
        var profile = PlatformProfile.builder().floatingFormat("long double", "binary80").varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
        var result = Cpg.read(directory, new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(profile));
        assertFalse(result.hasErrors(), () -> result.parseProblems() + " / " + result.semanticDiagnostics());
        return result;
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static FunctionFlow flow(Project project) {
        return project.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow();
    }
    private static boolean dependency(FunctionFlow flow, Node from, Node to) {
        return flow.data().stream().anyMatch(e -> e.from() == from && e.to() == to && e.kind() == FunctionFlow.Dependency.DEFINITION);
    }
    @Test void discardedArgumentsAdvanceStateAndStartDoesNotReadLastParameter() throws Exception {
        var p = read("std::va_list ap; va_start(ap,n); va_arg(ap,int); va_arg(ap,int); va_end(ap); return 0;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        assertEquals(4,ops.size());
        for (int i=1;i<ops.size();i++) assertTrue(dependency(f,ops.get(i-1),ops.get(i)), "state transition " + i);
        assertFalse(f.data().stream().anyMatch(e -> e.to() == ops.getFirst().lastParameter().orElseThrow()));
        assertFalse(f.data().stream().anyMatch(e -> e.to() == ops.getFirst().list()));
        assertEquals(4,nodes(ConstantFolder.fold(p),VaListExpression.class).size());
        assertTrue(nodes(UnreadWrites.analyze(p),VaListExpression.class).stream()
                .noneMatch(op -> op.diagnostics().stream().anyMatch(d -> d.code().equals(UnreadWrites.CODE))));
    }
    @Test void independentListsDoNotShareStateDefinitions() throws Exception {
        var p = read("std::va_list a; std::va_list b; va_start(a,n); va_start(b,n); va_arg(a,int); va_arg(b,int); va_end(a); va_end(b); return 0;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        assertTrue(dependency(f,ops.get(0),ops.get(2)));
        assertTrue(dependency(f,ops.get(1),ops.get(3)));
        assertTrue(dependency(f,ops.get(2),ops.get(4)));
        assertTrue(dependency(f,ops.get(3),ops.get(5)));
        assertFalse(dependency(f,ops.get(0),ops.get(3)));
        assertFalse(dependency(f,ops.get(1),ops.get(2)));
    }
    @Test void sizeofDoesNotReadOrAdvanceListState() throws Exception {
        var p = read("std::va_list ap; va_start(ap,n); sizeof(va_arg(ap,int)); va_end(ap); return 0;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        assertTrue(dependency(f,ops.get(0),ops.get(2)));
        Set<Node> unevaluated = Collections.newSetFromMap(new IdentityHashMap<>());
        ops.get(1).walk(new NodeVisitor() { @Override public void visit(Node node) { unevaluated.add(node); }});
        assertFalse(f.data().stream().anyMatch(e -> unevaluated.contains(e.from()) || unevaluated.contains(e.to())));
    }
    @Test void argumentValueDependsOnStateAndFeedsItsConsumer() throws Exception {
        var p = read("std::va_list ap; va_start(ap,n); int value=va_arg(ap,int); va_end(ap); return value;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        var value = nodes(f.entry(),VariableDeclaration.class).stream().filter(v -> v.name().equals("value")).findFirst().orElseThrow();
        assertTrue(dependency(f,ops.get(0),ops.get(1)));
        assertTrue(f.data().stream().anyMatch(e -> e.from() == ops.get(1) && e.to() == value && e.kind() == FunctionFlow.Dependency.VALUE));
    }
    @Test void extractedPointerMayAliasExposedLocalAndGlobalStorage() throws Exception {
        var p = read("int x=1; int* exposed=&x; global=2; std::va_list ap; va_start(ap,n); int* ptr=va_arg(ap,int*); *ptr=3; va_end(ap); return x+global;");
        var f = flow(p);
        var write = nodes(f.entry(),AssignmentExpression.class).stream().filter(a -> a.target() instanceof DereferenceExpression).findFirst().orElseThrow();
        for (var use : nodes(nodes(f.entry(),ReturnStatement.class).getFirst(),NameExpression.class))
            assertTrue(dependency(f,write,use),use.name());
    }
    @Test void branchMergeRetainsPossibleAdvanceAndOriginalState() throws Exception {
        var p = read("std::va_list ap; va_start(ap,n); if(n) va_arg(ap,int); va_end(ap); return 0;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        assertTrue(dependency(f,ops.get(0),ops.get(2)));
        assertTrue(dependency(f,ops.get(1),ops.get(2)));
    }
    @Test void typedPointersKeepStateTransitionsAndIndirectWrites() throws Exception {
        var p = read("int x=1; int* exposed=&x; global=2; std::va_list ap; va_start(ap,n); "
                + "Mutator action=va_arg(ap,Mutator); Row row=va_arg(ap,Row); va_end(ap); action(&x); (*row)[1]=8; return x+global;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        for (int i=1;i<ops.size();i++) assertTrue(dependency(f,ops.get(i-1),ops.get(i)));
        var call = nodes(f.entry(),CallExpression.class).getFirst(); assertTrue(call.target().isEmpty());
        assertEquals("void",TypeSemantics.callType(call,profile()).orElseThrow().returnType().canonicalIdentity());
        assertTrue(f.control().stream().anyMatch(e -> e.kind() == FunctionFlow.Branch.EXCEPTION));
        var write = nodes(f.entry(),AssignmentExpression.class).stream().filter(a -> a.target() instanceof IndexExpression).findFirst().orElseThrow();
        for (var use : nodes(nodes(f.entry(),ReturnStatement.class).getFirst(),NameExpression.class)) {
            assertTrue(dependency(f,call,use), "indirect call reaches " + use.name());
            assertTrue(dependency(f,write,use), "array pointer write reaches " + use.name());
        }
        var folded = ConstantFolder.fold(p);
        assertEquals(1,nodes(folded,CallExpression.class).size()); assertEquals(4,nodes(folded,VaListExpression.class).size());
    }
    private static PlatformProfile profile() {
        return PlatformProfile.builder().floatingFormat("long double", "binary80").varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
    }
    @Test void typeOnlyPromotedEnumsAndPointersHaveNoCursorEffects() throws Exception {
        var p = read("std::va_list ap; va_start(ap,n); sizeof(va_arg(ap,float)); sizeof(va_arg(ap,E)); "
                + "sizeof(va_arg(ap,Callback)); sizeof(va_arg(ap,Row)); va_end(ap); return 0;");
        var f = flow(p); var ops = nodes(f.entry(),VaListExpression.class);
        assertTrue(dependency(f,ops.getFirst(),ops.getLast()));
        Set<Node> unused = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var op : ops.subList(1,ops.size()-1)) op.walk(new NodeVisitor() { @Override public void visit(Node n) { unused.add(n); }});
        assertFalse(f.data().stream().anyMatch(e -> unused.contains(e.from()) || unused.contains(e.to())));
    }

}
