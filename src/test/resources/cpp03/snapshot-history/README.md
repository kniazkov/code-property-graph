# Standalone snapshot history

The selected JSON snapshot contains file-local deterministic IDs and only its
reachable transformation history. The scenario follows the folded value 5 back
to 2 + 3 and its physical source location, without reading original sources.
It checks all four explicit stage labels, schema version 1, contribution roles,
reverse provenance queries and old-snapshot immutability. Original, green and
folded programs compile and run with strict GCC and Clang.
