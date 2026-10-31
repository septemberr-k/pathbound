# Pathbound — аудит

История до переименования (trq_difficulty 0.1–0.4.1) осталась в `thaumaturge-quests/difficulty_mod/AUDIT.md`.

## 1.0.0 (2026-10-31): переименование в Pathbound

- id `pathbound`, пакет `dev.pathbound`, команда `/pathbound`, KubeJS `PathboundEvents` и биндинг `Pathbound`, ключи `pathbound.*`, конфиг `config/pathbound/`, `pathbound-server.toml`, данные мира `pathbound.dat`, версия сети `1`.
- Мод больше не привязан к сборке: сложности по умолчанию `classic` / `expert` без своих рецептов, тема (рамки, заголовки, иконки, layout.json) в jar. Генератор рецептов сборки и датапак `trq_hard` убраны из мода.
- Новое: датапаки `config:имя` из `config/pathbound/datapacks/` (папка или .zip), чтобы сборка могла поставлять датапаки сложности без своего jar.
- Убраны псевдонимы id (`classic`→`normal` и т. п.): они были нужны только сборке и мешали своим сложностям с такими id.
- Миры trq_difficulty (`data/trq_difficulty.dat`) не переносятся: такой мир снова покажет экран выбора.
- Проверено: `./gradlew build`, GameTests 7/7 (датапак из `config/pathbound/datapacks`), клиентские прогоны `hard` и `nightmare` с конфигом сборки. Серверные проверки не запускались (нужен `eula.txt`).

## 1.0.0, доработка (2026-10-31): свои сложности, без ассетов сборки

- Сложности по умолчанию: `classic` («Классика») и `trial` («Испытание»), стадии `pathbound_classic` / `pathbound_trial`, свои описания. Экран по умолчанию — простые карточки без картинок (padding 12, min_height 110, spacing 14).
- Из jar убраны рамки, заголовки, иконки кролика и черепа, `card.png` и `layout.json`: это оформление сборки. Оно переехало в `thaumaturge-quests/pathbound_pack/kubejs/assets` (текстуры под namespace `trq`, layout — `pathbound/chooser/layout.json`).
- Проверено: `./gradlew build`, GameTests 7/7, клиент с конфигом и темой сборки (`hard`), клиент без сборки и без KubeJS/FTB на настройках по умолчанию (`trial`).
