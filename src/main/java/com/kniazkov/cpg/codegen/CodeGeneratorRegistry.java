package com.kniazkov.cpg.codegen;

import com.kniazkov.cpg.ast.Node;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A registry of language-specific {@link CodeGenerator} instances.
 *
 * <p>Language identifiers are case-insensitive. Registration order is preserved
 * for diagnostics and iteration.</p>
 */
public final class CodeGeneratorRegistry {

    private final Map<String, CodeGenerator> generators = new LinkedHashMap<>();

    /**
     * Registers a generator.
     *
     * @param generator the generator to register
     * @return this registry
     * @throws NullPointerException if {@code generator} is {@code null}
     * @throws IllegalArgumentException if a generator for the same language is
     *         already registered
     */
    public CodeGeneratorRegistry register(CodeGenerator generator) {
        Objects.requireNonNull(generator, "generator");
        String key = normalize(generator.language());
        if (generators.putIfAbsent(key, generator) != null) {
            throw new IllegalArgumentException("generator already registered: " + key);
        }
        return this;
    }

    /**
     * Finds a generator for the given language.
     *
     * @param language the language identifier
     * @return the generator, or an empty optional if none is registered
     * @throws NullPointerException if {@code language} is {@code null}
     */
    public Optional<CodeGenerator> find(String language) {
        return Optional.ofNullable(generators.get(normalize(language)));
    }

    /**
     * Returns a generator for the given language.
     *
     * @param language the language identifier
     * @return the generator
     * @throws NullPointerException if {@code language} is {@code null}
     * @throws IllegalArgumentException if no generator is registered
     */
    public CodeGenerator require(String language) {
        return find(language)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no generator registered for language: " + language));
    }

    /**
     * Generates source code with the generator registered for the language.
     *
     * @param language the language identifier
     * @param root the root of the tree or subtree
     * @return the generated source code
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if no generator is registered
     */
    public String generate(String language, Node root) {
        return require(language).generate(root);
    }

    private static String normalize(String language) {
        return Objects.requireNonNull(language, "language").toLowerCase(Locale.ROOT);
    }
}
