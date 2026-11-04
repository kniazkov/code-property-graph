/* Reproduce C++03 build configuration selection through the public API. MIT License. */
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;

public final class SelectBuilds {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("SelectBuilds COMPILE_COMMANDS NEW_OUTPUT_DIRECTORY");
        Path database = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        var all = Cpg.readCompilationDatabase(database, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        Files.createDirectories(output);
        ProjectJson.write(all, output.resolve("all-variants.json"));
        var entries = (List<?>) Json.parse(database.toFile()).toJavaObject();
        for (String macro : List.of("SERVER", "CLIENT")) {
            String name = macro.toLowerCase(Locale.ROOT);
            var ids = all.files().stream().filter(f -> f.provenance().orElseThrow().unit().configuration()
                    .macros().stream().anyMatch(m -> m.define() && m.operand().equals(macro)))
                    .map(f -> f.variantId().orElseThrow()).toList();
            var selected = all.selectBuild(new Build.Composition(name, ids));
            Path directory = output.resolve(name);
            var files = Cpg.sources(selected, Language.CPP);
            Files.createDirectories(directory);
            for (var entry : files.entrySet()) {
                Path path = directory.resolve("sources").resolve(entry.getKey());
                Files.createDirectories(path.getParent()); Files.writeString(path, entry.getValue(), StandardOpenOption.CREATE_NEW);
            }
            var filtered = entries.stream().filter(e -> ((List<?>) ((Map<?, ?>) e).get("arguments")).contains("-D" + macro)).toList();
            Files.writeString(directory.resolve("compile_commands.json"), Json.serialize(filtered), StandardOpenOption.CREATE_NEW);
            ProjectJson.write(selected, directory.resolve("project.json"));
            System.out.println(name + ": " + ids);
        }
    }
}
