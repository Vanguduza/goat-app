// Validate the installed single visual authority. Historical package preservation lives in Git history.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const pack = path.join(root, 'docs/ux/animal-farm-visual-lock');
const mustExist = [
  'START-HERE.md',
  'AGENT-INSTRUCTIONS.md',
  'DESIGN-SYSTEM.md',
  'PAGE-PATTERNS.md',
  'MIGRATION-AND-GATES.md',
  'VALIDATION.md',
  'reference-manifest.json',
  'references/animal-farm-locked-homes.html',
  'assets/farm_animal_lineup.png',
  'assets/farm_family_portraits_v1.png',
  'assets/inter_variable.ttf',
  'theme/locked-web-tokens.json',
];
for (const rel of mustExist) {
  const file = path.join(pack, rel);
  if (!fs.existsSync(file) || fs.statSync(file).size === 0) throw Error('Missing canonical visual authority artifact: ' + rel);
}
const forbidden = [
  'docs/ux/FARM_OS_VISUAL_AUTHORITY.md',
  'docs/ux/FARM_OS_CANONICAL_VISUAL_REFERENCE_MANIFEST.yaml',
  'docs/FARM_OS_DESIGN_SYSTEM_SPEC.md',
  'docs/FARM_OS_FRONTEND_UX_PLAYBOOK.md',
  'core/design/src/main/res/font/caveat_variable.ttf',
  'docs/ux/licenses/CAVEAT_OFL.txt',
  'docs/ux/animal-farm-visual-lock/references/native',
  'docs/ux/animal-farm-visual-lock/references/quantum-atlas-rev2.md',
];
for (const rel of forbidden) {
  if (fs.existsSync(path.join(root, rel))) throw Error('Superseded visual artifact returned: ' + rel);
}
function assertExactCopy(source, target) {
  const a = fs.readFileSync(path.join(root, source));
  const b = fs.readFileSync(path.join(root, target));
  if (!a.equals(b)) throw Error('Android visual-lock copy differs from canonical source: ' + target);
}
assertExactCopy(
  'docs/ux/animal-farm-visual-lock/assets/farm_animal_lineup.png',
  'core/design/src/main/res/drawable-nodpi/farm_animal_lineup.png',
);
assertExactCopy(
  'docs/ux/animal-farm-visual-lock/assets/farm_family_portraits_v1.png',
  'core/design/src/main/res/drawable-nodpi/farm_family_portraits_v1.png',
);
const manifest = JSON.parse(fs.readFileSync(path.join(pack, 'reference-manifest.json'), 'utf8'));
if (manifest.policy?.single_visual_authority !== true) throw Error('Single visual authority policy missing from reference manifest');
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
for (const item of manifest.files) {
  const file = path.join(pack, item.path);
  const bytes = fs.readFileSync(file);
  if (bytes.length !== item.bytes || hash(bytes) !== item.sha256) throw Error('Changed locked reference: ' + item.path);
}
console.log('PASS: Animal Farm is the sole installed visual authority.');
console.log('PASS: superseded visual artifacts are absent and Android brand assets match the lock.');
console.log('Integrity only; no native, feature, module or MVP certification.');
