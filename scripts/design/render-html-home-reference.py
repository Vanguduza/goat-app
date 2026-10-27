#!/usr/bin/env python3
"""Render the unchanged approved HTML with its own controls; capture-only framing is disclosed.

This script never changes repository references, installs new fonts, uploads farm data,
uses a generated redesign, or treats a screenshot as visual approval.
"""
from pathlib import Path
from html.parser import HTMLParser
import argparse
import hashlib
import json
import os
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
REFERENCE=ROOT/'docs/ux/animal-farm-visual-lock/references/animal-farm-locked-homes.html'
EXPECTED='94766ebbd122861b6c4dbfe65e6908b21895bc8ea44596475899cb45fc49fd11'

class Embedded(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True);self.document=None
    def handle_starttag(self,tag,attrs):
        row=dict(attrs)
        if tag=='iframe' and row.get('id')=='codex-visualization':self.document=row.get('data-srcdoc')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--chrome')
    args=parser.parse_args()
    raw=REFERENCE.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=EXPECTED:raise SystemExit('Approved HTML identity differs; do not silently update the reference.')
    extracted=Embedded();extracted.feed(raw.decode());inner=extracted.document
    if inner is None or 'data-role="management"' not in inner or 'data-role="worker"' not in inner:raise SystemExit('Missing approved role controls')
    chrome=args.chrome or shutil.which('google-chrome') or shutil.which('chromium') or shutil.which('chromium-browser')
    if not chrome:raise SystemExit('A qualified local Chromium executable is required')
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    cases=[('management','light',None),('management','dark',None),('worker','light',None),('worker','dark',None),('management','light','animals'),('management','light','settings'),('management','light','task-health'),('worker','light','task-nest')]
    results=[]
    for role,theme,sheet in cases:
        name='reference-'+(sheet or role)+'-'+theme
        extra=''
        if sheet:
            selector = '[data-task="'+sheet.removeprefix('task-')+'"]' if sheet.startswith('task-') else '[data-open="'+sheet+'"]'
            extra="root.querySelector("+json.dumps(selector)+").click();"
        script="""<script>window.addEventListener('load',()=>{
const root=document.getElementById('animal-farm-locked-homes');
root.querySelector('[data-role="ROLE"]').click();
root.querySelector('[data-open="settings"]').click();
root.querySelector('[data-theme="THEME"]').click();
root.querySelector('[data-close]').click();
const phone=document.getElementById('af-phone');phone.style.margin='0';phone.style.borderRadius='0';phone.style.border='0';
root.style.margin='0';root.querySelector('.viz-row').style.display='none';
root.querySelector('#af-direction-label').parentElement.style.display='none';
document.body.style.setProperty('padding','0','important');
EXTRA
});</script>"""
        script=script.replace('ROLE',role).replace('THEME',theme).replace('EXTRA',extra)
        page=output/(name+'.html');page.write_text(inner.replace('</body>',script+'</body>'))
        image=output/(name+'.png')
        if image.exists():raise SystemExit('Evidence output exists: '+str(image))
        profile=output/(name+'-browser-profile')
        command=[chrome,'--headless','--no-sandbox','--disable-dev-shm-usage','--hide-scrollbars','--force-device-scale-factor=1','--window-size=390,940','--virtual-time-budget=5000','--user-data-dir='+str(profile),'--screenshot='+str(image),page.as_uri()]
        proc=subprocess.run(command,capture_output=True,text=True,timeout=70)
        (output/(name+'.log')).write_text(proc.stdout+'\n'+proc.stderr)
        if proc.returncode or not image.is_file():raise SystemExit('Chromium reference render failed: '+name)
        results.append({'name':name,'role':role,'theme':theme,'sheet':sheet,'image':image.name,'image_sha256':hashlib.sha256(image.read_bytes()).hexdigest(),'bytes':image.stat().st_size})
        # Profile data is local browser runtime, not a deliverable or identity authority.
        shutil.rmtree(profile,ignore_errors=True)
    (output/'reference-source.json').write_text(json.dumps({'source_path':str(REFERENCE.relative_to(ROOT)),'source_sha256':EXPECTED,'source_unchanged':hashlib.sha256(REFERENCE.read_bytes()).hexdigest()==EXPECTED,'method':'Actual source role/theme/sheet handlers with capture-only removal of the external comparison toolbar, ornamental phone frame and caption','viewport':[390,940],'data':'Original HTML demonstration fixture, not real farm data','renders':results,'approval':'NOT_GRANTED'},indent=2)+'\n')
    print(json.dumps({'source_sha256':EXPECTED,'renders':len(results),'approved':False}))

if __name__=='__main__':main()
