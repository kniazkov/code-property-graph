/* Code Property Graph: deterministic JSON snapshot export. MIT License. */
package com.kniazkov.cpg.serialization;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.ClassSemantics;
import com.kniazkov.cpg.analysis.InheritanceSemantics;
import com.kniazkov.json.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Writes syntax ownership as nested fields and non-owning edges as destination IDs. */
public final class ProjectJson {
    private ProjectJson() {}

    /** Numbers syntax nodes in preorder before encoding any edge, including forward and cyclic references. */
    public static JsonObject encode(Project project) {
        return new Export(project).node(project);
    }

    /** Creates a UTF-8 file containing the complete snapshot. Existing files are protected. */
    public static void write(Project project, Path file) throws IOException {
        String text = encode(project).toText("  ") + "\n";
        Files.writeString(file, text, StandardOpenOption.CREATE_NEW);
    }

    private record Edge(String kind, int to) {
        int group() {
            return kind.equals("ref") ? 0 : kind.startsWith("cfg.") ? 1 : 2;
        }
    }

    private static final class Export {
        private final boolean compilationBuild;
        private final Project project;
        private final Map<FlowVertex,Integer> ids = new IdentityHashMap<>();
        private final Map<Preprocessing,Integer> units = new IdentityHashMap<>();
        private final Map<FlowVertex,List<Edge>> outgoing = new IdentityHashMap<>();

        Export(Project project) {
            this.project = project;
            compilationBuild = project.build().isPresent();
            // Configuration attribution can differ between API and CLI. Number executable
            // syntax and its flow operations first so their IDs do not depend on that catalogue.
            var index = new NodeVisitor() {
                @Override public void visit(Node n) { ids.put(n, ids.size()); }
            };
            index.visit(project);
            project.files().forEach(file -> file.walk(index));
            project.standardLibrary().ifPresent(library -> library.walk(index));
            project.templateInstances().forEach(instance -> instance.walk(index));
            project.classTemplateInstances().forEach(instance -> instance.walk(index));
            project.dynamicArrayTypes().forEach(type -> type.walk(index));
            project.dynamicArraySemantics().forEach(operation -> operation.walk(index));
            for (var flow : project.flows()) for (var vertex : flow.auxiliaryVertices()) ids.put(vertex, ids.size());
            project.externalInputs().forEach(input -> input.walk(index));
            for (SourceFile file : java.util.stream.Stream.concat(project.files().stream(), project.librarySources().stream()).toList()) file.provenance().ifPresent(p -> units.put(p.unit(), ids.get(file)));
            for (FlowVertex vertex : ids.keySet()) if (vertex instanceof Node n) for (Node target : n.references()) edge(n, target, "ref");
            for (FlowVertex vertex : ids.keySet()) if (vertex instanceof Node n)
                for (Comment comment : n.commentReferences()) edge(n, comment, "HAS_COMMENT");
            for (FlowVertex vertex : ids.keySet()) {
                if (vertex instanceof DynamicArrayType type) {
                    if (type.concreteClass() != null) edge(type, type.concreteClass(), "DYNAMIC_ARRAY_CLASS");
                    edge(type, type.elementType(), "ELEMENT_TYPE");
                    type.writtenTypes().forEach(site -> edge(site, type, "COMMON_TYPE"));
                }
                if (vertex instanceof DynamicArrayOperation operation) {
                    edge(operation.syntax(), operation, "COMMON_OPERATION");
                    edge(operation, operation.type(), "DYNAMIC_ARRAY_TYPE");
                    edge(operation, operation.destination(), "DESTINATION");
                    operation.sources().forEach(source -> edge(operation, source, "SOURCE"));
                    edge(operation, operation.selectedTarget(), "CPP_TARGET");
                    operation.elementCopyConstructor().ifPresent(target -> edge(operation, target, "ELEMENT_COPY_CONSTRUCTOR"));
                }
                if (vertex instanceof VariableDeclaration variable) {
                    variable.constructorTarget().ifPresent(target -> edge(variable, target, "CONSTRUCTOR_TARGET"));
                    variable.destructorTarget().ifPresent(target -> edge(variable, target, "DESTRUCTOR_TARGET"));
                }
                if (vertex instanceof IncludeDirective include) include.libraryHeader().ifPresent(header -> edge(include, header, "LIBRARY_HEADER"));
                if (vertex instanceof Declaration declaration) declaration.templateOrigin().ifPresent(origin -> edge(declaration, origin, "TEMPLATE_ORIGIN"));
            }
            for (FlowVertex vertex : ids.keySet()) if (vertex instanceof CallExpression call)
                call.target().ifPresent(target -> edge(call, target, "CALL_TARGET"));
            for (TemplateInstance instance : project.templateInstances()) for (Node request : instance.requests()) {
                edge(request, instance, "INSTANTIATION_CAUSE");
                edge(instance, request, "INSTANTIATION_REQUEST");
            }
            for (ClassTemplateInstance instance : project.classTemplateInstances()) for (Node request : instance.requests()) {
                edge(request, instance, "INSTANTIATION_CAUSE");
                edge(instance, request, "INSTANTIATION_REQUEST");
            }
            for (var flow : project.flows()) {
                for (var e : flow.control()) edge(e.from(), e.to(), "cfg." + lower(e.kind()));
                for (var e : flow.data()) edge(e.from(), e.to(), "dfg." + lower(e.kind()));
            }
        }

        private static String lower(Enum<?> kind) { return kind.name().toLowerCase(Locale.ROOT); }

        private void edge(FlowVertex from, FlowVertex to, String kind) {
            if (!ids.containsKey(from) || !ids.containsKey(to)) throw new IllegalStateException("Edge outside project");
            outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Edge(kind, ids.get(to)));
        }

        private JsonArray nodes(List<? extends Node> children) {
            var array = Json.array();
            children.forEach(child -> array.add(node(child)));
            return array.build();
        }

        private JsonObject node(Node n) {
            var obj = Json.object().put("id", ids.get(n)).put("kind", n.getClass().getSimpleName());
            switch (n) {
                case Project p -> {
                    obj.put("files", nodes(p.files()));
                    p.standardLibrary().ifPresent(library -> obj.put("standardLibrary", node(library)));
                    if (!p.librarySources().isEmpty()) obj.array("librarySources", a -> p.librarySources().forEach(header -> a.add(ids.get(header))));
                    if (!p.templateInstances().isEmpty()) obj.put("templateInstances", nodes(p.templateInstances()));
                    if (!p.classTemplateInstances().isEmpty()) obj.put("classTemplateInstances", nodes(p.classTemplateInstances()));
                    if (!p.dynamicArrayTypes().isEmpty()) obj.put("dynamicArrayTypes", nodes(p.dynamicArrayTypes()));
                    if (!p.dynamicArraySemantics().isEmpty()) obj.put("dynamicArraySemantics", nodes(p.dynamicArraySemantics()));
                    if (p.hasErrors()) {
                        obj.put("hasErrors", true);
                        obj.array("diagnosticGroups", a -> p.diagnosticGroups().forEach(g -> a.add(diagnosticGroup(g))));
                    }
                    if (!p.externalInputs().isEmpty()) obj.put("externalInputs", nodes(p.externalInputs()));
                    p.build().ifPresent(b -> obj.object("build", v -> {
                        v.array("units", a -> b.units().forEach(u -> a.add(configuration(u))));
                        p.composition().ifPresent(c -> v.object("composition", o -> o.put("name", c.name())
                                .array("variantIds", a -> a.addAll(c.variantIds()))));
                    }));
                }
                case DynamicArrayType type -> {
                    obj.put("elementTypeId", ids.get(type.elementType()))
                        .array("writtenTypeIds", a -> type.writtenTypes().forEach(site -> a.add(ids.get(site))));
                    if (type.concreteClass() != null) obj.put("concreteClassId", ids.get(type.concreteClass()));
                }
                case DynamicArrayOperation operation -> {
                    obj.put("operation", lower(operation.kind())).put("syntaxId", ids.get(operation.syntax()))
                        .put("destinationId", ids.get(operation.destination())).put("typeId", ids.get(operation.type()))
                        .put("selectedTargetId", ids.get(operation.selectedTarget()))
                        .array("sourceIds", a -> operation.sources().forEach(source -> a.add(ids.get(source))));
                    operation.elementCopyConstructor().ifPresent(target -> obj.put("elementCopyConstructorId", ids.get(target)));
                }
                case StandardLibrary library -> obj.put("headers", nodes(library.headers()));
                case ClassTemplateInstance instance -> {
                    obj.put("originId", ids.get(instance.origin())).put("selectedId", ids.get(instance.selected()))
                        .put("sourceId", ids.get(instance.source())).put("identity", instance.identity())
                        .array("requestIds", a -> instance.requests().forEach(request -> a.add(ids.get(request))))
                        .put("arguments", nodes(instance.arguments())).put("declaration", node(instance.declaration()));
                }
                case TemplateInstance instance -> {
                    obj.put("originId", ids.get(instance.origin()))
                        .array("requestIds", a -> instance.requests().forEach(request -> a.add(ids.get(request))))
                        .array("causeIds", a -> instance.causes().forEach(cause -> a.add(ids.get(cause))))
                        .put("sourceId", ids.get(instance.source())).put("status", instance.status()).put("complete", instance.complete())
                        .put("structuredArguments", nodes(instance.structuredArguments()))
                        .array("structuredArgumentIds", a -> instance.structuredArguments().forEach(argument -> a.add(ids.get(argument))))
                        .array("argumentIds", a -> instance.arguments().forEach(argument -> a.add(ids.get(argument))))
                        .put("declaration", node(instance.declaration()));
                    if (instance.cause() != null) obj.put("causeId", ids.get(instance.cause()));
                }
                case ExternalInput input -> obj.put("key", input.key()).put("role", input.role())
                        .put("text", input.text()).put("inputs", nodes(input.inputs()));
                case Comment comment -> {
                    obj.put("commentKind", comment.kind().name()).put("text", comment.text());
                    if (!comment.documentationEntries().isEmpty()) obj.put("documentationEntries", nodes(comment.documentationEntries()));
                }
                case DocumentationEntry entry -> {
                    obj.put("documentationKind", entry.kind().name()).put("name", entry.name()).put("description", entry.description());
                    entry.nameRange().ifPresent(r -> obj.put("nameRange", range(r)));
                    entry.tagRange().ifPresent(r -> obj.put("tagRange", range(r)));
                    obj.array("bindings", a -> entry.bindings().forEach(binding -> a.object(b -> {
                        b.put("declarationId", ids.get(binding.declaration()));
                        if (binding.target() != null) b.put("targetId", ids.get(binding.target()));
                        binding.declaration().provenance().ifPresent(p -> {
                            b.put("translationUnitId", units.get(p.unit()));
                            if (p.startToken() < p.unit().tokens().size()) b.put("includeId", p.unit().tokens().get(p.startToken()).includeId());
                        });
                    })));
                }
                case TemplateParameterDeclaration parameter -> {
                    obj.put("name", parameter.name()).put("keyword", parameter.keyword());
                    if (parameter.kind() != TemplateParameterDeclaration.Kind.TYPE) obj.put("parameterKind", parameter.kind().name());
                    parameter.type().ifPresent(type -> obj.put("type", node(type)));
                    parameter.defaultArgument().ifPresent(argument -> obj.put("defaultArgument", node(argument)));
                    if (!parameter.templateParameters().isEmpty()) obj.put("templateParameters", nodes(parameter.templateParameters()));
                }
                case TemplateArgument argument -> {
                    obj.put("argumentKind", argument.kind().name());
                    argument.type().ifPresent(type -> obj.put("type", node(type)));
                    argument.expression().ifPresent(expression -> obj.put("expression", node(expression)));
                }
                case SourceFile f -> {
                    if (project.librarySources().contains(f)) obj.put("libraryDeclaration", true);
                    obj.put("path", f.range().map(SourceRange::file)
                                .orElseGet(() -> Path.of(f.path()).toAbsolutePath().normalize().toString()))
                        .put("language", f.language().name()).put("items", nodes(f.items()));
                    if (!f.comments().isEmpty()) obj.put("comments", nodes(f.comments()));
                    if (f.hasErrors()) obj.put("hasErrors", true);
                    if (!f.parseProblems().isEmpty()) obj.array("parseProblems", a ->
                            f.parseProblems().forEach(problem -> a.add(problem(problem))));
                    f.stoppedAt().ifPresent(position -> obj.put("stoppedAt", range(position)));
                }
                case NamespaceDeclaration namespace -> obj.put("name", namespace.name()).put("anonymous", namespace.anonymous())
                        .put("members", nodes(namespace.members()));
                case NamespaceAliasDeclaration alias -> {
                    obj.put("name", alias.name()).put("targetName", alias.targetName());
                    alias.target().ifPresent(target -> obj.put("targetId", ids.get(target)));
                }
                case UsingDirective using -> {
                    obj.put("namespaceName", using.namespaceName());
                    using.target().ifPresent(target -> obj.put("targetId", ids.get(target)));
                }
                case UsingDeclaration using -> {
                    obj.put("qualifiedName", using.qualifiedName());
                    using.target().ifPresent(target -> obj.put("targetId", ids.get(target)));
                }
                case EnumDeclaration enumeration -> obj.put("name", enumeration.name()).put("enumerators", nodes(enumeration.enumerators()));
                case EnumeratorDeclaration value -> {
                    obj.put("name", value.name());
                    value.value().ifPresent(expression -> obj.put("value", node(expression)));
                }
                case ClassDeclaration c -> {
                    if (c.templateKind() != TemplateKind.NONE) obj.put("templateKind", c.templateKind().name());
                    if (c.exportedTemplate()) obj.put("exportedTemplate", true);
                    if (c.hasTemplateArguments()) obj.put("templateArguments", nodes(c.templateArguments()));
                    if (!c.templateParameters().isEmpty()) obj.put("templateParameters", nodes(c.templateParameters()));
                    if (c.isDefinition() && c.members().stream().anyMatch(member -> member.access().isPresent())) {
                        obj.put("aggregate", ClassSemantics.aggregate(c));
                        obj.array("specialMembers", values -> ClassSemantics.specialMembers(c).forEach(member -> values.add(Json.object()
                                .put("kind", member.kind().name()).put("origin", member.origin().name())
                                .array("declarationIds", declarations -> member.declarations().forEach(declaration -> declarations.add(ids.get(declaration)))).build())));
                    }
                    if (!c.bases().isEmpty()) obj.put("bases", nodes(c.bases()));
                    if (c.implicit()) obj.put("implicit", true);
                    obj.put("name", c.name()).array("modifiers", a -> a.addAll(c.modifiers())).put("members", nodes(c.members()));
                    if (!c.keyword().equals("class")) obj.put("keyword", c.keyword());
                    if (!c.memberAccess().equals("public")) obj.put("memberAccess", c.memberAccess());
                }
                case CallableDeclaration c -> {
                    if (c.templateKind() != TemplateKind.NONE) obj.put("templateKind", c.templateKind().name());
                    if (c.exportedTemplate()) obj.put("exportedTemplate", true);
                    if (c.hasTemplateArguments()) obj.put("templateArguments", nodes(c.templateArguments()));
                    project.flows().stream().filter(flow -> flow.entry() == c).findFirst().ifPresent(flow -> {
                        if (!flow.complete()) obj.put("flowComplete", false);
                        if (!flow.auxiliaryVertices().isEmpty()) obj.array("flowVertices", a -> flow.auxiliaryVertices().forEach(v -> a.add(auxiliary(v))));
                    });
                    if (!c.bodyComplete()) obj.put("bodyComplete", false);
                    if (c.hasExceptionSpecification()) obj.put("hasExceptionSpecification", true).put("exceptionTypes", nodes(c.exceptionTypes()));
                    if (c.functionTryBlock()) obj.put("functionTryBlock", true);
                    if (!c.handlers().isEmpty()) obj.put("handlers", nodes(c.handlers()));
                    if (c.kind() != CallableDeclaration.Kind.FUNCTION) obj.put("callableKind", c.kind().name());
                    if (!c.initializers().isEmpty()) obj.put("initializers", nodes(c.initializers()));
                    obj.put("name", c.name()).array("modifiers", a -> a.addAll(c.modifiers()));
                    c.returnType().ifPresent(t -> obj.put("returnType", node(t)));
                    c.conversionType().ifPresent(t -> obj.put("conversionType", node(t)));
                    c.writtenType().ifPresent(t -> obj.put("writtenType", node(t)));
                    obj.put("parameters", nodes(c.parameters()));
                    if (!c.templateParameters().isEmpty()) obj.put("templateParameters", nodes(c.templateParameters()));
                    c.body().ifPresent(b -> obj.put("body", node(b)));
                    obj.put("exit", node(c.exit()));
                    if (c.variadic()) obj.put("variadic", true);
                    if (c.isConst()) obj.put("const", true);
                    if (c.isVolatile()) obj.put("volatile", true);
                    if (c.isPureVirtual()) obj.put("pureVirtual", true);
                    if (InheritanceSemantics.isVirtual(project, c)) obj.put("virtual", true);
                    var overrides = InheritanceSemantics.overrides(project, c);
                    var overriddenBy = InheritanceSemantics.overriddenBy(project, c);
                    if (!overrides.isEmpty()) obj.array("overridesIds", a -> overrides.forEach(method -> a.add(ids.get(method))));
                    if (!overriddenBy.isEmpty()) obj.array("overriddenByIds", a -> overriddenBy.forEach(method -> a.add(ids.get(method))));
                }
                case BlockStatement b -> { obj.put("items", nodes(b.items())); if (!b.complete()) obj.put("complete", false); }
                case VariableDeclaration v -> {
                    v.constructorTarget().ifPresent(target -> obj.put("constructorTargetId", ids.get(target)));
                    v.destructorTarget().ifPresent(target -> obj.put("destructorTargetId", ids.get(target)));
                    v.bitWidth().ifPresent(value -> obj.put("bitWidth", node(value)));
                    obj.put("name", v.name());
                    if (!v.modifiers().isEmpty()) obj.array("modifiers", a -> a.addAll(v.modifiers()));
                    v.type().ifPresent(t -> obj.put("type", node(t)));
                    v.initializer().ifPresent(i -> obj.put("initializer", node(i)));
                }
                case BaseSpecifier b -> obj.put("access", b.access()).put("virtual", b.isVirtual()).put("type", node(b.type()));
                case InitializationExpression i -> {
                    obj.put("form", i.form().name()).put("arguments", nodes(i.arguments()));
                    i.type().ifPresent(type -> obj.put("type", node(type)));
                }
                case MemberInitializer i -> { obj.put("name", i.name()).put("arguments", nodes(i.arguments())); i.target().ifPresent(target -> obj.put("targetId", ids.get(target))); }
                case ParameterDeclaration p -> {
                    obj.put("name", p.name());
                    if (!p.modifiers().isEmpty()) obj.array("modifiers", a -> a.addAll(p.modifiers()));
                    p.type().ifPresent(t -> obj.put("type", node(t)));
                    p.defaultValue().ifPresent(value -> obj.put("defaultValue", node(value)));
                }
                case OpaqueLibraryType t -> obj.put("name", t.name()).put("identity", t.identity()).put("category", t.category().name());
                case NamedType t -> {
                    obj.put("name", t.name());
                    if (t.hasTemplateArguments()) obj.put("templateArguments", nodes(t.templateArguments()));
                    t.qualifier().ifPresent(qualifier -> obj.put("qualifier", node(qualifier)));
                    if (t.typenameKeyword()) obj.put("typenameKeyword", true);
                    if (t.templateKeyword()) obj.put("templateKeyword", true);
                    if (t.dependent()) obj.put("dependent", true);
                    t.elaboratedKeyword().ifPresent(keyword -> obj.put("elaboratedKeyword", keyword));
                    t.alias().ifPresent(a -> obj.put("aliasId", ids.get(a)).put("canonicalIdentity", t.canonicalIdentity()));
                    t.declaration().ifPresent(declaration -> obj.put("declarationId", ids.get(declaration))
                            .put("completeAtUse", t.completeAtUse()));
                    if (!t.resolutionSteps().isEmpty()) obj.array("resolutionSteps", steps -> t.resolutionSteps().forEach(step ->
                            steps.object(value -> value.put("kind", step.kind()).put("viaId", ids.get(step.via())).put("targetId", ids.get(step.target())))));
                }
                case TypedefDeclaration d -> obj.put("name", d.name()).put("type", node(d.type()));
                case QualifiedType t -> obj.put("baseType", node(t.baseType())).put("const", t.isConst()).put("volatile", t.isVolatile());
                case PointerType t -> obj.put("pointee", node(t.pointee()));
                case ReferenceType t -> obj.put("referredType", node(t.referredType()));
                case ArrayType t -> { obj.put("elementType", node(t.elementType())); t.bound().ifPresent(b -> obj.put("bound", node(b))); }
                case MemberPointerType t -> obj.put("ownerType", node(t.ownerType())).put("pointee", node(t.pointee()));
                case FunctionType t -> { obj.put("returnType", node(t.returnType())).put("parameters", nodes(t.parameters()))
                        .put("variadic", t.variadic()).put("const", t.isConst()).put("volatile", t.isVolatile());
                    if (t.hasExceptionSpecification()) obj.put("hasExceptionSpecification", true).put("exceptionTypes", nodes(t.exceptionTypes()));
                }
                case SizeofExpression e -> {
                    e.type().ifPresent(t -> obj.put("type", node(t)));
                    e.operand().ifPresent(operand -> obj.put("operand", node(operand)));
                }
                case TypeidExpression e -> {
                    e.type().ifPresent(t -> obj.put("type", node(t)));
                    e.operand().ifPresent(operand -> obj.put("operand", node(operand)));
                }
                case NewExpression e -> obj.put("type", node(e.type()))
                        .put("placementArguments", nodes(e.placementArguments()))
                        .put("initializerArguments", nodes(e.initializerArguments()))
                        .put("hasInitializer", e.hasInitializer()).put("global", e.isGlobal())
                        .put("parenthesizedType", e.isParenthesizedType());
                case DeleteExpression e -> obj.put("operand", node(e.operand()))
                        .put("array", e.isArray()).put("global", e.isGlobal());
                case NameExpression e -> {
                    obj.put("name", e.name());
                    if (e.hasTemplateArguments()) obj.put("templateArguments", nodes(e.templateArguments()));
                    if (e.templateKeyword()) obj.put("templateKeyword", true);
                }
                case IntegerLiteral i -> { obj.put("value", i.bigValue()); i.cppType().ifPresent(t -> obj.put("cppType", t));
                    i.spelling().ifPresent(spelling -> obj.put("spelling", spelling)); }
                case BooleanLiteral i -> obj.put("value", i.value()).put("spelling", i.spelling());
                case FloatingLiteral i -> obj.put("value", i.value()).put("cppType", i.cppType()).put("spelling", i.spelling());
                case CharacterLiteral i -> obj.put("value", i.value()).put("wide", i.wide()).put("cppType", i.cppType()).put("spelling", i.spelling())
                        .put("codeUnitBits", i.codeUnitBits()).put("valueBits", i.valueBits());
                case StringLiteral i -> obj.array("codeUnits", a -> i.codeUnits().forEach(a::add))
                        .put("wide", i.wide()).put("cppType", i.cppType()).put("spelling", i.spelling());
                case ConversionExpression c -> {
                    obj.put("operand", node(c.operand())).put("targetType", node(c.targetType()))
                            .put("explicit", c.isExplicit()).put("conversionKind", lower(c.kind()));
                    c.sourceType().ifPresent(type -> obj.put("sourceType", type));
                }
                case CompoundAssignmentExpression a -> obj.put("target", node(a.target())).put("value", node(a.value())).put("operator", a.operator());
                case ConditionalExpression c -> obj.put("condition", node(c.condition())).put("thenExpression", node(c.thenExpression()))
                        .put("elseExpression", node(c.elseExpression()));
                case ErrorExpression e -> obj.put("problem", problem(e.problem()));
                case IncludeDirective i -> {
                    obj.put("header", i.header()).put("system", i.system());
                    i.libraryHeader().ifPresent(header -> obj.put("libraryHeaderId", ids.get(header)));
                }
                case IfStatement i -> {
                    obj.put("condition", node(i.condition())).put("thenBranch", node(i.thenBranch()));
                    i.elseBranch().ifPresent(b -> obj.put("elseBranch", node(b)));
                }
                case WhileStatement w -> obj.put("condition", node(w.condition())).put("body", node(w.body()));
                case EmptyStatement ignored -> { }
                case BreakStatement ignored -> { }
                case ContinueStatement ignored -> { }
                case GotoStatement g -> obj.put("label", g.label());
                case LabelStatement l -> obj.put("label", l.label()).put("statement", node(l.statement()));
                case DoWhileStatement d -> obj.put("body", node(d.body())).put("condition", node(d.condition()));
                case ForStatement f -> {
                    obj.put("initializers", nodes(f.initializers())).put("body", node(f.body()));
                    f.condition().ifPresent(c -> obj.put("condition", node(c)));
                    f.update().ifPresent(u -> obj.put("update", node(u)));
                }
                case SwitchStatement s -> obj.put("condition", node(s.condition())).put("body", node(s.body()));
                case CaseStatement c -> { c.value().ifPresent(v -> obj.put("value", node(v))); obj.put("default", c.value().isEmpty()).put("statement", node(c.statement())); }
                case TryStatement t -> obj.put("body", node(t.body())).put("handlers", nodes(t.handlers()));
                case CatchClause c -> { c.parameter().ifPresent(p -> obj.put("parameter", node(p))); obj.put("catchAll", c.parameter().isEmpty()).put("body", node(c.body())); }
                case ThrowExpression t -> { obj.put("rethrow", t.value().isEmpty()); t.value().ifPresent(v -> obj.put("value", node(v))); }
                case ConditionDeclarationExpression c -> obj.put("declaration", node(c.declaration()));
                case ReturnStatement r -> r.value().ifPresent(v -> obj.put("value", node(v)));
                case PrintStatement p -> obj.put("value", node(p.value()));
                case ExpressionStatement e -> obj.put("expression", node(e.expression()));
                case AssignmentExpression a -> obj.put("target", node(a.target())).put("value", node(a.value()));
                case CallExpression c -> { obj.put("callee", node(c.callee())).put("arguments", nodes(c.arguments()))
                        .put("resolution", lower(c.resolution()))
                        .array("candidateIds", a -> c.candidates().forEach(candidate -> a.add(ids.get(candidate))));
                    c.target().ifPresent(target -> obj.put("staticTargetId", ids.get(target)));
                    if (c.isVirtualDispatch()) obj.put("virtualDispatch", true)
                            .put("virtualTargetsComplete", c.virtualTargetsComplete())
                            .array("virtualTargetIds", a -> c.virtualTargets().forEach(target -> a.add(ids.get(target))));
                    if (!c.defaultArguments().isEmpty()) obj.array("defaultArguments", a -> c.defaultArguments().forEach(argument -> a.object(value -> value
                                .put("parameterIndex", argument.parameterIndex()).put("declarationId", ids.get(argument.declaration()))
                                .put("parameterId", ids.get(argument.parameter())).put("expressionId", ids.get(argument.expression())))));
                }
                case MemberExpression m -> {
                    obj.put("receiver", node(m.receiver())).put("member", m.member());
                    if (m.hasTemplateArguments()) obj.put("templateArguments", nodes(m.templateArguments()));
                    if (m.templateKeyword()) obj.put("templateKeyword", true);
                    m.target().ifPresent(target -> obj.put("targetId", ids.get(target)));
                }
                case BinaryExpression b -> obj.put("left", node(b.left())).put("right", node(b.right()));
                case UnaryExpression u -> obj.put("operand", node(u.operand()));
                case Exit ignored -> { }
                default -> throw new IllegalStateException("Missing JSON schema for " + n.getClass().getName());
            }
            if (n instanceof Expression expression && expression.dependent()) obj.put("dependent", true);
            if (n instanceof Expression expression && (!expression.operatorCandidates().isEmpty()
                    || expression.operatorResolution() != CallExpression.Resolution.UNRESOLVED)) {
                obj.put("operatorResolution", lower(expression.operatorResolution()))
                        .array("operatorCandidateIds", a -> expression.operatorCandidates().forEach(candidate -> a.add(ids.get(candidate))));
                expression.operatorTarget().ifPresent(target -> obj.put("operatorTargetId", ids.get(target)));
            }
            if (n instanceof Expression expression) expression.semanticInfo().ifPresent(info -> obj.object("semanticInfo", value -> {
                info.resultType().ifPresent(type -> value.put("resultType", type));
                value.put("category", lower(info.category()));
            }));
            if (n instanceof Declaration declaration) {
                declaration.templateOrigin().ifPresent(origin -> obj.put("templateOriginId", ids.get(origin)));
                if (declaration.isFriend()) obj.put("friend", true);
                declaration.access().ifPresent(value -> obj.put("access", value));
                declaration.typeIdentity().ifPresent(identity -> obj.put("typeIdentity", identity));
                if (declaration.linkage() != Declaration.Linkage.NONE) obj.put("linkage", lower(declaration.linkage()));
                if (!declaration.languageLinkage().equals("C++")) obj.put("languageLinkage", declaration.languageLinkage());
                if (!declaration.isDefinition()) obj.put("isDefinition", false);
                declaration.previousDeclaration().ifPresent(previous -> obj.put("previousDeclarationId", ids.get(previous)));
                declaration.definition().ifPresent(definition -> obj.put("definitionId", ids.get(definition)));
            }
            if (n instanceof TypeNode type) {
                if (!(type instanceof NamedType)) obj.put("canonicalIdentity", type.canonicalIdentity());
                type.semanticType().ifPresent(facts -> obj.object("semanticType", value -> {
                    value.put("canonicalIdentity", facts.canonicalIdentity()).put("profileIdentity", facts.profileIdentity());
                    facts.sizeBits().ifPresent(bits -> value.put("sizeBits", bits));
                    facts.sizeBytes().ifPresent(bytes -> value.put("sizeBytes", bytes));
                    facts.alignmentBits().ifPresent(bits -> value.put("alignmentBits", bits));
                    facts.integerRange().ifPresent(range -> value.object("integerRange", limits -> limits.put("minimum", range.min()).put("maximum", range.max())));
                }));
            }
            if (compilationBuild && n instanceof SourceFile file)
                file.variantId().ifPresent(id -> obj.put("variantId", id));
            n.range().ifPresent(r -> obj.put("range", range(r)));
            n.provenance().ifPresent(p -> {
                Integer sourceFileId = units.get(p.unit());
                if (sourceFileId == null) throw new IllegalStateException("Provenance unit outside project");
                obj.object("provenance", v -> v.put("sourceFileId", sourceFileId)
                        .put("startToken", p.startToken()).put("endToken", p.endToken())
                        .array("expansionIds", a -> p.expansions().forEach(e -> a.add(e.id()))));
                if (n instanceof SourceFile) obj.put("preprocessing", preprocessing(p.unit()));
            });
            if (!n.diagnostics().isEmpty()) {
                obj.array("diagnostics", a -> n.diagnostics().forEach(d -> a.object(o -> {
                    o.put("code", d.code()).put("severity", lower(d.severity())).put("message", d.message());
                    if (d.primaryRange() != null) o.put("primaryRange", range(d.primaryRange()));
                    else n.range().ifPresent(r -> o.put("primaryRange", range(r)));
                    if (!d.relatedRanges().isEmpty()) o.array("relatedRanges", ranges -> d.relatedRanges().forEach(r ->
                            ranges.object(v -> v.put("role", r.role()).put("label", r.label()).put("range", range(r.range())))));
                })));

            }
            var links = outgoing.get(n);
            if (links != null) {
                links.sort(Comparator.comparingInt(Edge::group).thenComparing(Edge::kind).thenComparingInt(Edge::to));
                obj.array("edges", a -> links.forEach(e -> a.object(o -> o.put("kind", e.kind()).put("to", e.to()))));
            }
            return obj.build();
        }

        private JsonObject auxiliary(AuxiliaryFlowVertex vertex) {
            var obj = Json.object().put("id", ids.get(vertex)).put("kind", vertex.kind().name())
                    .put("ownerId", ids.get(vertex.owner())).put("index", vertex.index());
            vertex.object().ifPresent(object -> obj.put("objectId", ids.get(object)));
            vertex.destructor().ifPresent(destructor -> obj.put("destructorId", ids.get(destructor)));
            vertex.constructor().ifPresent(constructor -> obj.put("constructorId", ids.get(constructor)));
            var links = outgoing.get(vertex);
            if (links != null) {
                links.sort(Comparator.comparingInt(Edge::group).thenComparing(Edge::kind).thenComparingInt(Edge::to));
                obj.array("edges", a -> links.forEach(e -> a.object(o -> o.put("kind", e.kind()).put("to", e.to()))));
            }
            return obj.build();
        }

        private static JsonObject configuration(com.kniazkov.cpg.build.Build.TranslationUnit unit) {
            var obj = Json.object().put("variantId", unit.variantId()).put("source", unit.source().path().toString())
                    .put("directory", unit.directory().toString())
                    .array("includes", a -> unit.includes().forEach(p -> a.object(v ->
                            v.put("kind", p.kind()).put("path", p.path().toString()))))
                    .array("macros", a -> unit.macros().forEach(m -> a.object(v ->
                            v.put("define", m.define()).put("operand", m.operand())
                                    .put("originalArgument", m.originalArgument())
                                    .array("originalArguments", args -> args.addAll(m.originalArguments())))));
            obj.put("platform", settings(unit.platform().toSettings()));
            if (unit.standard() != null) obj.put("standard", unit.standard());
            var source = unit.configurationSource();
            obj.object("configurationSource", o -> {
                o.put("kind", source.kind()).array("arguments", a -> a.addAll(source.arguments()));
                if (source.database() != null) o.put("database", source.database().toString()).put("entryOrdinal", source.entryOrdinal());
                if (source.standardOverrideKind() != null) o.put("standardOverrideKind", source.standardOverrideKind());
            });
            return obj.build();
        }

        private static JsonObject settings(Map<?, ?> settings) {
            var object = Json.object();
            settings.forEach((key, value) -> {
                String name = key.toString();
                if (value == null) object.putNull(name);
                else if (value instanceof Map<?, ?> nested) object.put(name, settings(nested));
                else if (value instanceof String text) object.put(name, text);
                else if (value instanceof Boolean flag) object.put(name, flag);
                else if (value instanceof Number number) object.put(name, number);
                else throw new IllegalArgumentException("Unsupported platform setting " + name);
            });
            return object.build();
        }

        private static JsonObject range(SourceRange r) {
            return Json.object().put("file", r.file()).put("start", position(r.start()))
                    .put("end", position(r.end())).build();
        }

        private static JsonObject problem(ParseProblem p) {
            var obj = Json.object().put("code", p.code()).put("message", p.message())
                    .put("range", range(p.range())).put("skippedText", p.skippedText())
                    .put("recovered", p.recovered()).put("primaryRange", range(p.primaryRange()))
                    .put("reason", p.reason()).array("relatedRanges", a -> p.relatedRanges().forEach(r ->
                            a.object(o -> o.put("role", r.role()).put("label", r.label()).put("range", range(r.range())))));
            if (p.context() != null) obj.put("context", diagnosticContext(p.context()));
            return obj.build();
        }

        private static JsonObject diagnosticGroup(DiagnosticGroup group) {
            return Json.object().put("code", group.code()).put("primaryRange", range(group.primaryRange()))
                    .put("reason", group.reason()).put("message", group.message())
                    .put("translationUnitCount", group.translationUnitCount()).put("variantCount", group.variantCount())
                    .put("occurrenceCount", group.occurrenceCount())
                    .array("occurrences", a -> group.occurrences().forEach(p -> a.add(problem(p)))).build();
        }

        private static JsonObject diagnosticContext(DiagnosticContext context) {
            var obj = Json.object().put("translationUnit", context.translationUnit());
            if (context.variantId() != null) obj.put("variantId", context.variantId());
            return obj.array("includeChain", a -> context.includeChain().forEach(i -> a.object(o -> {
                        o.put("id", i.id()).put("file", i.file());
                        if (i.parentId() != null) o.put("parentId", i.parentId());
                    })))
                    .array("expansionChain", a -> context.expansionChain().forEach(e -> a.object(o -> {
                        o.put("id", e.id()).put("name", e.name()).put("definition", range(e.definition()))
                                .array("replacement", fs -> e.replacement().forEach(f -> fs.add(fragment(f))))
                                .put("invocation", range(e.invocation())).put("includeId", e.includeId())
                                .put("startToken", e.startToken()).put("endToken", e.endToken())
                                .put("definitionId", e.definitionId());
                        if (e.parentId() != null) o.put("parentId", e.parentId());
                        o.array("arguments", args -> e.arguments().forEach(arg -> {
                            var fragments = Json.array();
                            arg.forEach(f -> fragments.add(fragment(f)));
                            args.add(fragments.build());
                        }));
                        o.array("operations", ops -> e.operations().forEach(op -> ops.object(v -> v
                                .put("kind", op.kind()).put("operator", range(op.operator())).put("spelling", op.spelling())
                                .array("fragments", fs -> op.fragments().forEach(f -> fs.add(fragment(f)))))));
                    }))).build();
        }

        private static JsonObject fragment(Preprocessing.Fragment f) {
            return Json.object().put("range", range(f.range())).put("includeId", f.includeId())
                    .put("spelling", f.spelling()).build();
        }

        private JsonObject preprocessing(Preprocessing p) {
            return Json.object().put("translationUnit", p.translationUnit())
                    .array("includeStates", a -> p.includeStates().forEach(s -> a.object(o -> o.put("includeId", s.includeId())
                            .object("activeDefinitions", values -> s.activeDefinitions().forEach(values::put)))))
                    .array("definitions", a -> p.definitions().forEach(d -> a.object(o -> {
                        o.put("id", d.id()).put("name", d.name()).put("functionLike", d.functionLike())
                                .array("parameters", values -> values.addAll(d.parameters()))
                                .put("rawReplacement", d.rawReplacement()).put("range", range(d.range()))
                                .put("includeId", d.includeId()).put("originKind", d.originKind()).put("sourceKey", d.sourceKey());
                        project.externalInput(d.sourceKey()).ifPresent(input -> o.put("sourceNodeId", ids.get(input)));
                        o.array("expansionIds", values -> p.expansions().stream().filter(e -> e.definitionId() == d.id()).forEach(e -> values.add(e.id())));
                        o.array("presenceCheckIds", values -> p.presenceChecks().stream().filter(c -> c.definitionId() != null && c.definitionId() == d.id()).forEach(c -> values.add(c.id())));
                    })))
                    .array("branches", a -> p.branches().forEach(b -> a.object(o -> {
                        o.put("id", b.id()).put("directive", b.directive()).put("rawSource", b.rawSource())
                                .put("range", range(b.range())).put("directiveRange", range(b.directiveRange()))
                                .put("includeId", b.includeId()).put("parentActive", b.parentActive())
                                .put("evaluated", b.evaluated()).put("conditionResult", b.conditionResult()).put("active", b.active())
                                .put("startToken", b.startToken()).put("endToken", b.endToken());
                        if (p.configuration() != null) o.put("variantId", p.configuration().variantId());
                        if (b.parentId() != null) o.put("parentId", b.parentId());
                    })))
                    .array("presenceChecks", a -> p.presenceChecks().forEach(c -> a.object(o -> {
                        o.put("id", c.id()).put("name", c.name()).put("range", range(c.range()))
                                .put("includeId", c.includeId()).put("present", c.present())
                                .put("conditionResult", c.conditionResult()).put("branchId", c.branchId());
                        if (c.definitionId() != null) o.put("definitionId", c.definitionId());
                    })))
                    .array("directives", a -> p.directives().forEach(d -> a.object(o -> o.put("kind", d.kind())
                            .put("rawSource", d.rawSource()).put("range", range(d.range()))
                            .put("includeId", d.includeId()).put("active", d.active()))))
                    .array("logicalLocations", a -> p.logicalLocations().forEach(l -> a.object(o -> o.put("range", range(l.range()))
                            .put("includeId", l.includeId()).put("file", l.file()).put("line", l.line()))))
                    .array("includes", a -> p.includes().forEach(i -> a.object(o -> {
                        o.put("id", i.id()).put("file", i.file());
                        if (i.parentId() != null) o.put("parentId", i.parentId());
                    })))
                    .array("tokens", a -> p.tokens().forEach(t -> a.object(o -> o
                            .put("index", t.index()).put("spelling", t.spelling())
                            .put("spellingRange", range(t.spellingRange()))
                            .put("expansionRange", range(t.expansionRange())).put("includeId", t.includeId())
                            .array("expansionIds", ids -> t.expansionIds().forEach(ids::add)))))
                    .array("expansions", a -> p.expansions().forEach(e -> a.object(o -> {
                        o.put("id", e.id()).put("name", e.name()).put("definition", range(e.definition()))
                                .array("replacement", fs -> e.replacement().forEach(f -> fs.add(fragment(f))))
                                .put("invocation", range(e.invocation()))
                                .put("includeId", e.includeId()).put("startToken", e.startToken())
                                .put("endToken", e.endToken()).put("definitionId", e.definitionId());
                        if (e.parentId() != null) o.put("parentId", e.parentId());
                        o.array("arguments", args -> e.arguments().forEach(arg -> {
                            var fragments = Json.array();
                            arg.forEach(f -> fragments.add(fragment(f)));
                            args.add(fragments.build());
                        }));
                        o.array("operations", ops -> e.operations().forEach(op -> ops.object(v -> v
                                .put("kind", op.kind()).put("operator", range(op.operator()))
                                .put("spelling", op.spelling())
                                .array("fragments", fs -> op.fragments().forEach(f -> fs.add(fragment(f)))))));
                    }))).build();
        }

        private static JsonObject position(SourcePosition p) {
            return Json.object().put("line", p.line()).put("column", p.column()).put("offset", p.offset()).build();
        }
    }
}
