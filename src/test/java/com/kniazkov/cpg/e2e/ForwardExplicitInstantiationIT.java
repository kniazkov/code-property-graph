package com.kniazkov.cpg.e2e;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ForwardExplicitInstantiationIT {
    @TempDir Path temp;
    @Test void gccOriginalAndGenerated() throws Exception { verify("g++"); }
    @Test void clangOriginalAndGenerated() throws Exception { verify("clang++"); }
    private void verify(String compiler) throws Exception {
        String input=Files.readString(Path.of("src/test/resources/forward-explicit/main.cpp"));
        var cli=new CliSandbox(Files.createDirectory(temp.resolve("cli"))).source("main.cpp",input);
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
        String json=Files.readString(run.work().resolve("snapshot.json"));
        assertFalse(json.contains("invalid-explicit-instantiation"));assertFalse(json.contains("missing-typeinfo"));
        Path evidence=Files.createDirectories(Path.of("target/forward-explicit-oracle",compiler));
        int n=0;
        for(String code:List.of(input,Files.readString(run.work().resolve("generated/main.cpp")))) {
            Path source=evidence.resolve("case"+(++n)+".cpp").toAbsolutePath(),binary=temp.resolve("probe"+n);
            Files.writeString(source,code);execute(List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString()),evidence.resolve("compile"+n));
            execute(List.of(binary.toString()),evidence.resolve("run"+n));
        }
        execute(List.of(compiler,"--version"),evidence.resolve("version"));
    }
    private void execute(List<String> command,Path log) throws Exception {
        Files.writeString(Path.of(log+".command"),String.join(" ",command));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(Path.of(log+".txt").toFile()).start();
        try{assertTrue(p.waitFor(60,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),()->{try{return Files.readString(Path.of(log+".txt"));}catch(Exception e){return e.toString();}});}
        finally{if(p.isAlive())p.destroyForcibly();}
    }
    @Test void noCallCliAndMissingDefinition() throws Exception {
        var good=new CliSandbox(Files.createDirectory(temp.resolve("good"))).source("main.cpp","template<class T>void f();\n#include <typeinfo>\ntemplate<class U>void f(){typeid(U);}template void f<int>();\n");
        good.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
        var bad=new CliSandbox(Files.createDirectory(temp.resolve("bad"))).source("main.cpp","template<class T>void f();template void f<int>();\n");
        var run=bad.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(1);
        assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
    }
}
