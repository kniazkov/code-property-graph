#!/usr/bin/env python3
"""Focused renderer checks; uses pilot copies, never executes conformance cases."""
import importlib.util
import json
from pathlib import Path
import re
import unittest
import tempfile
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('renderer',Path(__file__).with_name('render-requirements.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)

def index_rows(output):
    data=json.loads(output)
    return data['dispositions']+[dict(source_id=s,locator=l,status='unprocessed')
        for s,sections in data['unprocessed'].items() for locators in sections.values() for l in locators]

class Rendering(unittest.TestCase):
    def setUp(self):
        self.rows=[r for p in sorted((g.DATA/'records').glob('*.jsonl')) for r in g.v.lines(p)]
        self.ds=g.v.lines(g.DATA/'dispositions.jsonl')
        self.ledger=g.v.read(g.DATA/'ids.json');self.sources=g.v.load_sources()
    def render(self):
        return g.render(self.rows,self.ds,self.ledger,self.sources)
    def test_ordering_and_repeatability(self):
        expected=self.render()
        self.rows.reverse();self.ds.reverse()
        for s in self.sources.values(): s['locators'].reverse()
        self.assertEqual(expected,self.render())
        self.assertEqual(expected,self.render())
    def test_stable_anchors_and_dependencies(self):
        id=self.rows[0]['id'];self.rows[0]['title']='Changed <title> | [text]'
        self.rows[1]['dependencies']=[id]
        out=self.render()['REQUIREMENTS.md']
        self.assertIn(f'<a id="{id.lower()}"></a>',out)
        self.assertIn(f'[{id}](#{id.lower()})',out)
        self.assertIn('&lt;title&gt; \\| \\[text\\]',out)
        self.assertEqual(len(self.rows),len(re.findall('<a id=',out)))
    def test_provisional_and_independent_states(self):
        row=self.rows[0];row['states']['readiness']='blocked'
        row['basis']['confidence']='unresolved';row['questions']=[dict(issue=199,question='Resolve source?')]
        out=self.render()['REQUIREMENTS.md']
        self.assertIn('**PROVISIONAL**',out)
        self.assertIn('readiness=blocked; implementation=partial; verification=not_run',out)
        self.assertIn('None recorded; no execution claim.',out)
        self.assertIn('#199: Resolve source?',out)
    def test_every_locator_and_reciprocal_links(self):
        out=self.render();idx=index_rows(out['requirements/source-index.json'])
        expected={(s,l) for s,m in self.sources.items() for l in m['locators']}
        self.assertEqual(expected,{(d['source_id'],d['locator']) for d in idx})
        self.assertEqual(len(expected),len(idx))
        for row in self.rows:
            for loc in row['basis']['locators']:
                d=next(d for d in idx if d['source_id']==row['basis']['source_id'] and d['locator']==loc)
                self.assertIn(row['id'],d['requirement_ids'])
        self.assertTrue(any(d['status']=='unprocessed' for d in idx))
    def test_nonrequirement_and_unresolved_dispositions(self):
        used={d['locator'] for d in self.ds}
        free=[l for l in self.sources['iso14882-2003']['locators'] if l not in used][:2]
        for loc,status,issue in zip(free,['no_independent_requirement','unresolved'],[None,199]):
            self.ds.append(dict(source_id='iso14882-2003',locator=loc,status=status,requirement_ids=[],justification='Synthetic disposition.',question_issue=issue))
        out=self.render()['SOURCE-INDEX.md']
        self.assertIn('no\\_independent\\_requirement',out)
        self.assertIn('unresolved',out);self.assertIn('#199',out)
    def test_invalid_input_prevents_render(self):
        self.rows[0]['dependencies']=['CPG-CPP03-REQ-999999']
        with self.assertRaisesRegex(ValueError,'dangling'): self.render()
        self.rows[0]['dependencies']=[]
        self.ds.remove(next(d for d in self.ds if d['status']=='requirements'))
        with self.assertRaisesRegex(ValueError,'missing reverse'): self.render()
    def test_empty_registry_has_no_false_completion(self):
        out=g.render([],[],{},self.sources)
        self.assertIn('0 authored requirements.',out['REQUIREMENTS.md'])
        self.assertTrue(all(d['status']=='unprocessed' for d in index_rows(out['requirements/source-index.json'])))
    def test_check_mode_detects_staleness_without_writing(self):
        with tempfile.TemporaryDirectory() as tmp:
            data=Path(tmp)/'requirements';data.mkdir()
            outputs={'REQUIREMENTS.md':'expected\n'}
            target=Path(tmp)/'REQUIREMENTS.md'
            with patch.object(g,'DATA',data), patch.object(g.v,'lines',return_value=[]), patch.object(g.v,'read',return_value={}), patch.object(g.v,'load_sources',return_value={}), patch.object(g,'render',return_value=outputs):
                with patch('sys.argv',['render','--check']):
                    with self.assertRaisesRegex(SystemExit,'Stale'): g.main()
                    self.assertFalse(target.exists())
                with patch('sys.argv',['render']): g.main()
                with patch('sys.argv',['render','--check']): g.main()
                target.write_text('stale\n')
                with patch('sys.argv',['render','--check']):
                    with self.assertRaisesRegex(SystemExit,'Stale'): g.main()
                self.assertEqual('stale\n',target.read_text())

    def test_generated_links_and_anchors(self):
        out=self.render()
        for name,body in out.items():
            if not name.endswith('.md'): continue
            for dest in re.findall(r'(?<!!)\[[^\n]*?\]\(([^)]+)\)',body):
                path,_,frag=dest.partition('#');target=path or name
                if target in out:
                    if frag: self.assertIn(f'id="{frag}"',out[target])
                else: self.assertTrue((g.DATA.parent/target).exists(),target)

if __name__=='__main__': unittest.main()
