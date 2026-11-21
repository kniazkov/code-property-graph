#!/usr/bin/env python3
"""Synthetic evidence tests, not C++ conformance execution."""
import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


def module(name,file):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file))
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

p=module('proof','render-proof.py');fixtures=module('fixtures','test-requirements.py')

class Proof(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.data=fixtures.fixture();self.inspections=[]
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        (self.root/'test.txt').write_text('methodExample\nassert example property\n')
        subprocess.run(['git','add','test.txt'],cwd=self.root,check=True)
        subprocess.run(['git','-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture'],cwd=self.root,check=True)
        self.rev=subprocess.check_output(['git','rev-parse','HEAD'],cwd=self.root,text=True).strip()
    def render(self):return p.render(*self.data,self.inspections,root=self.root)
    def inspection(self):
        return dict(requirement_id=fixtures.ID,check_id='example',path='test.txt',method='methodExample',assertion='assert example property',revision=self.rev,property='Synthetic property.',limitations='Not executed.')
    def evidence(self,result='pass'):
        return dict(check_id='example',path='test.txt',assertion='assert example property',revision=self.rev,command='synthetic command; not actually executed',result=result)
    def test_planned_only_never_supplies_pass(self):
        out=self.render();self.assertIn('0 recorded execution entries',out)
        self.assertIn('No recorded execution\\.',out);self.assertNotIn('recorded result:** pass',out)
    def test_inspection_stays_not_run_and_has_reverse_link(self):
        self.inspections=[self.inspection()];out=self.render()
        self.assertIn('Inspected existing assertions — not run',out)
        self.assertIn('verification=not\\_run',out)
        self.assertIn('Inspected only',out);self.assertIn('#cpg-cpp03-req-900001',out)
        self.assertIn(self.rev,out);self.assertNotIn('recorded result:** pass',out)
    def test_missing_assertion_method_revision_or_path_rejected(self):
        for field,value in [('assertion','absent'),('method','absent'),('revision','0'*40),('path','absent.txt'),('path','../escape'),('check_id','absent'),('requirement_id','absent')]:
            self.inspections=[dict(self.inspection(),**{field:value})]
            with self.subTest(field=field),self.assertRaises(ValueError):self.render()
    def test_inspection_cannot_smuggle_execution(self):
        self.inspections=[dict(self.inspection(),result='pass')]
        with self.assertRaises(ValueError):self.render()
        self.inspections=[];self.data[0][0]['states']['verification']='passed'
        with self.assertRaises(ValueError):self.render()
    def test_recorded_results_remain_historical_and_commands_are_literal(self):
        row=self.data[0][0];row['states']['verification']='partial';row['evidence']=[self.evidence()]
        out=self.render();self.assertIn('recorded result:** pass',out)
        self.assertIn('Pinned results are historical records.',out)
        self.assertIn('synthetic command; not actually executed',out)
        row['evidence']=[self.evidence('fail')];row['states']['verification']='failed'
        self.assertIn('recorded result:** fail',self.render())
    def test_missing_recorded_assertion_and_dangling_reverse_rejected(self):
        row=self.data[0][0];row['states']['verification']='partial';row['evidence']=[dict(self.evidence(),assertion='absent')]
        with self.assertRaises(ValueError):self.render()
        row['evidence']=[];row['states']['verification']='not_run';self.data[1].clear()
        with self.assertRaises(ValueError):self.render()
    def test_ordering_repeatability_and_safe_fence(self):
        self.inspections=[self.inspection()];first=self.render();self.assertEqual(first,self.render())
        self.data[0][0]['basis']['locators'].append('example:2')
        self.data[1].append(dict(self.data[1][0],locator='example:2'))
        expected=self.render();self.data[1].reverse();self.data[0][0]['basis']['locators'].reverse()
        self.assertEqual(expected,self.render())
        self.assertTrue(p.literal('```\ntext').startswith('````text\n'))
    def test_check_mode_does_not_rewrite_stale_output(self):
        data=self.root/'data';data.mkdir();(self.root/'docs').mkdir()
        target=self.root/'docs/PROOF.md'
        with patch.object(p,'ROOT',self.root),patch.object(p,'DATA',data),patch.object(p.v,'lines',return_value=[]),patch.object(p.v,'read',return_value={}),patch.object(p.v,'load_sources',return_value={}),patch.object(p,'render',return_value='expected\n'):
            with patch('sys.argv',['proof','--check']):
                with self.assertRaises(SystemExit):p.main()
            self.assertFalse(target.exists())
            with patch('sys.argv',['proof']):p.main()
            with patch('sys.argv',['proof','--check']):p.main()
            target.write_text('stale\n')
            with patch('sys.argv',['proof','--check']):
                with self.assertRaises(SystemExit):p.main()
            self.assertEqual('stale\n',target.read_text())

if __name__=='__main__':unittest.main()
