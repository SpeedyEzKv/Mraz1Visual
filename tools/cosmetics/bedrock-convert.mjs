export function convertBedrock(item, geoRoot, texture, animations, orphanAnimationBones) {
  const geo = geoRoot['minecraft:geometry']?.[0] ?? Object.entries(geoRoot).find(([k])=>k.startsWith('geometry.'))?.[1];
  if (!geo?.bones) throw Error('Missing geometry: ' + item.slug);
  const desc = geo.description ?? geo;
  const resolution = {width:desc.texture_width ?? desc.texturewidth ?? 64, height:desc.texture_height ?? desc.textureheight ?? 64};
  const elements = [], groups = new Map(), outliner = [];
  const model = {meta:{format_version:'4.10',model_format:'free'}, name:item.name, resolution,
    textures:[{name:'texture.png',source:'data:image/png;base64,'+texture.toString('base64'),uv_width:resolution.width,uv_height:resolution.height}], elements, outliner, animations:[]};
  for (const bone of geo.bones) groups.set(bone.name,{uuid:bone.name,name:bone.name,origin:bone.pivot??[0,0,0],rotation:bone.rotation??[0,0,0],children:[]});
  for (const bone of geo.bones) {
    const group = groups.get(bone.name);
    if (bone.parent && groups.has(bone.parent)) groups.get(bone.parent).children.push(group); else outliner.push(group);
    for (const [index,cube] of (bone.cubes??[]).entries()) {
      const uuid = `${bone.name}:cube:${index}`, origin = cube.origin??[0,0,0], size=cube.size??[0,0,0];
      const el = {uuid,name:uuid,type:'cube',from:origin,to:origin.map((v,i)=>v+size[i]),origin:cube.pivot??bone.pivot??[0,0,0],rotation:cube.rotation??[0,0,0],inflate:cube.inflate??0,faces:{}};
      if (Array.isArray(cube.uv)) {
        el.box_uv=true; el.uv_offset=cube.uv; el.mirror_uv=cube.mirror??bone.mirror??false;
        for (const dir of ['down','up','north','south','west','east']) el.faces[dir]={uv:[0,0,0,0],texture:0};
      } else {
        for (const [dir,uv] of Object.entries(cube.uv??{})) {
          const start=uv.uv??[0,0], span=uv.uv_size??[0,0];
          el.faces[dir]={texture:0,uv:[...start,start[0]+span[0],start[1]+span[1]],rotation:uv.uv_rotation??0};
        }
      }
      elements.push(el); group.children.push(uuid);
    }
  }
  if (animations) {
    // ModelDraw uses Blockbench's negative animated X/Y rotation and X translation.
    // BedrockModel$Bone in AuraVisual uses positive axes; compensate in the data.
    const point = (v,channel) => {
      const a=Array.isArray(v)?v:[v,v,v];
      const axis=(i)=>{
        const value=a[i]??0, negative=channel==='rotation'&&i<2||channel==='position'&&i===0;
        return negative?(typeof value==='number'?-value:`-(${value})`):value;
      };
      return {x:axis(0),y:axis(1),z:axis(2)};
    };
    for (const [name,anim] of Object.entries(animations)) {
      const converted={name:name.split('.').at(-1),length:anim.animation_length??0,loop:anim.loop===true?'loop':anim.loop==='hold_on_last_frame'?'hold':'once',animators:{}};
      for (const [bone,channels] of Object.entries(anim.bones??{})) {
        if (!groups.has(bone)) orphanAnimationBones.push({model:item.slug,animation:name,bone});
        const keyframes=[];
        for (const [channel,value] of Object.entries(channels)) {
          if (!['rotation','position','scale'].includes(channel)) continue;
          const frames=typeof value==='object' && !Array.isArray(value) && !('vector' in value) ? value : {'0':value};
          for (const [time,frame] of Object.entries(frames)) {
            if (!Number.isFinite(Number(time))) continue;
            const pre=frame?.pre??frame?.post??frame?.vector??frame, post=frame?.post??pre;
            keyframes.push({channel,time:Number(time),interpolation:frame?.lerp_mode??'linear',data_points:[point(pre,channel),point(post,channel)]});
          }
        }
        converted.animators[bone]={type:'bone',name:bone,keyframes};
      }
      model.animations.push(converted);
    }
  }
  return model;
}
