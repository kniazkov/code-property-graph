# C++03 oracles

Independent implementations of C++03 rules used as oracles. An oracle must not
call the parser and must not be generated from the parser.

Planned oracles:

- `char_literal_eval` — value of a character literal from the execution charset
  table and escape-sequence rules;
- `integer_literal_eval` — value and type of an integer literal from its base,
  digits, and suffix;
- `boolean_literal_eval` — value and type of a boolean literal.

Each oracle reports `NotApplicable` for statements outside its scope, so a
single check can stay bound to one atomic statement.
