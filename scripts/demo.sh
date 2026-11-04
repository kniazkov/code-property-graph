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
        assert label.text[0].isspace() and not label.text[1].isspace()
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
        if "id" in item:
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
