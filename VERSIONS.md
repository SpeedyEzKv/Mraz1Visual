# Версии Minecraft

Один исходный код (`mod/src`) собирается под все версии через [Stonecutter](https://stonecutter.kikugie.dev/):
отличия API — строковые замены в `mod/build.gradle` и блоки `//? if >=26.1 {` в коде, крупные куски — в пакете `dev.mraz1visual.compat`.
Зависимости каждой версии — `mod/versions/<версия>/gradle.properties`.

| Minecraft | Проект сборки | Статус в лаунчере | Java | Fabric Loader | Fabric API | Sodium | Iris | MCEF |
|---|---|---|---|---|---|---|---|---|
| 1.21.11 | `1.21.11` | Рекомендуется | 21 | ≥ 0.18.1 | 0.141.3+1.21.11 | 0.8.7 (mc1.21.11-0.8.15-beta.1) | 1.10.7–1.10.8 | 0.3.3+mc1.21.11 |
| 26.1, 26.1.1, 26.1.2 | `26.1.2` | Бета | 25 | ≥ 0.19.5 | 0.155.3+26.1.2 | mc26.1.2-0.9.2 | 1.11.4+26.1 | 0.3.3+mc26.1 |
| 26.2 | `26.2` | Бета | 25 | ≥ 0.19.5 | 0.161.0+26.2 | mc26.2-0.9.2 | 1.11.4+26.2 | 0.3.3+mc26.2 |
| 26.3 | `26.3` | Новая | 25 | ≥ 0.19.5 | 0.164.0+26.3 | mc26.3-0.9.3-beta.1 | 1.11.7+26.3 | 0.3.3+mc26.3 |

Снапшоты 26.4 не поддерживаются: API меняется каждую неделю.

## Что отличается между 1.21.11 и 26.x

- 26.x выходит **без обфускации**: Loom без ремапа (`net.fabricmc.fabric-loom`), без строки `mappings`. Выбор делает `dev.kikugie.loom-back-compat`.
- Java 25 вместо 21. Gradle запускается на JDK 25+ (Loom 1.19 этого требует), сам мод 1.21.11 по-прежнему компилируется под Java 21 (toolchain).
- GUI: `GuiGraphics` → `GuiGraphicsExtractor`, `Screen.render` → `extractRenderState`, `renderBackground` → `extractBackground`, `drawString` → `text`, `renderItem` → `item`;
  пакет `client.gui.render.state` → `client.renderer.state.gui`.
- Рендер: `LightTexture` → `Lightmap`, `ItemTransform` и `BakedQuad` переехали в `client.resources.model.*`.
- Fabric API: события мира переехали в пакет `world`, изменились `SpecialGuiElementRegistry` и регистрация слоёв сущностей.

## Сборка

```bash
cd mod
./gradlew build                 # все версии, jar'ы — в mod/build/libs/all/
./gradlew :26.3:build           # одна версия
./gradlew "Set active project to 26.3"   # развернуть src/ под 26.3 для IDE (в git всегда 1.21.11)
```

Имя файла: `mraz1visual-<версия мода>+mc<версия Minecraft>.jar`.
