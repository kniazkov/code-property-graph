package com.kniazkov.cpg.e2e;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
/** Controlled handlers run in isolated processes, including termination cases. */
class ExceptionBoundaryIT {
    @TempDir Path temp;
    @Test void gccOriginalAndGenerated() throws Exception { verify("g++"); }
    @Test void clangOriginalAndGenerated() throws Exception { verify("clang++"); }
    void verify(String compiler) throws Exception {
        for(String name:List.of("allowed","bad","terminate","empty","local","overwrite","cleanup")) {
            String source=Files.readString(Path.of("src/test/resources/exception-boundary",name+".cpp"));
            var run=new CliSandbox(Files.createDirectory(temp.resolve(name))).source("main.cpp",source).run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
            assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("UNEXPECTED_CALL"));
            var logs=Files.createDirectories(Path.of("target/exception-boundary-oracle",compiler,name));int i=0;
            for(String code:List.of(source,Files.readString(run.work().resolve("generated/main.cpp")))){
                var input=logs.resolve("case"+(++i)+".cpp").toAbsolutePath();Files.writeString(input,code);var binary=temp.resolve(name+i);
                execute(List.of(compiler,"-std=c++03","-pedantic-errors",input.toString(),"-o",binary.toString()),logs.resolve("compile"+i),0);
                execute(List.of(binary.toString()),logs.resolve("run"+i),name.equals("terminate")||name.equals("empty")?42:0);
            }
        }
    }
    void execute(List<String> command,Path log,int expected) throws Exception {
        Files.writeString(Path.of(log+".command"),String.join(" ",command));var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(Path.of(log+".txt").toFile()).start();
        try{assertTrue(p.waitFor(60,TimeUnit.SECONDS));assertEquals(expected,p.exitValue(),command.toString());}finally{if(p.isAlive())p.destroyForcibly();}
    }
}
