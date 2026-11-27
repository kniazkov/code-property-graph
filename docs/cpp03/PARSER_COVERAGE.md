# Parser coverage: C++03 literals

`com.kniazkov.cpg.parser.cpp.LiteralParser` parses one C++03 literal. The
following statements are implemented and proven by tests.

## Integer literals (CPP03-2.13.1)

| Statement | Requirement | Test |
| --- | --- | --- |
| 003 | `0` is an octal literal | `IntegerLiteralParserTest.statement_003_zeroIsAnOctalLiteral` |
| 011 | an integer literal is a digit sequence | `statement_011_integerLiteralIsADigitSequence` |
| 015 | decimal literals use base ten | `statement_015_decimalLiteralUsesBaseTen` |
| 016 | octal literals use base eight | `statement_016_octalLiteralUsesBaseEight` |
| 017 | hexadecimal literals use base sixteen | `statement_017_hexadecimalLiteralUsesBaseSixteen` |
| 019 | twelve is `12`, `014`, or `0XC` | `statement_019_twelveHasThreeSpellings` |
| 021 | unsuffixed decimal uses `int`, then `long int` | `statement_021_decimalWithoutSuffixUsesIntThenLong` |
| 022 | decimal overflow is rejected | `statement_022_decimalOverflowIsRejectedInIlp32` |
| 023 | unsuffixed octal/hex uses the full candidate list | `statement_023_octalAndHexUseTheFullCandidateList` |
| 024 | `u`/`U` uses `unsigned int`, then `unsigned long int` | `statement_024_unsignedSuffixSelectsUnsignedInt` |
| 025 | `l`/`L` uses `long int`, then `unsigned long int` | `statement_025_longSuffixSelectsLongInt` |
| 026 | `ul`, `lu`, `uL`, `Lu`, `Ul`, `lU`, `UL`, `LU` use `unsigned long int` | `statement_026_combinedSuffixSelectsUnsignedLongInt` |
| 027 | an unrepresentable literal is rejected | `statement_027_unrepresentableLiteralIsRejected` |

The grammar productions 001–010 are exercised by the same parsing tests.

## Character literals (CPP03-2.13.2)

| Statement | Requirement | Test |
| --- | --- | --- |
| 008 | one or more characters in single quotes, optional `L` | `CharacterLiteralParserTest.statement_010_ordinarySingleCCharHasTypeChar` |
| 009 | a literal without `L` is ordinary | `statement_010_ordinarySingleCCharHasTypeChar` |
| 010 | ordinary single c-char has type `char` | `statement_010_ordinarySingleCCharHasTypeChar` |
| 012 | more than one c-char is a multicharacter literal | `statement_013_multicharacterLiteralHasTypeInt` |
| 013 | multicharacter literal has type `int` | `statement_013_multicharacterLiteralHasTypeInt` |
| 015 | a literal beginning with `L` is wide | `statement_016_wideSingleCCharHasTypeWcharT` |
| 016 | wide literal has type `wchar_t` | `statement_016_wideSingleCCharHasTypeWcharT` |
| 017 | wide single c-char value is its wide encoding | `statement_016_wideSingleCCharHasTypeWcharT` |
| 018 | wide multicharacter literal has type `wchar_t` | `statement_018_wideMulticharacterLiteralHasTypeWcharT` |
| 019 | simple escape sequences | `statement_019_simpleEscapeSequencesAreSupported` |
| 020 | `"` and `?` may be themselves or `\"` and `\?` | `statement_019_simpleEscapeSequencesAreSupported` |
| 021 | `'` and `\` are represented by `\'` and `\\` | `statement_019_simpleEscapeSequencesAreSupported` |
| 022 | an unknown escape is rejected | `statement_022_unknownEscapeIsRejected` |
| 023 | an escape sequence specifies one character | `statement_019_simpleEscapeSequencesAreSupported` |
| 024 | octal escape uses one to three digits | `statement_024_octalEscapesUseOneToThreeDigits` |
| 025 | hex escape uses one or more digits | `statement_025_hexadecimalEscapesUseOneOrMoreDigits` |
| 026 | no limit on the number of hex digits | `statement_025_hexadecimalEscapesUseOneOrMoreDigits` |
| 027 | an octal/hex sequence stops at the first non-digit | `statement_025_hexadecimalEscapesUseOneOrMoreDigits` |
| 029 | universal-character-name is translated | `statement_029_universalCharacterNamesAreTranslated` |

## Floating literals (CPP03-2.13.3)

| Statement | Requirement | Test |
| --- | --- | --- |
| 001 | floating-literal grammar | `FloatingLiteralParserTest.parsesDecimalPoint` |
| 002 | integer part, point, fraction, exponent | `parsesLeadingAndTrailingDot` |
| 003 | type is `double`, `float`, or `long double` | `parsesFloatSuffix`, `parsesLongDoubleSuffix` |
| 005 | an out-of-range literal is rejected | `rejectsOutOfRangeValues` |

## String literals (CPP03-2.13.4)

| Statement | Requirement | Test |
| --- | --- | --- |
| 001 | string-literal grammar | `StringLiteralParserTest.parsesNarrowString` |
| 002 | array type and static storage duration | `parsesNarrowString`, `parsesWideString` |
| 006 | a terminating null is appended | `parsesNarrowString` |
| 007 | escapes and universal-character-names | `parsesSimpleEscapes`, `parsesOctalHexAndUniversalEscapes` |
| 008 | string literal size | `parsesNarrowString` |

## Boolean literals (CPP03-2.13.5)

| Statement | Requirement | Test |
| --- | --- | --- |
| 002 | `false` and `true` are keywords | `BooleanLiteralParserTest.statement_002_trueIsAKeyword` |
| 003 | boolean literals have type `bool` | `statement_003_booleanLiteralsHaveTypeBool` |
| 004 | boolean literals are not lvalues | `statement_004_booleanLiteralsAreNotLvalues` |

## Not implemented yet

- 2.13.2-014 and 2.13.2-018: implementation-defined multicharacter values. The
  parser uses a documented byte-accumulation choice but does not claim
  conformance.
- 2.13.2-028: values outside the `char`/`wchar_t` range.
- 2.13.2-030: universal-character-names without an execution encoding.
- 2.13.1-022: the parser rejects an unrepresentable decimal literal instead of
  modelling undefined behavior.
- 2.13.3-004: floating-literal rounding is implementation-defined.
- 2.13.4-003: whether string literals are distinct objects is
  implementation-defined.
- 2.13.4-004: modifying a string literal has undefined behavior.
- 2.13.4-005: adjacent string literal concatenation is a phase 6 rule and is
  not part of single-literal parsing.
