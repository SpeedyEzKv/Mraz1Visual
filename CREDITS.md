# Ассеты и лицензии

## Добавлено в редизайне «iOS 26 / Liquid Glass» (ветка `redesign-ios26`)

**Сторонних ассетов не добавлено.** Всё новое сделано своими руками, кодом и шейдерами, без чужих моделей, текстур, HDR и звуков:

| Что | Как сделано |
|---|---|
| Скругления, стекло, тени GUI | свой шейдер `assets/mraz1visual/shaders/core/sdf_rect.{vsh,fsh}` (signed distance field, суперэллипс) |
| Размытие фона меню | пирамида уменьшения кадра (`GlassBlur`), без шейдеров |
| 3D-мечи (6 стилей) | процедурная геометрия в коде (`CustomSwords`) |
| «Аура предмета» | частицы и мягкие пятна по контуру модели предмета (`FireHand`) |
| Крылья, ореол, аура игрока, «Световые шаги», волна удара | процедурная геометрия (`Wings3D`, `Halo`, `PlayerAura`, `LightSteps`, `HitEffects`) |
| Питомцы | оригинальные персонажи, собраны из примитивов в коде (`Pets`) |
| Палитры Эфир/Аметист/Закат/Изумруд/Обсидиан/Хром | свои (`render/Palette`) |

## Уже были в проекте

- Шрифты интерфейса: Manrope, Unbounded, Inter, DejaVu — SIL Open Font License / свободная лицензия DejaVu
  (файлы лицензий: `mod/src/main/resources/assets/mraz1visual/font/OFL-*.txt`, `LICENSE-DejaVu.txt`, `launcher/src/fonts/OFL-*.txt`).
- ZakoOpt — LGPL, лицензия и NOTICE в `release/mods/`.

## Проработка вкладки «Визуалы» (ветка `visual-polish`)

**Сторонних ассетов не добавлено.** Все эффекты процедурные: частицы, ленты, кольца, ромбы, виньетки и линии скорости
рисуются кодом (`render/Fx*`, `render/Ribbon`, модули в `module/`). Блеск зачарований перекрашивает **ванильную** текстуру
игры прямо в памяти (файлы не копируются). Идеи — общие для визуальных клиентов направления; чужой код, текстуры, модели и шейдеры не использовались.

## Встроенная косметика (ветка `ios26-overhaul`)

Паки встроены в клиент **с разрешения авторов** (подтверждения — `permissions/`, таблица — [`PERMISSIONS.md`](PERMISSIONS.md)):

| Коллекция | Автор | Ссылка | Статус |
|---|---|---|---|
| Blades of Majestica | Eftann | https://modrinth.com/project/3tXcbjvK | встроено, используется с разрешения автора |
| Weapons Pack — Free Edition | Redstone Labs | https://discord.gg/fBQRQyAsj7 | встроено, используется с разрешения автора |
| Fantasy Sword (`.bbmodel`) | автор модели | — | встроено под нейтральным именем, с разрешения автора |
| Carnivoret Weapon Set Vol. 1 | EliteCreatures / khawndev | https://mcmodels.net | ждёт подтверждения в `permissions/carnivoret/` |
| CUBEES Currets I, CUBEES RE Biomes 1 | Cubees Studio | https://discord.gg/N8K2sQdbx9 | ждёт подтверждения в `permissions/cubees_*` |

Набор **Mraz1Visual** (8 мечей, 2 топора, булава, крылья, плащи, нимбы, питомцы) — свой: модели и текстуры
сгенерированы кодом (`tools/cosmetics/gen-mraz1.js`, процедурные модули клиента).

## Музыка и тексты
- **JLayer** 1.0.1 (MP3) — LGPL-2.1, javazoom.net. Встроен в .jar отдельной библиотекой (jar-in-jar), не изменён.
- **jflac-codec** 1.5.2 (FLAC) — Apache License 2.0.
- **stb_vorbis** (OGG) — public domain / MIT, из LWJGL, который уже есть в игре.
- **LRCLIB** (https://lrclib.net) — открытый API синхронных текстов песен; тексты кэшируются только локально, для личного показа.

## AuraVisual integration

Visual resources: AuraVisual, archive `source auravisual by @fixedproject.zip`,
author notice `source by @fixedproject`, https://t.me/fixedproject.
Original license declaration: **All Rights Reserved**. The archive provides no
standalone redistribution license. Individual cosmetic authors remain recorded
in their original `meta.json` files and the converted catalog.

IAS: The_Fireplace / VidTu, LGPL-3.0-or-later,
https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher.
Mod Menu: TerraformersMC, MIT, https://github.com/TerraformersMC/ModMenu.
Upstream dependency jars retain their license files unchanged.

## Eclipse additions and native media bridge

Eclipse Crown, Shard Wings, posed Ghost Dash, reactive pet integration, surface/landing
effects, skin transitions and photo mode are implemented in Mraz1Visual source.
Imported Aura assets and their original author metadata remain unchanged.

The bundled `mraz1visual-media.exe` is built from `launcher/src-tauri/src/media.rs`
and `src/bin/mraz1visual-media.rs`. It reuses the launcher media implementation.
Windows API bindings: Microsoft `windows` crate 0.58, MIT OR Apache-2.0,
https://github.com/microsoft/windows-rs. The bridge uses Windows SMTC metadata and
commands; it does not record system audio or read browser profiles.

## Локальная коллекция Pulse

Модели, текстуры и анимации коллекции Pulse из debuda-beta 1.1. Автор указан как LuckyVIP в 27 карточках; для остальных сохранено название исходной коллекции. Текстура кольца прыжка — Pulse SRC. Исходные обозначения прав — All-Rights-Reserved. Перелив обода China Hat реализован средствами Mraz1Visual по визуальному приёму из mbreadysrc. Подробности включены в JAR в META-INF/PULSE-COSMETICS-NOTICE.txt.
