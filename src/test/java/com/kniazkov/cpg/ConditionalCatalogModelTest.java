package com.kniazkov.cpg;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalCatalogModelTest {
    @Test void externalDefinitionsAreOwnedAndSurviveSnapshotEdits() {
        Path path = Path.of("catalog.cpp").toAbsolutePath();
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), path.getParent(), List.of(),
                List.of(new Build.MacroOperation(true, "DEBUG=0"), new Build.MacroOperation(true, "UNUSED=9")), "c++03");
        var project = Project.create().addFile(Parser.parse(unit, "#ifdef DEBUG\nint main(){ return DEBUG; }\n#endif\n")).build();
        var record = project.files().getFirst().provenance().orElseThrow().unit();
        var definitions = record.definitions().stream().filter(d -> d.originKind().equals("command-line")).toList();
        assertEquals(2, definitions.size());
        assertEquals("-DDEBUG=0", project.externalInput(definitions.getFirst().sourceKey()).orElseThrow().text());
        assertEquals(1, record.presenceChecks().size());
        assertEquals(definitions.getFirst().id(), record.presenceChecks().getFirst().definitionId());
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        project.walk(new NodeVisitor() { @Override public void visit(Node node) { owned.add(node); } });
        for (var definition : record.definitions()) assertTrue(owned.contains(project.externalInput(definition.sourceKey()).orElseThrow()));
        var copy = project.edit().build();
        for (var definition : record.definitions()) assertNotSame(project.externalInput(definition.sourceKey()).orElseThrow(), copy.externalInput(definition.sourceKey()).orElseThrow());
        assertEquals(ProjectJson.encode(project).toText(""), ProjectJson.encode(copy).toText(""));
        assertTrue(ProjectJson.encode(project).toText("").contains("sourceNodeId"));
        assertTrue(DotVisualizer.toDot(project).contains("configuration source"));
    }

    @Test void branchOnlySourceRetainsUnparseableInactiveText() {
        String source = "#if 0\nthis is deliberately invalid @ syntax\n#else\nint main(){ return 0; }\n#endif\n";
        var project = Project.create().addFile(Parser.parse("inactive.cpp", source, Language.CPP)).build();
        var record = project.files().getFirst().provenance().orElseThrow().unit();
        assertTrue(record.expansions().isEmpty());
        assertEquals(2, record.branches().size());
        assertFalse(record.branches().getFirst().active());
        assertTrue(record.branches().getFirst().rawSource().contains("deliberately invalid"));
        assertEquals(source, Cpg.sources(project, Language.CPP).get("inactive.cpp"));
    }
}
