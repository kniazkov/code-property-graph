/* Reproduce the first C++03 milestone through the public editing API. MIT License. */
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.ArrayList;

public final class EditSquare {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("EditSquare COMPILE_COMMANDS NEW_OUTPUT_DIRECTORY");
        Path database = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        var project = Cpg.readCompilationDatabase(database, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        var matches = new ArrayList<MultiplyExpression>();
        project.walk(new NodeVisitor() { @Override public void visit(MultiplyExpression n) {
            if (n.left() instanceof NameExpression name && name.name().equals("a")) matches.add(n);
        }});
        if (matches.size() != 1) throw new IllegalArgumentException("Expected one SQUARE(a) expansion");
        var editor = new ProjectEditor(project);
        editor.replace(matches.getFirst().right(), NameExpression.create().setName("b").build());
        var edited = editor.build();
        var files = Cpg.sources(edited, Language.CPP);
        Files.createDirectories(output);
        for (var entry : files.entrySet()) {
            Path path = output.resolve("sources").resolve(entry.getKey());
            Files.createDirectories(path.getParent()); Files.writeString(path, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
        Files.copy(database, output.resolve("compile_commands.json"));
        ProjectJson.write(edited, output.resolve("edited.json"));
        System.out.println("Edited one right operand; preserved shared header and second invocation in " + output);
    }
}
