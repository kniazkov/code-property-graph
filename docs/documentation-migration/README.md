# Documentation migration inventory

This is the frozen scope allocation for issue #187, under [checklist #186](https://github.com/kniazkov/code-property-graph/issues/186).
It inventories the documents at commit `479ef6fc16ee6dc0e16030bde94d6712ce217ab5`.
It does not translate documents or certify their claims. The English
[requirements plan](../standards/cpp/cpp03/REQUIREMENTS-PLAN.md) is already published.

## Language and branch policy

Technical documentation is English. Only the root introductory `README.md` and
`README.ru.md` will have equivalent English/Russian versions: capabilities,
download/clone instructions and getting started. Preserve intentional source
spelling and non-English fixture data; do not blindly replace Unicode text.

All task PRs target `documents`, one bounded task per PR. GitHub CI remains
disabled until final integration. Run relevant local checks, then restore CI by
reverting `03b933126ac4f6af2ac0c1f57d8a0a9691cd151a` and require a successful final
workflow and integration PR before merging to `master`.

## Files and interpretation

- `inventory.json` indexes generated metadata: batches and bounded JSONL collections
  for documents, line ranges, reference candidates and explicit exclusions. It stores hashes and
  field shapes, not copies of source prose.
- `owners.json` maps every migration batch to exactly one GitHub issue.
- [batches.md](batches.md) is the readable execution index.
- [M095.json](M095.json) maps the two source-card ranges migrated by #188 to
  their English destinations. Task migration records supplement the frozen
  census; they do not rewrite its paths or hashes. Merge progress lives in #186.
- [M001.json](M001.json) maps decisions 1–73 migrated by #189. The remaining
  source ranges belong to #233; the original file stays until consolidation.
- [M008.json](M008.json) maps the opening implementation-plan ranges migrated
  by #190, retaining historical states and linking the #238–#242 continuations.
- [M003.json](M003.json) maps the initial matrix rows and evidence migrated by
  #191. Deep links into unmigrated sections stay with the retained Russian source.
- [M014-M070-M071.json](M014-M070-M071.json) maps all three core-model documents
  migrated by #192; their old paths are retired and examples checked against source.

Every source line in every included document belongs to one range. Headings are
recorded by line number; ranges prefer heading/paragraph boundaries but may split
large sections between lines. Each range has a SHA-256, proposed destination,
action and one issue owner. English/Russian counterparts are matched by filename,
not assumed semantically equivalent. Substantive parity checking is future work.

`migrate` covers Russian files, mixed-language files, and their paired versions.
For JSON/JSONL, only ranges containing Cyrillic string values need translation;
all string-field shapes are inventoried, including unknown keys. Counts for these
batches include the changed values and paired `_en` values. IDs, keys, source
locators, source/paragraph hashes, tests, statuses and other machine values must
remain unchanged unless a separately reviewed schema change is necessary.
Language-like key names such as `_ru` are schema identifiers, not translated prose.

`retain_candidate` means no Russian migration was detected in that range, not
that its language or meaning has been reviewed. Issue #230 owns the final
mechanical language/path scan and allocates any substantive residual review to
bounded follow-ups. Issue #231 owns the root README. Their ranges do not count as
thousands of words of semantic research assigned to one PR.

All tracked Markdown, reStructuredText, AsciiDoc and text files, plus JSON/JSONL
under `docs/`, are included. This covers examples and fixture READMEs. Binary
files and code/configuration/fixture inputs are explicitly excluded from prose
migration; UTF-8 files are still scanned for inbound references. This inventory
does not translate source-code comments or change runtime localization.
The generated inventory, index, ownership map, this guide and its tooling are
new outputs of #187 and intentionally outside their own frozen input census.

## Reference and preservation rules

Reference candidates record source file and line, matched destination basename
and all possible destination paths. These are a conservative worklist, not a
link-resolution result: resolve relative paths, URL paths, anchors, duplicate
basenames and generator consumers before changing them. Scan the current tree
again when editing because this list is pinned to the baseline.

Keep the frozen input hashes as provenance. On moved/edited files, map the old
range to current headings and content before writing. Add an explicit destination
mapping in the task PR; do not silently change a range ID or use stale line numbers
as edit offsets. Never remove a source file while another batch still needs its
unmigrated content. The last batch for a file consolidates final paths and removes
obsolete copies only after content parity and inbound references are checked.
No Russian redirect stubs remain at completion.

The index groups related small files and limits each migration issue to 3,000
source review words. Generated batch targets are at most 2,800, leaving some room
for context. Text counts conservatively include code, tables and Markdown syntax;
structured counts distinguish changed prose from mechanically preserved fields.
Extra counterpart/context reading counts toward the limit too. New/revised prose
remains limited to 2,500 words. Create and link a bounded continuation before
exceeding either limit; update the ownership map and parent checklist together.

## Reproduction and checks

Run from the repository root:

```sh
python3 scripts/test-inventory-documentation.py
python3 scripts/inventory-documentation.py --check
```

Regenerate with `python3 scripts/inventory-documentation.py`. The baseline commit
must be available in local Git history; a shallow clone may need to fetch it.
No standards PDF, network access or Maven build is used. The checker compares
reconstructed metadata and validates complete batch ownership and issue budgets.
A future census update must be explicit; regenerating this baseline is not a
claim that newly added documents have been inventoried.
