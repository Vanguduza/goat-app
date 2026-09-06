// One-time mechanical packaging of existing references and derived registry metadata.
// Does not execute the YAML generator or any instructions in reference content.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const workspace = path.resolve(root, '../..');
const old = path.join(workspace, 'Goat App');
const repo = path.join(workspace, 'audit/farm-os-2026-09-05');
if (fs.existsSync(path.join(root, 'reference-manifest.json'))) throw new Error('Already assembled; do not overwrite a reviewed pack.');
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
const put = (p, value) => { const f = path.join(root, p); fs.mkdirSync(path.dirname(f), {recursive:true}); fs.writeFileSync(f, value); };
const json = (p, value) => put(p, JSON.stringify(value, null, 2) + '\n');
const copies = [
 ['references/animal-farm-locked-homes.html', path.join(workspace, 'sites/animal-farm-views/dist/index.html')],
 ['references/HOME_DESIGN_LOCK.md', path.join(old, 'docs/development/HOME_DESIGN_LOCK.md')],
 ['references/SCREEN_DESIGN_STANDARD.md', path.join(old, 'docs/planning/implementation/SCREEN_DESIGN_STANDARD.md')],
 ['references/ANIMAL_FAMILY_ART_PROVENANCE.md', path.join(old, 'docs/development/ANIMAL_FAMILY_ART_PROVENANCE.md')],
 ['assets/farm_animal_lineup.png', path.join(old, 'core-design/src/main/res/drawable-nodpi/farm_animal_lineup.png')],
 ['assets/farm_family_portraits_v1.png', path.join(old, 'core-design/src/main/res/drawable-nodpi/farm_family_portraits_v1.png')],
 ['assets/inter_variable.ttf', path.join(old, 'core-design/src/main/res/font/inter_variable.ttf')],
 ['assets/Inter-OFL.txt', path.join(old, 'third_party/inter/OFL.txt')],
 ['registry/source-screen-registry.yaml', path.join(repo, 'docs/ux/FARM_OS_SCREEN_REGISTRY.yaml')],
 ['registry/source-implementation-map.yaml', path.join(repo, 'docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml')],
 ['references/quantum-atlas-rev2.md', path.join(repo, 'docs/ux/FARM_OS_QUANTUM_COMPLETE_SCREEN_FEATURE_VISUAL_MAPPING_REV2.md')],
 ...['theme/HomeTokens.kt','theme/FosTheme.kt','patterns/LockedHomes.kt','components/AnimalPortrait.kt','patterns/AnimalProfile.kt'].map(p => ['references/native/'+path.basename(p), path.join(old,'core-design/src/main/kotlin/com/farmos/design',p)])
];
const files = copies.map(([dest,src]) => { const b=fs.readFileSync(src); put(dest,b); return {path:dest,sha256:hash(b),bytes:b.length,source:src}; });
const fragment = fs.readFileSync(path.join(old,'docs/development/previews/animal-farm-locked-homes.html'),'utf8');
const pairs = [...fragment.matchAll(/--af-([\w-]+):light-dark\((#[\da-f]+),(#[\da-f]+)\)/gi)];
if (pairs.length < 15) throw new Error('Expected locked token pairs missing');
const themes={light:{},dark:{}};
for(const [,key,l,d] of pairs) {themes.light[key]=l.toUpperCase();themes.dark[key]=d.toUpperCase();}
json('theme/locked-web-tokens.json',{version:'animal-farm-lock-2026-09-05',authority:'exact retained locked-home fragment',themes,native_font:'Inter',web_font:'Segoe UI, Roboto, Arial, sans-serif',outdoor_status:'requires_native_acceptance'});
put('theme/locked-web.css', '/* Extracted home colors only; not an Android implementation. */\n'+Object.entries(themes).map(([mode,values])=>'[data-animal-farm-theme="'+mode+'"] {\n'+Object.entries(values).map(([k,v])=>'  --af-'+k+': '+v+';').join('\n')+'\n}').join('\n'));
json('theme/source-geometry.json',{authority:'locked HTML CSS; px values are not automatic dp/sp conversions',viewport_px:390,hero:{radius_px:24,padding_px:20,title_px:26},focus_card:{radius_px:28,padding_px:21,title_px:30},secondary_tile_radius_px:20,action_radius_px:16,content_inset_px:18,header_inset_px:22,tile_gap_px:10,module_gap_px:8,native_targets_dp:{normal:48,outdoor:64},native_text_candidate_sp:{display:[28,34],title:[22,28],body:[15,22],label:[13,18]}});
const yaml=fs.readFileSync(path.join(root,'registry/source-screen-registry.yaml'),'utf8');
function records(text) {
 return [...text.matchAll(/^  - screen_id: (FOS-[A-Z]+-\d{3}(?:-[A-Z])?)\r?\n([\s\S]*?)(?=^  - screen_id:|^screen_count:|(?![\s\S]))/gm)].map(m=>{
   const get=k=>m[2].match(new RegExp('^    '+k+': (.+)$','m'))?.[1].trim();
   const out={screen_id:m[1],name:get('name'),module:get('module'),source_visual_class:get('visual_class'),source_implementation_claim:get('implementation_audit')};
   if(Object.values(out).some(v=>!v)) throw new Error('Unparsed registry fields '+m[1]); return out;
 });
}
const screens=records(yaml);
if(screens.length!==[...yaml.matchAll(/^  - screen_id:/gm)].length || screens.length===0) throw new Error('Incomplete source parse');
const sourceMap=fs.readFileSync(path.join(root,'registry/source-implementation-map.yaml'),'utf8');
const paths=new Map();
for(const m of sourceMap.matchAll(/^  - file: (.+)\r?\n([\s\S]*?)(?=^  - file:|(?![\s\S]))/gm)) {
 const line=m[2].match(/target_screens: \[([^\]]+)\]/)?.[1]||'';
 for(const id of line.match(/FOS-[A-Z]+-\d{3}(?:-[A-Z])?/g)||[]) paths.set(id,[...(paths.get(id)||[]),m[1].trim()]);
}
const recipes = {
 P01:'Entrance and guided setup',P02:'Management command center A',P03:'Worker D+C workday',P04:'Module or specialist dashboard',P05:'Scoped list or queue',P06:'Identity or record detail',P07:'Capture and edit',P08:'Planning board or schedule',P09:'Timeline and history',P10:'Analytics comparison and reports',P11:'Guides references and documents',P12:'Safety review and decision',P13:'Search selection and hardware',P14:'Settings administration and access',P15:'Sync recovery and feedback',P16:'Evidence-bound AI workspace'
};
const exact = {
 'FOS-HOME-001':'P02','FOS-HOME-002':'P05','FOS-HOME-003':'P05','FOS-HOME-004':'P12','FOS-HOME-005':'P09','FOS-HOME-006':'P13','FOS-HOME-007':'P13','FOS-HOME-008':'P05','FOS-HOME-009':'P15','FOS-HOME-010':'P13','FOS-HOME-011':'P05','FOS-HOME-012':'P04',
 'FOS-HOME-012-A':'P02','FOS-HOME-012-B':'P02','FOS-HOME-012-C':'P04','FOS-HOME-012-D':'P03','FOS-HOME-012-E':'P04','FOS-HOME-012-F':'P04','FOS-HOME-012-G':'P04','FOS-HOME-012-H':'P05',
 'FOS-GOAT-008':'P06','FOS-GOAT-010':'P11','FOS-GOAT-041':'P06','FOS-SHEEP-015':'P06','FOS-RABBIT-003':'P06','FOS-RABBIT-004':'P06','FOS-RABBIT-018':'P06',
 'FOS-TASK-001':'P05','FOS-TASK-003':'P06','FOS-TASK-011':'P08','FOS-TASK-012':'P07','FOS-TASK-013':'P07','FOS-TASK-014':'P06','FOS-TASK-015':'P06',
 'FOS-GLOBAL-005':'P13','FOS-GLOBAL-019':'P06','FOS-GLOBAL-020':'P12',
 'FOS-HEALTH-007':'P07','FOS-HEALTH-017':'P12','FOS-HEALTH-018':'P12','FOS-HEALTH-019':'P07',
 'FOS-CATTLE-011':'P07','FOS-CATTLE-012':'P07','FOS-INV-015':'P12','FOS-INV-016':'P07','FOS-GEN-003':'P07','FOS-GEN-004':'P12','FOS-SIM-008':'P07'
};
function recipe(s) {
 if(exact[s.screen_id]) return exact[s.screen_id];
 const n=s.name.toLowerCase(), mod=s.module;
 if(mod==='sync')return 'P15';
 if(mod==='global')return /expired|revoked|unavailable/.test(n)?'P15':/terms|privacy|explanation|permission/.test(n)?'P11':'P01';
 if(mod==='admin')return 'P14';
 if(mod==='search')return 'P13';
 if(mod==='ai')return /settings|key|connection|privacy|controls/.test(n)?'P14':/unavailable/.test(n)?'P15':/action review/.test(n)?'P12':'P16';
 if(mod==='atom')return /confirmation|conflict/.test(n)?'P12':/loading|error|receipt/.test(n)?'P15':/permission/.test(n)?'P11':'P13';
 if(mod==='sim')return 'P10';
 if(mod==='an')return /insight|evidence|source/.test(n)?'P06':'P10';
 if(mod==='report')return /viewer|documents/.test(n)?'P11':/export|share|print/.test(n)?'P13':'P10';
 if(/reference|documents|document|library/.test(n))return 'P11';
 if(/search|scan|pairing|selector|identifier/.test(n))return 'P13';
 if(/timeline|history|movement history/.test(n))return 'P09';
 if(/report|analytics|analysis|compare|comparison|chart|ranking|profitability|cash flow|variance|cost|efficiency|forecast|capacity|pressure|days on feed|relationship|pedigree|benchmark|adg detail/.test(n))return 'P10';
 if(/dashboard|hub/.test(n))return 'P04';
 if(/schedule|plan|calendar|recurrence|rotation|due|wave/.test(n))return 'P08';
 if(/profile|detail|^inventory item$|^feed item$|^worker detail$|^formulary item$|^protocol pack detail$/.test(n))return 'P06';
 if(/red.flag|emergency|withdrawal status|status change|sale exit|mortality record|cull record|warning|alert|irreversible/.test(n))return 'P12';
 if(/list|register$|^groups$|herd|flock list|breeding animals|transactions|orders|formulary$|packs$|queue|stock$|enabled|kinds$|results$|all tasks|assigned|scheduled|overdue|completed|waitlist/.test(n))return 'P05';
 return 'P07';
}
const jobs={P01:'Complete this entrance/setup step with a clear next action and recoverable errors.',P02:'Understand farm-wide exceptions and open the highest-priority permitted action.',P03:'Choose an assigned work stage, inspect its task card and record the real outcome.',P04:'Understand this scope through actionable metrics and enter its specific workflows.',P05:'Find the correct scoped record or work item and open its owning action.',P06:'Identify this subject/record, inspect its status and act or drill into evidence.',P07:'Capture or edit the specified domain result with typed fields, units and a truthful receipt.',P08:'Inspect dates, stages, capacity and conflicts before making a permitted plan change.',P09:'Trace outcomes and evidence through time without losing correction or sync context.',P10:'Inspect comparable values and assumptions, then drill to source or export equivalent data.',P11:'Access the correct sourced and versioned guide, reference or document.',P12:'Review identity, restrictions, evidence and consequences before an authorized decision.',P13:'Find/select/connect the correct scoped subject or device and return safely.',P14:'Inspect or change the permitted setting without exposing unrelated administration.',P15:'Understand local/server state and recover safely without duplicate writes or lost data.',P16:'Review advisory assistance with evidence and explicitly authorize any permitted follow-up.'};
const rows=screens.map(s=>{
 const p=recipe(s), sourcePaths=paths.get(s.screen_id)||[];
 if(/FOS-HOME-012/.test(s.screen_id))sourcePaths.push('app/src/main/java/com/farmos/app/RoleDashboardScreens.kt');
 return {...s,recipe:p,recipe_name:recipes[p],composition_rule:'PAGE-PATTERNS.md#'+p.toLowerCase(),module_rules:s.module.toUpperCase(),page_intent:s.name+': '+jobs[p],mapping_status:'PROPOSED_CONTRACT_REVIEW_REQUIRED',mapping_method:exact[s.screen_id]?'explicit_ID_assignment':'name_and_module_classification_review_required',source_paths:[...new Set(sourcePaths)],source_paths_status:'source_map_or_static_code_claim_not_verified_route',risk_overlay:s.source_visual_class==='I4'?'P12 safety hierarchy; retain underlying page job':'resolve_from_feature_contract',reference_lineage:p==='P02'?'LOCK-MANAGEMENT-A':p==='P03'?'LOCK-WORKER-D+C':'DERIVED-LOCKED-FAMILY_REQUIRES_NATIVE_APPROVAL',required_contract:'templates/screen-contract.json',visual_green:false,feature_green:false};
});
const declared=Number(yaml.match(/^screen_count: (\d+)/m)[1]);
const variants=rows.filter(s=>/\d-[A-Z]$/.test(s.screen_id));
const counts={};for(const r of rows)counts[r.module]=(counts[r.module]||0)+1;
json('registry/page-alignment.json',{schema_version:1,snapshot_commit:'e86981682748605e9db1570224e8c801c35b6058',declared_source_count:declared,actual_count:rows.length,base_count:rows.length-variants.length,role_variant_count:variants.length,coverage_claim:'Every snapshot ID mapped; proposed recipes are not completed feature contracts or native approvals',recipes,screens:rows});
json('registry/coverage-findings.json',{declared_source_count:declared,actual_count:rows.length,base_count:rows.length-variants.length,role_variant_count:variants.length,counts_by_module:counts,source_registry_footer_matches:declared===rows.length,duplicate_ids:rows.length-new Set(rows.map(s=>s.screen_id)).size,source_implemented_claims:rows.filter(s=>s.source_implementation_claim==='VISUAL_IMPLEMENTED').length,verified_native_screens_by_this_pack:0,all_recipes_require_contract_review:true,generator_risk:'Current source generator enumerates base atlas only; preserve eight role variants before regeneration.'});
const csvValue=v=>'"'+String(v).replaceAll('"','""')+'"';
put('registry/page-alignment.csv',['screen_id,name,module,recipe,mapping_status,source_paths',...rows.map(s=>[s.screen_id,s.name,s.module,s.recipe,s.mapping_status,s.source_paths.join(' | ')].map(csvValue).join(','))].join('\n')+'\n');
put('registry/PAGE-INDEX.md','# Quantum page alignment index\n\n'+rows.length+' source entries: '+(rows.length-variants.length)+' base + '+variants.length+' role variants. All names/IDs are retained. Recipes are provisional contract assignments, not page-by-page native acceptance. See PAGE-PATTERNS and module rules before coding.\n\n'+Object.keys(counts).map(mod=>'## '+mod.toUpperCase()+' — '+counts[mod]+' entries\n\n'+rows.filter(s=>s.module===mod).map(s=>'- **'+s.screen_id+' — '+s.name+'** · '+s.recipe+' '+s.recipe_name+'. '+s.page_intent+' Source: '+(s.source_paths.join('; ')||'No path in supplied implementation map; inspect live routes.')+' Contract review required.').join('\n')).join('\n\n')+'\n');
for(const p of ['theme/locked-web-tokens.json','theme/locked-web.css','theme/source-geometry.json']){const b=fs.readFileSync(path.join(root,p));files.push({path:p,sha256:hash(b),bytes:b.length,source:'mechanically extracted from retained locked HTML'});}
json('reference-manifest.json',{version:'animal-farm-lock-2026-09-05',reference_approval:'Owner-locked home direction; no native certification implied',files});
console.log(JSON.stringify({actual:rows.length,base:rows.length-variants.length,role_variants:variants.length,files:files.length,counts},null,2));
