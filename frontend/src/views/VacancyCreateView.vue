<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  Building2,
  FileText,
  Layers,
  LoaderCircle,
  Plus,
  Sparkles,
  UploadCloud,
  X,
} from '@lucide/vue'
import type { ReferenceItem, Specialization, Vacancy, VacancySummary } from '@/api/generated'
import { apiErrorMessage, referenceApi, vacanciesApi } from '@/api'
import AppModal from '@/components/AppModal.vue'
import { useToast } from '@/composables/useToast'
import { requirementsCount } from '@/lib/format'

const router = useRouter()
const { success, error: toastError } = useToast()

const specializations = ref<Specialization[]>([])
const levels = ref<ReferenceItem[]>([])
const templates = ref<VacancySummary[]>([])
const templateId = ref('')
const templateVacancy = ref<Vacancy | null>(null)
const templateSource = ref('')
const templateHint = ref('')
const loadingTemplate = ref(false)
const specialization = ref('')
const level = ref('')
const title = ref('')
const experienceYears = ref<number | null>(null)
const mode = ref<'text' | 'file'>('text')
const text = ref('')
const file = ref<File | null>(null)
const dragging = ref(false)
const loading = ref(false)
const error = ref('')
const showSpecializationModal = ref(false)
const newSpecializationName = ref('')
const newSpecializationCode = ref('')
const newSpecializationIt = ref<boolean | null>(null)
const addingSpecialization = ref(false)
const specializationError = ref('')

const selectedSpecialization = computed(
  () => specializations.value.find((item) => item.code === specialization.value) ?? null,
)

// Грейд нужен только IT-специализациям: для остальных уровень не запрашивается.
const needsLevel = computed(() => selectedSpecialization.value?.it === true)

watch(needsLevel, () => {
  level.value = ''
})

watch(templateId, (value) => {
  if (!value) {
    templateVacancy.value = null
    templateSource.value = ''
    templateHint.value = ''
  }
})

const canSubmit = computed(
  () =>
    !loading.value &&
    specialization.value !== '' &&
    (!needsLevel.value || level.value !== '') &&
    (mode.value === 'text' ? text.value.trim() !== '' : file.value !== null),
)

const sourceSummary = computed(() => {
  if (mode.value === 'file') {
    return file.value ? file.value.name : 'Файл не выбран'
  }
  return text.value.trim()
    ? `${text.value.trim().length.toLocaleString('ru-RU')} символов`
    : 'Текст не вставлен'
})

onMounted(async () => {
  try {
    const [specs, levelList, templatePage] = await Promise.all([
      referenceApi.listSpecializations(),
      referenceApi.listLevels(),
      vacanciesApi.listVacancies({ limit: 200, offset: 0 }),
    ])
    specializations.value = specs.data
    levels.value = levelList.data
    templates.value = templatePage.data.items.filter((item) => item.is_confirmed)
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
})

async function applyTemplate() {
  if (!templateId.value || loadingTemplate.value) {
    return
  }
  loadingTemplate.value = true
  templateHint.value = ''
  try {
    const response = await vacanciesApi.getVacancy({ id: templateId.value })
    const template = response.data
    templateVacancy.value = template
    templateSource.value = template.source_text ?? ''
    title.value = template.title ?? ''
    experienceYears.value = template.experience_years ?? null
    specialization.value = template.specialization
    mode.value = 'text'
    file.value = null
    text.value = template.source_text ?? ''
    // watch(needsLevel) сбрасывает уровень при смене специализации, поэтому ставим после него.
    await nextTick()
    level.value = template.level
    templateHint.value = `Заполнено по шаблону «${template.title || 'Без названия'}». Если не менять текст и параметры, требования и приоритеты перенесутся.`
  } catch (e) {
    templateHint.value = apiErrorMessage(e)
  } finally {
    loadingTemplate.value = false
  }
}

function updateExperienceYears(event: Event) {
  const value = (event.target as HTMLInputElement).value
  experienceYears.value = value === '' ? null : Number(value)
}

function onFile(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0] ?? null
  file.value = selected
}

function onDrop(event: DragEvent) {
  dragging.value = false
  const dropped = event.dataTransfer?.files?.[0] ?? null
  if (dropped) {
    file.value = dropped
  }
}

function openSpecializationModal() {
  specializationError.value = ''
  newSpecializationName.value = ''
  newSpecializationCode.value = ''
  newSpecializationIt.value = null
  showSpecializationModal.value = true
}

async function addSpecialization() {
  if (!newSpecializationName.value.trim()) {
    specializationError.value = 'Укажите название специализации'
    return
  }
  if (newSpecializationIt.value === null) {
    specializationError.value = 'Выберите, нужен ли грейд для этой специализации'
    return
  }
  addingSpecialization.value = true
  specializationError.value = ''
  try {
    const response = await referenceApi.createSpecialization({
      createSpecializationRequest: {
        name: newSpecializationName.value.trim(),
        code: newSpecializationCode.value.trim() || null,
        it: newSpecializationIt.value,
      },
    })
    specializations.value = [...specializations.value, response.data].sort((left, right) =>
      left.name.localeCompare(right.name, 'ru'),
    )
    specialization.value = response.data.code
    level.value = ''
    showSpecializationModal.value = false
    success(
      'Специализация добавлена',
      `${response.data.name} · ${response.data.it ? 'IT, грейд обязателен' : 'без грейдов'}`,
    )
  } catch (e) {
    specializationError.value = apiErrorMessage(e)
  } finally {
    addingSpecialization.value = false
  }
}

async function submit() {
  if (!canSubmit.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await vacanciesApi.createVacancy({
      specialization: specialization.value,
      level: needsLevel.value ? level.value : undefined,
      title: title.value.trim() || undefined,
      experienceYears: experienceYears.value ?? undefined,
      text: mode.value === 'text' ? text.value : undefined,
      file: mode.value === 'file' ? file.value : undefined,
    })
    const created = response.data
    const canCopyTemplate =
      templateVacancy.value !== null &&
      mode.value === 'text' &&
      text.value.trim() === templateSource.value.trim() &&
      created.specialization === templateVacancy.value.specialization &&
      created.level === templateVacancy.value.level

    if (canCopyTemplate && templateVacancy.value) {
      try {
        await vacanciesApi.updateVacancyTemplate({
          id: created.vacancy_id,
          updateTemplateRequest: {
            base_version: created.template.version,
            specialization: created.specialization,
            level: needsLevel.value ? created.level : undefined,
            title: created.title ?? null,
            experience_years: templateVacancy.value.experience_years ?? null,
            confirm: false,
            requirements: templateVacancy.value.template.requirements.map((item) => ({
              id: null,
              skill: item.skill ?? null,
              kind: item.kind,
              years_required: item.years_required ?? null,
              quote: item.quote ?? null,
              clarity_warning: item.clarity_warning ?? null,
            })),
          },
        })
        success('Вакансия создана', 'Требования взяты из шаблона — проверьте и подтвердите')
      } catch (copyError) {
        toastError('Вакансия создана без шаблона', apiErrorMessage(copyError))
      }
    } else {
      success('Вакансия создана', 'Разложите требования по важности и подтвердите шаблон')
    }
    await router.push(`/vacancies/${created.vacancy_id}/edit`)
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось создать вакансию', message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <RouterLink
        to="/vacancies"
        class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900"
      >
        <ArrowLeft :size="16" />
        К списку вакансий
      </RouterLink>
      <h1 class="mt-3 text-3xl font-semibold tracking-tight text-slate-900">
        Добавить вакансию
      </h1>
      <p class="mt-1 text-sm text-slate-500">
        Опишите позицию и назначьте приоритеты требованиям.
      </p>
    </div>

    <div v-if="error" class="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      {{ error }}
    </div>

    <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <form id="create-form" class="space-y-5" @submit.prevent="submit">
        <div v-if="templates.length" class="card p-5">
          <div class="flex items-start gap-3">
            <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-600">
              <Layers :size="18" />
            </span>
            <div>
              <h2 class="text-base font-semibold text-slate-900">Начать с шаблона</h2>
              <p class="text-sm text-slate-500">
                Шаблон заполнит название, специализацию, уровень и текст. Его требования и
                приоритеты можно перенести в новую вакансию.
              </p>
            </div>
          </div>
          <div class="mt-4 flex flex-col gap-2 sm:flex-row">
            <select v-model="templateId" class="input" aria-label="Шаблон вакансии">
              <option value="">Без шаблона</option>
              <option v-for="item in templates" :key="item.id" :value="item.id">
                {{ item.title || 'Без названия' }} · {{ requirementsCount(item.requirements_count) }}
              </option>
            </select>
            <button
              type="button"
              class="btn-secondary shrink-0"
              :disabled="!templateId || loadingTemplate"
              @click="applyTemplate"
            >
              <LoaderCircle v-if="loadingTemplate" :size="16" class="animate-spin" />
              <Layers v-else :size="16" />
              Заполнить
            </button>
          </div>
          <p v-if="templateHint" class="hint">{{ templateHint }}</p>
        </div>

        <div class="card p-5">
          <div class="flex items-start gap-3">
            <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
              <Building2 :size="18" />
            </span>
            <div>
              <h2 class="text-base font-semibold text-slate-900">Параметры вакансии</h2>
              <p class="text-sm text-slate-500">
                Название видно в списке, специализация и уровень задают профиль требований.
              </p>
            </div>
          </div>

          <div class="mt-5 space-y-4">
            <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_7rem]">
              <div>
                <label class="label" for="title">
                  Название вакансии <span class="font-normal text-slate-400">(необязательно)</span>
                </label>
                <input
                  id="title"
                  v-model="title"
                  class="input"
                  placeholder="Например, Backend-разработчик"
                />
                <p class="hint">Если не заполнить, карточка будет называться «Без названия».</p>
              </div>
              <div>
                <label class="label" for="experience-years">Стаж, лет</label>
                <input
                  id="experience-years"
                  :value="experienceYears ?? ''"
                  class="input w-full"
                  type="number"
                  min="0"
                  max="50"
                  step="1"
                  placeholder="3"
                  @input="updateExperienceYears"
                />
              </div>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div>
                <label class="label" for="specialization">Специализация</label>
                <div class="flex gap-2">
                  <select id="specialization" v-model="specialization" class="input" required>
                    <option value="" disabled>Выберите специализацию</option>
                    <option v-for="item in specializations" :key="item.code" :value="item.code">
                      {{ item.name }}
                    </option>
                  </select>
                  <button
                    type="button"
                    class="btn-secondary shrink-0 px-3"
                    title="Добавить специализацию"
                    @click="openSpecializationModal"
                  >
                    <Plus :size="16" />
                    <span class="hidden sm:inline">Добавить</span>
                  </button>
                </div>
                <p class="hint">Нет нужной? Добавьте её рядом с полем.</p>
              </div>
              <div>
                <label class="label" for="level">
                  Уровень <span v-if="needsLevel" class="font-normal text-slate-400">(обязательно)</span>
                </label>
                <select v-if="needsLevel" id="level" v-model="level" class="input" required>
                  <option value="" disabled>Выберите грейд</option>
                  <option v-for="item in levels" :key="item.code" :value="item.code">
                    {{ item.name }}
                  </option>
                </select>
                <p
                  v-else
                  class="flex h-[38px] items-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-3 text-sm text-slate-500"
                >
                  Не требуется: специализация не IT
                </p>
                <p class="hint">
                  {{ needsLevel ? 'Для IT-специализации грейд обязателен.' : 'Грейд запрашивается только у IT-специализаций.' }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div class="card p-5">
          <div class="flex items-start gap-3">
            <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-violet-50 text-violet-600">
              <FileText :size="18" />
            </span>
            <div>
              <h2 class="text-base font-semibold text-slate-900">Описание вакансии</h2>
              <p class="text-sm text-slate-500">
                Вставьте текст или загрузите PDF, DOCX, TXT или MD.
              </p>
            </div>
          </div>

          <div class="mt-5 inline-flex rounded-xl bg-slate-100 p-1">
            <button
              type="button"
              class="rounded-lg px-3 py-1.5 text-sm font-medium transition"
              :class="mode === 'text' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
              @click="mode = 'text'"
            >
              Вставить текст
            </button>
            <button
              type="button"
              class="rounded-lg px-3 py-1.5 text-sm font-medium transition"
              :class="mode === 'file' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
              @click="mode = 'file'"
            >
              Загрузить файл
            </button>
          </div>

          <div v-if="mode === 'text'" class="mt-4">
            <label class="label" for="vacancy-text">Текст вакансии</label>
            <textarea
              id="vacancy-text"
              v-model="text"
              class="textarea min-h-64"
              placeholder="Вставьте описание вакансии: обязанности, требования, условия…"
            />
            <div class="mt-2 flex justify-end text-xs text-slate-400">
              <span>{{ text.length.toLocaleString('ru-RU') }} / 30 000</span>
            </div>
          </div>

          <div v-else class="mt-4">
            <label
              class="flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-12 text-center transition"
              :class="dragging ? 'border-indigo-400 bg-indigo-50' : 'border-slate-300 bg-slate-50/60 hover:border-indigo-300 hover:bg-indigo-50/40'"
              @dragover.prevent="dragging = true"
              @dragleave.prevent="dragging = false"
              @drop.prevent="onDrop"
            >
              <UploadCloud :size="28" class="text-indigo-500" />
              <span class="mt-3 text-sm font-semibold text-slate-900">
                {{ file ? file.name : 'Перетащите файл сюда или нажмите, чтобы выбрать' }}
              </span>
              <span class="mt-1 text-xs text-slate-500">
                {{ file ? `${Math.ceil(file.size / 1024)} КБ` : 'PDF, DOCX, TXT или MD · до 5 МБ' }}
              </span>
              <input type="file" class="sr-only" accept=".pdf,.docx,.txt,.md" @change="onFile" />
            </label>
            <button
              v-if="file"
              type="button"
              class="mt-2 inline-flex items-center gap-1 text-xs text-red-600 hover:underline"
              @click="file = null"
            >
              <X :size="12" />
              Убрать файл
            </button>
          </div>
        </div>
      </form>

      <aside class="card lg:sticky lg:top-24">
        <div class="border-b border-slate-100 px-5 py-4">
          <h2 class="text-sm font-semibold text-slate-900">Что будет дальше</h2>
          <p class="mt-0.5 text-xs text-slate-500">
            Требования можно проверить и сохранить на следующем экране.
          </p>
        </div>
        <dl class="space-y-3 px-5 py-4 text-sm">
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Название</dt>
            <dd class="text-right font-medium text-slate-900">{{ title.trim() || '—' }}</dd>
          </div>
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Специализация</dt>
            <dd class="text-right font-medium text-slate-900">
              {{ specializations.find((item) => item.code === specialization)?.name ?? '—' }}
            </dd>
          </div>
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Уровень</dt>
            <dd class="text-right font-medium text-slate-900">
              {{ needsLevel ? levels.find((item) => item.code === level)?.name ?? '—' : 'Без уровня' }}
            </dd>
          </div>
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Стаж</dt>
            <dd class="text-right font-medium text-slate-900">
              {{ experienceYears === null ? 'Не указан' : `${experienceYears} лет` }}
            </dd>
          </div>
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Источник</dt>
            <dd class="text-right font-medium text-slate-900">{{ sourceSummary }}</dd>
          </div>
        </dl>
        <div class="border-t border-slate-100 px-5 py-4">
          <button type="submit" form="create-form" class="btn-accent w-full" :disabled="!canSubmit">
            <Sparkles :size="17" />
            Создать вакансию
          </button>
          <div
            v-if="loading"
            class="mt-3 flex items-start gap-2 rounded-xl bg-indigo-50 p-3 text-xs text-indigo-800"
            role="status"
          >
            <LoaderCircle :size="16" class="mt-0.5 shrink-0 animate-spin" />
            <span>Создание может занять до 5 минут. Не закрывайте страницу.</span>
          </div>
        </div>
      </aside>
    </div>

    <AppModal
      :open="showSpecializationModal"
      title="Новая специализация"
      description="Название обязательно. Код можно оставить пустым — он сгенерируется автоматически."
      @close="showSpecializationModal = false"
    >
      <div class="space-y-4">
        <p v-if="specializationError" class="rounded-xl bg-red-50 p-3 text-sm text-red-700">
          {{ specializationError }}
        </p>
        <div>
          <label class="label" for="new-specialization-name">Название</label>
          <input
            id="new-specialization-name"
            v-model="newSpecializationName"
            class="input"
            placeholder="Например, Аналитика"
          />
        </div>
        <div>
          <label class="label" for="new-specialization-code">
            Код <span class="font-normal text-slate-400">(необязательно)</span>
          </label>
          <input id="new-specialization-code" v-model="newSpecializationCode" class="input" placeholder="analytics" />
          <p class="hint">Латиница, цифры и дефисы. Например, `data-engineering`.</p>
        </div>

        <fieldset>
          <legend class="label">Нужен ли уровень (грейд)?</legend>
          <div class="mt-1 grid gap-2">
            <label class="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 transition hover:border-indigo-300">
              <input
                v-model="newSpecializationIt"
                type="radio"
                :value="true"
                class="mt-0.5 h-4 w-4 accent-indigo-600"
              />
              <span class="text-sm">
                <span class="font-medium text-slate-900">IT — нужен грейд</span>
                <span class="block text-xs text-slate-500">
                  При создании вакансии попросим выбрать junior, middle или senior.
                </span>
              </span>
            </label>
            <label class="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 transition hover:border-indigo-300">
              <input
                v-model="newSpecializationIt"
                type="radio"
                :value="false"
                class="mt-0.5 h-4 w-4 accent-indigo-600"
              />
              <span class="text-sm">
                <span class="font-medium text-slate-900">Не IT — грейд не нужен</span>
                <span class="block text-xs text-slate-500">
                  Уровень не запрашивается ни при создании, ни в шаблоне.
                </span>
              </span>
            </label>
          </div>
        </fieldset>

        <div class="flex justify-end gap-2 pt-1">
          <button type="button" class="btn-secondary" @click="showSpecializationModal = false">
            Отмена
          </button>
          <button type="button" class="btn-primary" :disabled="addingSpecialization" @click="addSpecialization">
            {{ addingSpecialization ? 'Добавление…' : 'Добавить' }}
          </button>
        </div>
      </div>
    </AppModal>
  </section>
</template>
