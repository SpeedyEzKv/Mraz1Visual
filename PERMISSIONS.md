# Разрешения на встроенную косметику

Паки ниже встраиваются в клиент (.jar) и раздаются через лаунчер всем игрокам. Это возможно только с разрешения
авторов. Скриншоты переписки и письма кладутся в `permissions/<id пака>/`.

| Пак | id | Автор | Дата разрешения | Где получено | Условия | В сборке |
|---|---|---|---|---|---|---|
| Blades of Majestica | `majestica` | Eftann ([Modrinth](https://modrinth.com/project/3tXcbjvK)) | — | — | указать автора и ссылку | да |
| Weapons Pack — Free Edition | `weapons_free` | Redstone Labs | — | — | указать автора | да |
| Fantasy Sword (`.bbmodel`) | `fantasy_sword` | автор модели | — | — | нейтральное имя без «Zelda» (товарный знак Nintendo) | да |
| Carnivoret Weapon Set Vol. 1 | `carnivoret` | EliteCreatures / khawndev (MCModels) | — | — | — | **ждёт подтверждения** |
| CUBEES Currets I | `cubees_currets` | Cubees Studio | — | — | — | **ждёт подтверждения** |
| CUBEES RE Biomes 1 | `cubees_biomes` | Cubees Studio | — | — | — | **ждёт подтверждения** |

## Почему три пака «ждут подтверждения»
Внутри архивов есть условия, которые без отдельного разрешения запрещают распространение:
- **CUBEES RE Biomes** — `License.txt` Cubees Studio: «Share, upload… or give away the Pack or any file from it… to anyone who has not bought their own copy» — запрещено; модели нельзя использовать «in a product, plugin, pack… of your own».
  Currets — того же автора (Cubees Studio).
- **Carnivoret** — пак с MCModels (метка `mcmodels` в `pack.mcmeta`), условия маркетплейса тоже запрещают распространение.

Разрешение автора эти условия снимает, но репозиторий публичный, а коммит нельзя «забрать обратно», поэтому эти паки
попадают в ресурсы мода, только когда в `permissions/<id>/` лежит подтверждение.

## Как включить, когда разрешение есть
1. Положить скриншот(ы) переписки в `permissions/carnivoret/`, `permissions/cubees_currets/`, `permissions/cubees_biomes/`.
2. Заполнить строки таблицы выше (дата, где получено, условия).
3. В папке `mod`: `./gradlew importCosmetics` — паки отберутся в `src/main/resources/assets/mraz1visual/cosmetics/`.
4. Закоммитить — со следующего релиза паки встроены у всех.

Каталог, атрибуция и раздел «Авторы» в клиенте уже знают про все шесть паков. Пока пак не встроен, он работает через
локальный импорт (`mraz1visual/weapon_skins` и `mraz1visual/pets`) — у тебя на компьютере всё видно уже сейчас.
