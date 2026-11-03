/* Pretty-printing for the prototype's supported nodes. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.Graph.*;
import java.util.stream.Collectors;

final class Source {
    static String render(Node node, Language language) {
        if (!node.languages().contains(language)) throw new IllegalArgumentException("Node cannot be generated in " + language);
        return emit(node, language, 0);
    }
    private static String emit(Node n, Language l, int level) {
        String indent = "    ".repeat(level);
        var c = n.children();
        return switch(n.kind()) {
            case PROJECT -> throw new IllegalArgumentException("Generate individual files, not one concatenated project");
            case FILE -> c.stream().map(x -> emit(x, l, level)).collect(Collectors.joining("\n"));
            case INCLUDE -> n.text() + "\n";
            case CLASS -> {
                String name = n.text().replaceFirst("^public ", "");
                String head = l == Language.JAVA ? (n.text().startsWith("public ") ? "public " : "") + "class " + name : "class " + name;
                yield indent + head + " {\n" + (l == Language.CPP ? indent + "public:\n" : "")
                    + c.stream().map(x -> emit(x, l, level + 1)).collect(Collectors.joining("\n"))
                    + indent + "}" + (l == Language.CPP ? ";" : "") + "\n";
            }
            case METHOD -> indent + n.text() + " " + emit(c.get(0), l, level);
            case BLOCK -> "{\n" + c.stream().map(x -> emit(x, l, level + 1)).collect(Collectors.joining()) + indent + "}\n";
            case DECLARE -> indent + "int " + n.text() + " = " + emit(c.get(0), l, 0) + ";\n";
            case ASSIGN -> indent + emit(c.get(0), l, 0) + " = " + emit(c.get(1), l, 0) + ";\n";
            case RETURN -> indent + "return " + emit(c.get(0), l, 0) + ";\n";
            case EXPRESSION -> indent + emit(c.get(0), l, 0) + ";\n";
            case INTEGER, NAME -> n.text();
            case MEMBER -> emit(c.get(0), l, 0) + n.text();
            case CALL -> emit(c.get(0), l, 0) + "(" + c.subList(1, c.size()).stream().map(x -> emit(x, l, 0)).collect(Collectors.joining(", ")) + ")";
            case BINARY -> "(" + emit(c.get(0), l, 0) + " " + n.text() + " " + emit(c.get(1), l, 0) + ")";
            case UNARY -> "(" + n.text() + emit(c.get(0), l, 0) + ")";
            case PRINT -> indent + (l == Language.JAVA ? "System.out.println(" + emit(c.get(0), l, 0) + ");\n"
                : "std::cout << " + emit(c.get(0), l, 0) + " << std::endl;\n");
        };
    }
}
