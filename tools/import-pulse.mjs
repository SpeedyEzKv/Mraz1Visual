// Read only the catalog, Bedrock geometry and PNGs. tar supports both ZIP and RAR on Windows.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { convertBedrock } from './cosmetics/bedrock-convert.mjs';

const [debuda, pulse] = process.argv.slice(2);
if (!debuda || !pulse) throw Error('Usage: node tools/import-pulse.mjs <debuda-settings.zip> <Pulse SRC.rar>');
const assets = 'mod/src/main/resources/assets/mraz1visual';
const out = path.join(assets, 'cosmetics/pulse');
const prefix = 'src/main/resources/assets/debuda-client/cosmetic/pulse';
const read = (archive, file) => execFileSync('tar', ['-xOf', archive, file], {maxBuffer: 32 * 1024 * 1024});
fs.mkdirSync(out, { recursive: true });
const catalog = [];
for (const [folder, category] of Object.entries({wings:'wings', hat:'hats', bodywear:'back'})) {
  const index = JSON.parse(read(debuda, prefix + '/' + folder + '/index.json'));
  for (const [id, name] of Object.entries(index)) {
    if (!/^[a-z0-9_-]+$/.test(id)) throw Error('Invalid model identifier: ' + id);
    const dir = prefix + '/' + folder + '/' + id;
    const raw = JSON.parse(read(debuda, dir + '/model.json'));
    const texture = read(debuda, dir + '/texture.png');
    const slug = folder + '_' + id;
    const item = {slug, name, category, author:raw.author || 'Pulse', converted:slug + '.bbmodel'};
    const clips = {};
    for (const [sourceName, clip] of Object.entries(raw.animation?.animations ?? {})) {
      const key = sourceName.toLowerCase().replace(/^animation\.[^.]+\./, '');
      // GUI-only clips in this pack may target a different preview skeleton.
      if (/^gui($|\.)/.test(key)) continue;
      const normalized = key.replace(/^(walking|moving|walk)(?=\.|$)/, 'move')
        .replace(/^(main|flap)(?=\.|$)/, 'idle').replace(/^swimmings/, 'swimming');
      clips[normalized] = clip;
    }
    const warnings = [];
    const model = convertBedrock(item, raw.model, texture, clips, warnings);
    Object.keys(clips).forEach((key, i) => { model.animations[i].name = key; });
    if (raw.textureAnimation) {
      const {frameCount, frameHeight, frameTime} = raw.textureAnimation;
      const tex = model.textures[0];
      tex.width = texture.readUInt32BE(16);
      tex.uv_height = frameHeight;
      tex.frame_time = frameTime;
      if (texture.readUInt32BE(20) !== frameCount * frameHeight * tex.width / tex.uv_width) {
        throw Error('Unexpected animated sheet dimensions: ' + slug);
      }
    }
    item.sourceWarnings = warnings.map(w => w.animation + ': missing bone ' + w.bone);
    fs.writeFileSync(path.join(out,item.converted),JSON.stringify(model));
    catalog.push(item);
  }
}
fs.writeFileSync(path.join(out,'catalog.json'),JSON.stringify(catalog,null,2) + '\n');
const effect = path.join(assets,'textures/fx/pulse_jump.png');
fs.mkdirSync(path.dirname(effect),{recursive:true});
fs.writeFileSync(effect,read(pulse,'PulseSrc/src/main/resources/assets/pulse/textures/jump.png'));
console.log('Imported ' + catalog.length + ' wearables and the Pulse jump circle.');
