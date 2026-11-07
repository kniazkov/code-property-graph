/* C++03 opt-in relational operators through API, CLI and native generation. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RelOpsLibraryIT {
    @TempDir Path temp;
    @Test void truthTableSurvivesApiCliJsonAndBothNativeCompilers() throws Exception {
        var cli=new CliSandbox(temp).source("main.cpp",Files.readString(Path.of("src/test/resources/rel-ops/runtime.cpp")));
        nativePrograms(cli.input);
        for(var stage:List.of(Cpg.Stage.GREEN,Cpg.Stage.FOLDED)) {
            var p=Cpg.read(cli.input,new Cpg.Options(Language.CPP,stage,"c++03"));
            assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
            var targets=new ArrayList<String>();
            p.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node n){
                if(n instanceof CallExpression c && c.target().map(LibraryDeclarations::isLibrary).orElse(false)) {
                    var target=c.target().orElseThrow();assertTrue(LibraryDeclarations.isLibrary(target));assertTrue(target.body().isEmpty());targets.add(target.name());
                }
                if(n instanceof Expression e && e.operatorTarget().isPresent() && LibraryDeclarations.isLibrary(e.operatorTarget().orElseThrow())) targets.add(e.operatorTarget().orElseThrow().name());
            }});
            for(String name:List.of("operator!=","operator>","operator<=","operator>=")) assertEquals(2,Collections.frequency(targets,name),targets.toString());
            var run=cli.run("cpp","--stage",stage.name().toLowerCase(Locale.ROOT),"--cpp-standard","c++03").expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(p),Json.parse(run.work().resolve("snapshot.json").toFile()));
            assertEquals(Cpg.sources(p,Language.CPP).get("main.cpp"),Files.readString(run.work().resolve("generated/main.cpp")));
            nativePrograms(run.work().resolve("generated"));
        }
    }
    @Test void mixedArgumentsRetainErrorJsonAndBlockGeneration() throws Exception {
        var cli=new CliSandbox(temp).source("main.cpp","#include <utility>\nstruct Value {}; bool f(Value a){return std::rel_ops::operator!=(a,1);}");
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(1);
        Json.parse(run.work().resolve("snapshot.json").toFile());
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("no-viable"));
        assertFalse(Files.exists(run.work().resolve("generated")));assertFalse(run.stderr().isBlank());
    }
    @Test void nativeLookupAndApplicabilityOracles() throws Exception {
        var accepted=Set.of("own_non_template","using_declaration");
        // missing_prerequisites is a native-body oracle only: the declaration-only
        // CPG deliberately does not diagnose EqualityComparable prerequisites.
        for(String name:List.of("no_using","std_only","mismatched","ambiguous","using_not_exported",
                "builtin_qualified","missing_prerequisites","own_non_template","using_declaration")) {
            for(String compiler:List.of("g++","clang++")) {
                Path work=Files.createTempDirectory(temp,"native-oracle-");
                Path binary=work.resolve("probe");
                Path source=Path.of("src/test/resources/rel-ops/native",name+".cpp").toAbsolutePath();
                command(List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString()),work,accepted.contains(name));
                if(accepted.contains(name)) command(List.of(binary.toString()),work);
            }
        }
    }
    private void nativePrograms(Path source) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("rel-ops");
            command(List.of(compiler, "-std=c++03", "-pedantic-errors", source.resolve("main.cpp").toString(), "-o", binary.toString()), work);
            command(List.of(binary.toString()), work);
        }
    }
    private static void command(List<String> command, Path work) throws Exception {
        command(command,work,true);
    }
    private static void command(List<String> command, Path work, boolean success) throws Exception {
        Path log = Files.createTempFile(work, "process-", ".log");
        var process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), () -> "Timed out: " + command);
            if(success) assertEquals(0, process.exitValue(), () -> command + "\n" + read(log));
            else assertNotEquals(0,process.exitValue(), () -> "Expected rejection: " + command);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }
    private static String read(Path path) {
        try { return Files.readString(path); } catch (java.io.IOException error) { return error.toString(); }
    }
}
