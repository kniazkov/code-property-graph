#!/usr/bin/env python3
"""Deterministic metadata allocation; never certifies normative review."""
import argparse, collections, hashlib, json, re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/cpp03-paragraphs/inventory.json'
OUT = ROOT / 'docs/research-ownership'

def topic(u):
    s = u['section']
    if s == 'footnotes': return 0
    if u['unit_kind'] == 'numbering_anomaly': return -1
    parts = s.split('.')
    if not parts[0].isdigit(): return 226
    c = int(parts[0])
    if c == 1: return 201
    if c == 2: return 202
    if c == 3: return 205 if s.startswith(('3.9', '3.8', '3.7')) else 206
    if c == 4: return 208
    if c == 5: return 209
    if c == 6: return 210
    if c in (7, 8): return 207
    if c in (9, 10, 11): return 211
    if c == 12: return 212
    if c == 13: return 213
    if c == 14: return 215 if s.startswith(('14.5.4', '14.7')) else 214
    if c == 15: return 216
    if c == 16: return 203 if s.startswith('16.3') else 204
    if c in (17, 18, 19, 20): return 225 if s == '18.2' or s.startswith('18.2.') else 217
    return {21:218,22:219,23:220,24:221,25:222,26:223,27:224}[c]

def generate():
    data = json.loads(SOURCE.read_text())
    units = data['units']; ids = [u['id'] for u in units]
    assert len(ids) == len(set(ids)) == 5250
    assert dict(collections.Counter(u['unit_kind'] for u in units)) == data['counts']
    assert data['source_sha256'] == '6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a'
    assert all(u['pdf_page'] == u['printed_page'] + 28 for u in units)
    assert all(s in set(ids) for u in units for s in u.get('call_sites', []))
    groups = collections.defaultdict(list)
    for u in units: groups[topic(u)].append(u)
    batches = []
    for t, rows in sorted(groups.items()):
        rows.sort(key=lambda u: (u['printed_page'], [int(x) if x.isdigit() else x for x in re.split(r'(\d+)', u['section'])], u['id']))
        for start in range(0, len(rows), 50):
            chunk = rows[start:start+50]
            batches.append({'id':f'R{len(batches)+1:03}', 'topic_issue':t or None, 'initial_issue':t if t>0 and start==0 else None,
                'sections':list(dict.fromkeys(u['section'] for u in chunk)), 'locators':[u['id'] for u in chunk],
                'kind_counts':dict(sorted(collections.Counter(u['unit_kind'] for u in chunk).items())),
                'dependency_links':{u['id']:u['call_sites'] for u in chunk if u.get('call_sites')}})
    return {'inventory_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(), 'source_sha256':data['source_sha256'],
            'locator_count':len(ids), 'counts':data['counts'], 'batch_limit':50, 'batches':batches}

def validate_owners(value, owners):
    assert set(owners)=={b['id'] for b in value['batches']}, 'Missing/extra batch owner'
    assert all(type(n)==int and n>0 for n in owners.values()), 'Invalid issue owner'
    assert len(set(owners.values()))==len(owners), 'One issue must own one batch'
    for b in value['batches']:
        if b['initial_issue']: assert owners[b['id']]==b['initial_issue']

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');a=ap.parse_args()
    value=generate(); output=json.dumps(value,indent=2)+'\n'
    if a.check:
        assert (OUT/'batches.json').read_text()==output, 'Allocation drift'
        validate_owners(value,json.loads((OUT/'owners.json').read_text()))
    else: (OUT/'batches.json').write_text(output)
    print(f"{value['locator_count']} locators; {len(value['batches'])} bounded batches")
if __name__=='__main__': main()
