/* Code Property Graph: target profiles and recursive declarators through API/CLI. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.TypeSemantics;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class PlatformDeclaratorsAcceptance {
    private PlatformDeclaratorsAcceptance() {}
    record Output(Path original, Path generated, Path minimumOriginal, Path minimumGenerated) {}
    static Output verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var options = new Cpg.Options(Language.CPP, Cpg.Stage.PARSED, "c++03");
        Project parsed = Cpg.read(cli.input, options);
        assertFalse(parsed.hasErrors());
        shapes(parsed);
        assertEquals(ProjectJson.encode(parsed), ProjectJson.encode(parsed.edit().build()));
        var run = cli.run("cpp", "--stage", "parsed", "--cpp-standard", "c++03").expectStatus(0);
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(parsed), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertEquals(Cpg.sources(parsed, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        shapes(Cpg.read(run.work().resolve("generated"), options));
        var defaults = PlatformProfile.defaults();
        var long32 = PlatformProfile.read(fixture.resolve("long32.json"));
        TypeNode longType = NamedType.create().setName("long").build();
        assertEquals(BigInteger.valueOf(8), TypeSemantics.sizeof(longType, defaults).orElseThrow());
        assertEquals(BigInteger.valueOf(4), TypeSemantics.sizeof(longType, long32).orElseThrow());
        assertEquals(BigInteger.valueOf(2147483647), long32.integerRange("long").orElseThrow().max());
        assertEquals(new BigInteger("9223372036854775807"), defaults.integerRange("long").orElseThrow().max());
        var byte16 = PlatformProfile.builder().name("byte16").bitsPerByte(16)
                .sizeBits("char", 16).alignmentBits("char", 16)
                .sizeBits("bool", 16).alignmentBits("bool", 16).build();
        assertEquals(BigInteger.valueOf(4), TypeSemantics.sizeof(longType, byte16).orElseThrow());
        var unknownAlignment = PlatformProfile.fromSettings(Map.of("inherits", "default",
                "alignments", Collections.singletonMap("long", null)));
        assertTrue(unknownAlignment.alignmentBits("long").isEmpty());
        assertEquals(64, PlatformProfile.fromSettings(Map.of("inherits", "default"))
                .alignmentBits("long").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().bitsPerByte(7).build());
        assertThrows(IllegalArgumentException.class, () -> PlatformProfile.builder().wcharType("short").build());
        Path source = cli.input.resolve("main.cpp");
        var base = new Build.TranslationUnit(new Build.PhysicalSource(source), source.getParent(), List.of(), List.of(), "c++03");
        var changed = base.withPlatform(long32);
        assertNotEquals(base.variantId(), changed.variantId());
        assertEquals(base.variantId(), base.withPlatform(defaults).variantId());
        Project variants = Cpg.read(new Build(List.of(base, changed)), options);
        assertEquals(2, variants.files().size());
        assertEquals(Set.of(base.variantId(), changed.variantId()), variants.files().stream()
                .map(f -> f.variantId().orElseThrow()).collect(java.util.stream.Collectors.toSet()));
        shapes(Cpg.read(new Build(List.of(changed)), options));
        var profileRun = cli.run("cpp", "--stage", "parsed", "--cpp-standard", "c++03", "--platform-profile", fixture.resolve("long32.json").toAbsolutePath().toString()).expectStatus(0);
        String profileJson = Files.readString(profileRun.work().resolve("snapshot.json"));
        assertTrue(profileJson.contains("long32"), profileJson);
        Project customized = Cpg.read(cli.input, options.withPlatform(long32));
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(customized), Json.parse(profileRun.work().resolve("snapshot.json").toFile()));
        var foldedOptions = new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED, "c++03");
        Project green = Cpg.read(new Build(List.of(base)), new Cpg.Options(Language.CPP, Cpg.Stage.GREEN, "c++03"));
        Set<Node> members = Collections.newSetFromMap(new IdentityHashMap<>());
        green.walk(new NodeVisitor() { @Override public void visit(Node n) { members.add(n); } });
        green.walk(new NodeVisitor() { @Override public void visit(TypeNode n) {
            assertEquals(defaults.stableIdentity(), n.semanticType().orElseThrow().profileIdentity());
            for (Node target : n.references()) assertTrue(members.contains(target), "Fresh typedef links belong to the snapshot");
        } });
        var greenCopy = green.edit().build();
        assertEquals(typeFacts(green), typeFacts(greenCopy));
        Project folded = Cpg.read(cli.input, foldedOptions);
        assertEquals(20, returnedInteger(folded));
        Project folded32 = Cpg.read(cli.input, foldedOptions.withPlatform(long32));
        assertEquals(16, returnedInteger(folded32));
        assertEquals(20, returnedInteger(folded), "Custom profile cannot mutate a prior snapshot");
        var foldedRun = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03").expectStatus(0);
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(folded),
                Json.parse(foldedRun.work().resolve("snapshot.json").toFile()));
        var folded32Run = cli.run("cpp", "--stage", "folded", "--cpp-standard", "c++03",
                "--platform-profile", fixture.resolve("long32.json").toAbsolutePath().toString()).expectStatus(0);
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(folded32),
                Json.parse(folded32Run.work().resolve("snapshot.json").toFile()));
        var fields = List.of(NamedType.create().setName("char").build(), longType,
                NamedType.create().setName("char").build());
        var layout = TypeSemantics.layout(fields, defaults).orElseThrow();
        assertEquals(List.of(BigInteger.ZERO, BigInteger.valueOf(8), BigInteger.valueOf(16)), layout.offsets());
        assertEquals(BigInteger.valueOf(24), layout.sizeBytes());
        assertEquals(64, layout.alignmentBits());
        assertEquals(BigInteger.valueOf(12), TypeSemantics.layout(fields, long32).orElseThrow().sizeBytes());
        assertTrue(TypeSemantics.layout(fields, unknownAlignment).isEmpty());
        assertTrue(TypeSemantics.layout(fields, defaults.toBuilder().aggregateLayout("unknown").build()).isEmpty());
        var minimum = new CliSandbox(Files.createDirectory(work.resolve("signed-min")))
                .directory(fixture.resolve("signed-min"));
        var minimumRun = minimum.run("cpp", "--stage", "folded").expectStatus(0);
        Project minimumProject = Cpg.read(minimum.input, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        com.kniazkov.cpg.SnapshotAssertions.assertEquivalent(ProjectJson.encode(minimumProject),
                Json.parse(minimumRun.work().resolve("snapshot.json").toFile()));
        assertEquals(Cpg.sources(minimumProject, Language.CPP).get("main.cpp"),
                Files.readString(minimumRun.work().resolve("generated/main.cpp")));
        return new Output(cli.input, foldedRun.work().resolve("generated"), minimum.input,
                minimumRun.work().resolve("generated"));
    }

    private static List<TypeRepresentation> typeFacts(Project project) {
        List<TypeRepresentation> facts = new ArrayList<>();
        project.walk(new NodeVisitor() { @Override public void visit(TypeNode n) { facts.add(n.semanticType().orElseThrow()); } });
        return facts;
    }

    private static int returnedInteger(Project project) {
        List<ReturnStatement> returns = new ArrayList<>();
        project.walk(new NodeVisitor() { @Override public void visit(ReturnStatement n) { returns.add(n); } });
        assertEquals(1, returns.size());
        return assertInstanceOf(IntegerLiteral.class, returns.getFirst().value().orElseThrow()).value();
    }

    static void shapes(Project project) {
        Map<String, VariableDeclaration> variables = new HashMap<>();
        project.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration n) { variables.put(n.name(), n); } });
        var a = assertInstanceOf(ArrayType.class, variables.get("array_of_pointers").type().orElseThrow());
        assertInstanceOf(PointerType.class, a.elementType());
        var b = assertInstanceOf(PointerType.class, variables.get("pointer_to_array").type().orElseThrow());
        assertInstanceOf(ArrayType.class, b.pointee());
        var f = assertInstanceOf(PointerType.class, variables.get("function_pointer").type().orElseThrow());
        var function = assertInstanceOf(FunctionType.class, f.pointee());
        assertEquals(1, function.parameters().size());
        assertNotEquals(a.canonicalIdentity(), b.canonicalIdentity());
        assertNotEquals(b.canonicalIdentity(), f.canonicalIdentity());
        assertEquals(NamedType.create().setName("int").build().canonicalIdentity(), variables.get("scalar").type().orElseThrow().canonicalIdentity());
        assertEquals("Count", assertInstanceOf(NamedType.class, variables.get("scalar").type().orElseThrow()).name());
        assertTrue(assertInstanceOf(NamedType.class, variables.get("scalar").type().orElseThrow()).alias().isPresent());
        assertInstanceOf(MemberPointerType.class, variables.get("member_pointer").type().orElseThrow());
        assertInstanceOf(ReferenceType.class, variables.get("reference").type().orElseThrow());
        var qualified = assertInstanceOf(QualifiedType.class, variables.get("qualified_pointer").type().orElseThrow());
        assertTrue(qualified.isConst());
        var pointed = assertInstanceOf(PointerType.class, qualified.baseType());
        assertTrue(assertInstanceOf(QualifiedType.class, pointed.pointee()).isConst());
        List<CallableDeclaration> abstracts = new ArrayList<>();
        project.walk(new NodeVisitor() { @Override public void visit(CallableDeclaration n) {
            if (n.name().equals("consume")) abstracts.add(n);
        } });
        assertEquals(1, abstracts.size());
        assertEquals(2, abstracts.getFirst().parameters().size());
        for (var parameter : abstracts.getFirst().parameters()) {
            assertEquals("", parameter.name());
            assertInstanceOf(PointerType.class, parameter.type().orElseThrow());
        }
        assertInstanceOf(ArrayType.class, variables.get("local_array").type().orElseThrow());
        assertInstanceOf(PointerType.class, variables.get("local_matrix").type().orElseThrow());
        var profile = PlatformProfile.defaults();
        assertEquals(BigInteger.valueOf(24), TypeSemantics.sizeof(a, profile).orElseThrow());
        assertEquals(BigInteger.valueOf(8), TypeSemantics.sizeof(b, profile).orElseThrow());
        assertEquals(BigInteger.valueOf(8), TypeSemantics.sizeof(f, profile).orElseThrow());
        assertTrue(TypeSemantics.sizeof(function, profile).isEmpty(), "Functions have no object size");
    }
}
