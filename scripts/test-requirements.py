#!/usr/bin/env python3
"""Synthetic contracts only; no normative claims or real requirement IDs."""
import copy
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('requirements',Path(__file__).with_name('check-requirements.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
ID='CPG-CPP03-REQ-900001'

def fixture():
    row={'id':ID,'semantic_key':'synthetic.example','owner_issue':197,'status_owners':dict.fromkeys(['readiness','implementation','verification'],197),'title':'Synthetic validator example','basis':{'kind':'policy','source_id':'synthetic','locators':['example:1'],'confidence':'policy','review_note':'Synthetic, not a standard rule'},'conditions':[],'exceptions':[],'dependencies':[],'rule':'Check the fixture only.','classification':['policy'],'cpg_consequence':'No production semantics.','platform_policy':'No target assumption.','applicability':{'status':'applicable','justification':'Synthetic test case.'},'proposed_checks':[{'id':'example','description':'Assert the synthetic property.'}],'evidence':[],'questions':[],'states':{'readiness':'draft','implementation':'missing','verification':'not_run'}}
    d={'source_id':'synthetic','locator':'example:1','status':'requirements','requirement_ids':[ID],'justification':'Synthetic mapping.','question_issue':None}
    ledger={ID:{'semantic_key':'synthetic.example','status':'active'}}
    return [row],[d],ledger,{'synthetic':{'kind':'policy','locators':['example:1','example:2']}}

class RequirementsTests(unittest.TestCase):
    def test_minimal(self):m.validate(*fixture())
    def test_invalid_variants(self):
        changes={
            'duplicate ID':lambda r,d,l,s:r.append(copy.deepcopy(r[0])),
            'unknown field':lambda r,d,l,s:r[0].update(extra=True),
            'bad ID':lambda r,d,l,s:r[0].update(id='bad'),
            'foreign locator':lambda r,d,l,s:r[0]['basis'].update(locators=['absent']),
            'wrong edition':lambda r,d,l,s:r[0]['basis'].update(kind='iso2003'),
            'dependency':lambda r,d,l,s:r[0].update(dependencies=['absent']),
            'reverse link':lambda r,d,l,s:d.clear(),
            'unknown requirement':lambda r,d,l,s:d[0].update(requirement_ids=['absent']),
            'empty rule':lambda r,d,l,s:r[0].update(rule=' '),
            'false passed':lambda r,d,l,s:r[0]['states'].update(verification='passed'),
            'false failed':lambda r,d,l,s:r[0]['states'].update(verification='failed'),
            'false partial':lambda r,d,l,s:r[0]['states'].update(verification='partial'),
            'blocked':lambda r,d,l,s:r[0]['states'].update(readiness='blocked'),
            'NA mismatch':lambda r,d,l,s:r[0]['applicability'].update(status='not_applicable'),
            'unresolved reviewed':lambda r,d,l,s:(r[0]['basis'].update(confidence='unresolved'),r[0]['states'].update(readiness='reviewed')),
            'duplicate check':lambda r,d,l,s:r[0]['proposed_checks'].append(copy.deepcopy(r[0]['proposed_checks'][0])),
            'semantic reuse':lambda r,d,l,s:r[0].update(semantic_key='different'),
            'status owner':lambda r,d,l,s:r[0]['status_owners'].update(verification=0),
        }
        for name,change in changes.items():
            with self.subTest(name=name):
                data=fixture();change(*data)
                with self.assertRaises(ValueError):m.validate(*data)
    def test_history(self):
        data=fixture();prior=copy.deepcopy(data[2]);prior[ID]['semantic_key']='old'
        with self.assertRaises(ValueError):m.validate(*data,previous=prior)
        prior[ID]={'semantic_key':'synthetic.example','status':'retired'}
        with self.assertRaises(ValueError):m.validate(*data,previous=prior)
        with self.assertRaises(ValueError):m.validate([],[],{},data[3],previous=prior)
        m.validate([],[],prior,data[3],previous=prior)
    def test_many_to_many(self):
        r,d,l,s=fixture();other=copy.deepcopy(r[0]);other.update(id='CPG-CPP03-REQ-900002',semantic_key='synthetic.second');r.append(other);l[other['id']]={'semantic_key':other['semantic_key'],'status':'active'};d[0]['requirement_ids'].append(other['id'])
        r[0]['basis']['locators'].append('example:2');d.append(dict(d[0],locator='example:2',requirement_ids=[ID]));m.validate(r,d,l,s)
    def test_dispositions_and_na(self):
        r,d,l,s=fixture();r[0]['applicability']['status']='not_applicable';r[0]['states']['implementation']='not_applicable';m.validate(r,d,l,s)
        for status,question in [('no_independent_requirement',None),('unresolved',197)]:
            m.validate([], [dict(d[0],status=status,question_issue=question,requirement_ids=[])],{},s)
    def test_real_pinned_evidence_shape(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);subprocess.run(['git','init','-q',str(root)],check=True)
            (root/'check.txt').write_text('assert synthetic property\n')
            subprocess.run(['git','add','check.txt'],cwd=root,check=True)
            subprocess.run(['git','-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture'],cwd=root,check=True)
            sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
            data=fixture();row=data[0][0];row['states']={'readiness':'reviewed','implementation':'implemented','verification':'passed'}
            row['evidence']=[{'check_id':'example','path':'check.txt','assertion':'assert synthetic property','revision':sha,'command':'synthetic command; fixture only','result':'pass'}]
            m.validate(*data,root=root)
            for field,value in [('assertion','missing assertion'),('revision','0'*40),('path','../escape'),('check_id','missing'),('result','fail')]:
                bad=copy.deepcopy(data);bad[0][0]['evidence'][0][field]=value
                with self.subTest(field=field),self.assertRaises(ValueError):m.validate(*bad,root=root)
    def test_duplicate_json_key(self):
        with self.assertRaises(ValueError):m.decode('{"id":1,"id":2}')
if __name__=='__main__':unittest.main()
