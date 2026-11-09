<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft,
  Building2,
  CheckCircle2,
  FileText,
  LoaderCircle,
  Plus,
  Sparkles,
  UploadCloud,
  X,
} from 'lucide-vue-next'
import type { ReferenceItem } from '@/api/generated'
import { apiErrorMessage, referenceApi, vacanciesApi } from '@/api'
import AppModal from '@/components/AppModal.vue'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const { success, error: toastError } = useToast()

const specializations = ref<ReferenceItem[]>([])
const levels = ref<ReferenceItem[]>([])
const specialization = ref('')
const level = ref('none')
const title = ref('')
const mode = ref<'text' | 'file'>('text')
const text = ref('')
const file = ref<File | null>(null)
const dragging = ref(false)
const loading = ref(false)
const error = ref('')
const showSpecializationModal = ref(false)
const newSpecializationName = ref('')
const newSpecializationCode = ref('')
const addingSpecialization = ref(false)
const specializationError = ref('')

const canSubmit = computed(
  () =>
    !loading.value &&
    specialization.value !== '' &&
    (mode.value === 'text' ? text.value.trim() !== '' : file.value !== null),
)

const currentStep = computed(() => {
  if (!specialization.value) {
    return 1
  }
  return mode.value === 'text' ? (text.value.trim() ? 3 : 2) : file.value ? 3 : 2
})

const steps = [
  { number: 1, title: 'Параметры', hint: 'Специализация и уровень' },
  { number: 2, title: 'Источник', hint: 'Текст или файл' },
  { number: 3, title: 'Разбор', hint: 'LLM и проверка' },
]

const sourceSummary = computed(() => {
  if (mode.value === 'file') {
    return file.value ? file.value.name : 'Файл не выбран'
  }
  return text.value.trim() ? `${text.value.trim().length.toLocaleString('ru-RU')} символов` : 'Текст не вставлен'
})

onMounted(async () => {
  try {
    const [specs, levelList] = await Promise.all([
      referenceApi.listSpecializations(),
      referenceApi.listLevels(),
    ])
    specializations.value = specs.data
    levels.value = levelList.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
})

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
  showSpecializationModal.value = true
}

async function addSpecialization() {
  if (!newSpecializationName.value.trim()) {
    specializationError.value = 'Укажите название специализации'
    return
  }
  addingSpecialization.value = true
  specializationError.value = ''
  try {
    const response = await referenceApi.createSpecialization({
      createSpecializationRequest: {
        name: newSpecializationName.value.trim(),
        code: newSpecializationCode.value.trim() || null,
      },
    })
    specializations.value = [...specializations.value, response.data].sort((left, right) =>
      left.name.localeCompare(right.name, 'ru'),
    )
    specialization.value = response.data.code
    showSpecializationModal.value = false
    success('Специализация добавлена', `${response.data.name} · ${response.data.code}`)
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
      level: level.value || undefined,
      title: title.value.trim() || undefined,
      text: mode.value === 'text' ? text.value : undefined,
      file: mode.value === 'file' ? file.value : undefined,
    })
    success('Вакансия разобрана', 'Проверьте типы навыков и подтвердите шаблон')
    await router.push(`/vacancies/${response.data.vacancy_id}`)
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось разобрать вакансию', message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <RouterLink to="/vacancies" class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900">
        <ArrowLeft :size="16" />
        К списку вакансий
      </RouterLink>
      <h1 class="mt-3 text-3xl font-semibold tracking-tight text-slate-900">Новая вакансия</h1>
      <p class="mt-1 text-sm text-slate-500">
        Загрузите описание — LLM выделит требования, а вы проверите типы навыков.
      </p>
    </div>

    <div class="card flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
      <template v-for="(step, index) in steps" :key="step.number">
        <div class="flex flex-1 items-center gap-3">
          <span
            class="grid h-9 w-9 shrink-0 place-items-center rounded-full text-sm font-semibold transition"
            :class="currentStep >= step.number ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-400'"
          >
            <CheckCircle2 v-if="currentStep > step.number" :size="16" />
            <span v-else>{{ step.number }}</span>
          </span>
          <div>
            <p class="text-sm font-semibold" :class="currentStep >= step.number ? 'text-slate-900' : 'text-slate-400'">
              {{ step.title }}
            </p>
            <p class="text-xs text-slate-400">{{ step.hint }}</p>
          </div>
        </div>
        <div v-if="index < steps.length - 1" class="hidden h-px flex-1 bg-slate-200 sm:block" />
      </template>
    </div>

    <div v-if="error" class="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      {{ error }}
    </div>

    <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <form id="create-form" class="space-y-5" @submit.prevent="submit">
        <div class="card p-5">
          <div class="flex items-start gap-3">
            <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
              <Building2 :size="18" />
            </span>
            <div>
              <h2 class="text-base font-semibold text-slate-900">Параметры позиции</h2>
              <p class="text-sm text-slate-500">Специализация обязательна, уровень можно не указывать.</p>
            </div>
          </div>

          <div class="mt-5 grid gap-4 sm:grid-cols-2">
            <div>
              <label class="label" for="specialization">Специализация</label>
              <div class="flex gap-2">
                <select id="specialization" v-model="specialization" class="input" required>
                  <option value="" disabled>Выберите специализацию</option>
                  <option v-for="item in specializations" :key="item.code" :value="item.code">
                    {{ item.name }}
                  </option>
                </select>
                <button type="button" class="btn-secondary shrink-0 px-3" title="Добавить специализацию" @click="openSpecializationModal">
                  <Plus :size="16" />
                  <span class="hidden sm:inline">Добавить</span>
                </button>
              </div>
              <p class="hint">Нет нужной? Добавьте её рядом с полем.</p>
            </div>
            <div>
              <label class="label" for="level">Уровень</label>
              <select id="level" v-model="level" class="input">
                <option value="none">Без уровня</option>
                <option v-for="item in levels" :key="item.code" :value="item.code">
                  {{ item.name }}
                </option>
              </select>
              <p class="hint">Для специализаций без грейдов оставьте «Без уровня».</p>
            </div>
          </div>

          <div class="mt-4">
            <label class="label" for="title">Название вакансии <span class="font-normal text-slate-400">(необязательно)</span></label>
            <input id="title" v-model="title" class="input" placeholder="Например, Backend-разработчик" />
          </div>
        </div>

        <div class="card p-5">
          <div class="flex items-start gap-3">
            <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-violet-50 text-violet-600">
              <FileText :size="18" />
            </span>
            <div>
              <h2 class="text-base font-semibold text-slate-900">Источник описания</h2>
              <p class="text-sm text-slate-500">Вставьте текст или загрузите PDF, DOCX, TXT или MD.</p>
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
            <textarea
              v-model="text"
              class="textarea min-h-64"
              placeholder="Вставьте описание вакансии: обязанности, требования, условия…"
            />
            <div class="mt-2 flex justify-between text-xs text-slate-400">
              <span>Текст уходит во внешнюю LLM, PII кандидатов сюда не попадает.</span>
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
                {{ file ? file.name : 'Перетащите файл сюда' }}
              </span>
              <span class="mt-1 text-xs text-slate-500">
                {{ file ? `${Math.ceil(file.size / 1024)} КБ` : 'PDF, DOCX, TXT или MD · до 5 МБ' }}
              </span>
              <input type="file" class="sr-only" accept=".pdf,.docx,.txt,.md" @change="onFile" />
            </label>
            <button v-if="file" type="button" class="mt-2 inline-flex items-center gap-1 text-xs text-red-600 hover:underline" @click="file = null">
              <X :size="12" />
              Убрать файл
            </button>
          </div>
        </div>
      </form>

      <aside class="card lg:sticky lg:top-24">
        <div class="border-b border-slate-100 px-5 py-4">
          <h2 class="text-sm font-semibold text-slate-900">Перед разбором</h2>
          <p class="mt-0.5 text-xs text-slate-500">Проверьте параметры и источник.</p>
        </div>
        <dl class="space-y-3 px-5 py-4 text-sm">
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Специализация</dt>
            <dd class="text-right font-medium text-slate-900">
              {{ specializations.find((item) => item.code === specialization)?.name ?? '—' }}
            </dd>
          </div>
          <div class="flex items-start justify-between gap-3">
            <dt class="text-slate-500">Уровень</dt>
            <dd class="text-right font-medium text-slate-900">
              {{ level === 'none' ? 'Без уровня' : levels.find((item) => item.code === level)?.name ?? level }}
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
            Разобрать вакансию
          </button>
          <div v-if="loading" class="mt-3 flex items-start gap-2 rounded-xl bg-indigo-50 p-3 text-xs text-indigo-800" role="status">
            <LoaderCircle :size="16" class="mt-0.5 shrink-0 animate-spin" />
            <span>Разбор может занять до пары минут. Не закрывайте страницу.</span>
          </div>
          <p v-else class="mt-3 text-center text-xs text-slate-400">
            Обычно занимает 10–60 секунд
          </p>
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
        <p v-if="specializationError" class="rounded-xl bg-red-50 p-3 text-sm text-red-700">{{ specializationError }}</p>
        <div>
          <label class="label" for="new-specialization-name">Название</label>
          <input id="new-specialization-name" v-model="newSpecializationName" class="input" placeholder="Например, Аналитика" />
        </div>
        <div>
          <label class="label" for="new-specialization-code">Код <span class="font-normal text-slate-400">(необязательно)</span></label>
          <input id="new-specialization-code" v-model="newSpecializationCode" class="input" placeholder="analytics" />
          <p class="hint">Латиница, цифры и дефисы. Например, `data-engineering`.</p>
        </div>
        <div class="flex justify-end gap-2 pt-1">
          <button type="button" class="btn-secondary" @click="showSpecializationModal = false">Отмена</button>
          <button type="button" class="btn-primary" :disabled="addingSpecialization" @click="addSpecialization">
            {{ addingSpecialization ? 'Добавление…' : 'Добавить' }}
          </button>
        </div>
      </div>
    </AppModal>
  </section>
</template>
