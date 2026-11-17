package com.kniazkov.cpg.e2e;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IosfwdDeclarationIT {
    @TempDir Path temp;
    @Test void gccOriginalAndGenerated() throws Exception { verify("g++"); }
    @Test void clangOriginalAndGenerated() throws Exception { verify("clang++"); }
    private void verify(String compiler) throws Exception {
        String fixture=Files.readString(Path.of("src/test/resources/iosfwd-declarations/main.cpp"));
        int scenario=0;
        for(String input:List.of(fixture,fixture.substring(0,fixture.indexOf("#include <iterator>"))+"int main(){return 0;}\n")) {
        scenario++;
        var cli=new CliSandbox(Files.createDirectory(temp.resolve("cli"+scenario))).source("main.cpp",input);
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
        String json=Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("istreambuf_iterator")); assertTrue(json.contains("ostreambuf_iterator"));
        Path evidence=Files.createDirectories(Path.of("target/iosfwd-declarations-oracle",compiler,"scenario"+scenario));
        int n=0;
        for(String code:List.of(input,Files.readString(run.work().resolve("generated/main.cpp")))) {
            Path source=evidence.resolve("case"+(++n)+".cpp").toAbsolutePath(),binary=temp.resolve("probe"+scenario+"-"+n);
            Files.writeString(source,code);execute(List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString()),evidence.resolve("compile"+n));
            execute(List.of(binary.toString()),evidence.resolve("run"+n));
        }
        execute(List.of(compiler,"--version"),evidence.resolve("version"));
        }
    }
    private void execute(List<String> command,Path log) throws Exception {
        Files.writeString(Path.of(log+".command"),String.join(" ",command));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(Path.of(log+".txt").toFile()).start();
        try{assertTrue(p.waitFor(60,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),()->{try{return Files.readString(Path.of(log+".txt"));}catch(Exception e){return e.toString();}});}
        finally{if(p.isAlive())p.destroyForcibly();}
    }
}
