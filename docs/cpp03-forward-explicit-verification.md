# Explicit instantiation after a forward declaration (#104)

The explicit-instantiation path previously selected a forward declaration,
found no body and stopped. Ordinary calls already looked up a matching
function-template definition. Explicit requests now perform that definition
lookup using same translation unit, entity name, template kind and structural
signature (including template parameter identities and return type).
Unrelated overloads and namespace entities cannot supply the body.

The instance retains its generic origin and request identity. When the body
comes from another declaration, substitutions are recomputed against that
definition's parameter nodes. This supports renamed template parameters and
preserves the definition's source/typeinfo context. Existing full-specialization
handling is unchanged; IFNDR combinations are not introduced as native positives.

Original C++03 locators: §14.5.5.1 and §14.7.2/1–4, as recorded in issue104
and the existing paragraph ledger. No fresh original PDF review or claim of
complete paragraph conformance is made. Immutable classifications are unchanged.

ForwardExplicitInstantiationTest covers no-call explicit requests, a control
without forward declarations, repeated declarations, renamed parameters,
namespace/qualified forms, overload isolation, calls sharing one instance,
missing/foreign definitions and missing/late typeinfo. It also checks repeated
resolution, copies, GREEN/FOLDED, JSON, generated/reparsed sources and edits to
the original definition rebuilding the instance without changing the old graph.
ForwardExplicitInstantiationIT covers CLI positive/negative cases and executable
original/generated C++03 with GCC and Clang. The durable fixture is
src/test/resources/forward-explicit/main.cpp. Compiler commands/output and both
sources are uploaded under target/forward-explicit-oracle.

```sh
mvn -DargLine=-Xmx768m -Dtest=ForwardExplicitInstantiationTest,TemplateInstantiationSemanticsTest -Dit.test=ForwardExplicitInstantiationIT verify
```

Full GitHub CI on the final revision remains mandatory. Local focused runs are
not full verification; local Clang is unavailable and its execution is left to
CI. No existing compiler tests are skipped in the workflow.
