/* Host-independent declaration inventory, deliberately separate from behavior. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.StandardLibraryCatalogue;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandardLibraryCatalogueTest {
    private static SourceFile parse(String header, PlatformProfile profile) {
        String includes = StandardLibraryCatalogue.dependencies(header).stream().sorted()
            .map(name -> "#include <" + name + ">\n").reduce("", String::concat);
        Path path = Path.of("/__nonexistent_catalogue_test__/" + header + ".hpp");
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), path.getParent(),
            List.of(), List.of(), "c++03").withPlatform(profile);
        return Parser.parseLibrary(unit, includes + StandardLibraryCatalogue.declarations(header, profile)).build();
    }
    private static List<Declaration> declarations(SourceFile file) {
        return file.items().stream().flatMap(item -> item instanceof NamespaceDeclaration ns
            ? ns.members().stream() : item instanceof Declaration d ? Stream.of(d) : Stream.empty()).toList();
    }
    @Test void catalogueRecognizesExactlyCpp03HeadersAndCompatibilityNames() {
        var headers = StandardLibraryCatalogue.headers();
        // Independent ISO 17.4.1.2 Tables 11/12 and D.5 Table 100 inventory.
        var expected = new TreeSet<>(List.of(("algorithm bitset complex deque exception fstream functional iomanip ios iosfwd iostream istream iterator limits list locale map memory new numeric ostream queue set sstream stack stdexcept streambuf string strstream typeinfo utility valarray vector").split(" ")));
        for (String c : "assert ctype errno float iso646 limits locale math setjmp signal stdarg stddef stdio stdlib string time wchar wctype".split(" ")) {
            expected.add("c"+c); expected.add(c+".h");
        }
        assertEquals(expected, headers);
        assertEquals(69, headers.size());
        assertEquals(51, headers.stream().map(StandardLibraryCatalogue::canonicalHeader).distinct().count());
        assertEquals(18, headers.stream().filter(h -> h.endsWith(".h")).count());
        for (String absent : List.of("array", "tuple", "thread", "unordered_map", "cstdint", "stdint.h", "tr1/memory"))
            assertFalse(headers.contains(absent), absent);
        assertThrows(UnsupportedOperationException.class, () -> headers.add("custom"));
        assertTrue(StandardLibraryCatalogue.dependencies("custom.h").isEmpty());
        for (String header : headers) assertTrue(headers.containsAll(StandardLibraryCatalogue.dependencies(header)), header);
    }
    @Test void everyFragmentParsesWithoutHostHeadersOnBothSizeTypePolicies() {
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().sizeType("unsigned int").build()))
            for (String header : StandardLibraryCatalogue.headers()) {
                var file = assertDoesNotThrow(() -> parse(header, profile), header);
                file.walk(new NodeVisitor() {
                    @Override public void visit(Node node) {
                        if (node instanceof CallableDeclaration callable)
                            assertTrue(callable.body().isEmpty(), header + ": " + callable.name());
                    }
                });
            }
    }
    @Test void parserNameIndexExactlyMatchesDeclaredNamespaceTypes() {
        for (String header : StandardLibraryCatalogue.headers()) {
            var types = new LinkedHashSet<String>(); var templates = new LinkedHashSet<String>();
            for (Declaration declaration : declarations(parse(header, PlatformProfile.defaults()))) {
                String name = declaration instanceof ClassDeclaration c ? c.name()
                    : declaration instanceof TypedefDeclaration t ? t.name()
                    : declaration instanceof EnumDeclaration e ? e.name() : null;
                if (name == null) continue;
                types.add(name); if (!header.endsWith(".h")) types.add("std::" + name);
                if (declaration instanceof ClassDeclaration c && !c.templateParameters().isEmpty()) {
                    templates.add(name); if (!header.endsWith(".h")) templates.add("std::" + name);
                }
            }
            assertEquals(types, StandardLibraryCatalogue.typeNames(header), header + " types");
            assertEquals(templates, StandardLibraryCatalogue.templateNames(header), header + " templates");
        }
    }
    @Test void compatibilityDeclarationsAndDependenciesUseTheGlobalNamespace() {
        for (String header : StandardLibraryCatalogue.headers()) if (header.endsWith(".h")) {
            assertTrue(parse(header, PlatformProfile.defaults()).items().stream().noneMatch(NamespaceDeclaration.class::isInstance), header);
            assertTrue(StandardLibraryCatalogue.typeNames(header).stream().noneMatch(n -> n.startsWith("std::")), header);
        }
        assertEquals(Set.of("stddef.h", "stdarg.h"), StandardLibraryCatalogue.dependencies("stdio.h"));
        assertEquals(Set.of("cstddef", "exception"), StandardLibraryCatalogue.dependencies("new"));
        assertEquals(Set.of("cstddef"), StandardLibraryCatalogue.dependencies("valarray"));
    }
    @Test void stringStreamsRetainTheirThirdAllocatorParameter() {
        var classes = declarations(parse("iosfwd", PlatformProfile.defaults())).stream()
            .filter(ClassDeclaration.class::isInstance).map(ClassDeclaration.class::cast)
            .filter(c -> c.name().startsWith("basic_")).toList();
        for (var type : classes) {
            assertEquals(type.name().contains("string") ? 3 : 2, type.templateParameters().size(), type.name());
        }
    }
    @Test void cxx03AlgorithmReturnTypesDoNotBorrowCxx11Signatures() {
        String algorithms = StandardLibraryCatalogue.declarations("algorithm", PlatformProfile.defaults());
        assertTrue(algorithms.contains("void rotate("));
        assertTrue(algorithms.contains("void fill_n("));
        assertTrue(algorithms.contains("void generate_n("));
        assertFalse(algorithms.contains("is_sorted"));
        assertFalse(StandardLibraryCatalogue.declarations("utility", PlatformProfile.defaults()).contains("void swap(T&"));
        assertTrue(algorithms.contains("void swap(T&"));
    }
    @Test void booleanVectorHasItsOwnProxyAndOpaqueContainerChoices() {
        var vectors = declarations(parse("vector", PlatformProfile.defaults())).stream()
            .filter(ClassDeclaration.class::isInstance).map(ClassDeclaration.class::cast).toList();
        assertEquals(2, vectors.size());
        var specialization = vectors.stream().filter(c -> !c.templateArguments().isEmpty()).findFirst().orElseThrow();
        assertTrue(specialization.members().stream().anyMatch(d -> d instanceof ClassDeclaration c && c.name().equals("reference")));
        assertTrue(specialization.members().stream().anyMatch(d -> d instanceof MethodDeclaration m && m.name().equals("flip")));
        for (var vector : vectors) for (String member : List.of("size_type", "difference_type", "iterator", "const_iterator")) {
            var alias = vector.members().stream().filter(d -> d instanceof TypedefDeclaration t && t.name().equals(member))
                .map(TypedefDeclaration.class::cast).findFirst().orElseThrow();
            assertInstanceOf(OpaqueLibraryType.class, alias.type(), member);
        }
    }
    @Test void allocationOverloadsStayInTheGlobalNamespace() {
        var source = parse("new", PlatformProfile.defaults());
        var functions = source.items().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).toList();
        assertEquals(12, functions.size());
        assertTrue(functions.stream().allMatch(f -> f.name().startsWith("operator")));
        assertTrue(functions.stream().allMatch(f -> f.body().isEmpty()));
    }
    @Test void expandedFamiliesRetainRequiredOverloadGroups() {
        assertMemberNames("list", "list", Set.of("splice", "merge", "sort", "reverse", "unique", "remove_if", "assign", "insert", "erase"));
        assertMemberNames("map", "map", Set.of("insert", "erase", "lower_bound", "upper_bound", "equal_range", "key_comp", "value_comp"));
        assertMemberNames("string", "basic_string", Set.of("append", "assign", "insert", "erase", "replace", "find", "rfind", "substr", "compare"));
        assertMemberNames("valarray", "valarray", Set.of("shift", "cshift", "apply", "resize", "sum", "min", "max"));
        assertMemberNames("iterator", "reverse_iterator", Set.of("base", "operator*", "operator->", "operator++", "operator--"));
    }
    private static void assertMemberNames(String header,String name,Set<String> required) {
        var type = declarations(parse(header,PlatformProfile.defaults())).stream().filter(d -> d instanceof ClassDeclaration c && c.name().equals(name) && !c.members().isEmpty())
            .map(ClassDeclaration.class::cast).findFirst().orElseThrow();
        var actual = new HashSet<String>();
        for (var member : type.members()) if (member instanceof CallableDeclaration callable) actual.add(callable.name());
        assertTrue(actual.containsAll(required), header + ": " + actual);
    }
    @Test void nestedTypeIndexDoesNotInventMembers() {
        for (String header : StandardLibraryCatalogue.headers()) {
            var actual = declaredNestedTypes(declarations(parse(header,PlatformProfile.defaults())));
            assertTrue(actual.containsAll(StandardLibraryCatalogue.nestedTypeNames(header)), header + ": " + StandardLibraryCatalogue.nestedTypeNames(header));
        }
    }
    @Test void stringAliasIndexIsBackedByDeclaredTargetMembers() {
        var declarations = declarations(parse("string",PlatformProfile.defaults()));
        var declared = declaredNestedTypes(declarations);
        var withoutTarget = declaredNestedTypes(declarations.stream()
                .filter(d -> !(d instanceof ClassDeclaration c && c.name().equals("basic_string"))).toList());
        assertFalse(withoutTarget.contains("string::iterator"));
        assertFalse(withoutTarget.contains("wstring::iterator"));
        var indexed = StandardLibraryCatalogue.nestedTypeNames("string");
        for (String alias : List.of("string", "wstring")) {
            assertTrue(indexed.contains(alias + "::iterator"));
            assertTrue(indexed.contains(alias + "::traits_type"));
            for (String path : indexed) if (path.startsWith(alias + "::")) {
                String target = "basic_string" + path.substring(alias.length());
                assertTrue(declared.contains(target), path + " must name a declared target member");
                assertTrue(declared.contains(path), path + " must follow its parsed typedef");
            }
            assertFalse(declared.contains(alias + "::append"), "Methods are not nested types");
            assertFalse(declared.contains(alias + "::invented_member"));
        }
    }
    private static Set<String> declaredNestedTypes(List<Declaration> declarations) {
        var members = new HashMap<String,Set<String>>();
        for (Declaration declaration : declarations)
            if (declaration instanceof ClassDeclaration type && type.isDefinition()
                    && (type.templateKind()==TemplateKind.NONE || type.templateKind()==TemplateKind.PRIMARY)) {
                var nested = new HashSet<String>();
                collectNested(type,"",nested);
                members.computeIfAbsent(type.name(), key -> new HashSet<>()).addAll(nested);
            }
        // A typedef does not invent declarations: expose only nested paths already
        // established for its parsed target. Repeated passes also handle alias chains.
        for (int round=0;round<declarations.size();round++) {
            boolean changed=false;
            for (Declaration declaration : declarations)
                if (declaration instanceof TypedefDeclaration alias && alias.type() instanceof NamedType target) {
                    String name=target.name().replaceFirst("^(::)?std::", "");
                    Set<String> nested=members.get(name);
                    if(nested!=null)changed|=members.computeIfAbsent(alias.name(), key -> new HashSet<>()).addAll(nested);
                }
            if(!changed)break;
        }
        var result = new HashSet<String>();
        members.forEach((owner,nested)->nested.forEach(path->result.add(owner+path)));
        return result;
    }
    private static void collectNested(ClassDeclaration type,String path,Set<String> names) {
        for (Declaration member : type.members()) {
            if (member instanceof TypedefDeclaration t) names.add(path + "::" + t.name());
            if (member instanceof EnumDeclaration e) names.add(path + "::" + e.name());
            if (member instanceof ClassDeclaration c) { names.add(path + "::" + c.name()); collectNested(c,path + "::" + c.name(),names); }
        }
    }

}
