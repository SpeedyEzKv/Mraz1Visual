import { convertBedrock } from './cosmetics/bedrock-convert.mjs';
// Deterministic local asset conversion. Never executes code from the source archive.
import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const assets = path.join(root, 'mod/src/main/resources/assets');
const source = path.join(assets, 'auravisuals');
const out = path.join(assets, 'mraz1visual/cosmetics/aura');
fs.mkdirSync(out, {recursive:true});
const read = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
const write = (p,v) => fs.writeFileSync(p, JSON.stringify(v));
const catalog = read(path.join(source, 'cosmetics/catalog.json'));
const result = [], missing = [], orphanAnimationBones = [];
for (const item of catalog) {
  const modelFile = path.join(source, item.modelPath);
  const textureFile = path.join(source, item.texturePath);
  if (!fs.existsSync(modelFile) || !fs.existsSync(textureFile)) { missing.push(item.slug); continue; }
  const geoRoot = read(modelFile);
  const geometry = geoRoot['minecraft:geometry']?.[0] ?? Object.entries(geoRoot).find(([key]) => key.startsWith('geometry.'))?.[1];
  if (!geometry?.bones) { missing.push(item.slug); continue; }
  const clips = item.animationPath && fs.existsSync(path.join(source,item.animationPath))
    ? read(path.join(source,item.animationPath)).animations : null;
  const model = convertBedrock(item, geoRoot, fs.readFileSync(textureFile), clips, orphanAnimationBones);
  write(path.join(out,item.slug+'.bbmodel'),model);
  const metaFile=path.join(source,item.metaPath??'');
  const meta=fs.existsSync(metaFile)&&fs.statSync(metaFile).isFile()?read(metaFile):{};
  const sourceWarnings=orphanAnimationBones.filter(v=>v.model===item.slug).map(v=>`Source animation references missing bone: ${v.bone}`);
  result.push({...item,converted:item.slug+'.bbmodel',author:meta.contributor??'AuraVisual / @fixedproject',name:meta.name??item.name,sourceWarnings});
}
write(path.join(out,'catalog.json'),result);
write(path.join(out,'import-report.json'),{imported:result.length,missing,orphanAnimationBones,source:'source auravisual by @fixedproject.zip',license:'All Rights Reserved'});
console.log(JSON.stringify({imported:result.length,missing,categories:result.reduce((r,v)=>(r[v.category]=(r[v.category]??0)+1,r),{})}));
