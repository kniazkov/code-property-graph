# CV unnamed padding

[Contract, manual CLI and tests](../../../../docs/cpp03-cv-padding-verification.md).

This fixture follows original C++03. GCC accepts it; Clang applies a later DR
and rejects cv-qualified unnamed fields. That difference is tested explicitly.
