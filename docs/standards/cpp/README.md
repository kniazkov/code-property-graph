# C++ requirements sources

The first extended parser subset targets **C++03**. See the
[C++03 source record](cpp03/README.md) for the target edition, verified file
identities, source availability and the policy for later corrections.

For later-edition reference work, the project pins **N4950** for C++23
(ISO/IEC 14882:2024) and **N5050** for C++26. WG21 working drafts and published
ISO editions are distinct documents; a draft link does not establish equivalence.

| Edition | Pinned editor revision | Committee PDF | Selection basis |
| --- | --- | --- | --- |
| C++23 | [4e4de1d](https://github.com/cplusplus/draft/tree/4e4de1df8ee941255b653b61d0a62050b34cf8c9) | [N4950](https://open-std.org/jtc1/sc22/wg21/docs/papers/2023/n4950.pdf) | [N4951](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2023/n4951.html) identifies the final C++23 working draft and basis for the DIS ballot. See the [ISO/IEC 14882:2024 catalogue](https://www.iso.org/standard/83626.html). |
| C++26 | [8ab1a9b](https://github.com/cplusplus/draft/tree/8ab1a9b574d97fea3809a2885c7f41f5d60b93f6) | [N5050](https://open-std.org/jtc1/sc22/wg21/docs/papers/2026/n5050.pdf) | [N5051](https://open-std.org/jtc1/sc22/wg21/docs/papers/2026/n5051.html) identifies the final C++26 working draft and basis for the DIS ballot. |

The earlier source card recorded on 4 November 2026 that editor work had moved
to C++29: [N5055](https://open-std.org/jtc1/sc22/wg21/docs/papers/2026/n5055.html)
identifies N5054 as a C++29 draft. Always pin requirement references to a specific
edition. These later-edition pins are preserved references, not a claim that
their PDFs or complete contents were audited in the C++03 provenance check.

## Source storage

Keep bibliographic records and external links in the current Git tree. Do not
include standards PDFs, extracted texts, document sources or submodules containing
them. Obtain reading copies from the publisher or WG21 under the applicable
terms. An accessible link is not permission to republish; CPG's MIT licence does
not cover external documents. See [ISO copyright terms](https://www.iso.org/copyright.html).

Keep any separate reading copy outside the Git working directory, within the
rights granted for that copy. Normal CPG builds and checks do not require the
standards texts and must not download or publish them as CI artifacts.

## CPG requirements and evidence

CPG registries describe testable implementation behaviour, original examples,
expected results and coverage gaps. They are not a new edition, translation or
replacement of a standard. A brief rule explanation neither establishes complete
support nor replaces checking exceptions in the original source.

A requirement reference identifies the exact edition or draft, clause, named
tag where present, and paragraph. Record known edition differences and CPG policy
separately. Do not replace a test description with sequential paraphrases of the
whole standard or copies of its examples.

Removing files from the current tree does not remove earlier copies from Git
history, old clones, forks or artifacts. History cleanup is a separate operation.
