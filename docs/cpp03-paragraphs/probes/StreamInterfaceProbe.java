/* Read-only checks against the printed C++03 stream interfaces. */
import com.kniazkov.cpg.analysis.StandardLibraryCatalogue;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;

public final class StreamInterfaceProbe {
    public static void main(String[] args) {
        var profile = PlatformProfile.defaults();
        String forward = StandardLibraryCatalogue.declarations("iosfwd", profile);
        String base = StandardLibraryCatalogue.declarations("ios", profile);
        String file = StandardLibraryCatalogue.declarations("fstream", profile);
        System.out.println("iosfwd-istreambuf-iterator: " + forward.contains("class istreambuf_iterator"));
        System.out.println("iosfwd-ostreambuf-iterator: " + forward.contains("class ostreambuf_iterator"));
        System.out.println("ios-base-copy-declaration: " + base.contains("ios_base(const ios_base&"));
        System.out.println("basic-ios-copy-declaration: " + base.contains("basic_ios(const basic_ios&"));
        System.out.println("file-is-open-const-declarations: " + count(file, "bool is_open() const;"));
        resolve("copy-ios-base", "#include <ios>\nvoid f(std::ios_base& src){std::ios_base copy(src);}");
        resolve("copy-ios", "#include <ios>\nvoid f(std::ios& src){std::ios copy(src);}");
    }
    private static int count(String text, String needle) {
        int result = 0, offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) { result++; offset += needle.length(); }
        return result;
    }
    private static void resolve(String label, String code) {
        try {
            var project = NameResolver.resolve(Project.create()
                .addFile(Parser.parse(label + ".cpp", code, Language.CPP)).build());
            System.out.println(label + ": " + project.semanticDiagnostics().stream().map(d -> d.code()).toList());
        } catch (Exception failure) {
            System.out.println(label + ": probe could not resolve: " + failure.getClass().getSimpleName());
        }
    }
}
