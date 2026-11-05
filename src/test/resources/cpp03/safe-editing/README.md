# Safe C++03 editing

This scenario checks immutable edited snapshots, declaration-family renaming,
qualified spelling after namespace lookup changes, diagnostic repair and atomic
rejection of invalid generation. Original and edited sources are compiled and run
with both strict C++03 compilers. API snapshots are also inspected through JSON,
DOT and reparsed generated sources.
