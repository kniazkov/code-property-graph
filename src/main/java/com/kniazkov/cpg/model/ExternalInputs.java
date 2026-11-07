/* Code Property Graph: external input ownership. MIT License. */
package com.kniazkov.cpg.model;

import com.kniazkov.cpg.build.Build;
import java.util.*;

/** Creates the non-syntax configuration tree and its stable provenance keys. */
final class ExternalInputs {
    private ExternalInputs() {}
    static String configurationKey(Build.TranslationUnit unit) {
        var source = unit.configurationSource();
        return source.kind().equals("database") ? "database:" + source.database() + ":entry:" + source.entryOrdinal()
                : source.kind() + ":" + unit.variantId();
    }
    static String operationKey(Build.TranslationUnit unit, int ordinal) {
        var operation = unit.macros().get(ordinal - 1);
        return configurationKey(unit) + ":" + (operation.sourceKey() == null ? "operation:" + ordinal : operation.sourceKey());
    }
    static List<ExternalInput.Builder> create(List<SourceFile> files, Build build) {
        var roots = new LinkedHashMap<String, ExternalInput.Builder>();
        var seen = new HashSet<String>();
        var units = new ArrayList<Build.TranslationUnit>();
        if (build != null) units.addAll(build.units());
        for (var file : files) file.provenance().map(TokenProvenance::unit).map(Preprocessing::configuration).ifPresent(units::add);
        for (var unit : units) {
            if (!roots.containsKey("profile:c++03")) {
                var profile = ExternalInput.create("profile:c++03", "language-profile", "C++03");
                for (String name : List.of("__cplusplus", "__FILE__", "__LINE__", "__DATE__", "__TIME__"))
                    profile.addInput(ExternalInput.create("profile:c++03:" + name, "predefined-macro",
                            name.equals("__cplusplus") ? "__cplusplus=199711L" : name));
                roots.put("profile:c++03", profile);
            }
            String platformKey = "profile:platform:" + unit.platform().identityHash();
            if (!roots.containsKey(platformKey)) {
                var platform = ExternalInput.create(platformKey, "platform-profile", unit.platform().stableIdentity());
                unit.platform().predefinedMacros().forEach((name, value) ->
                        platform.addInput(ExternalInput.create(platformKey + ":" + name, "predefined-macro", name + "=" + value)));
                roots.put(platformKey, platform);
            }
            var source = unit.configurationSource();
            String key = configurationKey(unit);
            if (!seen.add(key)) continue;
            ExternalInput.Builder entry;
            if (source.kind().equals("database")) {
                String databaseKey = "database:" + source.database();
                var database = roots.computeIfAbsent(databaseKey, ignored -> ExternalInput.create(databaseKey,
                        "compilation-database", source.database().toString()));
                entry = ExternalInput.create(key, "compilation-command", "entry " + source.entryOrdinal());
                database.addInput(entry);
            } else {
                entry = ExternalInput.create(key, source.kind(), source.kind().equals("cli") ? "Command-line configuration" : "Java API configuration");
                roots.put(key, entry);
            }
            if (!source.arguments().isEmpty()) entry.addInput(ExternalInput.create(key + ":argv", "arguments",
                    source.arguments().toString()));
            entry.addInput(ExternalInput.create(key + ":platform", "platform-selection", platformKey));
            if (unit.standard() != null) entry.addInput(ExternalInput.create(key + ":standard", "language-standard", unit.standard()));
            if (source.standardOverrideKind() != null) entry.addInput(ExternalInput.create(key + ":standard-override", source.standardOverrideKind(), unit.standard()));
            for (int i = 0; i < unit.macros().size(); i++) {
                var operation = unit.macros().get(i);
                String text = operation.originalArgument();
                if (text == null) text = (operation.define() ? "-D" : "-U") + operation.operand();
                var argument = ExternalInput.create(operationKey(unit, i + 1), operation.define() ? "macro-definition-argument" : "macro-undefinition-argument", text);
                if (operation.originalArguments().size() > 1)
                    argument.addInput(ExternalInput.create(operationKey(unit, i + 1) + ":operand", "argument-operand", operation.originalArguments().get(1)));
                entry.addInput(argument);
            }
        }
        var libraryKeys = new HashSet<String>();
        for (var file : files) file.provenance().ifPresent(provenance -> {
            for (var definition : provenance.unit().definitions()) if (definition.originKind().equals("library") && libraryKeys.add(definition.sourceKey())) {
                String platformKey = "profile:platform:" + provenance.unit().configuration().platform().identityHash();
                roots.get(platformKey).addInput(ExternalInput.create(definition.sourceKey(), "library-macro",
                        definition.name() + (definition.functionLike() ? "(" + String.join(",", definition.parameters()) + ")" : "")
                                + " " + (definition.unavailableReason() == null ? definition.rawReplacement() : "[unavailable: " + definition.unavailableReason() + "]")));
            }
        });
        return List.copyOf(roots.values());
    }
}
