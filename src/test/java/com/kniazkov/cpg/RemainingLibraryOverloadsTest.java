/* Independent C++03 overload-group contract; declarations do not imply effects. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.StandardLibraryCatalogue;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemainingLibraryOverloadsTest {
    private static SourceFile parse(String header) {
        String includes = StandardLibraryCatalogue.dependencies(header).stream().sorted()
            .map(name -> "#include <" + name + ">\n").reduce("", String::concat);
        Path path = Path.of("/__absent_remaining_library__/" + header + ".hpp");
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), path.getParent(),
            List.of(), List.of(), "c++03");
        return Parser.parseLibrary(unit, includes + StandardLibraryCatalogue.declarations(header, PlatformProfile.defaults())).build();
    }
    @Test void remainingCpp03FamiliesKeepExactOverloadGroups() throws Exception {
        var files = new HashMap<String, SourceFile>();
        var failures = new ArrayList<String>();
        try(var stream = getClass().getResourceAsStream("/library-catalogue/cpp03-remaining-overloads.tsv")) {
            assertNotNull(stream);
            for(String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                if(line.isBlank() || line.startsWith("#")) continue;
                String[] row=line.split("\\t"); var file=files.computeIfAbsent(row[0], RemainingLibraryOverloadsTest::parse);
                var scope=new ArrayList<Node>();
                for(Node item:file.items()) {
                    if(item instanceof NamespaceDeclaration ns) scope.addAll(ns.members()); else scope.add(item);
                }
                if(!row[1].equals("-")) {
                    String owner=row[1];
                    scope=new ArrayList<>(scope.stream().filter(ClassDeclaration.class::isInstance)
                        .map(ClassDeclaration.class::cast).filter(c->c.name().equals(owner)&&c.templateArguments().isEmpty()&&!c.members().isEmpty())
                        .findFirst().orElseThrow(()->new AssertionError(line)).members());
                }
                long count=scope.stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                    .filter(c->c.name().equals(row[2])).count();
                if(count!=Integer.parseInt(row[3]))failures.add(line+"; actual="+count);
            }
        }
        assertTrue(failures.isEmpty(),()->String.join("\n",failures));
    }
    @Test void callableTemplateIndexMatchesNamespaceDeclarationsWithoutAddingTypes() {
        var failures=new ArrayList<String>();
        for(String header:StandardLibraryCatalogue.headers()) {
            var expected=new TreeSet<String>();
            for(Node item:parse(header).items()) collectNamespaceTemplates(item,"",expected);
            var actual=new TreeSet<>(StandardLibraryCatalogue.callableTemplateNames(header));
            if(!expected.equals(actual)) failures.add(header+": expected="+expected+" actual="+actual);
        }
        assertTrue(failures.isEmpty(),()->String.join("\n",failures));
        assertFalse(StandardLibraryCatalogue.templateNames("memory").contains("std::get_temporary_buffer"));
        assertFalse(StandardLibraryCatalogue.typeNames("locale").contains("std::use_facet"));
    }
    /** Collect namespace members with their paths, without entering class scopes. */
    private static void collectNamespaceTemplates(Node node,String scope,Set<String> names) {
        if(node instanceof NamespaceDeclaration namespace) {
            String nested=scope.isEmpty()?namespace.name():scope+"::"+namespace.name();
            for(Node member:namespace.members())collectNamespaceTemplates(member,nested,names);
        } else if(node instanceof CallableDeclaration callable&&!callable.templateParameters().isEmpty()) {
            String name=scope.isEmpty()?callable.name():scope+"::"+callable.name();
            names.add(name);
            if(name.startsWith("std::"))names.add(name.substring(5));
        }
    }
    @Test void memberTemplateIndexMatchesDeclaredCallableIdentifiers() {
        var failures=new ArrayList<String>();
        for(String header:StandardLibraryCatalogue.headers()) {
            var expected=new TreeSet<String>();
            parse(header).walk(new NodeVisitor(){@Override public void visit(ClassDeclaration declaration){
                for(Node member:declaration.members())if(member instanceof CallableDeclaration callable
                    &&callable.kind()==CallableDeclaration.Kind.FUNCTION&&!callable.templateParameters().isEmpty())expected.add(callable.name());
            }});
            var actual=new TreeSet<>(StandardLibraryCatalogue.memberCallableTemplateNames(header));
            if(!expected.equals(actual))failures.add(header+": expected="+expected+" actual="+actual);
        }
        assertTrue(failures.isEmpty(),()->String.join("\n",failures));
    }
    @Test void explicitLibraryTemplateCallsAreCallsWithoutInventingFreeMemberNames() {
        var file=Parser.parse("calls.cpp", "#include <memory>\n#include <bitset>\n#include <locale>\nvoid f(std::bitset<8>& b,const std::locale& l){std::get_temporary_buffer<int>(2); b.to_string<char,std::char_traits<char>,std::allocator<char> >();std::has_facet<std::ctype<char> >(l);std::use_facet<std::ctype<char> >(l);} int to_string;int g(int x){return to_string < x;} struct S{int to_string;}; bool h(S s,int x){return s.to_string < x;}",Language.CPP).build();
        var calls=new ArrayList<CallExpression>();file.walk(new NodeVisitor(){@Override public void visit(CallExpression call){calls.add(call);}});
        assertEquals(4,calls.size());
    }
    @Test void numericLimitsPreserveUnknownTargetConstantsAndAllScalarSpecializations() {
        var classes=new ArrayList<ClassDeclaration>();parse("limits").walk(new NodeVisitor(){@Override public void visit(ClassDeclaration c){if(c.name().equals("numeric_limits"))classes.add(c);}});
        assertEquals(15,classes.size());
        for(var c:classes) {
            var constants=c.members().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast).toList();
            assertFalse(constants.isEmpty());
            for(var constant:constants)assertTrue(constant.initializer().isEmpty(),constant.name());
        }
    }
}
