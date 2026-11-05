/* C++03 catalogue families remain declarations, not guessed implementations. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.StandardLibraryCatalogue;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class LibraryCatalogueFamiliesTest {
 @TempDir Path directory;
 private static String source(String header){return StandardLibraryCatalogue.declarations(header,PlatformProfile.defaults());}
 @Test void cLibraryIncludesPortableC90AndWideDeclarationsWithoutC99Additions(){
  for(var item:Map.of("cstdio",List.of("vfprintf(","fgetpos(","freopen(","tmpfile("),"cwchar",List.of("mbsrtowcs(","wcsrtombs(","vfwprintf(","wcstok("),"cstdlib",List.of("bsearch(","qsort(","atexit(","mbstowcs("),"ctime",List.of("strftime(","mktime(","difftime("),"csignal",List.of("signal(","raise(")).entrySet())for(var name:item.getValue())assertTrue(source(item.getKey()).contains(name),item.getKey()+":"+name);
  for(String h:List.of("cstdio","cstdlib","cwchar","cmath"))for(String later:List.of("snprintf(","strtoll(","wcstoll(","hypot("))assertFalse(source(h).contains(later),h+":"+later);
 }
 @Test void localeIncludesAllFacetFamiliesAndCharacterSpecialization(){
  for(String type:List.of("ctype","codecvt","num_get","num_put","numpunct","collate","time_get","time_put","moneypunct","money_get","money_put","messages"))assertTrue(StandardLibraryCatalogue.templateNames("locale").contains("std::"+type),type);
  String locale=source("locale");assertTrue(locale.contains("template<> class ctype<char>"));assertTrue(locale.contains("classic_table()"));assertTrue(locale.contains("do_date_order()"));assertTrue(locale.contains("do_unshift("));
 }
 @Test void bitmaskTypesDoNotPretendToBeIntegralAbiTypes(){
  for(String name:List.of("fmtflags","iostate","openmode"))assertTrue(source("ios").contains("__cpg_opaque_unspecified_"+name));
  assertTrue(source("locale").contains("__cpg_opaque_unspecified_ctype_mask"));
 }
 @Test void cCompatibilityHeadersResolveGlobalDeclarationsWithoutInventingStdImports()throws Exception{
  Files.writeString(directory.resolve("main.cpp"),"#include <stdio.h>\nint f(){return printf(\"x\");} int g(){return std::printf(\"y\");}");
  var p=Cpg.read(directory,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03"));
  var calls=new ArrayList<CallExpression>();p.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression call){calls.add(call);}});
  assertEquals(2,calls.size());assertEquals("printf",calls.getFirst().target().orElseThrow().name());assertTrue(calls.getLast().target().isEmpty());
 }
 @Test void demandedTimeTypeKeepsUnknownUnderlyingRepresentation()throws Exception{
  Files.writeString(directory.resolve("main.cpp"),"#include <ctime>\nstd::time_t now(){return std::time(0);}");
  var p=Cpg.read(directory,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03"));assertFalse(p.hasErrors(),()->p.semanticDiagnostics()+" / "+p.parseProblems());
  var opaque=new ArrayList<OpaqueLibraryType>();p.walk(new NodeVisitor(){@Override public void visit(OpaqueLibraryType type){opaque.add(type);}});
  assertTrue(opaque.stream().anyMatch(t->t.name().equals("time_t")&&t.category()==OpaqueLibraryType.Category.ARITHMETIC));
  assertTrue(opaque.stream().noneMatch(t->t.name().equals("clock_t")),"Unrequested companion declarations are pruned");
  for(var t:opaque){assertEquals(t,t.canonicalType());assertTrue(t.semanticType().isEmpty()||t.semanticType().orElseThrow().canonicalIdentity().contains("opaque"));}
 }
}
