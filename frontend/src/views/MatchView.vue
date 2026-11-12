<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { FileUp, Loader2, Sparkles } from '@lucide/vue'
import type { ResumeBatchUploadResponse, ResumeSummary, VacancySummary } from '@/api/generated'
import { apiErrorMessage, resumesApi, vacanciesApi } from '@/api'
import { useToast } from '@/composables/useToast'

type Mode = 'candidates' | 'vacancies'

const route = useRoute()
const router = useRouter()
const { success, error: toastError } = useToast()

const mode = ref<Mode>(route.query.mode === 'vacancies' ? 'vacancies' : 'candidates')
const vacancies = ref<VacancySummary[]>([])
const vacancyId = ref<string>('')
const consentGithub = ref(false)
const files = ref<File[]>([])
const pastedText = ref('')
const pastedCounter = ref(0)
const uploading = ref(false)
const error = ref('')
const response = ref<ResumeBatchUploadResponse | null>(null)
const recent = ref<ResumeSummary[]>([])
const recentLoading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const canUpload = computed(
  () => files.value.length > 0 && (mode.value === 'vacancies' || vacancyId.value !== ''),
)

onMounted(async () => {
  if (route.query.vacancyId) {
    vacancyId.value = String(route.query.vacancyId)
  }
  try {
    const page = await vacanciesApi.listVacancies({ limit: 100, offset: 0 })
    vacancies.value = page.data.items ?? []
    if (!vacancyId.value && vacancies.value.length > 0) {
      vacancyId.value = vacancies.value[0].id
    }
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
  await loadRecent()
})

async function loadRecent() {
  recentLoading.value = true
  try {
    const page = await resumesApi.listResumes({ limit: 10, offset: 0 })
    recent.value = page.data.items ?? []
  } catch {
    // История не должна мешать основной загрузке.
    recent.value = []
  } finally {
    recentLoading.value = false
  }
}

function formatDate(value: string): string {
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function appendFiles(incoming: File[]) {
  files.value = [...files.value, ...incoming]
}

function pickFiles(event: Event) {
  const input = event.target as HTMLInputElement
  appendFiles(Array.from(input.files ?? []))
  input.value = ''
}

function dropFiles(event: DragEvent) {
  appendFiles(Array.from(event.dataTransfer?.files ?? []))
}

/** Вставленный текст отправляется как обычный .txt: бэкенд уже умеет TEXT. */
function addPastedText() {
  const text = pastedText.value.trim()
  if (!text) {
    toastError('Пустой текст', 'Вставьте текст резюме')
    return
  }
  pastedCounter.value += 1
  appendFiles([
    new File([text], `pasted-resume-${pastedCounter.value}.txt`, { type: 'text/plain' }),
  ])
  pastedText.value = ''
  success('Текст добавлен', `Резюме №${pastedCounter.value}`)
}

function removeFile(index: number) {
  files.value = files.value.filter((_, current) => current !== index)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} Б`
  }
  return `${(bytes / 1024).toFixed(1)} КБ`
}

async function upload() {
  if (!canUpload.value) {
    return
  }
  uploading.value = true
  error.value = ''
  response.value = null
  try {
    const payload = {
      files: files.value,
      consentGithub: consentGithub.value,
      ...(mode.value === 'candidates' ? { vacancyId: vacancyId.value } : {}),
    }
    const result = await resumesApi.uploadResumes(payload)
    response.value = result.data
    success('Резюме приняты', 'Обработка идёт в фоне')
    await loadRecent()
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    files.value = []
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось загрузить резюме', message)
  } finally {
    uploading.value = false
  }
}

function openRanking() {
  const result = response.value?.resumes?.[0]
  if (!result?.resumeId) {
    return
  }
  if (mode.value === 'vacancies') {
    router.push(`/resumes/${result.resumeId}`)
    return
  }
  router.push(`/vacancies/${vacancyId.value}/candidates`)
}
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Подбор</h1>
        <p class="mt-1 text-sm text-slate-500">
          Выберите режим: ищем кандидатов под вакансию или вакансии под резюме.
        </p>
      </div>
      <RouterLink to="/vacancies" class="text-sm font-medium text-indigo-600 hover:text-indigo-700">
        К вакансиям
      </RouterLink>
    </header>

    <div class="inline-flex rounded-2xl border border-slate-200 bg-white p-1">
      <button
        class="rounded-xl px-4 py-2 text-sm font-medium transition"
        :class="mode === 'candidates' ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'"
        @click="mode = 'candidates'"
      >
        Кандидаты под вакансию
      </button>
      <button
        class="rounded-xl px-4 py-2 text-sm font-medium transition"
        :class="mode === 'vacancies' ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'"
        @click="mode = 'vacancies'"
      >
        Вакансии под резюме
      </button>
    </div>

    <div class="grid gap-6 lg:grid-cols-[2fr,1fr]">
      <div class="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <label
          class="flex cursor-pointer flex-col items-center justify-center gap-3 rounded-3xl border-2 border-dashed border-slate-300 bg-slate-50 px-6 py-12 text-center transition hover:border-indigo-400 hover:bg-indigo-50/40"
          @dragover.prevent
          @drop.prevent="dropFiles"
        >
          <FileUp :size="32" class="text-indigo-500" />
          <span class="text-sm font-medium text-slate-700">
            Перетащите PDF, DOCX, TXT или MD
          </span>
          <span class="text-xs text-slate-500">
            Можно выбрать несколько файлов сразу или вставить текст ниже
          </span>
          <input
            ref="fileInput"
            type="file"
            multiple
            accept=".pdf,.docx,.txt,.md"
            class="hidden"
            @change="pickFiles"
          />
        </label>

        <div class="mt-4 rounded-2xl border border-slate-200 bg-white p-4">
          <div class="flex items-center justify-between gap-3">
            <p class="text-sm font-medium text-slate-700">Или вставьте текст резюме</p>
            <span class="text-xs text-slate-400">{{ pastedText.length }} символов</span>
          </div>
          <textarea
            v-model="pastedText"
            rows="6"
            class="input mt-2 w-full resize-y font-mono text-sm"
            placeholder="Вставьте текст резюме: опыт, навыки, образование…"
          />
          <div class="mt-2 flex justify-end">
            <button
              type="button"
              class="btn-secondary"
              :disabled="!pastedText.trim()"
              @click="addPastedText"
            >
              Добавить текст
            </button>
          </div>
        </div>

        <ul v-if="files.length" class="mt-4 space-y-2">
          <li
            v-for="(file, index) in files"
            :key="`${file.name}-${index}`"
            class="flex items-center gap-3 rounded-xl border border-slate-200 px-3 py-2 text-sm"
          >
            <span class="truncate text-slate-700">{{ file.name }}</span>
            <span class="ml-auto shrink-0 text-xs text-slate-400">{{ formatSize(file.size) }}</span>
            <button
              type="button"
              class="shrink-0 text-slate-400 transition hover:text-rose-600"
              title="Убрать"
              @click="removeFile(index)"
            >
              ×
            </button>
          </li>
        </ul>

        <div v-if="mode === 'candidates'" class="mt-6 space-y-2">
          <label class="text-sm font-medium text-slate-700">Вакансия</label>
          <select v-model="vacancyId" class="input w-full">
            <option v-for="item in vacancies" :key="item.id" :value="item.id">
              {{ item.title }}
            </option>
          </select>
          <p v-if="vacancies.length === 0" class="text-xs text-slate-500">
            Сначала создайте вакансию.
          </p>
        </div>

        <label class="mt-6 flex items-center gap-2 text-sm text-slate-700">
          <input v-model="consentGithub" type="checkbox" class="h-4 w-4 rounded border-slate-300" />
          Согласие кандидата на обогащение из GitHub
        </label>

        <p v-if="error" class="mt-4 rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {{ error }}
        </p>

        <div class="mt-6 flex flex-wrap items-center gap-3">
          <button class="btn-primary" :disabled="!canUpload || uploading" @click="upload">
            <Loader2 v-if="uploading" :size="16" class="animate-spin" />
            <Sparkles v-else :size="16" />
            {{ uploading ? 'Загружаем…' : 'Загрузить и оценить' }}
          </button>
          <span class="text-xs text-slate-500">
            Обработка асинхронная: рейтинг появится по мере готовности.
          </span>
        </div>
      </div>

      <aside class="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 class="text-sm font-semibold text-slate-900">Результат загрузки</h2>
        <p v-if="!response" class="mt-3 text-sm text-slate-500">
          Здесь появятся статусы файлов и ссылка на рейтинг.
        </p>
        <div v-else class="mt-3 space-y-3">
          <div
            v-for="(item, index) in response.resumes"
            :key="index"
            class="rounded-2xl border border-slate-200 px-4 py-3 text-sm"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="font-medium text-slate-800">{{ item.status }}</span>
              <span class="text-xs text-slate-400">#{{ index + 1 }}</span>
            </div>
            <p v-if="item.warnings?.length" class="mt-1 text-xs text-amber-600">
              {{ item.warnings.join(', ') }}
            </p>
          </div>
          <button class="btn-secondary w-full" @click="openRanking">
            Открыть рейтинг
          </button>
        </div>

        <div class="mt-6 border-t border-slate-200 pt-4">
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-semibold text-slate-900">Последние резюме</h2>
            <button
              class="text-xs text-slate-400 transition hover:text-slate-700"
              :disabled="recentLoading"
              @click="loadRecent"
            >
              Обновить
            </button>
          </div>
          <p v-if="recentLoading && !recent.length" class="mt-2 text-sm text-slate-500">
            Загрузка…
          </p>
          <p v-else-if="!recent.length" class="mt-2 text-sm text-slate-500">
            Пока ничего не загружено.
          </p>
          <ul v-else class="mt-2 space-y-2">
            <li v-for="item in recent" :key="item.resumeId">
              <RouterLink
                :to="`/resumes/${item.resumeId}`"
                class="flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-xs transition hover:border-indigo-300 hover:bg-indigo-50/40"
              >
                <span class="font-mono text-slate-500">{{ item.resumeId.slice(0, 8) }}…</span>
                <span class="rounded-full bg-slate-100 px-2 py-0.5 font-medium text-slate-700">
                  {{ item.status }}
                </span>
                <span class="ml-auto text-slate-400">{{ formatDate(item.createdAt) }}</span>
              </RouterLink>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  </section>
</template>
