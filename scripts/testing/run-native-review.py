#!/usr/bin/env python3
"""Run the exact reviewed native test classes for one declared matrix cell.

This does not approve screenshots or claim unobserved screens. Canonical product scope
is all 545 IDs; this selected suite records whatever actual owners it renders.
"""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]

def require(ok,message):
    if not ok: raise ValueError(message)

def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True,timeout=30).strip()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mode',choices=['LIGHT','DARK','OUTDOOR'],required=True)
    p.add_argument('--width',type=int,choices=[360,411,780],required=True)
    p.add_argument('--scale',type=float,choices=[1,1.3,2],required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--timeout-seconds',type=int,default=2100)
    p.add_argument('--verify-selection-only',action='store_true')
    a=p.parse_args()
    selection=json.loads((ROOT/'scripts/testing/native-review-selection.json').read_text())
    require(selection['schema_version']==1 and bool(selection['classes']),'Empty test selection')
    by_task={};expected=set()
    for row in selection['classes']:
        task=row['task'];require(task in [':app:testDebugUnitTest',':feature:goat:testDebugUnitTest'],'Unknown test task')
        require(re.fullmatch(r'com\.farmos\.(?:app|feature\.goat)\.[A-Za-z0-9_]+',row['class']),'Invalid native test class')
        src=ROOT/row['source'];require(src.resolve().is_relative_to(ROOT) and src.is_file(),'Invalid native test source')
        raw=src.read_bytes();require(hashlib.sha256(raw).hexdigest()==row['sha256'],'Stale selected source '+row['source'])
        methods=sorted(re.findall(r'@Test(?:\([^)]*\))?\s+fun\s+(\w+)\s*\(',raw.decode()))
        require(methods==row['methods'] and len(set(methods))==len(methods),'Native test methods changed '+row['class'])
        require('NativeModuleReviewRule()' in raw.decode(),'Native observation rule missing')
        by_task.setdefault(task,[]).append(row)
        for method in methods:
            key=(row['class'],method);require(key not in expected,'Duplicate native case');expected.add(key)
    if a.verify_selection_only:
        print(json.dumps({'classes':len(selection['classes']),'cases':len(expected),'selection':'PASS'}));return
    require(not git('status','--porcelain'),'Native review requires a clean source checkout')
    output=a.output.resolve();require(not output.exists(),'Use a new evidence directory per exact run/cell')
    output.mkdir(parents=True)
    head=git('rev-parse','HEAD');tree=git('rev-parse','HEAD^{tree}')
    (output/'commit.txt').write_text(head+'\n');(output/'tree.txt').write_text(tree+'\n')
    (output/'worktree-status.txt').write_text('')
    env=dict(os.environ,GOAT_MODULE_REVIEW_MODE=a.mode,GOAT_MODULE_REVIEW_WIDTH=str(a.width),GOAT_MODULE_REVIEW_SCALE=str(a.scale),GOAT_MODULE_REVIEW_DIR=str(output/'native-module-review'))
    cmd=['./gradlew']
    for task in [':feature:goat:testDebugUnitTest',':app:testDebugUnitTest']:
        if task not in by_task:continue
        cmd.append(task)
        for row in by_task[task]:cmd+=['--tests',row['class']]
    cmd+=['-I','scripts/testing/native-module-review.gradle','-I','scripts/testing/fresh-test-evidence.gradle','--no-daemon','--no-parallel','--max-workers=2','--continue','--console=plain','--stacktrace','-Dorg.gradle.jvmargs=-Xmx2048m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8','-Pkotlin.compiler.execution.strategy=in-process']
    begin=time.time();started=dt.datetime.now(dt.timezone.utc).isoformat();code=None
    with (output/'gradle.log').open('w') as log:
        process=subprocess.Popen(cmd,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:code=process.wait(timeout=a.timeout_seconds)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGTERM)
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=10)
            code=124
    cases={};missing=[]
    for row in selection['classes']:
        module=row['task'].removesuffix(':testDebugUnitTest').strip(':').replace(':','/')
        source=ROOT/module/'build/test-results/testDebugUnitTest'/('TEST-'+row['class']+'.xml')
        if not source.is_file() or source.stat().st_mtime<begin:
            missing.append(row['class']);continue
        dest=output/module/'test-results'/source.name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest)
        suite=ET.fromstring(source.read_bytes())
        for case in suite.findall('testcase'):
            key=(case.get('classname') or suite.get('name'),case.get('name'))
            status='FAIL' if case.find('failure') is not None or case.find('error') is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
            require(key not in cases,'Duplicate emitted native case');cases[key]=status
    dirty=git('status','--porcelain');(output/'worktree-status.txt').write_text(dirty+'\n' if dirty else '')
    accepted=code==0 and not dirty and git('rev-parse','HEAD')==head and set(cases)==expected and all(v=='PASS' for v in cases.values())
    result={'source_commit':head,'source_tree':tree,'started_at':started,'finished_at':dt.datetime.now(dt.timezone.utc).isoformat(),'mode':a.mode,'width':a.width,'scale':a.scale,'command':cmd,'gradle_exit':code,'expected_cases':len(expected),'observed_cases':len(cases),'passed':sum(v=='PASS' for v in cases.values()),'failed':sum(v=='FAIL' for v in cases.values()),'skipped':sum(v=='SKIP' for v in cases.values()),'missing_classes':missing,'unobserved_cases':[list(x) for x in sorted(expected-set(cases))],'all_selected_tests_passed':accepted,'visual_approval':'NOT_GRANTED'}
    (output/'matrix-result.json').write_text(json.dumps(result,indent=2)+'\n')
    (output/'images.sha256').write_text(''.join(hashlib.sha256(image.read_bytes()).hexdigest()+'  '+str(image.relative_to(output))+'\n' for image in sorted(output.rglob('*.png'))))
    print(json.dumps(result,indent=2))
    raise SystemExit(0 if accepted else 1)

if __name__=='__main__': main()
