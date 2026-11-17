/* C++03 iosfwd declarations and header boundaries. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IosfwdDeclarationTest {
    private Project resolve(String source, PlatformProfile profile) {
        Path path=Path.of("iosfwd.cpp").toAbsolutePath();
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit,source)).build());
    }
    @Test void forwardUsesAndLaterDefinitionsSurviveCopiesAndGeneration() {
        for(var profile:List.of(PlatformProfile.defaults(),PlatformProfile.builder().sizeType("unsigned int").build()))
            for(String character:List.of("char","wchar_t")) {
                String source="#include <iosfwd>\nstd::istreambuf_iterator<"+character+">* input;std::ostreambuf_iterator<"+character+">* output;std::char_traits<"+character+">* traits;\n";
                for(String suffix:List.of("","#include <iterator>\n#include <string>\nvoid f(std::ostreambuf_iterator<"+character+">& x){x.failed();}int g(){return std::char_traits<"+character+">::eq(0,0);}")) {
                    var p=resolve(source+suffix,profile);
                    if(suffix.isEmpty()) {
                        var referenced=new java.util.ArrayList<Declaration>();
                        p.files().getFirst().walk(new NodeVisitor(){@Override public void visit(NamedType type){
                            if(List.of("std::istreambuf_iterator","std::ostreambuf_iterator","std::char_traits").contains(type.name()))
                                referenced.add(type.declaration().orElseThrow());
                        }});
                        assertEquals(3,referenced.size());
                        assertTrue(referenced.stream().allMatch(d -> !d.isDefinition()));
                    }
                    for(var snapshot:List.of(p,NameResolver.resolve(p),NameResolver.resolve(p.edit().build()))) {
                        assertFalse(snapshot.hasErrors(),()->snapshot.semanticDiagnostics().toString());
                        snapshot.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){assertTrue(call.target().isPresent());}});
                        assertFalse(ProjectJson.encode(snapshot).toText("  ").isEmpty());
                        var regenerated=resolve(Cpg.sources(snapshot,Language.CPP).get("iosfwd.cpp"),profile);
                        assertFalse(regenerated.hasErrors(),()->regenerated.semanticDiagnostics().toString());
                    }
                }
            }
    }
    @Test void specializationForwardsDoNotHidePrimaryTemplateInOneScope() {
        var p=resolve("template<class T>struct Traits;template<>struct Traits<char>;template<>struct Traits<wchar_t>;template<class T>struct Traits{static int value();};template<>struct Traits<char>{static int value();};int f(){return Traits<int>::value()+Traits<char>::value();}",PlatformProfile.defaults());
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics().toString());
        p.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){assertTrue(call.target().isPresent());}});
    }
    @Test void iosfwdDoesNotExposeIteratorOrTraitsMembers() {
        for(String character:List.of("char","wchar_t")) {
            var p=resolve("#include <iosfwd>\nvoid f(std::ostreambuf_iterator<"+character+">& x){x.failed();}int g(){return std::char_traits<"+character+">::eq(0,0);}",PlatformProfile.defaults());
            var calls=new java.util.ArrayList<CallExpression>();
            p.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){calls.add(call);}});
            assertEquals(2,calls.size());assertTrue(calls.stream().allMatch(c->c.target().isEmpty()));
        }
    }
}
