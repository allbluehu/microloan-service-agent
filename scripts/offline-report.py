#!/usr/bin/env python3
"""Aggregate actual JUnit results; never present offline routing as real model accuracy."""
import datetime
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
suites = []
for path in sorted((root / 'target/surefire-reports').glob('TEST-*.xml')):
    element = ET.parse(path).getroot()
    suites.append({key: element.attrib.get(key, '0') for key in ('name', 'tests', 'failures', 'errors', 'skipped', 'time')})
report = {
    'schemaVersion': 'offline-regression-v1',
    'generatedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'scope': 'Offline JUnit regression; excludes real supplier accuracy',
    'tests': sum(int(s['tests']) for s in suites),
    'failures': sum(int(s['failures']) for s in suites),
    'errors': sum(int(s['errors']) for s in suites),
    'skipped': sum(int(s['skipped']) for s in suites),
    'datasets': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root / 'src/test/resources/evaluation').glob('*.json'))},
    'suites': suites,
}
report['passed'] = bool(suites) and report['failures'] == 0 and report['errors'] == 0
output = root / 'target/evaluation/offline-regression.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(f"{output}: {report['tests']} tests, {report['failures']} failures, {report['errors']} errors, {report['skipped']} skipped")
if not report['passed']:
    raise SystemExit(1)
