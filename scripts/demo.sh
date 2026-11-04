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
    return {text.text for text in root.findall(".//s:text", ns)}
assert {"Project", "PrintStatement", "ref"} <= labels(sys.argv[1])
assert "CallExpression" in labels(sys.argv[2])
assert "PrintStatement" not in labels(sys.argv[2])
PY
cp "$work/tree.svg" target/demo/tree.svg
echo 'Both translations and SVG exports passed.'
