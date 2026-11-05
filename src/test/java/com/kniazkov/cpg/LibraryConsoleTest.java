/* Console normalization recognizes only actual library declarations. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class LibraryConsoleTest {
 @TempDir Path directory;
 private Project read(String source)throws Exception{Files.writeString(directory.resolve("main.cpp"),source);return Cpg.read(directory,new Cpg.Options(Language.CPP,Cpg.Stage.GREEN,"c++03"));}
 private static long prints(Project p){var out=new ArrayList<PrintStatement>();p.files().forEach(f->f.walk(new NodeVisitor(){@Override public void visit(PrintStatement n){out.add(n);}}));return out.size();}
 @Test void catalogueBoundCoutAndEndlNormalize()throws Exception{var p=read("#include <iostream>\nint main(){std::cout << 3 << std::endl;return 0;}");assertEquals(1,prints(p));}
 @Test void userNamespaceWithSameSpellingDoesNotNormalize()throws Exception{var p=read("namespace std { int cout; int endl; } int main(){std::cout << 3 << std::endl;return 0;}");assertEquals(0,prints(p));}
}
