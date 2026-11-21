import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const files=[
  'mod/src/main/java/dev/mraz1visual/ui/Fonts.java',
  'mod/src/main/java/dev/mraz1visual/ui/Theme.java',
  'mod/src/main/java/dev/mraz1visual/ui/Mraz1VisualScreen.java',
  'mod/src/main/java/dev/mraz1visual/ui/menu/MenuBase.java',
  'mod/src/main/java/dev/mraz1visual/render/HudStyle.java',
  'mod/src/main/java/dev/mraz1visual/render/HudKit.java',
  'mod/src/main/java/dev/mraz1visual/module/UiSounds.java'
];
for(const file of files){
  const baseline=execFileSync('git',['show',`HEAD:${file}`],{encoding:'utf8',maxBuffer:16*1024*1024});
  assert.equal(fs.readFileSync(file,'utf8').replace(/\r\n/g,'\n'),baseline.replace(/\r\n/g,'\n'),`${file}: existing client appearance must be preserved`);
}
for(const name of ['text.ttf','bold.ttf','head.ttf','brand.ttf','symbols.ttf']){
  const file='mod/src/main/resources/assets/mraz1visual/font/'+name;
  assert.deepEqual(fs.readFileSync(file),execFileSync('git',['show',`HEAD:${file}`],{maxBuffer:16*1024*1024}),`original font bytes: ${name}`);
}
for(const file of ['mod/src/main/java/dev/mraz1visual/ui/AuraStyle.java','mod/src/main/java/dev/mraz1visual/ui/menu/AuraTitleScreen.java',
  'mod/src/main/resources/assets/mraz1visual/font/aura_native.json'])assert(!fs.existsSync(file),`Aura shell must be removed: ${file}`);
assert(!fs.existsSync('mod/src/main/java/dev/mraz1visual/ui/PreviewScene.java'),'requested demo preview is removed');
assert(!fs.readFileSync('mod/src/main/java/dev/mraz1visual/ui/ClientScreen.java','utf8').includes('ПРЕДПРОСМОТР'),'no old demo banner in client settings');
console.log(`PASS: ${files.length} original UI/HUD sources and 5 original font files match the repository baseline; Aura shell removed.`);
