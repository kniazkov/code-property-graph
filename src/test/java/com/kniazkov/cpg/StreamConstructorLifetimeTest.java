/* Exact string-stream construction survives opaque bitmask alternatives. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class StreamConstructorLifetimeTest {
    @TempDir Path directory;
    @Test void stringConstructorRemainsSelectedAcrossStagesAndCopies() throws Exception {
        Files.writeString(directory.resolve("main.cpp"),"#include <sstream>\n#include <string>\nvoid f(){std::string input(\"12 34\");std::istringstream stream(input);std::ostringstream output;}");
        for(var stage:new Cpg.Stage[]{Cpg.Stage.RESOLVED,Cpg.Stage.GREEN}) {
            var project=Cpg.read(directory,new Cpg.Options(Language.CPP,stage,"c++03"));
            verify(project);
            verify(UnreadWrites.analyze(project.edit().build()));
        }
    }
    private static void verify(Project project) {
        assertFalse(project.hasErrors(),()->project.semanticDiagnostics().toString());
        var selected=new boolean[2];
        project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(VariableDeclaration variable){
            if(variable.name().equals("stream")) {
                var constructor=variable.constructorTarget().orElseThrow();
                assertEquals(2,constructor.parameters().size());
                assertTrue(constructor.parameters().getFirst().type().orElseThrow().canonicalIdentity().contains("basic_string"));
                selected[0]=true;
            } else if(variable.name().equals("output")) {
                assertTrue(variable.constructorTarget().isPresent());
                selected[1]=true;
            }
        }});
        assertTrue(selected[0] && selected[1]);
    }
}
