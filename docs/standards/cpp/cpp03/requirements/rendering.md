# Rendering requirements and source indexes

The renderer implements #199. Run from the repository root:

```sh
python3 scripts/render-requirements.py
python3 scripts/render-requirements.py --check
python3 scripts/test-render-requirements.py
```

Edit authored JSONL, dispositions and the permanent ID ledger, then regenerate.
Do not edit generated Markdown or JSON. Run the separate history validator with
`--baseline-ref origin/documents` after fetching the integration branch: rendering
validates the current registry, not historical ID reuse.

The renderer uses the existing validator before writing any output. It reads
local catalogue inventories only; it does not download or extract standards,
interpret normative text, or run cited tests. Outputs use UTF-8 and LF bytes,
without timestamps or machine paths. Records, source keys and locator keys sort
lexically; source order is not a claim about numeric clause order. Authored list
order within conditions and exceptions is preserved.

[REQUIREMENTS.md](../REQUIREMENTS.md) retains all card fields. Explicit lowercase
requirement-ID anchors survive title changes. Draft, blocked, draft-only and
unresolved-source cards carry a provisional label. Reviewed readiness never
implies passed verification. Actual evidence remains distinct from proposed
checks; assertion details are preserved literally. The proof publication remains
owned by #200.

[SOURCE-INDEX.md](../SOURCE-INDEX.md) lists authored dispositions and counts
unprocessed locators by source/section. [source-index.json](source-index.json)
contains every catalogue locator. `dispositions` retain exact status, requirement
IDs, justification and question owner. `unprocessed` groups the remaining exact
locator lists by source and section; every entry has implicit unprocessed status. No disposition means unprocessed. A linked
paragraph may contain further obligations; neither a linked-locator count nor
zero unprocessed locators proves completeness. Existing research ownership
continues to apply.

`--check` compares exact bytes and exits unsuccessfully for missing or changed
outputs without rewriting them. Validation errors also stop generation. CI wiring
is deferred to #228; the temporary CI disable remains unchanged. The renderer
replaces only its three named outputs, and never mutates authored inputs.
