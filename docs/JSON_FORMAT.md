# JSON format for AST/CPG

Version: 2  
Date: 2026-11-25  
Status: draft

## 1. Purpose

This document describes the JSON representation of a syntax tree and of the
code property graph (CPG) built on top of it. The tree is exported as an array
of nodes, and semantic relations (declarations, calls, types, scopes,
inclusions) are exported as a separate `relations` object.

The exporter is `com.kniazkov.cpg.utils.JsonExporter`:

```java
JsonObject json = JsonExporter.toJson(graph);
String text = JsonExporter.toJsonString(graph);
```

The `toJson(Node)` and `toJsonString(Node)` overloads export the AST only,
without relations. Relations live in the immutable
`com.kniazkov.cpg.graph.Graph` class.

## 2. Top level

The root JSON object has five fields:

```json
{
  "format": "cpp03-cpg",
  "version": 2,
  "root": 0,
  "nodes": [],
  "relations": {
    "declarations": [],
    "calls": [],
    "types": [],
    "members": [],
    "scopes": [],
    "includes": []
  }
}
```

- `format` — string format identifier, always `cpp03-cpg`.
- `version` — integer format version; currently `2`.
- `root` — identifier of the root node; always `0`.
- `nodes` — array of node objects.
- `relations` — object of relations, one array per relation type.

## 3. Node

Each element of the `nodes` array describes one node:

```json
{
  "id": 0,
  "type": "TranslationUnit",
  "tags": ["declarations"],
  "fragment": {
    "start": { "offset": 0, "line": 1, "column": 1 },
    "end": { "offset": 120, "line": 5, "column": 1 },
    "code": "..."
  },
  "children": [1]
}
```

Fields:

- `id` — integer unique within one JSON generation.
- `type` — string node type name returned by `Node.name()`. By default this is
  the Java simple name of the node class; for an anonymous class the fully
  qualified name is used. A node may override `name()` to provide a stable
  language-level name.
- `tags` — array of child tags. Tags are unique within a node and correspond to
  `children` by index. A list node has an empty array.
- `fragment` — source fragment covered by the node, or `null`.
- `children` — array of child AST node identifiers in source order.

Invariants:

- for a regular node, `tags.size() == children.size()`;
- for a list node, `tags` is empty and `children` are list elements;
- the same tag cannot appear twice in `tags`;
- a list of same-type children is represented by one tagged node, for example
  `arguments`, not by several children with the same tag.

## 4. Fragment

```json
{
  "start": { "offset": 0, "line": 1, "column": 1 },
  "end": { "offset": 10, "line": 1, "column": 11 },
  "code": "int main()"
}
```

- `start` — inclusive start position.
- `end` — exclusive end position.
- `code` — `Source` text for the half-open interval `[start.offset, end.offset)`.

Position:

```json
{
  "offset": 0,
  "line": 1,
  "column": 1
}
```

- `offset` — absolute character offset, starting at zero.
- `line` — line number, starting at one.
- `column` — column number, starting at one.

## 5. Relations

The `relations` object contains one array per relation type. Each array element
is a directed edge `from → to`:

```json
"relations": {
  "declarations": [
    { "from": 7, "to": 2 }
  ],
  "calls": [
    { "from": 9, "to": 4 }
  ],
  "types": [],
  "members": [],
  "scopes": [],
  "includes": []
}
```

Edge fields:

- `from` — identifier of the source node of the relation;
- `to` — identifier of the target node of the relation.

Relation types (keys of the `relations` object):

- `declarations` — a node that uses an entity → the node that declares it;
- `calls` — a call node → the called entity;
- `types` — a node → its type;
- `members` — a node → its containing scope;
- `scopes` — a node → its lexical scope;
- `includes` — a node → the included unit.

Rules:

- each array contains only edges of its own type;
- relations are not stored in the `nodes` array, so the tree stays immutable;
- one source may have several targets; in that case one edge is emitted per
  target;
- an empty relation type is emitted as an empty array to keep the schema stable.

## 6. List nodes

Several same-type children are grouped into one list node. For example, call
arguments:

```json
{
  "id": 3,
  "type": "ExpressionList",
  "tags": [],
  "fragment": {
    "start": { "offset": 4, "line": 1, "column": 5 },
    "end": { "offset": 9, "line": 1, "column": 10 },
    "code": "x, y"
  },
  "children": [4, 5]
}
```

The parent node refers to it by tag:

```json
{
  "id": 0,
  "type": "CallExpression",
  "tags": ["callee", "arguments"],
  "children": [1, 3]
}
```

Rules:

- a list node is a regular node and receives its own `id`;
- `tags` of a list node is always empty;
- list elements are not tagged from the list node side;
- an empty list is a list node with empty `children`;
- an empty list uses a zero-length fragment at the insertion position;
- concrete types: `ExpressionList`, `StatementList`, `ParameterList`.

## 7. Identifier rules

- The root node of the exported tree or subtree always receives `id = 0`.
- Identifiers are unique within one JSON generation.
- Identifiers increase monotonically in traversal order.
- Traversal is deterministic: child nodes in `children` order first, then
  relation targets in a fixed relation-type order.
- An identifier is assigned on the first visit; a repeated visit does not change
  it.
- Identification uses object identity, not `equals`; two structurally equal but
  distinct nodes receive different identifiers.
- Identifiers are not reused and do not depend on previous generations.

## 8. Relation closure

The export includes not only the AST subtree but also every node reachable
through relations. Therefore every edge points to an `id` present in `nodes`.

Algorithm:

1. Start at the root and push it onto the stack.
2. While the stack is not empty:
   - pop a node;
   - if it has already been visited, skip it;
   - assign the next identifier and append the node to the list;
   - collect the targets of all relations in relation-type order, then the child
     nodes;
   - push the collected nodes onto the stack in reverse order.
3. Build the JSON from the node list.
4. For each relation type, emit edges by iterating sources in identifier order.

## 9. Determinism

For the same graph, the export must produce identical JSON:

- node order corresponds to increasing `id`;
- `children` order is preserved;
- relation-type order is fixed: `declarations`, `calls`, `types`, `members`,
  `scopes`, `includes`;
- edge order within a type is determined by source order and target order;
- `type` does not depend on time, platform, or hash code;
- `code` is taken from `Source` without normalization.

If nodes or relations are created in a different order, identifiers and edge
order may differ; this is acceptable because the contract fixes traversal order,
not object creation order.

## 10. Example

```json
{
  "format": "cpp03-cpg",
  "version": 2,
  "root": 0,
  "nodes": [
    {
      "id": 0,
      "type": "TranslationUnit",
      "tags": ["declarations"],
      "fragment": {
        "start": { "offset": 0, "line": 1, "column": 1 },
        "end": { "offset": 10, "line": 1, "column": 11 },
        "code": "int main()"
      },
      "children": [1]
    },
    {
      "id": 1,
      "type": "DeclarationList",
      "tags": [],
      "fragment": {
        "start": { "offset": 0, "line": 1, "column": 1 },
        "end": { "offset": 8, "line": 1, "column": 9 },
        "code": "int main"
      },
      "children": [2]
    },
    {
      "id": 2,
      "type": "Declaration",
      "tags": ["type", "name"],
      "fragment": {
        "start": { "offset": 0, "line": 1, "column": 1 },
        "end": { "offset": 8, "line": 1, "column": 9 },
        "code": "int main"
      },
      "children": [3, 4]
    },
    {
      "id": 3,
      "type": "Name",
      "tags": [],
      "fragment": {
        "start": { "offset": 0, "line": 1, "column": 1 },
        "end": { "offset": 3, "line": 1, "column": 4 },
        "code": "int"
      },
      "children": []
    },
    {
      "id": 4,
      "type": "Name",
      "tags": [],
      "fragment": {
        "start": { "offset": 4, "line": 1, "column": 5 },
        "end": { "offset": 8, "line": 1, "column": 9 },
        "code": "main"
      },
      "children": []
    }
  ],
  "relations": {
    "declarations": [],
    "calls": [],
    "types": [
      { "from": 2, "to": 3 }
    ],
    "members": [],
    "scopes": [],
    "includes": []
  }
}
```

## 11. Current limitations

- `type` uses the Java class name by default. For a long-lived format, nodes
  should override `name()` with a stable language-level name.
- `code` duplicates the source text for every node. For large trees this may be
  redundant; a mode without `code` can be added later.
- Relations are emitted in a separate `relations` object; they are not
  duplicated in nodes.
- The format does not describe diagnostics, symbol tables, or types; these are
  the next CPG layers.
- `fragment` may be `null` if a node is synthetic and has no source text.

## 12. Versioning

An incompatible structural change increments `version`. Compatible additions
(new optional fields) do not change the version. A consumer must check `format`
and `version` before parsing.
