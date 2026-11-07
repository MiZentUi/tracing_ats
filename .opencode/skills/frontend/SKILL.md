---
name: Frontend
description: Использовать при любых задачах по frontend Tracing ATS — создание и правка интерфейса на Vue 3 + TypeScript, настройка Vite, Tailwind CSS, shadcn-vue, генерация клиента из OpenAPI, формы, авторизация, слепой режим и отображение Score_pre/Score_final.
---

# Frontend Tracing ATS

Референс стека и методологии — репозиторий `temp/frontend-example/fractal-flame`
(ветка `feature/add-frontend`). Домен и экраны там другие: копируй технологии,
структуру и подход, но не бизнес-логику, тексты и вёрстку.

## Источники истины

1. `README.md` — раздел 14 (структура) и описание web-слоя.
2. `contracts/openapi.yaml` — публичный REST-контракт.
3. `AGENTS.md` — раздел 9 (Frontend) и инварианты 4, 11, 12, 13.
4. Этот скилл — как эти правила применяются в коде.

Если контракта или поля нет в `contracts/openapi.yaml`, не выдумывай эндпоинт,
поле или тип.

## Стек

| Слой | Технология | Версия в референсе |
|---|---|---|
| Фреймворк | Vue 3, Composition API, `<script setup lang="ts">` | 3.5 |
| Язык | TypeScript, strict, `vue-tsc -b` | 6.0 |
| Сборка | Vite + `@vitejs/plugin-vue` + `vite-svg-loader` | 8.0 |
| Роутинг | Vue Router, history mode, meta-guards | 5.0 |
| Стили | Tailwind CSS 4 через `@tailwindcss/vite`, CSS-first | 4.3 |
| UI-кит | shadcn-vue (style `new-york`, neutral, CSS variables) | — |
| Примитивы | reka-ui | 2.9 |
| Варианты классов | class-variance-authority + `cn()` (clsx + tailwind-merge) | 0.7 / 3.6 |
| Иконки | lucide-vue-next | 1.0 |
| Уведомления | vue-sonner | 2.0 |
| Формы | vee-validate + `@vee-validate/yup` + yup | 4.15 / 1.7 |
| HTTP | axios + сгенерированный `typescript-axios` клиент | 1.16 |
| Утилиты | `@vueuse/core` | 14.3 |
| Контейнер | multi-stage: OpenAPI generator → Node 24 → nginx | — |

Версии — ориентир из референса. В `package.json` фиксируй конкретные версии и
коммить `package-lock.json`; не смешивай мажоры без причины.

## Структура `frontend/`

Каталог `frontend/` в Tracing ATS пока не создан. При создании придерживайся
слоистой структуры референса:

```text
frontend/
├── src/
│   ├── main.ts                 # createApp, router, provide глобальных stores
│   ├── App.vue                 # layout + RouterView
│   ├── router/index.ts         # named routes, meta.requiresAuth / meta.noAuth
│   ├── api/
│   │   ├── index.ts            # axios instance, interceptors, фабрики URL
│   │   └── generated/          # генерация из OpenAPI, в .gitignore
│   ├── components/
│   │   ├── ui/                 # shadcn-vue, только примитивы
│   │   └── <domain>/           # доменные компоненты
│   ├── views/                  # страницы: оркестрация данных и сценариев
│   ├── schemas/                # yup-схемы, типизированные от DTO
│   ├── composables/            # useAuth, useScroll, use<Domain>
│   ├── stores/                 # только общее состояние
│   ├── lib/utils.ts            # cn()
│   └── style.css               # @import tailwindcss + токены
├── nginx/templates/default.conf.template
├── components.json
├── vite.config.ts
├── tsconfig*.json
├── Dockerfile
└── package.json
```

Правила структуры:

- `views` знают про API и сценарии, `components` — презентационные и получают
  данные через props/`defineModel`.
- `components/ui` — скопированные примитивы shadcn-vue. Доменные обёртки не
  клади в `ui`, выноси в `components/<domain>`.
- Один компонент — один файл, имя `PascalCase.vue`; composables — `useX.ts`,
  схемы — `xSchema.ts`, типы — `PascalCase`.
- Импорты внутри `src` — через alias `@/`.

## Подход разработки

### 1. Контракт → клиент

- Источник типов — `contracts/openapi.yaml`. Клиент генерируется командой
  `npm run gen-api` в `src/api/generated` (`typescript-axios`).
- Сгенерированный каталог не коммить: он в `.gitignore`, как в референсе.
- Не дублируй DTO руками и не пиши «похожие» интерфейсы. Если типа не хватает —
  меняй контракт в том же изменении и перегенерируй клиент.
- Скрипт генерации должен указывать на реальный путь контракта
  (`../contracts/openapi.yaml` из `frontend/`, либо контракт копируется в
  build-context). CLI генератора объяви в `devDependencies` или используй
  Docker-образ; в референсе CLI не объявлен — это его дефект, не повторяй.
- `npm run build` не должен зависеть от уже сгенерированного каталога: добавь
  генерацию в `prebuild`/`build` или в отдельный шаг CI.

### 2. API-слой

- Все запросы — через `src/api/index.ts`. В компонентах нет «голого» `axios`.
- Один `axiosInstance` с `baseURL` из `import.meta.env.VITE_API_URL`; dev-прокси
  `/api` → `http://localhost:8080`.
- Токен доступа — в request interceptor, обработка `401` — в response
  interceptor с single-flight refresh: параллельные запросы ждут один refresh,
  затем повторяются.
- Refresh-token не храни в JS. Референс использует HttpOnly cookie; для
  Tracing ATS это предпочтительный вариант, если backend его поддерживает.
  Решение о хранении access-token (`localStorage` vs память/HttpOnly) согласуй.
- Ошибки приводи к одному виду: `ApiStatusResponse` (`code`, `status`,
  `message`) → toast или `setFieldError`, без `alert`.

### 3. Состояние

- Сначала локальное состояние компонента (`ref`, `reactive`).
- Общее состояние авторизации и текущего пользователя — модульный composable
  (`useAuth`) или `provide/inject` с типизированным `Symbol`, как в референсе.
- Глобальный store создавай только при доказанной необходимости. Pinia —
  новая зависимость: согласуй и опиши причину.
- Не храни в store PII, токены и данные, которые должны исчезать после reveal.

### 4. Формы

- `useForm` + `useField`/`useFieldArray` из vee-validate, схема — yup.
- Тип формы выводи из сгенерированного DTO; не описывай поля повторно вручную.
- Серверные ошибки маппь на поля через `setFieldError`, общие — в toast.
- Для вложенных массивов (`affine_params`, `functions`) используй
  `useFieldArray`, а не ручной `push` в массив модели.
- Валидация на клиенте дублирует контракт, но не заменяет серверную.

### 5. Компоненты и стиль

- Tailwind utility-first. Токены темы — CSS-переменные в `style.css`
  (`@theme inline`, `oklch`), тёмная тема — через `@custom-variant dark`.
- Условные классы — только через `cn()`.
- Варианты компонентов — через CVA (`buttonVariants`), а не набор булевых props
  и разрозненных классов.
- Инлайн-стили допустимы только для динамических значений (например, прогресс);
  статичные стили — классами.
- Иконки — `lucide-vue-next`; не тащи вторую библиотеку иконок без причины.
- UI-примитивы — shadcn-vue (`new-york`, neutral, CSS variables). Компонент
  добавляй через CLI или копируй в `components/ui`, сохраняя API примитива.

### 6. Роутинг и доступ

- Именованные маршруты (`name`), `meta.requiresAuth` и `meta.noAuth`,
  глобальный `beforeEach` с редиректом.
- Тяжёлые views подключай лениво (`() => import(...)`), если растёт бандл.
- Проверка авторизации — по фактическому состоянию сессии, а не только по
  наличию токена в `localStorage`.

### 7. Асинхронные события

- Прогресс долгих операций — через `EventSource` (SSE) или WebSocket по
  контракту; закрывай соединение в `onUnmounted` и при повторном запуске.
- Не оставляй модульные `document.addEventListener`/`setInterval` без cleanup.

### 8. Файлы и уведомления

- Загрузка файлов: `accept`, проверка MIME/размера на клиенте, preview через
  `FileReader`; окончательная проверка — на сервере.
- Глобальные ошибки и успехи — `vue-sonner`; поля — inline-текстом рядом с
  `Label`/`Input`, с `aria`-атрибутами и `autocomplete`.
- Не логируй в консоль payload с PII и токенами.

### 9. Docker и окружение

- Multi-stage: `openapitools/openapi-generator-cli` → `node:24-alpine` →
  `nginx`. Генерация клиента — в первом stage, сборка — во втором.
- API URL передаётся build-arg (`VITE_API_URL`), не хардкодом.
- nginx отдаёт SPA с fallback `try_files $uri $uri/ /index.html` и healthcheck.
- В браузер попадают только `VITE_*`; секретов во frontend быть не может.
- Контракт лежит в `contracts/`, поэтому build-context в compose указывай от
  корня (`context: .`, `dockerfile: frontend/Dockerfile`) либо копируй контракт
  в контекст `frontend/`. Не рассчитывай на `../contracts` внутри Docker.

## Правила домена Tracing ATS

Это важнее UI-деталей референса.

1. **Скоринг не считается на клиенте.** `q`, `E`, `D`, `c_r`, `M`, `N`, `F`,
   `Score`, cap и вилку считает `scoring-service`; frontend только отображает.
2. **`Score_pre` и `Score_final` не смешивать.** Разные поля, разные подписи,
   рядом `delta` и флаг `external_skipped`. Не показывай одно число как «итог»,
   если это предварительный балл.
3. **Слепой режим.** До `POST /applications/{id}/reveal` в UI не должно быть
   имени, контактов, пола, возраста, фото, вуза и других PII. Не запрашивай их
   «заранее», не клади в store/localStorage/URL, не рендери в скрытые DOM-узлы.
   Раскрытие — отдельное действие с обязательной причиной; после него обнови
   данные и не сохраняй PII дольше сессии.
4. **Человек решает.** Никаких автоматических отказов/приглашений; письмо
   отказа — черновик для HR, а не отправленное сообщение.
5. **Доказательства, а не догадки.** В отчёте показывай «требование →
   доказательство → пробел». Цитата без подтверждения в тексте не показывается
   как доказательство.
6. **Веса.** HR правит веса; профиль запоминается по специализации и уровню.
   Не применяй обученный профиль автоматически и не пересчитывай сохранённые
   запуски.
7. **Контракт.** Не выдумывай эндпоинты, поля и enum. Меняешь ответ — обнови
   `contracts/openapi.yaml` и клиент в том же изменении.
8. **Без защищённых признаков в скоринге.** Пол, возраст, национальность, фото
   и вуз не участвуют в расчёте и не должны выглядеть как фактор оценки.

## Чего не копировать из референса

- Опечатки и мёртвый код: `shemas`, `useGenearatorStore`, `FracatalStore`,
  закомментированный store.
- Неtyped props (`defineProps(["href", "alt"])`), `any` в моделях и событиях.
- `v-model` на поле из `useField` в дочернем компоненте — используй
  `defineModel`/`useField` на уровне формы.
- Модульные слушатели без cleanup (`useScroll` в референсе).
- `localStorage` для access-token без решения по безопасности.
- `npm i` вместо `npm ci` при наличии lock-файла; теги `latest` в Docker.
- Отсутствие ESLint/Prettier/тестов и CI для frontend. Для Tracing ATS
  добавление линтера и Vitest — отдельное решение с зависимостями, но критичные
  сценарии (reveal, Score_pre/Score_final, формы) должны быть покрыты.
- Ссылку на `tailwind.config.ts` в `components.json`: при Tailwind 4 конфиг
  живёт в `src/style.css`, отдельного файла нет.

## Проверки перед сдачей

- После изменения контракта: `npm run gen-api`, затем `npm run build`.
- `npm run build` — обязательный минимум: `vue-tsc -b && vite build`.
- `npm run dev` — ручная проверка сценария; dev-прокси `/api` работает.
- Если настроены линтер/тесты — запусти их; если нет, скажи прямо.
- Слепой режим: проверь Network, DOM, store и localStorage — до reveal PII нет.
- Баллы: на экране явно подписано `Score_pre` или `Score_final`, нет пересчёта
  в TypeScript.
- Dockerfile/compose: `docker compose config` и `docker build --check`.
- Не пиши «готово», если проверки не запускались. Перечисли, что проверено,
  а что нет.

## Когда остановиться и спросить

- нужна новая зависимость (Pinia, другой UI-кит, тестовый раннер, ESLint);
- нужно изменить `contracts/openapi.yaml`;
- неясно, где хранить access-token и как backend отдаёт refresh;
- меняется сценарий `reveal` или состав PII;
- неясно, какой балл показывать (`Score_pre`, `Score_final`, `delta`);
- требуется отправить PII в аналитику, логи или сторонний сервис.
