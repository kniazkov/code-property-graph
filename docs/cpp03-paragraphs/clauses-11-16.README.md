# Clauses 11–16: source review and evidence limits

This shard reviews the supplied ISO/IEC 14882:2003 text, not a later working draft.
It contains original paraphrases and illustrative scenarios, not a reproduction of
the supplied PDF. The PDF has not been added or published by this work.

## Scope and status

- 621 numbered paragraphs, including 14.6.1/2a–2d.
- 43 grammar productions and 2 tables.
- 45 informative footnotes.
- 711 source-reviewed records total: 98 partial evidence mappings, 511 explicit
  implementation/test-evidence gaps, and 102 informative/not-applicable records.
- **Zero records claim fully verified implementation coverage.** Source review is
  not acceptance. The scenarios are usually prose, not new executable fixtures.

A paragraph can contain several requirements and exceptions. Its summary does not
establish a separately tested denominator for every bullet, grammar alternative,
or table combination. Supplemental grammar/table anchors remain open where no
exhaustive assertion mapping was established. Informative examples/notes do not
become additional normative obligations merely because they have paragraph numbers.

`diagnostic` describes the standard's diagnostic/behavior category for the scenario.
`not_applicable` on a normative runtime or selection rule means that no compile-time
error is prescribed for its valid example, **not** that the implementation may ignore
the rule. `evidence_status=gap` means evidence was not established here, not that all
implementations of the feature are proven absent. Component paths and inspected test
assertions support only the specified partial mapping. No existing JUnit test run
is claimed by this shard; the separate nested-access probe was executed as documented
in [probes/README.md](probes/README.md).

## Concrete findings

1. **11.8/1, incomplete private access enforcement.** The executed nested-access
   probe diagnoses private method access in both directions between enclosing and
   nested classes, as the printed 2003 text requires. It emits no diagnostics for
   either corresponding private-field access or the nested use of an enclosing
   private typedef. This is inconsistent enforcement, not proof of a deliberate
   later-defect-report profile.
2. **14/6–8, exported templates remain unsupported at semantic resolution.**
   `NameResolver` emits `unsupported-export-template` for exported callable
   declarations. Existing generation/reparse assertions prove preserved syntax,
   not separate exported-template translation.
3. **15.2/2, array cleanup is explicitly incomplete.**
   `partialArrayConstructionIsConservativelyIncomplete` expects incomplete flow
   and no control edges for a class-object array. This assertion documents a limit,
   rather than proving required partial-array cleanup.
4. **15.4/8, dynamic exception-specification coverage is partial.**
   `exceptionSpecificationsEnforceTheirBoundary` expects incomplete flow for an
   exception disallowed by `throw(int)`. General `unexpected` replacement behavior
   is not closed by its `throw()` boundary checks.
5. **UB is not a mandatory-diagnostic category.** Empty macro arguments
   (16.3/10), invalid paste results (16.3.3/3), out-of-range `#line` values
   (16.4/3), and predefined-name redefinition/removal (16.8/3) have UB in this
   edition. Existing rejection tests may implement useful policy without proving
   that ISO requires those diagnostics.
6. **An existing positive-looking template test uses IFNDR input.**
   `TemplateInstantiationSemanticsTest.fullSpecializationAndExplicitInstantiation`
   explicitly specializes and explicitly instantiates the same specialization.
   Printed 14.7/5 prohibits that combination without requiring a diagnostic. Its
   observed selection/count behavior must not serve as a portable positive
   conformance oracle.

The continuing informative blocks at 16.3.5/3–7, 14.8.2.4/5–8 and /13–14,
and 14.8.3/2–5 are classified accordingly. Seven extraction artifacts resembling
production headings were rejected; the genuine `operator: one of` production at
13.5 was included.

## Validation

The JSONL records were checked against the common JSON schema. Numbered IDs were
compared to the source inventory with no missing or extra IDs. Every referenced
implementation/test path exists, and each referenced JUnit method name was checked
against its source. `git diff --check` passed for the shard and probe. These are
artifact validations, not a full runtime or conformance test run.
