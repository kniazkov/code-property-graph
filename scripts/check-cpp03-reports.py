#!/usr/bin/env python3
"""Fail CI if Failsafe skipped any registered C++03 scenario or its harness tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
names = [line for line in (root / 'src/test/resources/cpp03/scenarios.txt').read_text().splitlines()
         if line.strip() and not line.startswith('#')]
report = root / 'target/failsafe-reports/TEST-com.kniazkov.cpg.e2e.Cpp03CorpusIT.xml'
suite = ET.parse(report).getroot()
expected = len(names) + 2  # manifest/nested-copy contract and missing-compiler negative control
assert int(suite.attrib['tests']) == expected, f'Expected {expected} corpus/harness tests: {suite.attrib}'
assert len(suite.findall('testcase')) == expected, 'Missing test result entries'
for field in ('failures', 'errors', 'skipped'):
    assert int(suite.attrib[field]) == 0, f'{field}: {suite.attrib}'
print(f'C++03: all {len(names)} scenarios and 2 harness checks executed successfully.')
