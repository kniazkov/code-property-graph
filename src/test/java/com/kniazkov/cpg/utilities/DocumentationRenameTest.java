/* Doxygen rename keeps physical prose intact and declaration occurrences distinct. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentationRenameTest {
    private static final String ROOT = Path.of("/virtual/docs").toAbsolutePath().normalize() + java.io.File.separator;
    private static final String HEADER = "/** @brief count stays.\r\n * @param count count stays verbatim.\r\n * @unknown count untouched\r\n * @return result\r\n */\r\nint process(int count);\r\n";
    private static Project parse(Map<String, String> sources, String... files) {
        var root = Project.create();
        for (String file : files) {
            var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of(ROOT + file)), Path.of(ROOT), List.of(), List.of(), "c++03");
            root.addFile(Parser.parse(unit, sources.get(ROOT + file), sources));
        }
        return NameResolver.resolve(root.build());
    }
    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        project.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); }});
        return result;
    }
    @Test void declarationAndDefinitionRenamesAreIndependentAndByteExactAcrossTranslationUnits() {
        String definition = "#include \"api.h\"\n/** @brief Definition brief. */\nint process(int n) { return n; }\n";
        var sources = Map.of(ROOT + "api.h", HEADER, ROOT + "impl.cpp", definition,
                ROOT + "main.cpp", "#include \"api.h\"\nint main() { return process(0); }\n");
        var project = parse(sources, "impl.cpp", "main.cpp");
        String original = ProjectJson.encode(project).toText("");
        var count = nodes(project, ParameterDeclaration.class).stream().filter(p -> p.name().equals("count")).findFirst().orElseThrow();
        var changed = Documentation.renameParameter(project, count, "amount");
        var output = Source.physicalSources(changed);
        assertEquals(HEADER.replace("@param count", "@param amount").replace("int count", "int amount"), output.get(ROOT + "api.h"));
        assertEquals(definition, output.get(ROOT + "impl.cpp"));
        var n = nodes(project, ParameterDeclaration.class).stream().filter(p -> p.name().equals("n")).findFirst().orElseThrow();
        var defined = Documentation.renameParameter(project, n, "value");
        assertEquals(HEADER, Source.physicalSources(defined).get(ROOT + "api.h"));
        assertEquals(definition.replace("int n", "int value").replace("return n", "return value"), Source.physicalSources(defined).get(ROOT + "impl.cpp"));
        assertEquals(original, ProjectJson.encode(project).toText(""));
    }
    @Test void templateRenameChangesTypeUsesAndOnlyBackslashTagName() {
        String text = "/** \\brief T remains.\n * \\tparam T T remains.\n * \\param value unchanged\n * \\return T remains.\n */\ntemplate <class T> T identity(T value) { return value; }\n";
        var project = parse(Map.of(ROOT + "main.cpp", text), "main.cpp");
        var changed = Documentation.renameParameter(project, nodes(project, TemplateParameterDeclaration.class).getFirst(), "Element");
        assertEquals(text.replace("\\tparam T", "\\tparam Element").replace("class T", "class Element").replace("> T identity(T", "> Element identity(Element"), Source.physicalSources(changed).get(ROOT + "main.cpp"));
        String json = ProjectJson.encode(changed).toText("");
        assertTrue(json.contains("documentationEntries")); assertTrue(json.contains("templateParameters"));
        assertTrue(json.contains("targetId")); assertTrue(json.contains("includeId"));
        assertTrue(DotVisualizer.toDot(changed, false).contains("documents parameter"));
        var copied = changed.edit().build();
        var copiedNodes = nodes(copied, Node.class);
        for (var entry : nodes(copied, DocumentationEntry.class)) for (var binding : entry.bindings()) {
            assertTrue(copiedNodes.stream().anyMatch(node -> node == binding.declaration()));
            assertTrue(binding.target() == null || copiedNodes.stream().anyMatch(node -> node == binding.target()));
        }
    }
    @Test void repeatedIncludeWithDifferentTypesRejectsPhysicalAmbiguity() {
        String header = "/** @param value value stays */\nint f(TYPE value);\n";
        String source = "#define TYPE int\n#include \"api.h\"\n#undef TYPE\n#define TYPE long\n#include \"api.h\"\n";
        var project = parse(Map.of(ROOT + "api.h", header, ROOT + "main.cpp", source), "main.cpp");
        var value = nodes(project, ParameterDeclaration.class).getFirst();
        assertThrows(IllegalArgumentException.class, () -> Documentation.renameParameter(project, value, "renamed"));
        assertEquals(header, Source.physicalSources(project).get(ROOT + "api.h"));
    }
    @Test void unsafeNamesAndMacroOccurrencesAreRejectedWithoutMutation() {
        var project = parse(Map.of(ROOT + "main.cpp", "#define ARG arg\n/** @param arg text */\nint f(int ARG) { return arg; }\n"), "main.cpp");
        var parameter = nodes(project, ParameterDeclaration.class).getFirst();
        assertThrows(IllegalArgumentException.class, () -> Documentation.renameParameter(project, parameter, "newName"));
        assertThrows(IllegalArgumentException.class, () -> Documentation.renameParameter(project, parameter, "int"));
        assertThrows(IllegalArgumentException.class, () -> Documentation.renameParameter(project, parameter, "f"));
    }
}
