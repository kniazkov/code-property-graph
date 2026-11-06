/* C++03 call-site-sensitive object definitions and conservative effects. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions deliberately distinguish a stored object from its pointer/reference binding. */
class CallSiteDataFlowTest {
    private Project analyze(String source) {
        var resolved = NameResolver.resolve(Project.create().addFile(Parser.parse("calls.cpp", source, Language.CPP)).build());
        assertFalse(resolved.hasErrors(), resolved.semanticDiagnostics().toString());
        return UnreadWrites.analyze(resolved);
    }
    private void assertReanalysisStable(Project before, java.util.function.UnaryOperator<Project> analysis, String passId) {
        String original = ProjectJson.encode(before).toText("");
        Project after = analysis.apply(before);
        var expected = new LinkedHashMap<>((Map<?, ?>) ProjectJson.encode(before).toJavaObject());
        var actual = new LinkedHashMap<>((Map<?, ?>) ProjectJson.encode(after).toJavaObject());
        expected.remove("passRuns"); actual.remove("passRuns");
        assertEquals(expected, actual);
        assertEquals(before.passRuns().size() + 1, after.passRuns().size());
        assertEquals(passId, after.passRuns().getLast().passId());
        assertEquals(original, ProjectJson.encode(before).toText(""));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        return LazyLibraryGraphTest.nodes(root, type);
    }
    private FunctionFlow flow(Project p, String name) {
        return p.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow();
    }
    private VariableDeclaration local(FunctionFlow f, String name) {
        return nodes(f.entry(), VariableDeclaration.class).stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }
    private Expression returned(FunctionFlow f) {
        return value(nodes(f.entry(), ReturnStatement.class).getLast().value().orElseThrow());
    }
    private Expression value(Expression expression) {
        while (expression instanceof ConversionExpression conversion) expression = conversion.operand();
        return expression;
    }
    private Set<Node> definitions(FunctionFlow f, Node read) {
        if (read instanceof Expression expression) read = value(expression);
        final Node target = read;
        var result = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        f.data().stream().filter(e -> e.kind() == FunctionFlow.Dependency.DEFINITION && e.to() == target).forEach(e -> result.add(e.from()));
        return result;
    }
    private boolean originates(Project p, Node read, Node producer) {
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var queue = new ArrayDeque<Node>(); queue.add(read);
        var edges = p.flows().stream().flatMap(f -> f.data().stream()).toList();
        while (!queue.isEmpty()) {
            Node node = queue.remove();
            if (node == producer) return true;
            if (seen.add(node)) edges.stream().filter(e -> e.to() == node).forEach(e -> queue.add(e.from()));
        }
        return false;
    }
    private void assertReadFrom(FunctionFlow f, Node read, Node producer) {
        assertTrue(definitions(f, read).contains(producer), () -> "Missing definition from " + producer.source(Language.CPP) + " to " + read.source(Language.CPP));
    }

    @Test void twoPointersSharePointeeButNotPointerBinding() {
        var p = analyze("int f(){int x=1; int* p=&x; int* q=p; *p=7; return *q;}");
        var f = flow(p, "f");
        assertReadFrom(f, returned(f), nodes(f.entry(), AssignmentExpression.class).getFirst());
    }
    @Test void referenceToScalarSharesExactObject() {
        var p = analyze("int f(){int x=1; int& r=x; r=7; return x;}");
        var f = flow(p, "f");
        assertEquals(Set.of(nodes(f.entry(), AssignmentExpression.class).getFirst()), definitions(f, returned(f)));
    }
    @Test void pointerRebindingDoesNotWriteFormerPointee() {
        var p = analyze("int f(){int x=1; int y=2; int* p=&x; p=&y; *p=7; return x;}");
        var f = flow(p, "f");
        assertEquals(Set.of(local(f,"x")), definitions(f, returned(f)));
    }
    @Test void uncertainPointerWritePreservesBothEarlierObjects() {
        var p = analyze("int f(bool flag){int x=1; int y=2; int* p=&x; if(flag) p=&y; *p=7; return x+y;}");
        var f = flow(p, "f");
        var write = nodes(f.entry(), AssignmentExpression.class).getLast();
        for (var read : nodes(returned(f), NameExpression.class)) {
            assertReadFrom(f, read, local(f, read.name()));
            assertReadFrom(f, read, write);
        }
    }
    @Test void distinctObjectFieldsDoNotClobberEachOther() {
        var p = analyze("struct S{int value;}; int f(){S a; S b; a.value=1; b.value=2; int& r=a.value; r=7; return b.value;}");
        var f = flow(p,"f");
        assertEquals(Set.of(nodes(f.entry(), AssignmentExpression.class).get(1)), definitions(f, returned(f)));
    }
    @Test void fixedArrayElementIdentitySurvivesReferenceBinding() {
        var p = analyze("int f(){int a[2]; a[0]=1; a[1]=2; int& r=a[0]; r=7; return a[1];}");
        var f = flow(p,"f");
        assertEquals(Set.of(nodes(f.entry(), AssignmentExpression.class).get(1)), definitions(f, returned(f)));
    }
    @Test void callThroughPointerUpdatesOnlyTheSelectedFieldOrArrayElement() {
        for(String source:List.of(
                "void update(int* p){*p=7;}struct S{int a;int b;};int f(){S s;s.a=1;s.b=2;update(&s.a);return s.a+s.b;}",
                "void update(int* p){*p=7;}int f(){int a[2];a[0]=1;a[1]=2;update(&a[0]);return a[0]+a[1];}")) {
            var p=analyze(source);var f=flow(p,"f");
            var reads=returned(f).children().stream().filter(Expression.class::isInstance).map(Expression.class::cast).map(this::value).toList();
            assertEquals(2,reads.size());
            assertTrue(originateCallBody(p,reads.getFirst(),"update"));
            assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).get(1)),definitions(f,reads.getLast()));
        }
    }
    @Test void unavailablePointerUpdateIsWeakAndReadOnlyBodyDoesNotWrite() {
        var p=analyze("void update(int*);int f(){int x=1;update(&x);return x;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),local(f,"x"));
        assertReadFrom(f,returned(f),nodes(f.entry(),CallExpression.class).getFirst());
        p=analyze("int inspect(const int& x){return x;}int f(){int x=1;inspect(x);return x;}");
        f=flow(p,"f");assertEquals(Set.of(local(f,"x")),definitions(f,returned(f)));
    }
    @Test void arrayDecayDoesNotTurnElementWriteIntoWholeArrayKill() {
        var p=analyze("void update(int* p){*p=7;}int f(){int a[2];a[0]=1;a[1]=2;update(a);return a[1];}");
        var f=flow(p,"f");assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).get(1)),definitions(f,returned(f)));
    }
    @Test void unavailableArrayDecayCallMayWriteAnyReachableElement() {
        var p=analyze("void update(int*);int f(){int a[2];a[0]=1;a[1]=2;update(a);return a[1];}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).get(1));
        assertReadFrom(f,returned(f),nodes(f.entry(),CallExpression.class).getFirst());
    }
    @Test void unavailableDoublePointerCallMayWriteTransitivePointee() {
        var p=analyze("void update(int**);int f(){int x=1;int* p=&x;update(&p);return x;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),local(f,"x"));
        assertReadFrom(f,returned(f),nodes(f.entry(),CallExpression.class).getFirst());
    }
    @Test void uncertainPointerSlotAssignmentDoesNotStronglyRebindEveryCandidate() {
        var p=analyze("int f(bool b){int x=1;int y=2;int* p=&x;int* q=&y;int** r=&p;if(b)r=&q;*r=&y;*p=7;return x;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),local(f,"x"));
        assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getLast());
    }
    @Test void pointerIncrementCannotKillOriginalElementDefinition() {
        var p=analyze("int f(){int a[2];a[0]=1;a[1]=2;int* p=&a[0];p++;*p=7;return a[0];}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
    }
    @Test void loopCarriedPointerOffsetsHaveFiniteConservativeJoin() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var p=analyze("int f(){int a[4];a[0]=1;int* p=a;for(int i=0;i<3;++i)++p;*p=7;return a[0];}");
            var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
        });
    }
    @Test void pointerCompoundOffsetCannotKillOriginalElementDefinition() {
        var p=analyze("int f(){int a[2];a[0]=1;a[1]=2;int* p=a;p+=1;*p=7;return a[0];}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
    }
    @Test void returnedStaticLocalReferenceKeepsStorageAcrossCalls() {
        var p=analyze("int& storage(){static int x=1;return x;}int f(){int& r=storage();r=7;return storage();}");
        var f=flow(p,"f");var write=nodes(f.entry(),AssignmentExpression.class).getFirst();
        assertTrue(originates(p,returned(f),write), "Calling again must not recreate or reinitialize static storage");
    }
    @Test void externalReferenceUpdateAddsMayWriteWithoutKillingInitialization() {
        var p = analyze("void update(int&); void use(int); int f(){int x=1; update(x); use(x); return 0;}");
        var f = flow(p,"f");
        var calls = nodes(f.entry(), CallExpression.class);
        var read = nodes(calls.get(1).arguments().getFirst(), NameExpression.class).getFirst();
        var definitions = definitions(f, read);
        assertTrue(definitions.contains(local(f,"x")));
        assertTrue(definitions.size() >= 2, "An unavailable mutating target contributes its own may-write source");
        assertTrue(originates(p, read, calls.getFirst()), "The extra source must retain the update call's identity");
        assertTrue(local(f,"x").diagnostics().isEmpty());
        Map<Node,Integer> ids=new IdentityHashMap<>();p.walk(new NodeVisitor(){@Override public void visit(Node node){ids.put(node,ids.size());}});
        Object json=ProjectJson.encode(p).toJavaObject();
        for(Node source:List.of(local(f,"x"),calls.getFirst()))
            assertTrue(jsonDefinition(json,ids.get(source),ids.get(read)), "JSON must publish both real producer IDs, not only an opaque dependency");
    }
    private boolean jsonDefinition(Object value, int from, int to) { return jsonDefinition(value,from,to,null); }
    private boolean jsonDefinition(Object value, int from, int to, Integer callSite) {
        if(value instanceof List<?> list) return list.stream().anyMatch(item -> jsonDefinition(item,from,to,callSite));
        if(!(value instanceof Map<?,?> map)) return false;
        if(map.get("id") instanceof Number id && id.intValue()==from && map.get("edges") instanceof List<?> edges)
            for(Object item:edges) if(item instanceof Map<?,?> edge && "dfg.definition".equals(edge.get("kind"))
                    && edge.get("to") instanceof Number target && target.intValue()==to
                    && (callSite==null || edge.get("callSiteId") instanceof Number site && site.intValue()==callSite)) return true;
        return map.values().stream().anyMatch(item -> jsonDefinition(item,from,to,callSite));
    }
    @Test void availableUnconditionalReferenceWriteKillsEarlierSource() {
        var p = analyze("void update(int& x){x=7;} int f(){int x=1; update(x); return x;}");
        var f = flow(p,"f");
        assertFalse(definitions(f,returned(f)).contains(local(f,"x")));
        assertTrue(originateCallBody(p, returned(f), "update"));
    }
    private boolean originateCallBody(Project p, Node read, String name) {
        return originates(p,read,nodes(flow(p,name).entry(),AssignmentExpression.class).getFirst());
    }
    @Test void conditionalCalleeWriteRetainsEarlierSource() {
        var p = analyze("void update(int& x,bool b){if(b)x=7;} int f(bool b){int x=1; update(x,b); return x;}");
        var f = flow(p,"f");
        assertReadFrom(f,returned(f),local(f,"x"));
        assertTrue(originateCallBody(p,returned(f),"update"));
    }
    @Test void twoCallsOnDifferentObjectsKeepDistinctSources() {
        var p = analyze("void update(int& x){x=7;} int f(){int a=1; int b=2; update(a); update(b); return a+b;}");
        var f = flow(p,"f");
        var reads = nodes(returned(f),NameExpression.class);
        var a = definitions(f,reads.getFirst()); var b = definitions(f,reads.getLast());
        assertFalse(a.isEmpty()); assertFalse(b.isEmpty());
        assertFalse(a.contains(local(f,"b"))); assertFalse(b.contains(local(f,"a")));
        var calls = nodes(f.entry(),CallExpression.class);
        Map<Node,Integer> ids=new IdentityHashMap<>();p.walk(new NodeVisitor(){@Override public void visit(Node node){ids.put(node,ids.size());}});
        var bodyWrite=nodes(flow(p,"update").entry(),AssignmentExpression.class).getFirst();
        for(int i=0;i<2;i++) {
            final int index=i;
            assertTrue(jsonDefinition(ProjectJson.encode(p).toJavaObject(), ids.get(bodyWrite),ids.get(reads.get(index)),ids.get(calls.get(index))));
            assertFalse(jsonDefinition(ProjectJson.encode(p).toJavaObject(), ids.get(bodyWrite),ids.get(reads.get(index)),ids.get(calls.get(1-index))));
            assertTrue(f.data().stream().anyMatch(edge -> edge.to()==reads.get(index)
                    && edge.kind()==FunctionFlow.Dependency.DEFINITION && edge.callSite()==calls.get(index)));
            assertFalse(f.data().stream().anyMatch(edge -> edge.to()==reads.get(index)
                    && edge.kind()==FunctionFlow.Dependency.DEFINITION && edge.callSite()==calls.get(1-index)));
        }
        assertTrue(originateCallBody(p,reads.getFirst(),"update"));
        assertTrue(originateCallBody(p,reads.getLast(),"update"));
    }
    @Test void aliasedActualReferencesRespectCalleeWriteOrder() {
        for(String invocation:List.of("update(x,x);", "int& r=x;int& s=x;update(r,s);")) {
            var p = analyze("void update(int& a,int& b){a=3;b=7;} int f(){int x=1;"+invocation+"return x;}");
            var f = flow(p,"f"); var writes = nodes(flow(p,"update").entry(),AssignmentExpression.class);
            assertFalse(definitions(f,returned(f)).contains(local(f,"x")));
            assertTrue(originates(p,returned(f),writes.getLast()));
            assertFalse(originates(p,returned(f),writes.getFirst()), "Earlier write through the same actual object is overwritten");
        }
    }
    @Test void byValueParameterAssignmentDoesNotMutateCaller() {
        var p = analyze("void update(int x){x=7;} int f(){int x=1;update(x);return x;}");
        var f = flow(p,"f"); assertEquals(Set.of(local(f,"x")),definitions(f,returned(f)));
    }
    @Test void pointerParameterRebindingDoesNotMutateCallerObject() {
        var p = analyze("void update(int* p,int* q){p=q;*p=7;} int f(){int x=1;int y=2;update(&x,&y);return x;}");
        var f = flow(p,"f"); assertEquals(Set.of(local(f,"x")),definitions(f,returned(f)));
    }
    @Test void returnValueDependsOnAvailableBodyAndActualArgument() {
        var p = analyze("int identity(int x){return x;} int f(){int a=7;int b=identity(a);return b;}");
        var f = flow(p,"f"); var call = nodes(f.entry(),CallExpression.class).getFirst();
        assertTrue(originates(p, call, returned(flow(p,"identity"))));
        assertTrue(originates(p, returned(f), local(f,"a")));
    }
    @Test void returnedReferenceAndPointerBindCallerStorage() {
        for (String pair : List.of("int& identity(int& x){return x;}|int& r=identity(x);r=7;", "int* identity(int* x){return x;}|int* r=identity(&x);*r=7;")) {
            var parts = pair.split("\\|"); var p = analyze(parts[0]+"int f(){int x=1;"+parts[1]+"return x;}");
            var f = flow(p,"f"); assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).getFirst()),definitions(f,returned(f)));
        }
    }
    @Test void recursiveSummariesTerminateAndRetainConditionalWrite() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var p = analyze("void update(int& x,int n){if(n)update(x,n-1);else x=7;} int f(int n){int x=1;update(x,n);return x;}");
            var f = flow(p,"f"); assertTrue(originateCallBody(p,returned(f),"update"));
            assertReanalysisStable(p, DataFlow::analyze, "DataFlow");
        });
    }
    @Test void mutuallyRecursiveSummariesTerminateWithReachableWrites() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var p = analyze("void second(int&,int); void first(int& x,int n){if(n)second(x,n-1);else x=7;} void second(int& x,int n){first(x,n);} int f(int n){int x=1;first(x,n);return x;}");
            assertTrue(originateCallBody(p,returned(flow(p,"f")),"first"));
        });
    }
    @Test void exceptionalPathKeepsPreCallSourceWhenWriteMayNotExecute() {
        var p = analyze("void update(int& x,bool b){if(b)throw 1;x=7;} int f(bool b){int x=1;try{update(x,b);}catch(...){return x;}return x;}");
        var f = flow(p,"f"); var returns = nodes(f.entry(),ReturnStatement.class);
        assertReadFrom(f,returns.getFirst().value().orElseThrow(),local(f,"x"));
        assertFalse(definitions(f,returns.getLast().value().orElseThrow()).contains(local(f,"x")), "Successful normal return proves the exact-object write");
    }
    @Test void unknownEntryPointersMayAliasEachOthersPointees() {
        var p=analyze("int f(int* p,int* q){*p=7;return *q;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
    }
    @Test void unknownEntryReferencesMayAliasWithoutKillingOtherInput() {
        var p=analyze("int f(int& a,int& b){a=7;return b;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
        assertReadFrom(f,returned(f),f.entry().parameters().get(1));
    }
    @Test void unknownEntryAggregateReferencesMayShareFields() {
        var p=analyze("struct S{int value;};int f(S& a,S& b){a.value=7;return b.value;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
        assertReadFrom(f,returned(f),f.entry().parameters().get(1));
    }
    @Test void conditionalAggregateFieldWriteRetainsIncomingFieldValue() {
        var p=analyze("struct S{int value;};int f(S& s,bool b){if(b)s.value=7;return s.value;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),f.entry().parameters().getFirst());
        assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
    }
    @Test void unknownEntryParameterMayAliasReachableGlobal() {
        for(String source:List.of("int g=1;int f(int& x){x=7;return g;}","int g=1;int f(int* x){*x=7;return g;}")) {
            var p=analyze(source);var f=flow(p,"f");
            assertReadFrom(f,returned(f),nodes(f.entry(),AssignmentExpression.class).getFirst());
            var global=nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("g")).findFirst().orElseThrow();
            assertReadFrom(f,returned(f),global);
        }
    }
    @Test void unknownEntryReferenceCannotAliasFreshLocalOrByValueParameter() {
        for(String source:List.of("int f(int& x){int y=1;x=7;return y;}","int f(int& x,int y){x=7;return y;}")) {
            var p=analyze(source);var f=flow(p,"f");
            assertFalse(definitions(f,returned(f)).contains(nodes(f.entry(),AssignmentExpression.class).getFirst()));
            assertEquals(1,definitions(f,returned(f)).size());
        }
    }
    @Test void concreteDistinctActualsRefineUnknownEntryAliasUncertainty() {
        var p=analyze("int other(int& a,int& b){a=7;return b;}int f(){int x=1;int y=2;return other(x,y);}");
        var f=flow(p,"f");var body=flow(p,"other");var read=returned(body);
        var write=nodes(body.entry(),AssignmentExpression.class).getFirst();
        var call=nodes(f.entry(),CallExpression.class).getFirst();
        assertTrue(f.data().stream().anyMatch(e->e.to()==read&&e.from()==local(f,"y")&&e.kind()==FunctionFlow.Dependency.DEFINITION&&e.callSite()==call));
        assertFalse(f.data().stream().anyMatch(e->e.to()==read&&e.from()==write&&e.kind()==FunctionFlow.Dependency.DEFINITION&&e.callSite()==call));
    }
    @Test void normalizedLibraryEffectsDoNotDisableExactUserCallEffects() {
        String source="#include <vector>\nvoid update(int& x){x=7;}int f(){std::vector<int> a;int x=1;a.push_back(3);update(x);return x+a.size();}";
        var p=UnreadWrites.analyze(DynamicArraySemantics.green(NameResolver.resolve(Project.create().addFile(Parser.parse("mixed.cpp",source,Language.CPP)).build())));
        var f=flow(p,"f");
        var read=nodes(returned(f),NameExpression.class).stream().filter(n->n.name().equals("x")).findFirst().orElseThrow();
        assertFalse(definitions(f,read).contains(local(f,"x")));
        assertTrue(originateCallBody(p,read,"update"));
    }
    @Test void calleeReferenceWriteReadOnlyByCallerIsNotUnread() {
        var p = analyze("void update(int& x){x=7;} int f(){int x=1;update(x);return x;}");
        assertTrue(nodes(flow(p,"update").entry(),AssignmentExpression.class).getFirst().diagnostics().isEmpty());
    }
    @Test void unknownArrayIndexWeaklyUpdatesPossibleElement() {
        var p = analyze("int f(int i){int a[2];a[0]=1;a[1]=2;a[i]=7;return a[0];}");
        var f=flow(p,"f");var writes=nodes(f.entry(),AssignmentExpression.class);
        assertReadFrom(f,returned(f),writes.getFirst()); assertReadFrom(f,returned(f),writes.getLast());
    }
    @Test void memberReceiverSelectsOnlyItsOwnObject() {
        var p = analyze("struct S{int value;void update(){value=7;}}; int f(){S a;S b;a.value=1;b.value=2;a.update();return b.value;}");
        var f=flow(p,"f"); assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).get(1)),definitions(f,returned(f)));
        p=analyze("struct S{int value;void update(){value=7;}}; int f(){S a;a.value=1;a.update();return a.value;}");
        f=flow(p,"f");assertTrue(originateCallBody(p,returned(f),"update"));
    }
    @Test void defaultReferenceArgumentRetainsActualObjectBinding() {
        var p=analyze("int g=1;void update(int& x=g){x=7;}int f(){update();return g;}");
        assertTrue(originateCallBody(p,returned(flow(p,"f")),"update"));
    }
    @Test void globalWriteInAvailableBodyReachesCallerRead() {
        var p=analyze("int g=1;void update(){g=7;}int f(){update();return g;}");
        assertTrue(originateCallBody(p,returned(flow(p,"f")),"update"));
    }
    @Test void partialObjectWriteDoesNotKillSiblingField() {
        var p=analyze("struct S{int a;int b;};void update(S& x){x.a=7;}int f(){S x;x.a=1;x.b=2;update(x);return x.b;}");
        var f=flow(p,"f");assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).get(1)),definitions(f,returned(f)));
    }
    @Test void writeBeforeThrowReachesCatchRead() {
        var p=analyze("void update(int& x){x=7;throw 1;}int f(){int x=1;try{update(x);}catch(...){return x;}return 0;}");
        var read=nodes(flow(p,"f").entry(),ReturnStatement.class).getFirst().value().orElseThrow();
        assertTrue(originateCallBody(p,read,"update"));
    }
    @Test void virtualDispatchJoinsAvailableOverridesWithoutKillingOnPartialTarget() {
        var p=analyze("struct B{virtual void update(int& x){x=3;}};struct D:B{void update(int& x){}};int f(B& b){int x=1;b.update(x);return x;}");
        var f=flow(p,"f");assertReadFrom(f,returned(f),local(f,"x"));
        var write=nodes(p,AssignmentExpression.class).getFirst();assertTrue(originates(p,returned(f),write));
    }
    @Test void analysisCopiesOwnAllDataEndpointsAndReanalysisIsStable() {
        var p=analyze("void update(int& x){x=7;}int f(){int unused=0;int x=1;update(x);return x;}");
        Set<Node> owned=Collections.newSetFromMap(new IdentityHashMap<>());p.walk(new NodeVisitor(){@Override public void visit(Node node){owned.add(node);}});
        for(var flow:p.flows())for(var edge:flow.data()){assertTrue(owned.contains(edge.from()));assertTrue(owned.contains(edge.to()));if(edge.callSite()!=null)assertTrue(owned.contains(edge.callSite()));}
        assertReanalysisStable(p, UnreadWrites::analyze, "UnreadWrites");
        assertTrue(p.edit().build().flows().isEmpty());
    }
    @Test void javaScalarReachingDefinitionsRemainUnchanged() {
        var p = UnreadWrites.analyze(NameResolver.resolve(Project.create().addFile(Parser.parse("Demo.java", "class Demo {public static void main(String[] args){int x=1;x=7;System.out.println(x);}}",Language.JAVA)).build()));
        var f = flow(p,"main"); var read = nodes(f.entry(),CallExpression.class).getFirst().arguments().getFirst();
        assertEquals(Set.of(nodes(f.entry(),AssignmentExpression.class).getFirst()),definitions(f,read));
        assertEquals(UnreadWrites.CODE,local(f,"x").diagnostics().getFirst().code());
    }
}
