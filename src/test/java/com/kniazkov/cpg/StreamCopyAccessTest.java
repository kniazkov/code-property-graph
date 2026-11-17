/* C++03 private stream-base copying. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StreamCopyAccessTest {
    private Project resolve(String source,PlatformProfile profile) {
        Path path=Path.of("copy.cpp").toAbsolutePath();
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit,source)).build());
    }
    @Test void copyingAndAssignmentAreInaccessibleOnBothProfiles() {
        for(var profile:List.of(PlatformProfile.defaults(),PlatformProfile.builder().sizeType("unsigned int").build()))
            for(String type:List.of("ios_base","ios","wios"))
                for(String body:List.of("std::"+type+" copy(src);","dst=src;","dst.operator=(src);")) {
                    var p=resolve("#include <ios>\nvoid f(std::"+type+"& dst,const std::"+type+"& src){"+body+"}",profile);
                    for(var snapshot:List.of(p,NameResolver.resolve(p.edit().build()))) {
                        assertTrue(snapshot.hasErrors(),type+body);
                        assertTrue(snapshot.semanticDiagnostics().stream().anyMatch(d->d.code().startsWith("inaccessible-")),()->type+body+snapshot.semanticDiagnostics());
                        assertTrue(ProjectJson.encode(snapshot).toText("  ").contains("inaccessible-"));
                        assertThrows(IllegalArgumentException.class,()->Cpg.sources(snapshot,Language.CPP));
                    }
                }
    }
    @Test void copyfmtSurvivesEditingAndRegeneration() {
        var p=resolve("#include <sstream>\nvoid f(){std::ostringstream a,b;a.width(7);b.copyfmt(a);}",PlatformProfile.defaults());
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        var numbers=new ArrayList<IntegerLiteral>();
        p.files().getFirst().walk(new NodeVisitor(){@Override public void visit(IntegerLiteral value){numbers.add(value);}});
        var editor=new ProjectEditor(p);editor.replace(numbers.getFirst(),IntegerLiteral.create().setValue(8).build());
        var changed=NameResolver.resolve(editor.build());
        assertFalse(changed.hasErrors(),()->changed.semanticDiagnostics().toString());
        assertEquals(7,numbers.getFirst().value());
        var text=Cpg.sources(changed,Language.CPP).get("copy.cpp");
        assertFalse(resolve(text,PlatformProfile.defaults()).hasErrors());
    }
    @Test void catalogueContainsExactPrivateDeclarationsWithoutBodies() {
        var file=Parser.parseLibrary(new Build.TranslationUnit(new Build.PhysicalSource(Path.of("ios.hpp").toAbsolutePath()),Path.of(".").toAbsolutePath(),List.of(),List.of(),"c++03"),"#include <iosfwd>\n#include <exception>\n#include <string>\n"+StandardLibraryCatalogue.declarations("ios",PlatformProfile.defaults())).build();
        var owners=new ArrayList<ClassDeclaration>();
        file.walk(new NodeVisitor(){@Override public void visit(ClassDeclaration c){if(List.of("ios_base","basic_ios").contains(c.name()))owners.add(c);}});
        assertEquals(2,owners.size());
        for(var owner:owners) {
            var copies=owner.members().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                .filter(c->c.access().orElse("").equals("private")).toList();
            assertEquals(2,copies.size());
            for(var copy:copies){assertTrue(copy.body().isEmpty());assertEquals(1,copy.parameters().size());var ref=assertInstanceOf(ReferenceType.class,copy.parameters().getFirst().type().orElseThrow());var qualified=assertInstanceOf(QualifiedType.class,ref.referredType());assertTrue(qualified.isConst());assertFalse(qualified.isVolatile());assertEquals(owner.name(),assertInstanceOf(NamedType.class,qualified.baseType()).name());}
            var assignment=copies.stream().filter(c->c.name().equals("operator=")).findFirst().orElseThrow();
            assertEquals(owner.name()+"&",assignment.returnType().orElseThrow().source(Language.CPP).replace(" ",""));
            assertTrue(copies.stream().anyMatch(c->c.kind()==CallableDeclaration.Kind.CONSTRUCTOR));
        }
    }
}
