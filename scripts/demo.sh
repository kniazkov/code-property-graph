#!/usr/bin/env bash
# Execute original and generated C++/Java examples and compare their output.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target
work=$(mktemp -d "$PWD/target/demo-run.XXXXXX")
for stage in PARSED RESOLVED GREEN FOLDED; do
  java -jar target/cpg-0.0.1-SNAPSHOT.jar JAVA "$stage" examples/java "$work/java-$stage"
  javac "$work/java-$stage/Program.java"
  java -cp "$work/java-$stage" Program > "$work/java-$stage/output.txt"
  java -jar target/cpg-0.0.1-SNAPSHOT.jar CPP "$stage" examples/cpp "$work/cpp-$stage"
  g++ -std=c++17 "$work/cpp-$stage/program.cpp" -o "$work/cpp-$stage/program"
  "$work/cpp-$stage/program" > "$work/cpp-$stage/output.txt"
done
mkdir "$work/original-java"
javac -d "$work/original-java" examples/java/Program.java
java -cp "$work/original-java" Program > "$work/original-java/output.txt"
g++ -std=c++17 examples/cpp/program.cpp -o "$work/original-cpp"
"$work/original-cpp" > "$work/original-cpp.txt"
for stage in PARSED RESOLVED GREEN FOLDED; do
  diff -u "$work/original-java/output.txt" "$work/java-$stage/output.txt"
  diff -u "$work/original-cpp.txt" "$work/cpp-$stage/output.txt"
done
test "$(cat "$work/original-java/output.txt")" = 40
test "$(cat "$work/original-cpp.txt")" = 40
mkdir -p target/demo
cp "$work/java-FOLDED/Program.java" target/demo/Program.java
cp "$work/cpp-FOLDED/program.cpp" target/demo/program.cpp
echo 'Both languages, all four stages: output 40.'

jar=target/cpg-0.0.1-SNAPSHOT.jar
java -jar "$jar" --input examples/java --from java --to cpp --out "$work/java-to-cpp"
g++ -std=c++17 "$work/java-to-cpp/Program.cpp" -o "$work/converted-cpp"
"$work/converted-cpp" > "$work/converted-cpp.txt"
diff -u "$work/original-java/output.txt" "$work/converted-cpp.txt"

java -jar "$jar" --input examples/cpp --from cpp --to java --out "$work/cpp-to-java" --svg "$work/tree.svg"
javac "$work/cpp-to-java/Program.java"
java -cp "$work/cpp-to-java" Program > "$work/converted-java.txt"
diff -u "$work/original-cpp.txt" "$work/converted-java.txt"
java -jar "$jar" --input examples/java --from java --stage parsed --svg "$work/parsed.svg"
python3 - "$work/tree.svg" "$work/parsed.svg" <<'PY'
import sys
import xml.etree.ElementTree as ET
ns = {"s": "http://www.w3.org/2000/svg"}
def labels(path):
    root = ET.parse(path).getroot()
    assert root.tag == "{http://www.w3.org/2000/svg}svg"
    for element in root.iter():
        if element.get("stroke", "none") != "none":
            assert float(element.get("stroke-width", "1")) == 1
    colors = {}
    for group in root.findall(".//s:g[@class='node']", ns):
        if group.find("s:title", ns).text.startswith("p"):
            # Preprocessor catalogue/provenance uses purple note-shaped nodes.
            outline = group.find("s:polygon", ns)
            assert outline is not None and outline.get("stroke").lower() == "#7b1fa2"
            assert all(float(t.get("font-size")) == 10 for t in group.findall("s:text", ns))
            continue
        outline = group.find("s:path", ns)
        assert outline is not None and "C" in outline.get("d", "")
        texts = group.findall("s:text", ns)
        colors.setdefault(texts[0].text, set()).add(outline.get("stroke").lower())
        assert all(float(t.get("font-size")) == 10 for t in texts)
        assert all(t.get("fill", "").lower() in {"navy", "#000080"} for t in texts[1:])
    assert colors["IntegerLiteral"] == {"#008000"}
    assert colors["Project"] == {"#ff0000"}
    for group in root.findall(".//s:g[@class='edge']", ns):
        label = group.find("s:text", ns)
        assert float(label.get("font-size")) == 9
        endpoints = group.find("s:title", ns).text.split("->")
        if all(endpoint.startswith("n") for endpoint in endpoints):
            assert label.text[0].isspace() and not label.text[1].isspace()
        else:
            assert label.text and label.text.strip() == label.text
            assert group.find("s:path", ns).get("stroke").lower() == "#7b1fa2"
    return {text.text.strip() for text in root.findall(".//s:text", ns)}
assert {"Project", "PrintStatement", "ref"} <= labels(sys.argv[1])
assert "CallExpression" in labels(sys.argv[2])
assert "PrintStatement" not in labels(sys.argv[2])
PY
cp "$work/tree.svg" target/demo/tree.svg
echo 'Both translations and SVG exports passed.'

mkdir "$work/flow-input"
cat > "$work/flow-input/Loop.java" <<'JAVA'
class Loop {
    public static void main(String[] args) {
        int x = 0;
        while (x < 3) {
            if (x == 1) x = x + 2;
            else x = x + 1;
        }
        System.out.println(x);
    }
}
JAVA
java -jar "$jar" --from java --input "$work/flow-input" --to cpp --out "$work/flow-cpp" --svg "$work/flow.svg"
g++ -std=c++17 "$work/flow-cpp/Loop.cpp" -o "$work/loop"
test "$("$work/loop")" = 3
java -jar "$jar" --from cpp --input "$work/flow-cpp" --to java --out "$work/flow-java"
javac "$work/flow-java/Loop.java"
test "$(java -cp "$work/flow-java" Loop)" = 3
python3 - "$work/flow.svg" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
ns = {"s": "http://www.w3.org/2000/svg"}
labels = {}
for edge in root.findall(".//s:g[@class='edge']", ns):
    label = edge.find("s:text", ns).text.strip()
    labels.setdefault(label, set()).add(edge.find("s:path", ns).get("stroke").lower())
for label in ("CFG entry", "CFG true", "CFG false"):
    assert labels[label] == {"#1565c0"}, (label, labels)
for label in ("DFG value", "DFG def"):
    assert labels[label] == {"#aa3377"}, (label, labels)
def node_id(label):
    matches = [g.find("s:title", ns).text for g in root.findall(".//s:g[@class='node']", ns)
               if g.find("s:text", ns).text == label]
    assert len(matches) == 1, (label, matches)
    return matches[0]

exit_id = node_id("Exit")
owner_id = node_id("MethodDeclaration")
incoming = [(g.find("s:title", ns).text.split("->"), g.find("s:text", ns).text.strip())
            for g in root.findall(".//s:g[@class='edge']", ns)]
assert any(pair == [owner_id, exit_id] and label.isdecimal() for pair, label in incoming)
assert any(pair[1] == exit_id and label.startswith("CFG ") for pair, label in incoming)
PY
cp "$work/flow.svg" target/demo/flow.svg

# Verify the distributable JAR includes its JSON runtime and all three output modes compose.
java -jar "$jar" --from cpp --input examples/cpp --stage folded --out "$work/json-src" --svg "$work/json.svg" --json target/demo/project.json
python3 - target/demo/project.json <<'PY'
import json, pathlib, sys
project = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
nodes = {}
edges = []
def visit(item):
    if isinstance(item, list):
        for child in item:
            visit(child)
    elif isinstance(item, dict):
        if "id" in item and "kind" in item:
            assert item["id"] not in nodes
            nodes[item["id"]] = item
            edges.extend(item.get("edges", ()))
        for key, value in item.items():
            if key != "edges":
                visit(value)
visit(project)
assert project["id"] == 0 and sorted(nodes) == list(range(len(nodes)))
assert all(edge["to"] in nodes for edge in edges)
assert {"ref", "cfg.entry", "dfg.definition"} <= {edge["kind"] for edge in edges}
assert pathlib.Path(project["files"][0]["path"]).is_absolute()
PY

java -jar "$jar" --from java --input examples/unread-write/java --stage resolved \
  --out "$work/warn-src" --svg "$work/warn.svg" --json "$work/warn.json" 2>"$work/warn.txt"
javac "$work/warn-src/Demo.java"
test "$(java -cp "$work/warn-src" Demo)" = 2
python3 - "$work" <<'PY'
import json, pathlib, sys, xml.etree.ElementTree as ET
work = pathlib.Path(sys.argv[1])
messages = (work / "warn.txt").read_text().splitlines()
assert len(messages) == 1 and "Demo.java:5:9: warning [unread-write]" in messages[0], messages
method = json.loads((work / "warn.json").read_text())["files"][0]["items"][0]["members"][0]
decl, assign, output = method["body"]["items"]
assert decl["diagnostics"] == [{"code": "unread-write", "severity": "warning"}]
assert "diagnostics" not in assign["expression"]
ns = {"s": "http://www.w3.org/2000/svg"}
root = ET.parse(work / "warn.svg").getroot()
badges = [t for t in root.findall(".//s:text", ns) if "unread-write" in (t.text or "")]
assert len(badges) == 1 and badges[0].get("fill").lower() == "#ad6800"
PY

# Macro provenance is a separate, switchable SVG layer with physical source links.
java -jar "$jar" --from cpp --input src/test/resources/cpp03/provenance/sources --stage resolved \
  --svg "$work/provenance.svg" --json "$work/provenance.json"
java -jar "$jar" --from cpp --input src/test/resources/cpp03/provenance/sources --stage resolved \
  --svg "$work/provenance-off.svg" --provenance off
python3 - "$work" <<'PY'
import json, pathlib, sys, xml.etree.ElementTree as ET
work = pathlib.Path(sys.argv[1]); ns = {"s": "http://www.w3.org/2000/svg"}
root = ET.parse(work / "provenance.svg").getroot()
texts = [t.text or "" for t in root.findall(".//s:text", ns)]
assert any("macro SQUARE" in t for t in texts)
assert any("macros.h:3:" in t for t in texts)
assert any("main.cpp:5:" in t for t in texts)
labels = {t.text for e in root.findall(".//s:g[@class='edge']", ns) for t in e.findall("s:text", ns)}
assert {"origin", "invocation", "definition", "replacement operator", "parent expansion"} <= labels
plain = ET.parse(work / "provenance-off.svg").getroot()
assert not any("macro SQUARE" in (t.text or "") for t in plain.findall(".//s:text", ns))
data = json.loads((work / "provenance.json").read_text())["files"][0]
events = data["preprocessing"]["expansions"]
empty = next(e for e in events if e["name"] == "EMPTY")
assert empty["startToken"] == empty["endToken"]
assert sum(e["name"] == "SQUARE" for e in events) == 2
PY
cp "$work/provenance.svg" target/demo/provenance.svg

# Public API macro edit: only first SQUARE right operand changes; expected 58 -> 64.
mkdir "$work/edit-square-classes"
javac -cp "$jar" -d "$work/edit-square-classes" examples/cpp03-square/EditSquare.java
java -cp "$jar:$work/edit-square-classes" EditSquare \
  src/test/resources/cpp03/square/compile_commands.json "$work/square-edited"
java -jar "$jar" --from cpp --compile-commands "$work/square-edited/compile_commands.json" \
  --stage resolved --json "$work/square-reparsed.json"
cmp src/test/resources/cpp03/square/sources/include/square.h "$work/square-edited/sources/include/square.h"
cmp src/test/resources/cpp03/square/sources/second.cpp "$work/square-edited/sources/second.cpp"
for compiler in g++ clang++; do
  "$compiler" -std=c++03 -pedantic-errors -Isrc/test/resources/cpp03/square/sources/include \
    src/test/resources/cpp03/square/sources/main.cpp src/test/resources/cpp03/square/sources/second.cpp -o "$work/square-before"
  test "$("$work/square-before")" = 58
  "$compiler" -std=c++03 -pedantic-errors -I"$work/square-edited/sources/include" \
    "$work/square-edited/sources/main.cpp" "$work/square-edited/sources/second.cpp" -o "$work/square-after"
  test "$("$work/square-after")" = 64
done
cp "$work/square-edited/edited.json" target/demo/square-edited.json

# Select named server/client compositions from one project containing four TU variants.
mkdir "$work/build-selection-classes"
javac -cp "$jar" -d "$work/build-selection-classes" examples/cpp03-builds/SelectBuilds.java
java -cp "$jar:$work/build-selection-classes" SelectBuilds \
  src/test/resources/cpp03/build-variants/compile_commands.json "$work/selected-builds"
for variant in server client; do
  macro=$(printf '%s' "$variant" | tr '[:lower:]' '[:upper:]')
  for compiler in g++ clang++; do
    "$compiler" -std=c++03 -pedantic-errors "-D$macro" \
      "$work/selected-builds/$variant/sources/main.cpp" "$work/selected-builds/$variant/sources/engine.cpp" \
      -o "$work/selected-builds/$variant/program"
    "$work/selected-builds/$variant/program" > "$work/selected-builds/$variant/output.txt"
    test ! -s "$work/selected-builds/$variant/output.txt"
  done
  java -jar "$jar" --from cpp --stage resolved \
    --compile-commands "$work/selected-builds/$variant/compile_commands.json" \
    --json "$work/selected-builds/$variant/reparsed.json"
done
cp "$work/selected-builds/all-variants.json" target/demo/build-variants.json

# Shared physical header: reject incompatible requirements before any filesystem export.
mkdir "$work/shared-header-classes"
javac -cp "$jar" -d "$work/shared-header-classes" examples/cpp03-shared-headers/EditSharedHeaders.java
shared_fixture=src/test/resources/cpp03/shared-headers
if java -cp "$jar:$work/shared-header-classes" EditSharedHeaders \
    "$shared_fixture/common/compile_commands.json" server "$work/shared-conflict" \
    >"$work/shared-conflict.stdout" 2>"$work/shared-conflict.stderr"; then
  echo 'Expected a shared-header conflict for the server-only edit.' >&2
  exit 1
fi
test ! -e "$work/shared-conflict"
grep -q 'conflict' "$work/shared-conflict.stderr"
grep -q 'common.h:4:12-4:13' "$work/shared-conflict.stderr"
grep -q -- '-DSERVER' "$work/shared-conflict.stderr"
grep -q -- '-DCLIENT' "$work/shared-conflict.stderr"
grep -q 'requires "2"' "$work/shared-conflict.stderr"
grep -q 'requires "1"' "$work/shared-conflict.stderr"
java -cp "$jar:$work/shared-header-classes" EditSharedHeaders \
  "$shared_fixture/common/compile_commands.json" both "$work/shared-both"
java -cp "$jar:$work/shared-header-classes" EditSharedHeaders \
  "$shared_fixture/conditional/compile_commands.json" server "$work/shared-conditional"
python3 - "$shared_fixture" "$work" <<'PY'
from pathlib import Path
import sys
fixture, work = map(Path, sys.argv[1:])
for name, output in [('common', 'shared-both'), ('conditional', 'shared-conditional')]:
    before = (fixture / name / 'sources/include/common.h').read_bytes()
    after = (work / output / 'sources/include/common.h').read_bytes()
    assert after == before.replace(b'return 1;', b'return 2;', 1), (name, after)
    if name == 'common':
        assert after.count(b'return 2;') == 1 and b'return 1;' not in after
    else:
        assert before[before.index(b'#else'):] == after[after.index(b'#else'):]
    assert (fixture / name / 'sources/main.cpp').read_bytes() == (work / output / 'sources/main.cpp').read_bytes()
PY
for scenario in common conditional shared-both shared-conditional; do
  case "$scenario" in
    common|conditional) sources="$shared_fixture/$scenario/sources" ;;
    *) sources="$work/$scenario/sources" ;;
  esac
  for compiler in g++ clang++; do
    for configuration in server client; do
      flags=(-DCLIENT)
      if [ "$configuration" = server ]; then flags=(-DSERVER); fi
      expected=1
      if [ "$scenario" = shared-both ] || { [ "$scenario" = shared-conditional ] && [ "$configuration" = server ]; }; then
        expected=2
      fi
      "$compiler" -std=c++03 -pedantic-errors "${flags[@]}" "$sources/main.cpp" -o "$work/shared-program"
      test "$("$work/shared-program")" = "$expected"
    done
  done
done
for output in shared-both shared-conditional; do
  java -jar "$jar" --from cpp --stage resolved --compile-commands "$work/$output/compile_commands.json" \
    --out "$work/$output/reparsed-sources" --json "$work/$output/reparsed.json"
  cmp "$work/$output/sources/include/common.h" "$work/$output/reparsed-sources/include/common.h"
done
cp "$work/shared-conditional/edited.json" target/demo/shared-header-edited.json
echo 'Shared headers: conflicting edit rejected; common edit 2/2 and conditional edit 2/1 passed with GCC and Clang.'
