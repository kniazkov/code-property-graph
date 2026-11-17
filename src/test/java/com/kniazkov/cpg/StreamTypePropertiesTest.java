/* Implementation-defined stream types retain known properties, not invented ABI. MIT License. */
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
class StreamTypePropertiesTest {
    private Project resolve(String source,PlatformProfile profile) {
        Path path=Path.of("types.cpp").toAbsolutePath();
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit,source)).build());
    }
    private static List<OpaqueLibraryType> types(Project p) {
        var result=new ArrayList<OpaqueLibraryType>();
        for(var file:p.librarySources())file.walk(new NodeVisitor(){@Override public void visit(OpaqueLibraryType t){result.add(t);}});
        return result;
    }
    @Test void categoriesSurviveCopiesPassesAndJsonWithoutInventingLayout() {
        for(var profile:List.of(PlatformProfile.defaults(),PlatformProfile.builder().sizeType("unsigned int").build())) {
            var p=resolve("#include <ios>\nstd::streamsize n;std::ios_base::seekdir direction;",profile);
            for(var snapshot:List.of(p,NameResolver.resolve(p.edit().build()),TypeNormalizer.green(p),ConstantFolder.fold(p))) {
                assertFalse(snapshot.hasErrors(),()->snapshot.semanticDiagnostics().toString());
                for(String name:List.of("streamsize","seekdir")) {
                    var t=types(snapshot).stream().filter(x->x.name().equals(name)).findFirst().orElseThrow();
                    assertEquals(name.equals("streamsize")?OpaqueLibraryType.Category.SIGNED_INTEGER:OpaqueLibraryType.Category.ENUMERATION,t.category());
                    assertEquals(Optional.of(name.equals("streamsize")),t.fundamental());
                    assertEquals(name.equals("streamsize")?Optional.of(true):Optional.empty(),t.signed());
                    assertTrue(TypeSemantics.describe(t,profile).sizeBytes().isEmpty());
                    assertTrue(TypeSemantics.describe(t,profile).alignmentBits().isEmpty());
                    assertTrue(TypeSemantics.describe(t,profile).integerRange().isEmpty());
                    assertTrue(TypeSemantics.promoted(t,profile).isEmpty());
                    assertEquals(t.category(),t.edit().build().category());
                }
                String json=ProjectJson.encode(snapshot).toText("  ");assertTrue(json.contains("SIGNED_INTEGER"));assertTrue(json.contains("ENUMERATION"));assertTrue(json.contains("\"fundamental\""));assertTrue(json.contains("\"signed\""));
                assertFalse(resolve(Cpg.sources(snapshot,Language.CPP).get("types.cpp"),profile).hasErrors());
            }
        }
    }
    @Test void enumConversionIsDirectionalAndNumericRankingStaysUnknown() {
        var profile=PlatformProfile.defaults();
        var good=resolve("#include <ios>\nvoid take(std::streamsize);void number(int);void direction(std::ios_base::seekdir);enum E{e};void f(std::ios_base::seekdir d){take(2);take(e);number(d);direction(std::ios_base::beg);}",profile);
        assertFalse(good.hasErrors(),()->good.semanticDiagnostics().toString());
        good.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression c){assertTrue(c.target().isPresent());}});
        var bad=resolve("#include <ios>\nvoid direction(std::ios_base::seekdir);void f(){direction(0);}",profile);
        assertTrue(bad.semanticDiagnostics().stream().anyMatch(d->d.code().equals("no-viable-overload")),()->bad.semanticDiagnostics().toString());
        var foreign=resolve("#include <ios>\nenum E{e};void take(E);void f(std::ios_base::seekdir d){take(d);}",profile);
        assertTrue(foreign.semanticDiagnostics().stream().anyMatch(d->d.code().equals("no-viable-overload")),()->foreign.semanticDiagnostics().toString());
        var unknown=resolve("#include <ios>\nvoid number(int);void number(long);void f(std::streamsize n){number(n);}",profile);
        assertTrue(unknown.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unresolved-overload")),()->unknown.semanticDiagnostics().toString());
    }
}
