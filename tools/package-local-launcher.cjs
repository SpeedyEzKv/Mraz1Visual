// Local preview bundle. Does not publish or modify an installed game profile.
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const jars = path.join(root, 'mod/build/libs/all');
const output = path.join(root, 'launcher/portable');
const executable = path.join(root, 'launcher/src-tauri/target/release/mraz1visual-launcher.exe');
if (!fs.existsSync(executable)) throw new Error('Build the launcher with cargo build --release first.');
const modVersion = /^mod_version=(.+)$/m.exec(fs.readFileSync(path.join(root, 'mod/gradle.properties'), 'utf8'))[1].trim();
const versions = JSON.parse(execFileSync(process.execPath,
  [path.join(root, 'tools/release/versions-manifest.js'), jars, 'bundle://mods', modVersion], {encoding:'utf8'}));
// Expose only the four versions actually built/tested, not historical compatibility aliases.
for (const version of versions) version.covers = [version.minecraft];
fs.mkdirSync(path.join(output, 'mods'), {recursive:true});
for (const version of versions) {
  const destination = path.join(output, 'mods', version.file);
  fs.copyFileSync(path.join(jars, version.file), destination);
  const hash = crypto.createHash('sha256').update(fs.readFileSync(destination)).digest('hex');
  if (hash !== version.sha256) throw new Error(`Bundle checksum mismatch: ${version.file}`);
}
const main = versions.find(v => v.minecraft === '1.21.11');
const manifest = {
  client_version:modVersion, build:20261123, minecraft:main.minecraft,
  fabric_loader:main.fabric_loader, java:main.java, mods:[], versions,
  changelog:[
    '37 моделей Pulse, кольцо прыжка Pulse, обновлённые следы и обод China Hat. Шрифт Inter на экранах игры, реальные статусы загрузки и кнопка отмены.',
    'Eclipse — световой серп, гранёные крылья, движение питомцев на ступенях и направленная смена скина оружия. Обновлены следы шагов и приземления.',
    'Аудит визуалов: исправлены фоторежим при загрузке конфига, Aspect Ratio в 26.x, очистка следов/эффектов и сохранение косметики.',
    'Музыка: перемотка на паузе, поздние обложки, восстановление интерфейса после сбоя и отмена просроченных команд.',
    'Eclipse: живая корона, крылья из осколков, настоящие призрачные копии, реакции питомцев, вода/снег, приземление и затмение при убийстве.',
    'Фоторежим по F7: локальная орбита, FOV, яркость, заморозка косметики, снимок и восстановление настроек.',
    'Музыка в лаунчере: медиасессии Windows, выбор источника, настоящие команды и связанный локальный плеер игры. Музыкальный HUD работает и без лаунчера.',
    'Mraz1Visual: сохранены собственные главное меню, ClickGUI, темы, шрифты и фоны.',
    'Из AuraVisual взяты только косметика и игровые визуальные функции: 78 моделей косметики, 119 моделей предметов и эффекты.',
    'Cubes, World Lines, Aura Particles со своим PNG, Effect Remind, Armor Durability, Potion Highlight, See Invisible и No Fluids.',
    'Смена аккаунтов через In-Game Account Switcher. Полный список модов через Mod Menu.',
    'Игра и настройки этой сборки сохраняются в папке data рядом с лаунчером.',
    'Оболочка AuraVisual не используется. Полный перечень перенесённых функций и ограничения — в отчёте проекта.',
    'Исправлены мигание хотбара с Motion Blur и курсор после F11. Удалён рисованный предпросмотр. Вкладка косметики: 78 настоящих моделей Aura, категории, надевание и снятие.'
  ], releases:[], links:[], launcher_version:'', launcher_url:'',
  discord_app_id:'', github_client_id:''
};
fs.writeFileSync(path.join(output, 'manifest.local.json'), JSON.stringify(manifest,null,2));
fs.writeFileSync(path.join(output, 'portable.flag'), 'Mraz1Visual local preview\n');
fs.copyFileSync(path.join(root, 'AURA_INTEGRATION.md'),path.join(output,'ИНТЕГРАЦИЯ.md'));
fs.copyFileSync(path.join(root, 'CREDITS.md'),path.join(output,'CREDITS.md'));
fs.copyFileSync(path.join(root, 'UI_FIXES.md'),path.join(output,'UI_FIXES.md'));
fs.copyFileSync(path.join(root, 'ECLIPSE_FEATURES.md'),path.join(output,'ECLIPSE_FEATURES.md'));
fs.copyFileSync(path.join(root, 'VISUAL_AUDIT_20261118.md'),path.join(output,'VISUAL_AUDIT_20261118.md'));
fs.writeFileSync(path.join(output, 'ЧИТАТЬ.txt'),
  'Mraz1Visual — косметика и игровые визуалы AuraVisual, собственный интерфейс Mraz1Visual\r\n\r\n'+
  '1. Сохрани всю папку вместе: exe, manifest.local.json, portable.flag и mods.\r\n'+
  '2. Запусти Mraz1Visual.exe. Выбери 1.21.11 и нажми «Играть».\r\n'+
  '3. При первом запуске нужен интернет для Minecraft, Java, Fabric и дополнительных модов.\r\n'+
  '4. Новые визуалы уже в mods, скачивать их с GitHub не требуется.\r\n'+
  '5. Аккаунты: главное меню игры → «Аккаунты». Настройки визуалов: MRAZ1VISUAL или правый Shift.\r\n'+
  '6. Косметика: MRAZ1VISUAL → Косметика. Клик по модели — надеть или снять; есть «Снять всё».\r\n'+
  '7. Свой PNG частиц: data/game/config/mraz1visual/particles/particle.png.\r\n'+
  '8. После обновления закрой игру и заново запусти этот лаунчер. Исправления — в UI_FIXES.md.\r\n\r\n'+
  '9. Новые эффекты: вкладки Visuals, Combat FX и Cosmetics. Фоторежим — F7 в мире.\r\n'+
  '10. Музыка — карточка справа внизу лаунчера. В игре включи Music → Now Playing. Реальный PCM доступен встроенному плееру; внешние медиасессии не дают аудиосэмплы.\r\n'+
  '11. Проверки и ограничения новых функций — ECLIPSE_FEATURES.md.\r\n\r\n'+
  'Свои миры и настройки эта копия сохраняет в data. Старую установку в AppData она не изменяет.\r\n'+
  'Главное меню, темы, ClickGUI, шрифты, фон и HUD сохраняют стиль нашего клиента.\r\n');
const targetExecutable=path.join(output, 'Mraz1Visual.exe');
const digest=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
if(!fs.existsSync(targetExecutable)||digest(executable)!==digest(targetExecutable))fs.copyFileSync(executable,targetExecutable);
console.log(output);
