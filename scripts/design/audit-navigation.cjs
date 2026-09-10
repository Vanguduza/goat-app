#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const cp = require('child_process');

const root = path.resolve(__dirname, '../..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const rel = (p) => path.relative(root, p).replaceAll(path.sep, '/');
const sha = cp.execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim();

function enumValues(file, enumName) {
  const text = read(file);
  const match = text.match(new RegExp(`enum\\s+class\\s+${enumName}\\s*\\{([^}]*)\\}`, 's'));
  if (!match) throw new Error(`Missing enum ${enumName} in ${file}`);
  return match[1]
    .split(/[\n,]/)
    .map((v) => v.trim())
    .filter((v) => /^[A-Z][A-Z0-9_]*$/.test(v));
}

function stateMachine(enumName, declarationFile, implementationFiles = [declarationFile]) {
  const values = enumValues(declarationFile, enumName);
  const text = implementationFiles.map(read).join('\n');
  return {
    enum: enumName,
    declaration_file: declarationFile,
    implementation_files: implementationFiles,
    states: values.map((value) => {
      const token = `${enumName}.${value}`;
      const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      const refs = (text.match(new RegExp(escaped, 'g')) || []).length;
      const transitions = (text.match(new RegExp(`(?:=|onOpen\\(|onPage\\(|onSelect\\()[^\\n]{0,80}${escaped}`, 'g')) || []).length;
      return { value, refs, transition_refs: transitions, render_only_candidate: refs === 1 && transitions === 0 };
    }),
  };
}

const machines = [
  stateMachine('GoatPage', 'feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceModels.kt', [
    'feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceModels.kt',
    'feature/goat/src/main/kotlin/com/farmos/feature/goat/GoatExperienceScreen.kt',
  ]),
  stateMachine('RabbitPage', 'feature/rabbit/src/main/kotlin/com/farmos/feature/rabbit/RabbitProgrammeScreen.kt'),
  stateMachine('SpeciesPage', 'app/src/main/java/com/farmos/app/SpeciesHerdScreen.kt'),
  stateMachine('InventoryPage', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/InventoryExperienceScreen.kt'),
  stateMachine('HealthPage', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/HealthExperienceScreen.kt'),
  stateMachine('SheepOpsPage', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/SheepOperationsScreen.kt'),
  stateMachine('CattleOpsPage', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/CattleOperationsScreen.kt'),
  stateMachine('PoultryPage', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/PoultryExperienceScreen.kt'),
  stateMachine('FinancePage', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/FinanceExperienceScreen.kt'),
  stateMachine('TaskTab', 'feature/ops/src/main/kotlin/com/farmos/feature/ops/TasksBoardScreen.kt'),
];

const farmModules = enumValues('app/src/main/java/com/farmos/app/FarmHomeScreen.kt', 'FarmModule');
const personas = enumValues('app/src/main/java/com/farmos/app/RoleDashboardScreens.kt', 'FarmHomePersona');
const registryText = read('docs/ux/FARM_OS_SCREEN_REGISTRY.yaml');
const registryIds = [...registryText.matchAll(/^\s*- screen_id:\s*(FOS-[A-Z0-9-]+)\s*$/gm)].map((m) => m[1]);
const mapText = read('docs/ux/FARM_OS_CURRENT_UI_IMPLEMENTATION_MAP.yaml');
const mappedIds = new Set([...mapText.matchAll(/FOS-[A-Z]+-[0-9]+(?:-[A-Z])?/g)].map((m) => m[0]));
const registrySet = new Set(registryIds);
const mappedRegistered = [...mappedIds].filter((id) => registrySet.has(id));

const allKt = [];
for (const base of ['app', 'feature', 'core']) {
  const stack = [path.join(root, base)];
  while (stack.length) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const p = path.join(current, entry.name);
      if (entry.isDirectory()) stack.push(p);
      else if (entry.isFile() && p.endsWith('.kt')) allKt.push(p);
    }
  }
}
const codeText = allKt.map((p) => fs.readFileSync(p, 'utf8')).join('\n');
const codeIds = new Set([...codeText.matchAll(/FOS-[A-Z]+-[0-9]+(?:-[A-Z])?/g)].map((m) => m[0]));
const codeRegistered = [...codeIds].filter((id) => registrySet.has(id));

const session = read('app/src/main/java/com/farmos/app/FarmSessionContent.kt');
const operatingHost = read('app/src/main/java/com/farmos/app/OperatingModuleHost.kt');
const operatingCallers = [...allKt]
  .filter((p) => fs.readFileSync(p, 'utf8').includes('OperatingModuleHost('))
  .map(rel);
const dedicatedHosts = {
  TASKS: 'TasksModuleHost(',
  HEALTH: 'HealthModuleHost(',
  MONEY: 'MoneyModuleHost(',
  INVENTORY: 'InventoryModuleHost(',
};
const preemptedModules = Object.entries(dedicatedHosts)
  .filter(([module, host]) => session.includes(host) && operatingHost.includes(`FarmModule.${module}`))
  .map(([module]) => module);

const appKt = allKt
  .filter((p) => rel(p).startsWith('app/'))
  .map((p) => fs.readFileSync(p, 'utf8'))
  .join('\n');
const directActionViolations = [
  ['Record weight', 'GoatEntryPage.WEIGHT', 'FOS-GOAT-011'],
  ['Add task', 'TaskEntryPage.CREATE', 'FOS-TASK-004'],
  ['Add treatment', 'HealthEntryPage.TREATMENT', 'FOS-HEALTH-007'],
  ['Record observation', 'HealthEntryPage.RECORD_OBSERVATION', 'FOS-HEALTH-004'],
  ['Resources', 'FarmModule.INVENTORY', 'FOS-INV-001'],
  ['Scan animal', 'GoatEntryPage.SEARCH', 'FOS-GOAT-006'],
  ['Waiting to sync', 'GoatEntryPage.SYNC', 'FOS-SYNC-002'],
  ['Open sync status', 'FarmDestination.Goat(GoatEntryPage.SYNC)', 'FOS-SYNC-002'],
  ['Continue task', 'FarmDestination.Task', 'FOS-TASK-003'],
  ['Open task', 'FarmDestination.Task', 'FOS-TASK-003'],
  ['Open withdrawals', 'HealthEntryPage.WITHDRAWALS', 'FOS-HEALTH-009'],
  ['Open tasks', 'FarmDestination.Tasks(TaskEntryPage.BOARD)', 'FOS-TASK-001'],
  ['Team tasks', 'FarmDestination.Tasks(TaskEntryPage.BOARD)', 'FOS-TASK-001'],
  ['Due work', 'FarmDestination.Tasks(TaskEntryPage.BOARD)', 'FOS-TASK-001'],
  ['Follow-up tasks', 'FarmDestination.Tasks(TaskEntryPage.BOARD)', 'FOS-TASK-001'],
  ['Record vet visit', 'HealthEntryPage.VET_VISIT', 'FOS-HEALTH-021'],
  ['Record lab result', 'HealthEntryPage.LAB_RESULT', 'FOS-HEALTH-024'],
  ['Open formulary', 'HealthEntryPage.FORMULARY', 'FOS-HEALTH-013'],
  ['Record kidding', 'GoatEntryPage.KIDDING', 'FOS-GOAT-037'],
  ['Record mating', 'GoatEntryPage.REPRODUCTION', 'FOS-GOAT-032'],
  ['Open health', 'FarmDestination.Health()', 'FOS-HEALTH-001'],
  ['Open feed', 'FarmModule.FEED', 'FOS-FEED-001'],
  ['Open water', 'FarmModule.WATER', 'FOS-WATER-001'],
  ['Open pasture', 'FarmModule.PASTURE', 'FOS-PASTURE-001'],
  ['Open assets', 'FarmModule.ASSETS', 'FOS-ASSET-001'],
  ['Open groups', 'FarmModule.GROUPS', 'FOS-GROUP-001'],
  ['Open waitlist', 'FarmModule.WAITLIST', 'FOS-RABBIT-027'],
].filter(([label, token]) => appKt.includes(`"${label}"`) && !appKt.includes(token))
  .map(([label, , expected]) => ({
    label,
    current_destination: 'MISSING_EXACT_OWNER',
    expected_exact_owner: expected,
  }));

const renderOnly = machines.flatMap((m) => m.states.filter((s) => s.render_only_candidate).map((s) => `${m.enum}.${s.value}`));
const auth = read('app/src/main/java/com/farmos/app/FoundationAuthScreen.kt');
const authStates = ['BackendUnavailableState', 'SignInState', 'FarmSelectionState', 'FarmSetupWizardState']
  .filter((name) => auth.includes(`${name}(`));

const report = {
  schema_version: 1,
  evidence_class: 'STATIC_SOURCE_DERIVED_NOT_RUNTIME_REACHABILITY',
  tested_commit: sha,
  registry: {
    exact_entries: registryIds.length,
    unique_entries: new Set(registryIds).size,
    static_implementation_map_unique_registered_ids: new Set(mappedRegistered).size,
    registered_ids_absent_from_static_implementation_map: registryIds.filter((id) => !mappedIds.has(id)).length,
    registered_ids_mentioned_in_kotlin: new Set(codeRegistered).size,
  },
  top_level: {
    farm_modules: farmModules,
    role_personas: personas,
    auth_render_states: authStates,
    home_nested_destinations: ['home', 'animals', 'more'],
  },
  state_machines: machines,
  findings: {
    render_only_state_candidates: renderOnly,
    operating_module_host_callers: operatingCallers,
    operating_module_branches_preempted_by_dedicated_hosts: preemptedModules,
    direct_action_scope_violations: directActionViolations,
    feature_id_catalog: 'NOT_FOUND: screen contract template requires feature_ids, but no canonical feature-ID catalog was found in docs/code search',
  },
  limitations: [
    'This report is derived from checked-out Kotlin state machines and source text; it is not an instrumented runtime traversal.',
    'A composable declaration, source-map association, or FOS comment is not treated as proof of reachability.',
    'Dialogs/sheets implemented only as local booleans may require interaction tests before they can be enumerated completely.',
    'Authorization, restoration, deep links, return behavior, and parameter scoping remain unverified until executed tests exist.',
  ],
};

const args = process.argv.slice(2);
const outIndex = args.indexOf('--out');
if (outIndex >= 0) {
  const out = path.resolve(root, args[outIndex + 1]);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, JSON.stringify(report, null, 2) + '\n');
}
if (args.includes('--self-test')) {
  const assert = (ok, msg) => { if (!ok) throw new Error(msg); };
  assert(report.registry.exact_entries === 545, `expected 545 registry entries, got ${report.registry.exact_entries}`);
  assert(report.registry.unique_entries === 545, 'registry IDs must be unique');
  assert(farmModules.length === 19, `expected 19 FarmModule values, got ${farmModules.length}`);
  assert(personas.length === 9, `expected 9 role personas, got ${personas.length}`);
  assert(!renderOnly.includes('RabbitPage.NESTS'), 'RabbitPage.NESTS must have a dashboard transition');
  assert(preemptedModules.length === 0, 'dedicated module hosts must not remain duplicated in OperatingModuleHost');
  assert(directActionViolations.length === 0, 'worker quick actions must use exact owning entries');
  console.log('PASS navigation source audit self-test');
}
if (!args.includes('--quiet')) process.stdout.write(JSON.stringify(report, null, 2) + '\n');
