/* Reconcile a physical header edit across C++03 configurations. MIT License. */
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.*;

public final class EditSharedHeaders {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("server", "both").contains(args[1]))
            throw new IllegalArgumentException("EditSharedHeaders COMPILE_COMMANDS server|both NEW_OUTPUT_DIRECTORY");
        Path database = Path.of(args[0]), output = Path.of(args[2]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        var project = Cpg.readCompilationDatabase(database, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        var editor = new ProjectEditor(project);
        int replacements = 0;
        for (SourceFile file : project.files()) {
            boolean server = file.provenance().orElseThrow().unit().configuration().macros().stream()
                    .anyMatch(m -> m.define() && m.operand().equals("SERVER"));
            if (!server && !args[1].equals("both")) continue;
            var literals = new ArrayList<IntegerLiteral>();
            file.walk(new NodeVisitor() { @Override public void visit(FunctionDeclaration function) {
                if (function.name().equals("retry_limit")) function.walk(new NodeVisitor() {
                    @Override public void visit(IntegerLiteral literal) { literals.add(literal); }
                });
            }});
            if (literals.size() != 1 || literals.getFirst().value() != 1)
                throw new IllegalArgumentException("Expected one retry_limit() literal 1 per selected variant");
            editor.replace(literals.getFirst(), IntegerLiteral.create().setValue(2).build());
            replacements++;
        }
        if (replacements != (args[1].equals("both") ? 2 : 1))
            throw new IllegalArgumentException("Expected one server and one client translation-unit variant");
        var edited = editor.build();

        // Validate every exported variant before creating a directory or writing ANY export.
        // A server-only edit to a common physical literal must fail here with both requirements.
        var files = Cpg.sources(edited, Language.CPP);
        Files.createDirectories(output);
        for (var entry : files.entrySet()) {
            Path path = output.resolve("sources").resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
        Files.copy(database, output.resolve("compile_commands.json"));
        ProjectJson.write(edited, output.resolve("edited.json"));
        System.out.println("Edited " + replacements + " variant(s); emitted one shared source set in " + output);
    }
}
