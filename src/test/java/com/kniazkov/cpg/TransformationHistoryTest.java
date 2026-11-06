package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TransformationHistoryTest {
    @TempDir Path directory;

    private static <T extends Node> T first(Project project, Class<T> type) {
        var nodes = new ArrayList<T>();
        project.files().forEach(file -> file.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) nodes.add(type.cast(node)); }
        }));
        return nodes.getFirst();
    }

    @Test void consoleRolesAndFoldPredecessorSurviveSemanticCopies() {
        var parsed = Project.create().addFile(Parser.parse("main.cpp",
                "int main(){std::cout << (2+3) << std::endl;return 0;}", Language.CPP, "c++03")).build();
        var green = ConsoleNormalizer.green(parsed);
        var print = first(green, PrintStatement.class);
        assertEquals(Set.of("source-construct", "stream", "value", "newline", "flush"),
                new HashSet<>(print.transformationProvenance().stream().map(TransformationProvenance::role).toList()));
        var newline = print.transformationProvenance().stream().filter(p -> p.role().equals("newline")).findFirst().orElseThrow();
        var flush = print.transformationProvenance().stream().filter(p -> p.role().equals("flush")).findFirst().orElseThrow();
        assertSame(newline.source(), flush.source());
        var folded = ConstantFolder.fold(green);
        var literal = first(folded, PrintStatement.class).value();
        assertInstanceOf(IntegerLiteral.class, literal);
        assertTrue(literal.transformationProvenance().stream().anyMatch(p ->
                p.passId().equals("ConstantFolder") && p.source() instanceof AddExpression));
        assertInstanceOf(AddExpression.class, print.value());
        assertTrue(print.value().transformationProvenance().stream().anyMatch(p ->
                p.passId().equals("ConsoleNormalizer") && p.source() instanceof AddExpression));
        assertEquals(0, parsed.files().stream().flatMap(f -> f.children().stream()).filter(PrintStatement.class::isInstance).count());
        var resolved = NameResolver.resolve(folded);
        assertEquals(literal.transformationProvenance(), first(resolved, PrintStatement.class).value().transformationProvenance());
    }

    @Test void pipelineStageDoesNotDependOnGreenSyntax() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "int main(){return 0;}");
        for (var stage : Cpg.Stage.values()) {
            var project = Cpg.read(directory, new Cpg.Options(Language.CPP, stage, "c++03"));
            assertEquals(stage.name(), project.snapshotStage().name());
        }
    }
}
