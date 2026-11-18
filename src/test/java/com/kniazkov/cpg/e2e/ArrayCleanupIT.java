/* Native original/generated lifetime traces. MIT License. */
package com.kniazkov.cpg.e2e;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ArrayCleanupIT {
    @TempDir Path temp;
    @Test void gccOriginalAndGenerated() throws Exception { verify("g++"); }
    @Test void clangOriginalAndGenerated() throws Exception { verify("clang++"); }
    void verify(String compiler) throws Exception {
        String source=Files.readString(Path.of("src/test/resources/array-cleanup/main.cpp"));
        String expected=Files.readString(Path.of("src/test/resources/array-cleanup/expected.txt")).replace("\r\n","\n");
        var run=new CliSandbox(Files.createDirectory(temp.resolve("cli"))).source("main.cpp",source).run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
        var json=Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("\"elementIndex\": 2"));assertTrue(json.contains("ARRAY_DEALLOCATION"));assertTrue(json.contains("ARRAY_UNWIND"));
        var evidence=Files.createDirectories(Path.of("target/array-cleanup-oracle",compiler));
        Files.copy(run.work().resolve("snapshot.json"),evidence.resolve("snapshot.json"),StandardCopyOption.REPLACE_EXISTING);
        String generated=Files.readString(run.work().resolve("generated/main.cpp"));assertFalse(generated.contains(".~"));
        int index=0;
        for(String code:List.of(source,generated)) {
            Path file=evidence.resolve("case"+(++index)+".cpp").toAbsolutePath(),binary=temp.resolve("case"+index);
            Files.writeString(file,code);
            execute(List.of(compiler,"-std=c++03","-pedantic-errors",file.toString(),"-o",binary.toString()),evidence.resolve("compile"+index));
            assertEquals(expected,execute(List.of(binary.toString()),evidence.resolve("run"+index)).replace("\r\n","\n").replaceAll("(?m)[ \t]+$", ""));
        }
        execute(List.of(compiler,"--version"),evidence.resolve("version"));
    }
    String execute(List<String> command,Path log) throws Exception {
        Files.writeString(Path.of(log+".command"),String.join(" ",command));
        var output=Path.of(log+".txt");var process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try{assertTrue(process.waitFor(60,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),()->{try{return Files.readString(output);}catch(Exception e){return e.toString();}});return Files.readString(output);}
        finally{if(process.isAlive())process.destroyForcibly();}
    }
}
