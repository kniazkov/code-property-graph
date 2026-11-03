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
