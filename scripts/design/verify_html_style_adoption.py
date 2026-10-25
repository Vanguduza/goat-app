#!/usr/bin/env python3
"""Source-level guard against reintroducing a separate/default app visual system.

This verifies adoption, not screenshot fidelity, runtime route coverage or owner approval.
The original HTML, source registry and native golden references remain authoritative.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
from collections import Counter

ROOT=Path(__file__).resolve().parents[2]
REFERENCE="docs/ux/animal-farm-visual-lock/references/animal-farm-locked-homes.html"
EXPECTED="94766ebbd122861b6c4dbfe65e6908b21895bc8ea44596475899cb45fc49fd11"
ROOTS=("app/src/main","feature/goat/src/main","feature/rabbit/src/main","feature/ops/src/main")
CONTROLS=("Button","TextButton","OutlinedButton","Card","ElevatedCard","TextField","OutlinedTextField","Scaffold")
COMPONENTS=("FarmOperationalPage","AnimalFarmCanvas","AnimalFarmModuleHeader","FarmModuleTools","FarmSectionTabs","FarmMetricStrip","FarmMetricBars","FarmWorkflowTimeline","FarmOutlinedTextField","FarmActionButton","FarmPrimaryButton","FarmResourceAction","FarmEntitySelector","FarmSearchSelector","FarmIllustratedSectionSurface")


def violations(text):
    errors=[]
    for name in CONTROLS:
        if re.search(r"(?m)^import androidx\.compose\.material3\."+name+r"(?:\s|$)",text):
            errors.append("Default Material control bypass: "+name)
        if re.search(r"androidx\.compose\.material3\."+name+r"\s*\(",text):
            errors.append("Fully qualified default control bypass: "+name)
    if re.search(r"\b(?:MaterialTheme|Typography|lightColorScheme|darkColorScheme)\s*\(",text):
        errors.append("Feature declares a separate theme instead of using the HTML-derived native theme")
    return errors


def audit(root):
    issues=[];records=[];counts=Counter()
    reference=root/REFERENCE
    if hashlib.sha256(reference.read_bytes()).hexdigest()!=EXPECTED:
        issues.append({"path":REFERENCE,"errors":["Approved HTML identity changed; do not silently regenerate authority"]})
    theme=root/"core/design/src/main/kotlin/com/farmos/core/design/FarmOsTheme.kt"
    if "typography = AnimalFarmHtmlTypography" not in theme.read_text():
        issues.append({"path":str(theme.relative_to(root)),"errors":["Global native HTML typography is not selected"]})
    if 'android:label="Animal Farm"' not in (root/'app/src/main/AndroidManifest.xml').read_text():
        issues.append({"path":"app/src/main/AndroidManifest.xml","errors":["Launcher display brand is not Animal Farm"]})
    for folder in ROOTS:
        for path in sorted((root/folder).rglob('*.kt')):
            text=path.read_text()
            if '@Composable' not in text:continue
            errors=violations(text)
            if errors:issues.append({"path":str(path.relative_to(root)),"errors":errors})
            used=[c for c in COMPONENTS if re.search(r'\b'+c+r'\b',text)]
            counts.update(used)
            records.append({"path":str(path.relative_to(root)),"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"shared_components":used,"screen_id_mentions":sorted(set(re.findall(r'FOS-[A-Z]+-[0-9]{3}(?:-[A-Z])?',text)))})
    return {"schema_version":1,"purpose":"SOURCE_ADOPTION_NOT_VISUAL_CERTIFICATION","reference_sha256":EXPECTED,"source_files":len(records),"component_consumer_counts":dict(counts),"issues":issues,"files":records,"visual_approval":"NOT_GRANTED"}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--output',type=Path);a=p.parse_args()
    result=audit(a.root.resolve())
    if a.output:
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='files'},indent=2))
    raise SystemExit(1 if result['issues'] else 0)

if __name__=='__main__': main()
