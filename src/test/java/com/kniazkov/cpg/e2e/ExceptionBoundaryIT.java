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
        for(String name:List.of("allowed","bad","terminate","empty","local","overwrite","cleanup","saved","helper","throwing-helper","pointer-branch","helper-restore","outcome-allowed","outcome-bad","outcome-terminate","outcome-stop","outcome-catch","outcome-rethrow","outcome-pointer","outcome-function-pointer","qualification-deep","qualification-void","qualification-member","qualification-member-mixed","memory-alias","memory-cleanup","memory-terminate","lifetime-replacement","lifetime-unwind","lifetime-normal","lifetime-constructor","lifetime-order","lifetime-array-partial","lifetime-array-double","lifetime-return","lifetime-nested-catch","composite-order","composite-body-failure","composite-field-failure","composite-destructor-body","composite-double","composite-array-failure","composite-construction-order","composite-nested","virtual-diamond-order","virtual-base-ctor-spec","virtual-base-dtor-spec","virtual-field-identity","virtual-partial","virtual-body-failure","virtual-nested-order","virtual-array")) {
            String source=Files.readString(Path.of("src/test/resources/exception-boundary",name+".cpp"));
            var run=new CliSandbox(Files.createDirectory(temp.resolve(name))).source("main.cpp",source).run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
            assertEquals(!name.startsWith("qualification-"),Files.readString(run.work().resolve("snapshot.json")).contains("UNEXPECTED_CALL"));
            var logs=Files.createDirectories(Path.of("target/exception-boundary-oracle",compiler,name));int i=0;
            for(String code:List.of(source,Files.readString(run.work().resolve("generated/main.cpp")))){
                var input=logs.resolve("case"+(++i)+".cpp").toAbsolutePath();Files.writeString(input,code);var binary=temp.resolve(name+i);
                execute(List.of(compiler,"-std=c++03","-pedantic-errors",input.toString(),"-o",binary.toString()),logs.resolve("compile"+i),0);
                execute(List.of(binary.toString()),logs.resolve("run"+i),Set.of("terminate","empty","outcome-terminate","outcome-stop","outcome-function-pointer").contains(name)?42:0);
            }
        }
        verifyMultipleUnits(compiler);
    }
    void verifyMultipleUnits(String compiler) throws Exception {
        var sources=Path.of("src/test/resources/exception-boundary/units");
        var run=new CliSandbox(Files.createDirectory(temp.resolve("units"))).directory(sources)
                .run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(0);
        var logs=Files.createDirectories(Path.of("target/exception-boundary-oracle",compiler,"units"));
        for(int i=1;i<=2;i++){
            var command=new ArrayList<>(List.of(compiler,"-std=c++03","-pedantic-errors"));
            for(String name:List.of("a.cpp","b.cpp")){
                var input=logs.resolve(i+name).toAbsolutePath();
                Files.writeString(input,Files.readString(i==1?sources.resolve(name):run.work().resolve("generated").resolve(name)));
                command.add(input.toString());
            }
            var binary=temp.resolve("units"+i);command.add("-o");command.add(binary.toString());
            execute(command,logs.resolve("compile"+i),0);
            execute(List.of(binary.toString()),logs.resolve("run"+i),0);
        }
    }
    void execute(List<String> command,Path log,int expected) throws Exception {
        Files.writeString(Path.of(log+".command"),String.join(" ",command));var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(Path.of(log+".txt").toFile()).start();
        try{assertTrue(p.waitFor(60,TimeUnit.SECONDS));assertEquals(expected,p.exitValue(),command.toString());}finally{if(p.isAlive())p.destroyForcibly();}
    }
}
