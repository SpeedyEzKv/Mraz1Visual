import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const assets='mod/src/main/resources/assets';
const source=path.join(assets,'auravisuals');
const converted=path.join(assets,'mraz1visual/cosmetics/aura');
const json=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const catalog=json(path.join(converted,'catalog.json'));
assert.equal(catalog.length,78,'all source cosmetic entries must be present');
assert.equal(new Set(catalog.map(i=>i.slug)).size,78,'cosmetic identifiers must be unique');
let cubes=0,animations=0,keys=0;
for(const item of catalog){
  const original=json(path.join(source,item.modelPath));
  const geo=original['minecraft:geometry']?.[0]??Object.entries(original).find(([k])=>k.startsWith('geometry.'))?.[1];
  const model=json(path.join(converted,item.converted));
  const count=geo.bones.reduce((n,b)=>n+(b.cubes?.length??0),0);
  assert.equal(model.elements.length,count,`${item.slug}: no cubes may be lost`);cubes+=count;
  assert.deepEqual(Buffer.from(model.textures[0].source.split(',')[1],'base64'),fs.readFileSync(path.join(source,item.texturePath)),`${item.slug}: original texture must survive byte-for-byte`);
  const bones=new Set(geo.bones.map(b=>b.name));
  const originalAnimations=item.animationPath?json(path.join(source,item.animationPath)).animations??{}:{};
  for(const animation of model.animations){animations++;
    const sourceAnimation=Object.entries(originalAnimations).find(([name])=>name.split('.').at(-1)===animation.name)?.[1];
    assert(sourceAnimation,`${item.slug}: animation must have an original source`);
    for(const [name,animator] of Object.entries(animation.animators)){
      if(!bones.has(name)) {
        assert(Object.values(originalAnimations).some(a=>Object.hasOwn(a.bones??{},name)),`${item.slug}: conversion must not invent orphan bones`);
        assert(item.sourceWarnings?.some(w=>w.endsWith(': '+name)),`${item.slug}: source orphan channels must be reported`);
      }
      for(const key of animator.keyframes){keys++;
        assert(Number.isFinite(key.time));
        for(const point of key.data_points)for(const value of Object.values(point))assert(['number','string'].includes(typeof value),`${item.slug}: animation values must not contain JSON objects`);
        const channel=sourceAnimation.bones[name][key.channel];
        const frames=typeof channel==='object'&&!Array.isArray(channel)&&!('vector' in channel)?channel:{'0':channel};
        const frame=Object.entries(frames).find(([time])=>Number(time)===key.time)?.[1];
        const pre=frame?.pre??frame?.post??frame?.vector??frame,post=frame?.post??pre;
        for(const [index,raw] of [pre,post].entries())for(const [axis,i] of [['x',0],['y',1],['z',2]]){
          const value=(Array.isArray(raw)?raw[i]:raw)??0;
          const negative=key.channel==='rotation'&&i<2||key.channel==='position'&&i===0;
          let expected=negative?(typeof value==='number'?-value:`-(${value})`):value;
          if(Object.is(expected,-0))expected=0;
          assert.equal(key.data_points[index][axis],expected,`${item.slug}: Bedrock animation axis ${key.channel}.${axis} must survive the Blockbench coordinate conversion`);
        }
      }
    }
  }
  const meta=json(path.join(source,item.metaPath));
  assert.equal(item.author,meta.contributor??'AuraVisual / @fixedproject','individual attribution must be retained');
}
for(const file of ['gui_open','gui_close','enable','disable','hitbubble','jump2','cape_aura']){
 const sound=['gui_open','gui_close','enable','disable'].includes(file);
 assert(fs.existsSync(path.join(source,sound?'sounds':'textures',file+(sound?'.ogg':'.png'))));
}
for(const file of ['star','heart','snowflake','dollar','crown','ded','crest','light','cube'])assert(fs.existsSync(path.join(source,'textures/particles',file+'.png')),`source particle texture: ${file}`);
for(const file of ['glow','bloom'])assert(fs.existsSync(path.join(source,'textures',file+'.png')),`source particle texture: ${file}`);
console.log(`PASS: ${catalog.length} cosmetics, ${cubes} cubes, ${animations} animations, ${keys} keyframes; exact textures and author attribution verified.`);
