<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowLeft,
  Copy,
  Loader2,
  Pencil,
  Play,
  Plus,
  RefreshCcw,
  Users,
  X,
} from '@lucide/vue'
import type {
  RankingResponse,
  ScreeningBatchStatus,
  Specialization,
  Vacancy,
} from '@/api/generated'
import {
  apiErrorMessage,
  rankingApi,
  referenceApi,
  screeningApi,
  vacanciesApi,
} from '@/api'
import MarkdownText from '@/components/MarkdownText.vue'
import ResumeUploader from '@/components/ResumeUploader.vue'
import { useToast } from '@/composables/useToast'
import { batchStatusLabel, decisionLabel, phaseLabel } from '@/lib/labels'
import { levelLabel } from '@/types'

const props = defineProps<{ id: string }>()
const { success, info, error: toastError } = useToast()

const vacancy = ref<Vacancy | null>(null)
const ranking = ref<RankingResponse | null>(null)
const specializations = ref<Specialization[]>([])
const phase = ref<'PRELIMINARY' | 'FINAL'>('PRELIMINARY')
const loading = ref(true)
const starting = ref(false)
const showUploader = ref(false)
const error = ref('')
const batchId = ref<string>('')
const batch = ref<ScreeningBatchStatus | null>(null)
let timer: number | null = null

const progress = computed(() => {
  if (!batch.value || batch.value.total === 0) {
    return 0
  }
  return Math.round((batch.value.processed / batch.value.total) * 100)
})

const running = computed(
  () => batch.value?.status === 'PENDING' || batch.value?.status === 'RUNNING',
)

const candidates = computed(() => ranking.value?.items ?? [])
const sourceText = computed(() => vacancy.value?.source_text ?? '')

const specializationName = computed(
  () =>
    specializations.value.find((item) => item.code === vacancy.value?.specialization)?.name ??
    vacancy.value?.specialization ??
    '—',
)

const phaseHint = computed(() =>
  phase.value === 'PRELIMINARY'
    ? 'Только по тексту резюме, без внешних источников.'
    : 'С учётом внешних данных для отобранных кандидатов.',
)

function decisionClass(decision: string): string {
  switch (decision) {
    case 'SHORTLISTED':
      return 'bg-emerald-50 text-emerald-700'
    case 'BORDERLINE':
      return 'bg-amber-50 text-amber-700'
    default:
      return 'bg-slate-100 text-slate-600'
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [vacancyResponse, rankingResponse] = await Promise.all([
      vacanciesApi.getVacancy({ id: props.id }),
      rankingApi.getRanking({ vacancyId: props.id, phase: phase.value }),
    ])
    vacancy.value = vacancyResponse.data
    ranking.value = rankingResponse.data
    try {
      const specs = await referenceApi.listSpecializations()
      specializations.value = specs.data
    } catch {
      // Без справочника показываем код специализации, страница остаётся рабочей.
    }
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function startScreening() {
  starting.value = true
  error.value = ''
  try {
    const response = await screeningApi.startScreening({
      vacancyId: props.id,
      screeningRequest: { applicationIds: [] },
    })
    batchId.value = response.data.batchId
    batch.value = null
    success('Подбор запущен', 'Баллы появятся в списке кандидатов после завершения')
    pollBatch()
  } catch (e) {
    error.value = apiErrorMessage(e)
    toastError('Не удалось запустить подбор', error.value)
  } finally {
    starting.value = false
  }
}

function pollBatch() {
  stopPolling()
  timer = window.setInterval(async () => {
    if (!batchId.value) {
      return
    }
    try {
      const response = await screeningApi.getScreeningBatch({ batchId: batchId.value })
      batch.value = response.data
      if (response.data.status === 'COMPLETED' || response.data.status === 'FAILED') {
        stopPolling()
        if (response.data.status === 'COMPLETED') {
          success('Подбор завершён', `Обработано кандидатов: ${response.data.processed}`)
        }
        await load()
      }
    } catch (e) {
      stopPolling()
      error.value = apiErrorMessage(e)
    }
  }, 1500)
}

function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

async function switchPhase(value: 'PRELIMINARY' | 'FINAL') {
  if (phase.value === value) {
    return
  }
  phase.value = value
  await load()
}

async function onUploaded() {
  showUploader.value = false
  phase.value = 'PRELIMINARY'
  await load()
  info('Резюме добавлено', 'Нажмите «Подобрать кандидатов», чтобы получить баллы')
}

async function copySource() {
  if (!sourceText.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(sourceText.value)
    success('Текст скопирован')
  } catch {
    toastError('Не удалось скопировать текст')
  }
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="space-y-5 pb-4">
    <div>
      <RouterLink
        to="/vacancies"
        class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900"
      >
        <ArrowLeft :size="16" />
        К списку вакансий
      </RouterLink>
    </div>

    <div v-if="loading" class="space-y-4">
      <div class="card space-y-3 p-6">
        <div class="skeleton h-8 w-1/3" />
        <div class="skeleton h-4 w-1/2" />
      </div>
      <div class="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div class="skeleton h-96" />
        <div class="skeleton h-72" />
      </div>
    </div>

    <template v-else-if="vacancy">
      <header class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0">
          <h1 class="text-3xl font-semibold tracking-tight text-slate-900">
            {{ vacancy.title || 'Без названия' }}
          </h1>
          <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-slate-500">
            <span v-if="vacancy.template.is_confirmed" class="badge-info">Шаблон</span>
            <span>{{ specializationName }}</span>
            <span class="text-slate-300">·</span>
            <span>{{ levelLabel(vacancy.level) }}</span>
            <span class="text-slate-300">·</span>
            <span>{{ vacancy.template.requirements.length }} требований</span>
            <span class="text-slate-300">·</span>
            <span>версия {{ vacancy.template.version }}</span>
          </p>
        </div>
        <RouterLink :to="`/vacancies/${props.id}/edit`" class="btn-primary shrink-0">
          <Pencil :size="16" />
          Редактировать
        </RouterLink>
      </header>

      <div
        v-if="error"
        class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
      >
        <span>{{ error }}</span>
        <button class="btn-secondary shrink-0" @click="load">Повторить</button>
      </div>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <div class="space-y-5">
          <article class="card p-5">
            <h2 class="text-base font-semibold text-slate-900">О вакансии</h2>
            <dl class="mt-4 grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Специализация</dt>
                <dd class="mt-1 font-medium text-slate-900">{{ specializationName }}</dd>
              </div>
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Уровень</dt>
                <dd class="mt-1 font-medium text-slate-900">{{ levelLabel(vacancy.level) }}</dd>
              </div>
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Требований</dt>
                <dd class="mt-1 font-medium text-slate-900">
                  {{ vacancy.template.requirements.length }}
                </dd>
              </div>
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Версия</dt>
                <dd class="mt-1 font-medium text-slate-900">{{ vacancy.template.version }}</dd>
              </div>
              <div class="sm:col-span-2">
                <dt class="text-xs uppercase tracking-wide text-slate-400">Как разобрана</dt>
                <dd class="mt-1 text-slate-600">
                  {{ vacancy.template.meta?.model ?? 'модель не указана' }} ·
                  {{ vacancy.template.meta?.prompt_version ?? 'промпт не указан' }}
                </dd>
              </div>
            </dl>
          </article>

          <article class="card">
            <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <div>
                <h2 class="text-base font-semibold text-slate-900">Текст вакансии</h2>
                <p class="mt-0.5 text-xs text-slate-400">
                  {{ sourceText.length.toLocaleString('ru-RU') }} символов
                </p>
              </div>
              <button class="btn-secondary px-3 py-1.5 text-xs" @click="copySource">
                <Copy :size="14" />
                Скопировать
              </button>
            </div>
            <div class="px-5 py-4">
              <MarkdownText :source="sourceText" />
            </div>
          </article>
        </div>

        <aside class="space-y-4 lg:sticky lg:top-24">
          <article class="card overflow-hidden">
            <div class="border-b border-slate-100 px-4 py-4">
              <div class="flex items-center justify-between gap-2">
                <h2 class="flex items-center gap-2 text-base font-semibold text-slate-900">
                  <Users :size="17" class="text-indigo-600" />
                  Кандидаты
                </h2>
                <span v-if="candidates.length" class="badge-neutral">{{ candidates.length }}</span>
              </div>
              <p class="mt-1 text-xs text-slate-500">
                Оценка по текущей версии требований.
              </p>

              <div class="mt-3 flex rounded-lg border border-slate-200 bg-slate-50 p-0.5">
                <button
                  v-for="value in (['PRELIMINARY', 'FINAL'] as const)"
                  :key="value"
                  type="button"
                  class="flex-1 rounded-md px-2 py-1.5 text-xs font-medium transition"
                  :class="phase === value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'"
                  @click="switchPhase(value)"
                >
                  {{ phaseLabel(value) }}
                </button>
              </div>
              <p class="mt-1.5 text-[11px] leading-4 text-slate-400">{{ phaseHint }}</p>

              <div class="mt-3 grid gap-2">
                <button class="btn-primary w-full" :disabled="starting || running" @click="startScreening">
                  <Loader2 v-if="starting || running" :size="16" class="animate-spin" />
                  <Play v-else :size="16" />
                  {{ running ? 'Подбираем…' : 'Подобрать кандидатов' }}
                </button>
                <button class="btn-secondary w-full" @click="showUploader = !showUploader">
                  <X v-if="showUploader" :size="15" />
                  <Plus v-else :size="15" />
                  {{ showUploader ? 'Свернуть загрузку' : 'Добавить резюме' }}
                </button>
              </div>
            </div>

            <div v-if="showUploader" class="border-b border-slate-100 p-4">
              <ResumeUploader :vacancy-id="props.id" compact @uploaded="onUploaded" />
            </div>

            <div v-if="batch" class="border-b border-slate-100 px-4 py-3">
              <div class="flex items-center justify-between text-xs text-slate-600">
                <span>{{ batchStatusLabel(batch.status) }}</span>
                <span>{{ batch.processed }} / {{ batch.total }}</span>
              </div>
              <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                <div class="h-full bg-indigo-500 transition-all" :style="{ width: `${progress}%` }" />
              </div>
            </div>

            <div v-if="!candidates.length" class="px-4 py-8 text-center text-sm text-slate-500">
              <p>Пока нет оценённых кандидатов.</p>
              <p class="mt-1 text-xs text-slate-400">
                Добавьте резюме и запустите подбор.
              </p>
            </div>

            <ul v-else class="divide-y divide-slate-100">
              <li v-for="item in candidates" :key="item.applicationId" class="px-4 py-3">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="text-sm font-semibold text-slate-900">
                      Кандидат #{{ item.rank }}
                    </p>
                    <p class="mt-0.5 text-xs text-slate-500">
                      Скор {{ item.score.toFixed(1) }} · вилка
                      {{ item.scoreLow.toFixed(1) }}–{{ item.scoreHigh.toFixed(1) }}
                    </p>
                  </div>
                  <span
                    class="shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium"
                    :class="decisionClass(item.decision)"
                  >
                    {{ decisionLabel(item.decision) }}
                  </span>
                </div>
                <div class="mt-2 flex items-center justify-between gap-2">
                  <span class="truncate text-[11px] text-slate-400">
                    {{ item.flags?.length ? item.flags.join(', ') : 'без флагов' }}
                  </span>
                  <RouterLink
                    :to="`/applications/${item.applicationId}/report`"
                    class="text-xs font-medium text-indigo-600 hover:text-indigo-700"
                  >
                    Отчёт
                  </RouterLink>
                </div>
              </li>
            </ul>

            <div class="border-t border-slate-100 px-4 py-3">
              <button class="btn-ghost w-full px-0 text-xs" :disabled="loading" @click="load">
                <RefreshCcw :size="14" />
                Обновить список
              </button>
            </div>
          </article>
        </aside>
      </div>
    </template>
  </section>
</template>
