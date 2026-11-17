package com.kniazkov.cpg.e2e;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StreamCopyAccessIT {
    @TempDir Path temp;
    @Test void gccOriginalAndGenerated() throws Exception { verify("g++"); }
    @Test void clangOriginalAndGenerated() throws Exception { verify("clang++"); }
    private void verify(String compiler) throws Exception {
        String fixture=Files.readString(Path.of("src/test/resources/stream-copy/main.cpp"));
        for(String type:List.of("ios_base","ios","wios")) for(String operation:List.of("copy","assign")) {
            String name=type+"-"+operation;
            Path source=Path.of("src/test/resources/stream-copy",name+".cpp").toAbsolutePath();
            var negative=new CliSandbox(Files.createDirectory(temp.resolve(name))).source("main.cpp",Files.readString(source));
            var failed=negative.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(1);
            assertTrue(Files.readString(failed.work().resolve("snapshot.json")).contains("inaccessible-"));
            assertFalse(Files.exists(failed.work().resolve("generated/main.cpp")));
            Path evidence=Files.createDirectories(Path.of("target/stream-copy-oracle",compiler,name));
            execute(List.of(compiler,"-std=c++03","-pedantic-errors","-fsyntax-only",source.toString()),evidence.resolve("reject"),false);
        }
        int scenario=0;
        for(String input:List.of(fixture)) {
        scenario++;
        var cli=new CliSandbox(Files.createDirectory(temp.resolve("cli"+scenario))).source("main.cpp",input);
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
        String json=Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("copyfmt"));
        Path evidence=Files.createDirectories(Path.of("target/stream-copy-oracle",compiler,"scenario"+scenario));
        int n=0;
        for(String code:List.of(input,Files.readString(run.work().resolve("generated/main.cpp")))) {
            Path source=evidence.resolve("case"+(++n)+".cpp").toAbsolutePath(),binary=temp.resolve("probe"+scenario+"-"+n);
            Files.writeString(source,code);execute(List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString()),evidence.resolve("compile"+n));
            execute(List.of(binary.toString()),evidence.resolve("run"+n));
        }
        execute(List.of(compiler,"--version"),evidence.resolve("version"));
        }
    }
    private void execute(List<String> command,Path log) throws Exception { execute(command,log,true); }
    private void execute(List<String> command,Path log,boolean accepted) throws Exception {
        Files.writeString(Path.of(log+".command"),String.join(" ",command));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(Path.of(log+".txt").toFile()).start();
        try{assertTrue(p.waitFor(60,TimeUnit.SECONDS));assertEquals(accepted,p.exitValue()==0,()->{try{return Files.readString(Path.of(log+".txt"));}catch(Exception e){return e.toString();}});}
        finally{if(p.isAlive())p.destroyForcibly();}
    }
}
