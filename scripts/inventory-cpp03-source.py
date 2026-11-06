#!/usr/bin/env python3
"""Reproduce safe C++03 source locators from a separately supplied exact PDF.

No PDF, extracted body or quoted normative content is written to the repository.
The census reflects manually reviewed extraction corrections, not atomic rule
completeness or implementation conformance. Requires Poppler's pdftotext.
"""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import re
import subprocess

SOURCE_HASH = "6314a2a25dad5a7ed43559261767ac51226113a0954aade3d8f24b35b9ff981a"
SOURCE_BYTES = 2461934
REJECTED = {'cpp03:3.4.3:grammar:ple': 'Hyphenated Example label, not a grammar production.',
 'cpp03:3.4.3.2:grammar:ple': 'Hyphenated Example label, not a grammar production.',
 'cpp03:3.8:grammar:when': 'Prose introduces lifetime conditions, not a grammar production.',
 'cpp03:4:grammar:order': 'Prose introduces conversion order, not a grammar production.',
 'cpp03:4.4:grammar:form': 'Prose introduces pointer-type notation, not a grammar production.',
 'cpp03:5.1:grammar:used': 'Prose introduces allowed uses, not a grammar production.',
 'cpp03:6.5.1:grammar:label': 'Label in a code example, not a grammar production.',
 'cpp03:6.7:grammar:ly': 'Label in a code example, not a grammar production.',
 'cpp03:6.7:grammar:lx': 'Label in a code example, not a grammar production.',
 'cpp03:7.3.2:grammar:mar': 'Hyphenated grammar in prose, not a grammar production.',
 'cpp03:10.2:grammar:ple': 'Hyphenated Example label, not a grammar production.',
 'cpp03:12.6.1:grammar:ple': 'Hyphenated Example label, not a grammar production.',
 'cpp03:12.6.2:grammar:ple': 'Hyphenated Example label, not a grammar production.',
 'cpp03:13.1:grammar:tion': 'Hyphenated distinction in prose, not a grammar production.',
 'cpp03:13.3.1.4:grammar:lows': 'Hyphenated follows in prose, not a grammar production.',
 'cpp03:14.5.5.2:grammar:refers': 'Prose introduces specialization-reference cases, not a grammar '
                                  'production.',
 'cpp03:14.8.3:grammar:tion': 'Deduction prose introducing an example, not a grammar production.',
 'cpp03:14.8.3:grammar:deduction': 'Deduction prose introducing an example, not a grammar '
                                   'production.',
 'cpp03:18.2.1.4:grammar:ues': 'Hyphenated values in prose, not a grammar production.',
 'cpp03:21.3:grammar:tion': 'Hyphenated exception in prose, not a grammar production.',
 'cpp03:21.3.6.1:grammar:obtain': 'Prose introduces conditions, not a grammar production.',
 'cpp03:21.3.6.2:grammar:obtain': 'Prose introduces conditions, not a grammar production.',
 'cpp03:21.3.6.3:grammar:obtain': 'Prose introduces conditions, not a grammar production.',
 'cpp03:21.3.6.4:grammar:obtain': 'Prose introduces conditions, not a grammar production.',
 'cpp03:21.3.6.5:grammar:obtain': 'Prose introduces conditions, not a grammar production.',
 'cpp03:21.3.6.6:grammar:obtain': 'Prose introduces conditions, not a grammar production.',
 'cpp03:21.4:grammar:tions': 'Prose introduces function declarations, not a grammar production.',
 'cpp03:21.4:grammar:declarations': 'Prose introduces function declarations, not a grammar '
                                    'production.',
 'cpp03:21.4:grammar:larations': 'Prose introduces function declarations, not a grammar '
                                 'production.',
 'cpp03:21.4:grammar:rations': 'Prose introduces function declarations, not a grammar production.',
 'cpp03:23.3.5:grammar:exception': 'Prose introduces exception categories, not a grammar '
                                   'production.',
 'cpp03:26.3.1:grammar:follows': 'Prose introduces requirements, not a grammar production.',
 'cpp03:D.7.1.2:grammar:lows': 'Hyphenated follows in prose, not a grammar production.'}
CALL_SITE_FIXES = {
    '5': '1.9:p1', '7': '1.9:p7', '10': '1.9:p16', '13': '2.1:p1',
    '16': '2.5:p1', '21': '2.13:p1', '57': '5.2.2:p1', '61': '5.2.8:p1',
    '63': '5.2.9:p9', '102': '11.5:p1', '119': '13.3.1.2:p6',
    '133': '14.8.3:p1', '146': '17.3.1.3:p1', '158': '17.4.1.2:p1',
    '169': '17.4.3.1.4:p1', '170': '17.4.4.1:p1',
}
INVENTORY_SCOPE = "Source-locator census, not a count of atomic normative requirements. Numbered paragraphs, unnumbered section blocks, grammar productions, tables and footnotes are separate overlapping locators. Bullets, table cells and unnumbered member signatures inside paragraphs are not independent units. Footnotes use global IDs, unique note numbers and source pages; their call_sites are separate cross-references, with unresolved cases explicit. Confirmed false extraction candidates are excluded and retained in rejected_candidates."


def audit_shard(section):
    chapter = section.split('.')[0]
    if not chapter.isdigit():
        return 'annexes-A-E'
    n = int(chapter)
    for first, last, name in ((1, 4, 'clauses-01-04'), (5, 10, 'clauses-05-10'),
                             (11, 16, 'clauses-11-16'), (17, 22, 'clauses-17-22'),
                             (23, 27, 'clauses-23-27')):
        if first <= n <= last:
            return name
    raise ValueError('Unexpected chapter')


def parse_layout(text):
    """Parse private source text in memory; return only safe metadata."""
    pages = text.split('\f')
    if len(pages) < 750:
        raise ValueError('Layout text does not contain the normative page range')
    H=re.compile(r'^\s*(\d+(?:\.\d+)*|[A-E](?:\.\d+)*|Annex [A-E])\s+(.+?)\s+\[([^\]\s]+)\]\s*$')
    AH=re.compile(r'^\s*Annex ([A-E])\s+\[([a-z.]+)\]\s*$')
    P=re.compile(r'^(\d+[a-z]?)\s{2,}(\S.*)$')
    items=[];supp=[];sections=[];current=None;active=None;buffer=[];preamble=[];pending=None
    special_headers={'17.1.11':'narrow-oriented iostream classes','17.1.19':'wide-oriented iostream classes'}
    def bodyhash(text):return hashlib.sha256(text.encode()).hexdigest()
    def finish():
     nonlocal active,buffer
     if active is not None:
      body='\n'.join(buffer).strip();active['body']=body;active['sha256']=bodyhash(body);items.append(active)
     active=None;buffer=[]
    def prefix():
     nonlocal preamble
     if current and any(x.strip() for x in preamble):
      b='\n'.join(preamble).strip()
      if len(b)>10 and not re.fullmatch(r'\([a-z]+\)',b):
       supp.append({**current,'id':'cpp03:'+current['section']+':u1','kind':'unnumbered_block','body':b,'sha256':bodyhash(b)})
     preamble=[]
    def newsection(sec,title,tag,pdfpage):
     nonlocal current,active,buffer,pending
     finish();prefix();current={'section':sec,'title':title,'tag':tag,'pdf_page':pdfpage,'printed_page':pdfpage-28};sections.append(current)
     if pending:
      active={**current,'paragraph':pending,'id':'cpp03:'+sec+':p'+str(pending),'extraction_note':'Number-only label precedes section heading; inspect PDF layout.'};pending=None
    for pdfpage in range(29,751):
     lines=pages[pdfpage-1].splitlines(); footnote_mode=False
     for lineidx,line in enumerate(lines):
      if line.strip().startswith(('ISO/IEC 14882:2003',' ISO/IEC','© ISO/IEC','INTERNATIONAL STANDARD')):continue
      if line.strip()==str(pdfpage-28) and all(not x.strip() for x in lines[lineidx+1:]):continue
      if lineidx>10 and re.match(r'^\s*_{10,}\s*$',line) and (re.match(r'^\s*\d+[a-z]?\)',next((x for x in lines[lineidx+1:] if x.strip()),'')) or pdfpage==612):footnote_mode=True;continue
      if footnote_mode:
       fm=re.match(r'^\s*(\d+[a-z]?)\)\s+(.+)$',line)
       if fm and current:supp.append({**current,'id':'cpp03:'+current['section']+':footnote'+fm[1],'kind':'footnote','pdf_page':pdfpage,'printed_page':pdfpage-28,'title':'Footnote '+fm[1],'body':'','sha256':''})
       continue
      h=H.match(line);ah=AH.match(line)
      if ah:newsection(ah[1],'Annex '+ah[1],ah[2],pdfpage);continue
      if h:newsection(h[1],h[2],h[3],pdfpage);continue
      untagged=next((k for k,v in special_headers.items() if line.strip()==k+' '+v),None)
      if untagged:newsection(untagged,special_headers[untagged],None,pdfpage);continue
      # Running heads are not source paragraphs; tagged headings and numbered paragraphs were retained.
      if lineidx<6 and not P.match(line):continue
      if re.fullmatch(r'\d+',line):
       n=int(line)
       if pdfpage==459 and n==1:
        supp.append({**current,'id':'cpp03:'+current['section']+':number-label1','kind':'numbering_anomaly','pdf_page':pdfpage,'printed_page':pdfpage-28,'title':'Detached number-only label 1 before next heading','body':'','sha256':''});continue
       finish();prefix();active={**current,'paragraph':n,'pdf_page':pdfpage,'printed_page':pdfpage-28,'id':'cpp03:'+current['section']+':p'+str(n),'extraction_note':'Standalone/overprinted paragraph label; body not inferred.'};continue
      p=P.match(line)
      if p:
       finish();prefix();active={**current,'paragraph':int(p[1]) if p[1].isdigit() else p[1],'pdf_page':pdfpage,'printed_page':pdfpage-28,'id':'cpp03:'+current['section']+':p'+p[1]};buffer=[p[2]];continue
      if active is not None:buffer.append(line)
      else:preamble.append(line)
      # Tables, grammar productions and footnotes are indexed separately, without claiming each row/alternative audited.
      if current:
       tm=re.match(r'^\s*Table (\d+)\s*[—–-]?\s*(.*)$',line)
       gm=re.match(r'^\s+([a-z][a-z0-9-]*):\s*(?:one of)?\s*$',line)
       fm=re.match(r'^\s*(\d+[a-z]?)\)\s+(.+)$',line)
       if tm:supp.append({**current,'id':'cpp03:'+current['section']+':table'+tm[1],'kind':'table','pdf_page':pdfpage,'printed_page':pdfpage-28,'title':tm[2],'body':'','sha256':''})
       if gm and gm[1] not in ('public','private','protected','default','case'):supp.append({**current,'id':'cpp03:'+current['section']+':grammar:'+gm[1],'kind':'grammar_production','pdf_page':pdfpage,'printed_page':pdfpage-28,'title':gm[1],'body':'','sha256':''})
    
    finish();prefix()
    unique = {}
    for item in supp:
        unique.setdefault(item['id'], item)
    supp = list(unique.values())
    rejected = [{key: item[key] for key in ('id', 'section', 'printed_page', 'pdf_page')}
                | {'reason': REJECTED[item['id']]}
                for item in supp if item['id'] in REJECTED]
    supp = [item for item in supp if item['id'] not in REJECTED]
    units = []
    for item in items + supp:
        unit = {key: item.get(key) for key in ('id', 'section', 'tag', 'printed_page', 'pdf_page')}
        unit['unit_kind'] = 'numbered_paragraph' if 'paragraph' in item else item['kind']
        unit['paragraph'] = item.get('paragraph')
        if item.get('extraction_note'):
            unit['extraction_note'] = item['extraction_note']
        if item.get('kind') == 'footnote':
            number = item['id'].split('footnote')[1]
            pattern = re.compile(r'(?<!\d)(?<!\d\.)' + number + r'\)')
            calls = [candidate['id'] for candidate in items + supp
                     if candidate.get('body')
                     and candidate['printed_page'] in (item['printed_page'], item['printed_page'] - 1, item['printed_page'] - 2)
                     and pattern.search(candidate['body'])]
            if number in CALL_SITE_FIXES:
                calls = ['cpp03:' + CALL_SITE_FIXES[number]]
            if len(calls) != 1:
                calls = []
            unit.update(id='cpp03:footnotes:footnote' + number, section='footnotes', tag=None,
                        footnote_number=number, call_sites=calls,
                        call_site_status='source_reference_located' if calls else 'not_uniquely_located',
                        audit_shard=audit_shard(item['section']))
        units.append(unit)
    if len({unit['id'] for unit in units}) != len(units):
        raise ValueError('Duplicate source locators; manual layout review required')
    return {'edition': 'ISO/IEC 14882:2003', 'source_sha256': SOURCE_HASH,
            'source_bytes': SOURCE_BYTES, 'pdf_pages': 786, 'printed_pages': 757,
            'printed_to_pdf_offset': 28, 'inventory_scope': INVENTORY_SCOPE,
            'counts': dict(collections.Counter(unit['unit_kind'] for unit in units)),
            'units': units, 'rejected_candidates': rejected}


def check_original_pdf(path):
    data = path.read_bytes()
    if len(data) != SOURCE_BYTES or hashlib.sha256(data).hexdigest() != SOURCE_HASH:
        raise ValueError('Not the exact independently verified 2003 PDF; refuse edition substitution')



def compare_annex_e(layout, java_source):
    """Compare interval facts without returning or publishing the source table."""
    annex = "\n".join(layout.split("\f")[748:750])
    annex = "\n".join(line for line in annex.splitlines()
                      if not line.strip().startswith(("ISO/IEC", " ISO/IEC", "© ISO/IEC")))
    if "Latin:" not in annex:
        raise ValueError("Annex E table marker absent; review the source layout")
    table = annex.split("Latin:", 1)[1]
    pairs = [(int(lo, 16), int(hi or lo, 16))
             for lo, hi in re.findall(r"\b([0-9a-fA-F]{4})(?:[–—-]([0-9a-fA-F]{4}))?\b", table)]
    match = re.search(r"RANGES\s*=\s*\{(.*?)\}", java_source, re.S)
    if not match:
        raise ValueError("Implementation RANGES initializer absent")
    values = [int(value, 16) for value in re.findall(r"0x([0-9a-fA-F]+)", match[1])]
    if len(values) % 2:
        raise ValueError("Implementation interval endpoint count is odd")
    implementation = list(zip(values[::2], values[1::2]))
    return {"source_range_count": len(pairs), "implementation_range_count": len(implementation),
            "exact_order_equal": pairs == implementation,
            "scope": "Interval-table facts only; not lexer-context or implementation conformance"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pdf', type=Path, required=True,
                        help='Separately supplied original; never added to the repository')
    parser.add_argument('--output', type=Path, required=True, help='Safe locator JSON output')
    parser.add_argument('--annex-e-java', type=Path, help='Optionally compare the implementation interval table; only counts/equality are printed')
    args = parser.parse_args()
    check_original_pdf(args.pdf)
    result = subprocess.run(['pdftotext', '-layout', str(args.pdf), '-'],
                            check=True, stdout=subprocess.PIPE)
    layout = result.stdout.decode('utf-8')
    manifest = parse_layout(layout)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'counts': manifest['counts'], 'rejected': len(manifest['rejected_candidates'])}))
    if args.annex_e_java:
        print(json.dumps({'annex_e': compare_annex_e(layout, args.annex_e_java.read_text())}))
    print('Extraction reproduces reviewed source anchors; it does not prove exhaustive rules or conformance.')


if __name__ == '__main__':
    main()
