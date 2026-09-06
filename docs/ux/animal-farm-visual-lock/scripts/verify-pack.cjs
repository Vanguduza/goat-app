// Dependency-free integrity gate. This is not an Android renderer or authenticated approval service.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const assert=(ok,msg)=>{if(!ok)throw new Error(msg);};
function safe(p){assert(typeof p==='string'&&p.length>0,'Missing relative artifact path');const f=path.resolve(root,p),rel=path.relative(root,f);assert(!path.isAbsolute(p)&&rel!=='..'&&!rel.startsWith('..'+path.sep),'Path outside pack: '+p);if(fs.existsSync(f)){const real=fs.realpathSync(f),r=path.relative(fs.realpathSync(root),real);assert(r!=='..'&&!r.startsWith('..'+path.sep)&&!path.isAbsolute(r),'Symlink outside pack');}return f;}
const read=p=>JSON.parse(fs.readFileSync(safe(p),'utf8'));
function sourceRecords(text){return [...text.matchAll(/^  - screen_id: (FOS-[A-Z]+-\d{3}(?:-[A-Z])?)\r?\n    name: (.+)\r?\n    module: (.+)/gm)].map(m=>({screen_id:m[1],name:m[2].trim(),module:m[3].trim()}));}
function validateRegistry(x,source){
 assert(Array.isArray(x.screens),'Missing screens');const map=new Map();
 for(const s of x.screens){assert(!map.has(s.screen_id),'Duplicate ID '+s.screen_id);map.set(s.screen_id,s);assert(x.recipes[s.recipe],'Invalid recipe '+s.screen_id);assert(s.page_intent&&s.module_rules&&s.reference_lineage&&s.required_contract,'Missing design assignment '+s.screen_id);assert(typeof s.visual_green==='boolean'&&typeof s.feature_green==='boolean','Invalid status');}
 assert(new Set(source.map(s=>s.screen_id)).size===source.length,'Duplicate source IDs');
 assert(x.screens.length===source.length&&x.actual_count===source.length,'Registry count mismatch');
 for(const s of source){const r=map.get(s.screen_id);assert(r,'Missing source ID '+s.screen_id);assert(r.name===s.name&&r.module===s.module,'Changed source identity '+s.screen_id);}
 const variants=x.screens.filter(s=>/\d-[A-Z]$/.test(s.screen_id)).length;
 assert(x.role_variant_count===variants&&x.base_count+variants===x.actual_count,'Variant count mismatch');
 for(const id of ['FOS-HOME-012-A','FOS-HOME-012-B'])assert(map.get(id)?.recipe==='P02','Management A lock changed');
 assert(map.get('FOS-HOME-012-D')?.recipe==='P03','Worker D+C lock changed');
 return map;
}
function validateEvidence(e,s,expectedCommit){
 assert(e.screen_id===s.screen_id,'Evidence ID mismatch');assert(/^[a-f0-9]{40}$/.test(e.tested_commit),'Missing tested SHA');assert(e.tested_commit===expectedCommit,'Stale evidence SHA');
 for(const k of ['state_matrix_complete','route_tests_passed','interaction_tests_passed','accessibility_passed'])assert(e[k]===true,'Incomplete evidence: '+k);
 assert(e.visual_green===true,'Visual gate not green');
 if(s.feature_green)assert(e.functional_contract_passed===true&&e.feature_green===true,'Functional gate not green');
 assert(/^https:\/\//.test(e.ci_run_url||''),'Missing CI run');
 for(const k of ['baseline_approval','independent_review'])assert(e[k]?.reviewer&&e[k]?.reference,'Missing '+k);
 assert(Array.isArray(e.exceptions)&&e.exceptions.every(x=>x.approval_reference&&x.reviewer),'Unapproved exception');
 const contract=read(e.contract_file);assert(contract.screen_id===s.screen_id,'Contract ID mismatch');
 assert(!/REQUIRED|REPLACE_ME|REPLACE_WITH_EACH/.test(JSON.stringify(contract)),'Unfinished screen contract');
 assert(contract.feature_ids?.length&&contract.navigation?.route&&contract.design?.ordered_sections?.length&&contract.state_matrix?.length,'Incomplete screen contract');
 assert(contract.review?.design_contract_reviewer&&contract.review?.approval_reference,'Unreviewed contract');
 const kinds=new Set();for(const a of e.artifacts||[]){assert(a.kind&&a.path&&/^[a-f0-9]{64}$/.test(a.sha256||''),'Invalid artifact');assert(hash(fs.readFileSync(safe(a.path)))===a.sha256,'Changed evidence artifact');kinds.add(a.kind);}
 for(const k of ['native_golden','screenshot_diff','tests','accessibility','state_matrix'])assert(kinds.has(k),'Missing artifact '+k);
}
function run(){
 const manifest=read('reference-manifest.json');
 for(const f of manifest.files){const b=fs.readFileSync(safe(f.path));assert(b.length===f.bytes&&hash(b)===f.sha256,'Reference changed: '+f.path);}
 const html=fs.readFileSync(safe('references/animal-farm-locked-homes.html'),'utf8');assert(html.includes('sandbox=')&&html.includes('Content-Security-Policy'),'Missing supplied sandbox/CSP');
 const text=fs.readFileSync(safe('registry/source-screen-registry.yaml'),'utf8'),source=sourceRecords(text);
 assert(source.length===[...text.matchAll(/^  - screen_id:/gm)].length,'Unsupported source structure; use a full YAML parser, do not silently skip');
 const x=read('registry/page-alignment.json'),map=validateRegistry(x,source);
 const args=process.argv.slice(2),arg=k=>{const i=args.indexOf(k);return i>=0?args[i+1]:undefined;};
 if(arg('--registry')){const currentText=fs.readFileSync(path.resolve(arg('--registry')),'utf8'),current=sourceRecords(currentText);assert(current.length===[...currentText.matchAll(/^  - screen_id:/gm)].length,'Unsupported live registry syntax');validateRegistry(x,current);}
 if(arg('--routes')){const routes=JSON.parse(fs.readFileSync(path.resolve(arg('--routes')),'utf8'));assert(Array.isArray(routes.screen_ids),'Expected route export screen_ids array including reachable nested atoms/aliases');const set=new Set(routes.screen_ids);assert(set.size===map.size&&[...map.keys()].every(id=>set.has(id)),'Route/registry coverage mismatch');}
 const marked=x.screens.filter(s=>s.visual_green||s.feature_green);
 const release=args.includes('--release'),requested=(arg('--release')||'').split(',').filter(v=>v&&!v.startsWith('--'));
 const selected=release?(requested.length?requested.map(id=>{assert(map.has(id),'Unknown selected ID');return map.get(id);}):x.screens):marked;
 if(selected.length){const commit=arg('--expected-commit');assert(/^[a-f0-9]{40}$/.test(commit||''),'Supply --expected-commit FULL_SHA for evidence checks');for(const s of selected)validateEvidence(read('evidence/'+s.screen_id+'.json'),s,commit);}
 if(args.includes('--self-test')){
   let count=0;const reject=(name,fn)=>{let failed=false;try{fn();}catch{failed=true;}assert(failed,'Negative test did not reject '+name);count++;};
   const clone=()=>structuredClone(x);
   reject('missing ID',()=>{const y=clone();y.screens.pop();validateRegistry(y,source);});
   reject('duplicate ID',()=>{const y=clone();y.screens[1]=y.screens[0];validateRegistry(y,source);});
   reject('changed name',()=>{const y=clone();y.screens[0].name='Wrong';validateRegistry(y,source);});
   reject('invalid recipe',()=>{const y=clone();y.screens[0].recipe='P99';validateRegistry(y,source);});
   reject('worker lock',()=>{const y=clone();y.screens.find(s=>s.screen_id==='FOS-HOME-012-D').recipe='P05';validateRegistry(y,source);});
   reject('false evidence',()=>validateEvidence({screen_id:x.screens[0].screen_id,tested_commit:'a'.repeat(40)},x.screens[0],'b'.repeat(40)));
   reject('traversal',()=>safe('../outside'));
   reject('asset mutation',()=>assert(hash(Buffer.from('changed'))===manifest.files[0].sha256,'Reference hash mismatch'));
   console.log('PASS: '+count+' negative integrity tests (in-memory; references not altered).');
 }
 console.log('PASS: '+manifest.files.length+' reference/theme hashes; '+x.actual_count+' exact IDs ('+x.base_count+' base + '+x.role_variant_count+' variants); locked home mappings.');
 console.log('Source footer: '+x.declared_source_count+'; actual entries: '+x.actual_count+'. Fix upstream count/generator; no IDs dropped.');
 console.log(release?'Selected evidence records are complete; CI/reviewer authenticity still requires protected remote checks.':'Pack integrity only. No Android/native/feature certification claimed.');
}
try{run();}catch(e){console.error('FAIL: '+e.message);process.exitCode=1;}
