// Verify the complete original handover independently of its narrower reference manifest.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const prefix = 'docs/ux/animal-farm-visual-lock/';
const inventory = JSON.parse(fs.readFileSync(path.join(root, 'docs/ux/ANIMAL_FARM_HANDOVER_FILES.json'), 'utf8'));
const expected = new Set();
for (const file of inventory.files) {
  if (!file.path.startsWith(prefix) || file.path.includes('..') || expected.has(file.path)) throw Error('Invalid or duplicate inventory path');
  expected.add(file.path);
  const bytes = fs.readFileSync(path.join(root, file.path));
  if (bytes.length !== file.bytes || crypto.createHash('sha256').update(bytes).digest('hex') !== file.sha256) throw Error('Changed handover file: ' + file.path);
}
function walk(dir) {
  for (const entry of fs.readdirSync(path.join(root, dir), {withFileTypes: true})) {
    const file = dir + '/' + entry.name;
    if (entry.isSymbolicLink()) throw Error('Unexpected symlink: ' + file);
    if (entry.isDirectory()) walk(file);
    else if (!expected.has(file)) throw Error('Uninventoried handover file: ' + file);
  }
}
walk(prefix.slice(0, -1));
console.log('PASS: ' + expected.size + ' complete original handover files; exact bytes and inventory.');
console.log('Integrity only; no native, feature, module or MVP certification.');
