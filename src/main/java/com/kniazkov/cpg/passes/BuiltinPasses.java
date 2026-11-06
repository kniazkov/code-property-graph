/* Built-in IDs shared by custom dependencies and recorded public invocations. MIT License. */
package com.kniazkov.cpg.passes;

/** Built-ins registered by PassRunner. Their implementations retain their existing history
 * records; the runner never invents or duplicates a built-in completion. */
public final class BuiltinPasses {
    private BuiltinPasses() {}
    public static final PassId NAME_RESOLVER = new PassId("NameResolver");
    public static final PassId CONTROL_FLOW = new PassId("ControlFlow");
    public static final PassId DATA_FLOW = new PassId("DataFlow");
    public static final PassId UNREAD_WRITES = new PassId("UnreadWrites");
    public static final PassId DOCUMENTATION = new PassId("Documentation");
    public static final PassId CONSTANT_FOLDER = new PassId("ConstantFolder");
    public static final PassId CONSOLE_NORMALIZER = new PassId("ConsoleNormalizer");
    public static final PassId TYPE_NORMALIZER = new PassId("TypeNormalizer");
    public static final PassId STANDARD_CONVERSIONS = new PassId("StandardConversions");
    public static final PassId WRITTEN_CONVERSIONS = new PassId("StandardConversions.written");
    public static final PassId CLASS_TEMPLATE_SEMANTICS = new PassId("ClassTemplateSemantics");
    public static final PassId CLASS_TEMPLATE_DIAGNOSTICS = new PassId("ClassTemplateSemantics.diagnose");
    public static final PassId LIBRARY_DECLARATIONS = new PassId("LibraryDeclarations.install");
    public static final PassId DYNAMIC_ARRAY_SEMANTICS = new PassId("DynamicArraySemantics");
    public static final PassId CONTAINER_SEMANTICS = new PassId("ContainerSemantics");
    public static final PassId SEQUENCE_SEMANTICS = new PassId("SequenceSemantics");
}
