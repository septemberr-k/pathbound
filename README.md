<p align="center"><img src="branding/logo.png" width="600" alt="Pathbound"></p>

Мод для NeoForge 1.21.1 (NeoForge 21.1.265+). При первом входе в мир хозяин выбирает путь: **Классика** или **Испытание**. Выбор делается один раз. Сложности, их стадии, датапаки, команды и вид экрана выбора задаются данными: конфигом, ресурс-паком и (по желанию) скриптами KubeJS. Сборке не нужен свой jar.
*English version below.*

## Как это работает

- Пока сложность не выбрана (`none`), хозяин мира (или оператор на сервере) видит экран выбора. Esc его не закрывает. Остальные игроки видят «Хозяин мира выбирает сложность…» и могут закрыть это окно.
- Выбор хранится в мире (`data/pathbound.dat`). После выбора мод:
  - выдаёт игрокам стадии сложности (теги игрока, это же стадии KubeJS и FTB Quests) и снимает стадии других сложностей;
  - включает датапаки сложности и выключает датапаки других сложностей (с перезагрузкой данных);
  - выполняет команды сложности (после перезагрузки данных);
  - пишет в чат «Сложность мира: …».
- Команды:
  - `/pathbound mode` — текущая сложность;
  - `/pathbound mode set <id|none>` — сменить сложность сразу (стадии, датапаки, команды);
  - `/pathbound choose` — сбросить выбор и снова показать экран выбора хозяину;
  - `/pathbound list` — все сложности со стадиями и датапаками.

  `set` и `choose` доступны операторам (уровень 2) и хозяину одиночного мира даже без читов. Квесты FTB Quests, уже засчитанные для прежней сложности, не откатываются.

## Из коробки

Две сложности: `classic` («Классика», стадия `pathbound_classic`) и `trial` («Испытание», стадия `pathbound_trial`). Экран — простые карточки: тёмная заливка и рамка цветом сложности, без картинок. Сами сложности ничего не меняют в игре: что значит «Испытание», решает сборка (датапаки, стадии, команды). Картинки, рамки и свой `layout.json` добавляются ресурс-паком (см. «Вид экрана»).

## Сложности: `config/pathbound/difficulties.json`

Файл создаётся при первом запуске мира из шаблона в jar. Читается при каждом старте мира или сервера. Новые поля шаблона в уже созданный файл не попадают.

```json
{
  "difficulties": [
    {
      "id": "trial",
      "name": { "en_us": "Trial", "ru_ru": "Испытание" },
      "description": { "en_us": "Tougher recipes.", "ru_ru": "Сложные рецепты." },
      "color": "#B48CFF",
      "icon": "mypack:textures/gui/skull.png",
      "banner": { "en_us": "mypack:textures/gui/trial_en.png", "ru_ru": "mypack:textures/gui/trial_ru.png" },
      "frame": "mypack:chooser/trial",
      "order": 1,
      "selectable": true,
      "stages": ["mypack_trial"],
      "datapacks": ["config:trial_recipes", "file/extra.zip"],
      "commands": ["gamerule keepInventory false"]
    }
  ]
}
```

| Поле | Значение |
|---|---|
| `id` | `a-z 0-9 _`, до 32 символов, не `none`. Хранится в мире; регистр в командах не важен. |
| `name`, `description` | Строка (как есть), `{"translate": "ключ"}` или `{"en_us": "...", "ru_ru": "..."}` (по языку игрока, иначе en_us). |
| `color` | `"#RRGGBB"`, `"#AARRGGBB"` или число. Цвет названия, подсказки, подсветки и простой рамки. |
| `icon` | id предмета (`minecraft:torch`) или текстура (`ns:textures/gui/x.png`, рисуется в своём размере). Необязательно. |
| `banner` | Нарисованный заголовок вместо названия: id текстуры или по одной на язык. Рисуется в своём размере, 1 пиксель = 1 пиксель GUI. Название остаётся для чата и подтверждения. |
| `frame` | Свой спрайт рамки карточки (см. ниже). |
| `order` | Порядок карточек (по возрастанию). |
| `selectable` | `false` — карточки нет, задать можно только командой или конфигом. |
| `stages` | Теги игрока = стадии KubeJS / FTB Quests. |
| `datapacks` | `config:имя` — папка или `имя.zip` в `config/pathbound/datapacks/`; `builtin:имя` — папка `datapacks/имя` в jar мода; иначе id пака, как в `/datapack list` (`file/...`). |
| `commands` | Команды сервера (уровень 4), один раз при выборе, после перезагрузки данных. |

**Датапаки сложности без своего мода.** Положите датапак (папку с `pack.mcmeta` или `.zip`) в `config/pathbound/datapacks/` и укажите его как `"config:имя"`. Такие паки сами не включаются: Pathbound включает их только в мирах с этой сложностью.

**Сервер.** `config/pathbound-server.toml`: `defaultMode = "none"` или id. Сложность из него применяется при старте к миру без сложности, экран выбора не показывается.

## Вид экрана

В jar нет ни картинок, ни `layout.json`: без них экран рисует простые карточки. Свою тему кладите в любой ресурс-пак или в `kubejs/assets/`: `assets/pathbound/chooser/layout.json` и текстуры под своим namespace.

`assets/pathbound/chooser/layout.json` — все ключи необязательны:

```json
{
  "background": { "blur": true, "texture": "mypack:textures/gui/chooser_bg.png", "tile": false, "color": "#700D0711" },
  "title":    { "text": { "translate": "pathbound.title" }, "color": "#E8C46A" },
  "subtitle": { "text": { "translate": "pathbound.subtitle" }, "color": "#B8A6C8" },
  "card": {
    "width": 190, "min_height": 110, "spacing": 14, "columns": 0, "padding": 12,
    "text_align": "left", "frame_inset": 0,
    "frame": "mypack:chooser/card", "tint_frame": false,
    "color": "#C0101018", "hover_color": "#E0201A2C",
    "text_color": "#E2D6EE", "name_color": "#FFFFFF", "hint": { "translate": "pathbound.pick" }, "hint_color": "#8C7AA0",
    "icons": true
  },
  "sounds": { "hover": "minecraft:ui.button.click", "click": "minecraft:ui.button.click" },
  "confirm": true
}
```

- `columns: 0` — сколько поместится. Карточки переносятся в новые ряды, при нехватке высоты появляется прокрутка колесом.
- `name_color` не указан — название рисуется цветом сложности.
- `text_align: "center"` — описание по центру карточки и посередине между заголовком и подсказкой (по умолчанию `left`).
- `frame_inset` — сколько пикселей с края спрайта рамки прозрачно (шипы, туман). Подсветка и тень под карточкой рисуются по видимой рамке (по умолчанию 0). Общий для всех рамок.
- `color` и `hover_color` относятся к простой рамке. Спрайт рисует фон сам.
- `confirm: false` — без окна «Играть в режиме…?». Карточка остаётся выбранной (`_selected`), пока сервер не ответит.
- Тексты экрана — ключи `pathbound.*` в `assets/pathbound/lang/<язык>.json`.

### Анимации

Всё в блоке `"animations"` layout.json; `"animations": false` выключает все анимации сразу. Значения по умолчанию:

```json
"animations": {
  "fade_ms": 250,            // появление фона и заголовка
  "intro_ms": 380,           // выезд одной карточки снизу
  "stagger_ms": 70,          // задержка между карточками
  "intro_offset": 14,        // насколько ниже карточка начинает выезд, px
  "easing": "ease_out_back", // linear | ease_out | ease_in_out | ease_out_back (с лёгким перелётом)
  "hover_lift": 4,           // подъём карточки под курсором, px (с тенью под ней)
  "hover_ms": 70,            // скорость подъёма: за это время проходится половина пути
  "press_ms": 140,           // «нажатие»: карточка чуть сжимается, затем открывается подтверждение
  "press_scale": 0.96,
  "glow": true,              // подсветка цветом сложности вокруг поднятой и выбранной карточки
  "hint_pulse_ms": 1400,     // «Нажмите, чтобы выбрать» мягко дышит под курсором; 0 — без пульса
  "selected_pulse_ms": 900,  // пульс подсветки выбранной карточки
  "icon_bob": 1              // покачивание иконки под курсором, px; 0 — нет
}
```

Анимации считаются по времени, а не по кадрам, поэтому не зависят от FPS. Вступление проигрывается один раз: после возврата из окна подтверждения оно не повторяется.

### Спрайты рамок

Спрайт GUI — `assets/<ns>/textures/gui/sprites/<путь>.png`, id `<ns>:<путь>`. Состояния: `<путь>.png` (обычное), `<путь>_hovered.png` (под курсором), `<путь>_selected.png` (выбрана). Если состояния нет, берётся обычное; если нет и его — простая рамка. Порядок поиска: `frame` сложности, затем `card.frame` из layout.json.

Масштабирование задаёт `<путь>.png.mcmeta` (как у ванильных спрайтов 1.21), можно с анимацией:

```json
{ "animation": { "frametime": 4, "width": 224, "height": 192 },
  "gui": { "scaling": { "type": "nine_slice", "width": 224, "height": 192, "border": 32 } } }
```

- `nine_slice`: углы `border`×`border` не растягиваются, края и середина повторяются по размеру карточки.
- Другие типы: `stretch` (растянуть всё), `tile` (`{"type": "tile", "width": 16, "height": 16}` — повторять).
- `padding` в layout.json должен быть больше видимой толщины рамки, иначе текст ляжет на неё.
- `tint_frame: true` окрашивает спрайт в цвет сложности. Под это удобно рисовать рамку в серых тонах.

## KubeJS

Модуль KubeJS загружается, только если KubeJS установлен (`kubejs.plugins.txt`). Без KubeJS мод работает так же, просто без скриптов.

**Стартовый скрипт** (`kubejs/startup_scripts/`) — выполняется при старте мира, после чтения конфига:

```js
PathboundEvents.register(e => {
  e.add('nightmare', d => d
    .name({ en_us: 'Nightmare', ru_ru: 'Кошмар' })      // или .name('Nightmare') / .nameKey('my.key')
    .description({ en_us: 'No mercy.', ru_ru: 'Без пощады.' })
    .color('#FF5050').icon('minecraft:wither_rose').order(2)
    .stage('nightmare')
    .datapack('config:nightmare')
    .command('difficulty hard')
    .frame('mypack:chooser/nightmare'))
  e.modify('trial', d => d.color(0xFF8040))             // изменить сложность из конфига
  e.remove('classic')                                     // убрать
  console.info(e.ids)
})
```

Методы `d`: `name`, `nameKey`, `description`, `descriptionKey`, `color`, `icon`, `banner`, `order`, `selectable`, `stage`, `clearStages`, `datapack`, `clearDatapacks`, `command`, `frame`.

**Серверные скрипты** (`kubejs/server_scripts/`):

```js
// e.cancel() запрещает выбор: игрок видит «Сейчас нельзя выбрать…», экран выбора возвращается
PathboundEvents.beforeChoose(e => {
  if (e.difficulty == 'nightmare' && !e.player.stages.has('veteran')) e.cancel()
})
// после выбора (e.player = null, если сложность пришла из конфига или команды)
PathboundEvents.afterChoose(e => {
  e.server.tell(`Сложность: ${e.difficulty}`)
})
// вход игрока в мир, где сложность уже выбрана
PathboundEvents.playerJoin(e => console.info(`${e.player.username}: ${e.difficulty}`))

// биндинг
Pathbound.get(server)             // 'trial' | 'none' | ...
Pathbound.set(server, 'classic')  // как /pathbound mode set; false для неизвестного id
Pathbound.ids()                   // ['classic', 'trial', ...]
Pathbound.info('trial')          // описание сложности
Pathbound.isChosen(server)
```

Рабочие тестовые скрипты лежат в `src/clientValidation/kubejs/`.

Java API для других модов: `dev.pathbound.DifficultyApi.get/set/difficulties`, хуки `dev.pathbound.Hooks.register/beforeChoose/afterChoose/playerJoin`.

## Сборка и проверки

```bash
./gradlew build
```

`build` также запускает JUnit-тесты математики анимаций.

```bash
./gradlew runGameTestServer > run.log 2>&1; grep -E "required tests (passed|failed)" run.log
```

```bash
./gradlew runDifficultyValidation -PvalidationChoice=hard -PvalidationProps="pathbound.sizes=true"
```

```bash
./gradlew runDifficultyValidation -PvalidationChoice=nightmare -PvalidationProps="pathbound.expectCards=3;pathbound.refuseFirst=hard;pathbound.expectTorch=1;pathbound.expectTag=kjs_after_choose_nightmare"
```

- GameTests: сохранение, разбор конфига, стадии, датапак из `config/pathbound/datapacks` (`src/gameTest/config`), третья сложность (встроенный датапак, стадия, команда), `defaultMode`, команды.
- Клиентский прогон проверяет мод на настоящей сборке (Thaumaturge Renaissance): её `config/pathbound` (`-PpackConfig=...`), квесты FTB (`-PquestSource=...`), FTB Quests и KubeJS из её папки `mods` (`-PpackMods=...`; `-PnoPackMods` — без них). Новый плоский мир, выбор через экран, проверка сервера, рецептов и квестов. Кадры и `proof.txt` — в `build/ci/difficulty/<имя>/`.
- Серверные проверки (`runServerCheck`, `runServerCheckKubejs`, `runServerCheckDefault`) требуют `eula.txt` в папке прогона.

Иконка проекта — `branding/icon.png`. Логотип в списке модов (`pathbound_logo.png` в jar) и логотип с надписью (`branding/drawn/logo_wordmark.png`) рисует `pathbound_wordmark_px.ps1`, аватарку (`branding/drawn/avatar.png`) — `pathbound_avatar_px.ps1` (оба скрипта в проекте квестов Thaumaturge Renaissance).

---

# Pathbound (English)

NeoForge 1.21.1 mod: on first join the world's host picks a path, **Classic** or **Trial**, once per world. Difficulties, their player stages, datapacks and commands, and the look of the chooser are data: a config file, any resource pack and optional KubeJS scripts. A modpack needs no jar of its own.

- **Flow.** While the world has no difficulty (`none`), the host (or an operator) gets the chooser; Esc does not close it. Others see a closable waiting notice. On a choice the mod grants the difficulty's stages (player tags = KubeJS / FTB Quests stages) and removes other difficulties' stages, enables its datapacks and disables other difficulties' packs (with a data reload), runs its commands after the reload, and announces it in chat. Commands: `/pathbound mode`, `/pathbound mode set <id|none>`, `/pathbound choose` (clear the choice and show the chooser again), `/pathbound list`; `set` and `choose` need permission 2 or the singleplayer world owner (cheats not needed).
- **Out of the box:** `classic` (stage `pathbound_classic`) and `trial` (stage `pathbound_trial`) as plain cards (a dark fill and a frame in the difficulty colour, no images). They change nothing by themselves; the pack decides what Trial means. Images, frames and a layout come from a resource pack.
- **`config/pathbound/difficulties.json`** (written from the jar's template on first run, read on every world/server start). Fields: `id`, `name`/`description` (string, `{"translate": key}` or `{"en_us": ..., "ru_ru": ...}`), `color`, `icon` (item id or a texture, drawn at its own size), `banner` (a drawn title texture, or one per language), `frame`, `order`, `selectable`, `stages`, `datapacks`, `commands`.
- **Datapacks:** `config:name` = a folder or `name.zip` in `config/pathbound/datapacks/` (never enabled on its own, only in worlds with that difficulty), `builtin:name` = `datapacks/name` in the mod jar, anything else = a pack id as in `/datapack list`.
- **Server:** `config/pathbound-server.toml` → `defaultMode` applies an id to new worlds at server start and skips the chooser.
- **Look.** The jar ships no images or layout; a resource pack (or `kubejs/assets/`) adds `assets/pathbound/chooser/layout.json` and textures under its own namespace. `chooser/layout.json`: background, title, subtitle, card size/spacing/columns/padding, `text_align`, `frame_inset`, frame sprite, tint, colours, hint, icons, sounds, confirm, `animations` (`false` turns them off; `fade_ms`, `intro_ms`, `stagger_ms`, `intro_offset`, `easing`, `hover_lift`, `hover_ms`, `press_ms`, `press_scale`, `glow`, `hint_pulse_ms`, `selected_pulse_ms`, `icon_bob`).
- **Frame sprites:** GUI sprites `assets/<ns>/textures/gui/sprites/<path>.png` with states `<path>`, `<path>_hovered`, `<path>_selected`; scaling (and optional animation) in `<path>.png.mcmeta`, e.g. `nine_slice`. A difficulty's own `frame` wins over the layout's.
- **KubeJS** (optional, loaded only with KubeJS): startup `PathboundEvents.register(e => e.add(id, d => ...) / e.modify(id, d => ...) / e.remove(id))`; server `PathboundEvents.beforeChoose` (`e.cancel()` refuses), `afterChoose` (`e.player` may be null), `playerJoin`; binding `Pathbound.get(server) / set(server, id) / ids() / info(id) / isChosen(server)`.
- **Build and checks:** see the commands above.

---

Лицензия: [MIT](LICENSE). / License: [MIT](LICENSE).
