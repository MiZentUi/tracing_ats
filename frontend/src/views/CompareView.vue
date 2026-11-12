<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Eye, EyeOff, Loader2 } from '@lucide/vue'
import type {
  ApplicationReport,
  ResumeFullText,
  ResumeText,
  Vacancy,
} from '@/api/generated'
import { apiErrorMessage, reportsApi, resumesApi, vacanciesApi } from '@/api'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const { error: toastError } = useToast()

const resumeId = computed(() => String(route.query.resumeId ?? ''))
const vacancyId = computed(() => String(route.query.vacancyId ?? ''))
const applicationId = computed(() =>
  route.query.applicationId ? String(route.query.applicationId) : undefined,
)
const phase = computed(
  () =>
    (String(route.query.phase ?? '').toUpperCase() === 'PRELIMINARY'
      ? 'PRELIMINARY'
      : 'FINAL') as 'PRELIMINARY' | 'FINAL',
)
const backTo = computed(() => (route.query.from ? String(route.query.from) : ''))

const vacancy = ref<Vacancy | null>(null)
const resume = ref<ResumeText | null>(null)
const report = ref<ApplicationReport | null>(null)
const revealed = ref<ResumeFullText | null>(null)
const loading = ref(true)
const revealing = ref(false)
const error = ref('')

const resumeText = computed(() => revealed.value?.text ?? resume.value?.maskedText ?? '')

const matched = computed(
  () =>
    report.value?.requirements?.filter(
      (item) => item.status === 'COVERED' || item.status === 'PARTIAL',
    ) ?? [],
)

const vacancyTerms = computed(() =>
  matched.value.map((item) => item.text).filter((text): text is string => !!text),
)

const resumeTerms = computed(() =>
  matched.value
    .flatMap((item) => [item.evidenceQuote, item.text])
    .filter((text): text is string => !!text),
)

const vacancySegments = computed(() => segments(vacancy.value?.source_text ?? '', vacancyTerms.value))
const resumeSegments = computed(() => segments(resumeText.value, resumeTerms.value))

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

interface Segment {
  text: string
  match: boolean
}

/** Делит текст на подсвеченные и обычные куски по фразам требований. */
function segments(text: string, terms: string[]): Segment[] {
  if (!text) {
    return []
  }
  const lower = text.toLowerCase()
  const effective: string[] = []
  for (const term of terms) {
    const trimmed = term.trim()
    if (trimmed.length < 3) {
      continue
    }
    if (lower.includes(trimmed.toLowerCase())) {
      effective.push(trimmed)
      continue
    }
    // Фраза не найдена дословно — подсвечиваем отдельные значимые слова.
    for (const token of trimmed.split(/[^\p{L}\p{N}+#.]+/u)) {
      if (token.length >= 4) {
        effective.push(token)
      }
    }
  }
  const unique = [...new Set(effective)]
  if (!unique.length) {
    return [{ text, match: false }]
  }
  const pattern = unique
    .sort((left, right) => right.length - left.length)
    .map((term) => escapeRegex(term).replace(/\s+/g, '\\s+'))
    .join('|')
  const regex = new RegExp(pattern, 'gi')
  const result: Segment[] = []
  let last = 0
  for (const match of text.matchAll(regex)) {
    const index = match.index ?? 0
    if (index > last) {
      result.push({ text: text.slice(last, index), match: false })
    }
    result.push({ text: match[0], match: true })
    last = index + match[0].length
  }
  if (last < text.length) {
    result.push({ text: text.slice(last), match: false })
  }
  return result
}

onMounted(async () => {
  try {
    const tasks: Array<Promise<unknown>> = []
    if (vacancyId.value) {
      tasks.push(
        vacanciesApi.getVacancy({ id: vacancyId.value }).then((response) => {
          vacancy.value = response.data
        }),
      )
    }
    if (resumeId.value) {
      tasks.push(
        resumesApi.getResumeText({ resumeId: resumeId.value }).then((response) => {
          resume.value = response.data
        }),
      )
    }
    if (applicationId.value) {
      tasks.push(
        reportsApi
          .getApplicationReport({ applicationId: applicationId.value, phase: phase.value })
          .then((response) => {
            report.value = response.data
          })
          .catch(() => undefined),
      )
    } else if (resumeId.value && vacancyId.value) {
      tasks.push(
        reportsApi
          .getResumeVacancyReport({ resumeId: resumeId.value, vacancyId: vacancyId.value })
          .then((response) => {
            report.value = response.data
          })
          .catch(() => undefined),
      )
    }
    await Promise.all(tasks)
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
})

async function reveal() {
  const reason = window.prompt('Причина раскрытия PII (обязательно)')
  if (!reason?.trim()) {
    return
  }
  revealing.value = true
  try {
    const response = await resumesApi.revealResumeText({
      resumeId: resumeId.value,
      revealTextRequest: {
        reason,
        applicationId: applicationId.value,
      },
    })
    revealed.value = response.data
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось раскрыть текст', message)
  } finally {
    revealing.value = false
  }
}

function hide() {
  revealed.value = null
}

function back() {
  if (backTo.value) {
    router.push(backTo.value)
    return
  }
  router.back()
}
</script>

<template>
  <section class="space-y-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-xl font-semibold tracking-tight text-slate-900">Тексты для сверки</h1>
        <p class="text-sm text-slate-500">
          Слева — исходный текст вакансии, справа — текст резюме.
          <span class="inline-flex items-center gap-1 text-emerald-700">
            <span class="inline-block h-2.5 w-2.5 rounded-sm bg-emerald-300" />
            зелёным подсвечены подтверждённые навыки
          </span>
        </p>
      </div>
      <button class="btn-secondary" @click="back">
        <ArrowLeft :size="16" />
        Вернуться к отчёту
      </button>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div v-if="loading" class="text-sm text-slate-500">Загрузка…</div>

    <div v-else class="grid gap-4 lg:grid-cols-2">
      <article class="flex min-h-0 flex-col rounded-3xl border border-slate-200 bg-white shadow-sm">
        <header class="flex items-center justify-between gap-2 border-b border-slate-100 px-5 py-3">
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Вакансия</p>
            <p class="text-sm font-semibold text-slate-900">
              {{ vacancy?.title ?? 'Вакансия' }}
            </p>
          </div>
          <RouterLink
            v-if="vacancyId"
            :to="`/vacancies/${vacancyId}`"
            class="text-xs font-medium text-indigo-600 hover:text-indigo-700"
          >
            Открыть карточку
          </RouterLink>
        </header>
        <div class="min-h-0 flex-1 overflow-auto px-5 py-4">
          <pre
            v-if="vacancy?.source_text"
            class="whitespace-pre-wrap font-mono text-sm leading-6 text-slate-800"
          ><template v-for="(segment, index) in vacancySegments" :key="index"><mark
                v-if="segment.match"
                class="rounded bg-emerald-200/80 px-0.5 text-slate-900"
              >{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></pre>
          <p v-else class="text-sm text-slate-500">Текст вакансии недоступен.</p>
        </div>
      </article>

      <article class="flex min-h-0 flex-col rounded-3xl border border-slate-200 bg-white shadow-sm">
        <header class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-5 py-3">
          <div>
            <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Резюме</p>
            <p class="text-sm font-semibold text-slate-900">
              {{ revealed ? 'Исходный текст с PII' : 'Обезличенный текст' }}
            </p>
          </div>
          <button
            v-if="!revealed"
            class="btn-secondary"
            :disabled="revealing"
            @click="reveal"
          >
            <Loader2 v-if="revealing" :size="16" class="animate-spin" />
            <Eye v-else :size="16" />
            Показать оригинал
          </button>
          <button v-else class="btn-secondary" @click="hide">
            <EyeOff :size="16" />
            Скрыть PII
          </button>
        </header>
        <div class="min-h-0 flex-1 overflow-auto px-5 py-4">
          <div
            v-if="revealed"
            class="mb-3 rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-800"
          >
            PII раскрыты по указанной причине, действие записано в аудит.
          </div>
          <pre
            v-if="resumeText"
            class="whitespace-pre-wrap font-mono text-sm leading-6 text-slate-800"
          ><template v-for="(segment, index) in resumeSegments" :key="index"><mark
                v-if="segment.match"
                class="rounded bg-emerald-200/80 px-0.5 text-slate-900"
              >{{ segment.text }}</mark><span v-else>{{ segment.text }}</span></template></pre>
          <p v-else class="text-sm text-slate-500">Текст резюме недоступен.</p>
        </div>
      </article>
    </div>
  </section>
</template>
