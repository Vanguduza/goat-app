// Reviewed corrections to initial name-based assignments; no reference or source IDs change.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const file=path.join(root,'registry/page-alignment.json');
const x=JSON.parse(fs.readFileSync(file,'utf8'));
const overrides={
 'FOS-GOAT-025':'P04','FOS-GOAT-028':'P06','FOS-GOAT-029':'P06','FOS-GOAT-040':'P06','FOS-GOAT-047':'P05',
 'FOS-RABBIT-008':'P08','FOS-RABBIT-012':'P06','FOS-RABBIT-024':'P10','FOS-RABBIT-029':'P06','FOS-RABBIT-033':'P04',
 'FOS-SHEEP-012':'P07','FOS-SHEEP-025':'P04','FOS-CATTLE-033':'P04',
 'FOS-POULTRY-019':'P06','FOS-POULTRY-025':'P04','FOS-HEALTH-002':'P05',
 'FOS-TASK-007':'P05','FOS-TASK-008':'P05','FOS-INV-005':'P07','FOS-INV-007':'P07','FOS-INV-012':'P14',
 'FOS-FEED-002':'P05','FOS-PASTURE-006':'P08','FOS-PASTURE-010':'P08','FOS-GROUP-005':'P05','FOS-GROUP-008':'P04',
 'FOS-LABOUR-008':'P08','FOS-ASSET-007':'P06','FOS-ASSET-011':'P06','FOS-FIN-010':'P08','FOS-FIN-013':'P13',
 'FOS-CAP-001':'P04','FOS-CAP-010':'P12','FOS-REPORT-001':'P05','FOS-ADMIN-024':'P09'
};
const jobs={P04:'Understand this scope through actionable metrics and enter its specific workflows.',P05:'Find the correct scoped record or work item and open its owning action.',P06:'Identify this subject/record, inspect its status and act or drill into evidence.',P07:'Capture or edit the specified domain result with typed fields, units and a truthful receipt.',P08:'Inspect dates, stages, capacity and conflicts before making a permitted plan change.',P09:'Trace outcomes and evidence through time without losing correction or sync context.',P10:'Inspect comparable values and assumptions, then drill to source or export equivalent data.',P12:'Review identity, restrictions, evidence and consequences before an authorized decision.',P13:'Find/select/connect the correct scoped subject or device and return safely.',P14:'Inspect or change the permitted setting without exposing unrelated administration.'};
for(const s of x.screens){if(overrides[s.screen_id]){const p=overrides[s.screen_id];s.recipe=p;s.recipe_name=x.recipes[p];s.composition_rule='PAGE-PATTERNS.md#'+p.toLowerCase();s.page_intent=s.name+': '+jobs[p];s.mapping_method='explicit_ID_assignment_after_inventory_review';}}
// Make source intent especially clear where the same word describes unrelated workflows.
x.screens.find(s=>s.screen_id==='FOS-SHEEP-012').page_intent='Pregnancy Scan: record the species-specific pregnancy scanning result; this is not an RFID/device search screen.';
x.screens.find(s=>s.screen_id==='FOS-CATTLE-011').page_intent='AI Record: capture artificial insemination with the specified service, subject and provider evidence; this is not the copilot.';
x.screens.find(s=>s.screen_id==='FOS-HOME-002').page_intent='Species Navigator: image-led family launcher using the five approved animal family portraits, opening species-native modules; never a mixed-species animal list.';
fs.writeFileSync(file,JSON.stringify(x,null,2)+'\n');
const q=v=>'"'+String(v).replaceAll('"','""')+'"';
fs.writeFileSync(path.join(root,'registry/page-alignment.csv'),['screen_id,name,module,recipe,mapping_status,source_paths',...x.screens.map(s=>[s.screen_id,s.name,s.module,s.recipe,s.mapping_status,s.source_paths.join(' | ')].map(q).join(','))].join('\n')+'\n');
const modules=[...new Set(x.screens.map(s=>s.module))];
fs.writeFileSync(path.join(root,'registry/PAGE-INDEX.md'),'# Quantum page alignment index\n\n'+x.actual_count+' entries: '+x.base_count+' base + '+x.role_variant_count+' role variants. Exact source IDs/names retained. Recipes require page-specific feature/contract review and native acceptance. See PAGE-PATTERNS.md for each recipe and module rule.\n\n'+modules.map(m=>'## '+m.toUpperCase()+' — '+x.screens.filter(s=>s.module===m).length+' entries\n\n'+x.screens.filter(s=>s.module===m).map(s=>'- **'+s.screen_id+' — '+s.name+'** · '+s.recipe+' '+s.recipe_name+'. '+s.page_intent+' Source: '+(s.source_paths.join('; ')||'No path in supplied implementation map; inspect live routes.')+' Contract review required.').join('\n')).join('\n\n')+'\n');
console.log('Refined '+Object.keys(overrides).length+' assignments; preserved '+x.screens.length+' IDs.');
