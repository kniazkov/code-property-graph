#!/usr/bin/env python3
"""Validate requirement records and provenance without claiming conformance."""
import argparse
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'docs/standards/cpp/cpp03/requirements'

def require(ok, message):
    if not ok:
        raise ValueError(message)

def unique_object(pairs):
    value = {}
    for key, child in pairs:
        require(key not in value, f'duplicate JSON key: {key}')
        value[key] = child
    return value

def decode(text):
    return json.loads(text, object_pairs_hook=unique_object)

def read(path):
    return decode(path.read_text())

def lines(path):
    return [decode(line) for line in path.read_text().splitlines() if line.strip()]

def schema(value, rule, where='record'):
    types = {'object':dict, 'array':list, 'string':str, 'integer':int, 'null':type(None)}
    allowed = rule.get('type', [])
    allowed = [allowed] if isinstance(allowed, str) else allowed
    require(not allowed or type(value) in [types[t] for t in allowed], f'{where}: type')
    if 'enum' in rule: require(value in rule['enum'], f'{where}: enum')
    if isinstance(value, str):
        require(len(value.strip()) >= rule.get('minLength', 0), f'{where}: empty')
        if 'pattern' in rule: require(re.fullmatch(rule['pattern'], value), f'{where}: pattern')
    if type(value) is int: require(value >= rule.get('minimum', value), f'{where}: minimum')
    if isinstance(value, dict):
        require(set(rule.get('required', [])) <= value.keys(), f'{where}: required fields')
        props = rule.get('properties', {})
        require(rule.get('additionalProperties') is not False or value.keys() <= props.keys(), f'{where}: unknown fields')
        for key, child in value.items():
            if key in props: schema(child, props[key], where+'.'+key)
    if isinstance(value, list):
        require(len(value) >= rule.get('minItems', 0), f'{where}: too few items')
        if rule.get('uniqueItems'): require(len({json.dumps(v,sort_keys=True) for v in value}) == len(value), f'{where}: duplicate items')
        for i, child in enumerate(value): schema(child, rule.get('items', {}), f'{where}[{i}]')

def local(root, path):
    target = (root / path).resolve()
    require(not Path(path).is_absolute() and target.is_relative_to(root.resolve()) and target.is_file(), f'invalid local path: {path}')
    return target

def validate(records, dispositions, ledger, sources, root=ROOT, previous=None):
    record_schema = read(DATA/'record.schema.json')
    disposition_schema = read(DATA/'disposition.schema.json')
    for row in records: schema(row, record_schema)
    for row in dispositions: schema(row, disposition_schema)
    ids = {row['id']:row for row in records}
    require(len(ids)==len(records), 'reused requirement ID')
    keys = [r['semantic_key'] for r in records]
    require(len(keys)==len(set(keys)), 'duplicate semantic key')
    for id, entry in ledger.items():
        require(re.fullmatch(r'CPG-CPP03-REQ-[0-9]{6}',id), 'invalid ledger ID')
        require(set(entry)=={'semantic_key','status'} and entry['status'] in ('active','retired'), 'invalid ledger entry')
        require(isinstance(entry['semantic_key'],str) and bool(entry['semantic_key'].strip()), 'invalid ledger meaning')
        require((id in ids)==(entry['status']=='active'), 'active/retired ledger mismatch')
    for id, old in (previous or {}).items():
        require(id in ledger and old['semantic_key']==ledger[id]['semantic_key'], 'removed/reused historical ID')
        require(old['status']!='retired' or ledger[id]['status']=='retired', 'reactivated retired ID')
    for row in records:
        id = row['id']; basis=row['basis']; states=row['states']
        require(id in ledger and ledger[id]['semantic_key']==row['semantic_key'], 'unregistered/reused ID')
        require(set(row['dependencies']) <= ids.keys() and id not in row['dependencies'], 'dangling/self requirement dependency')
        require(basis['source_id'] in sources, 'unknown source')
        source=sources[basis['source_id']]
        require(basis['kind']==source['kind'], 'edition/basis mismatch')
        require(set(basis['locators']) <= set(source['locators']), 'dangling source locator')
        confidence=basis['confidence']
        require(confidence!='unresolved' or bool(row['questions']), 'unresolved source without question owner')
        require(basis['kind']!='policy' or row['classification']==['policy'], 'policy masquerading as normative rule')
        require(confidence=='unresolved' or confidence=={'iso2003':'original_reviewed','draft':'draft_only','dr':'dr_reviewed','policy':'policy'}[basis['kind']], 'confidence/edition mismatch')
        require(states['readiness']!='reviewed' or (confidence!='unresolved' and not row['questions']), 'reviewed with unresolved questions/source')
        require(states['readiness']!='blocked' or bool(row['questions']), 'blocked without question owner')
        na=row['applicability']['status']=='not_applicable'
        require(na==(states['implementation']=='not_applicable'), 'applicability/implementation mismatch')
        checks=[x['id'] for x in row['proposed_checks']]
        require(len(checks)==len(set(checks)), 'duplicate proposed check')
        evidence=row['evidence']
        for e in evidence:
            require(e['check_id'] in checks, 'dangling evidence check')
            local(root,e['path'])
            run=subprocess.run(['git','show',e['revision']+':'+e['path']],cwd=root,text=True,capture_output=True)
            require(run.returncode==0 and e['assertion'] in run.stdout, 'missing pinned evidence assertion/revision')
        verification=states['verification']
        require((verification=='not_run')==(not evidence), 'execution/evidence mismatch')
        if verification=='failed': require(any(e['result']=='fail' for e in evidence), 'failed without failure')
        if verification=='partial': require(any(e['result']=='pass' for e in evidence), 'partial without passed evidence')
        if verification=='passed':
            require(states['readiness']=='reviewed' and states['implementation']=='implemented' and not na, 'invalid passed states')
            require(bool(checks) and all(e['result']=='pass' for e in evidence) and {e['check_id'] for e in evidence}==set(checks), 'passed without complete check evidence')
        if na: require(not evidence and bool(row['applicability']['justification'].strip()), 'invalid non-applicability')
    seen=set(); linked=set()
    for d in dispositions:
        key=(d['source_id'],d['locator'])
        require(key not in seen, 'duplicate disposition');seen.add(key)
        require(key[0] in sources and key[1] in sources[key[0]]['locators'], 'dangling disposition locator')
        require(set(d['requirement_ids']) <= ids.keys(), 'dangling disposition requirement')
        if d['status']=='requirements':
            require(bool(d['requirement_ids']) and d['question_issue'] is None, 'invalid requirement disposition')
            for id in d['requirement_ids']:
                require(ids[id]['basis']['source_id']==key[0] and key[1] in ids[id]['basis']['locators'], 'asymmetric locator link')
                linked.add((id,*key))
        else:
            require(not d['requirement_ids'], 'non-requirement disposition has links')
            require((d['question_issue'] is not None)==(d['status']=='unresolved'), 'question ownership mismatch')
    for r in records:
        for locator in r['basis']['locators']: require((r['id'],r['basis']['source_id'],locator) in linked, 'missing reverse disposition')

def load_sources(root=ROOT):
    result={}
    for id, source in read(DATA/'sources.json').items():
        inventory=read(local(root,source['inventory']))
        require(inventory['source_sha256']==source['source_sha256'], 'source identity mismatch')
        result[id]={'kind':source['kind'],'locators':[u['id'] for u in inventory['units']]}
    return result

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--baseline-ref');args=ap.parse_args()
    previous=None
    if args.baseline_ref:
        path=(DATA/'ids.json').relative_to(ROOT).as_posix()
        r=subprocess.run(['git','show',args.baseline_ref+':'+path],cwd=ROOT,text=True,capture_output=True)
        require(r.returncode==0, 'baseline ledger unavailable');previous=decode(r.stdout)
    records=[r for p in sorted((DATA/'records').glob('*.jsonl')) for r in lines(p)]
    validate(records,lines(DATA/'dispositions.jsonl'),read(DATA/'ids.json'),load_sources(),previous=previous)
    print(f'{len(records)} requirement records validated; no conformance or execution inferred')
if __name__=='__main__': main()
