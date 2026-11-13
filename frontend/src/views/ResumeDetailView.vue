<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowLeft,
  Eye,
  EyeOff,
  Loader2,
  Pencil,
  RefreshCcw,
  ShieldCheck,
} from '@lucide/vue'
import type {
  ResumeFullText,
  ResumeSummary,
  ResumeText,
  ResumeVacancyRankingResponse,
} from '@/api/generated'
import { apiErrorMessage, resumeRankingApi, resumesApi } from '@/api'
import RevealPiiDialog from '@/components/RevealPiiDialog.vue'
import { useToast } from '@/composables/useToast'
import { formatBytes, formatDateTime, resumeTitle, shortId } from '@/lib/format'
import { resumeStatusClass, resumeStatusLabel } from '@/lib/labels'

const props = defineProps<{ id: string }>()
const { success, error: toastError } = useToast()

const resume = ref<ResumeSummary | null>(null)
const resumeText = ref<ResumeText | null>(null)
const revealed = ref<ResumeFullText | null>(null)
const ranking = ref<ResumeVacancyRankingResponse | null>(null)
const loading = ref(true)
const matching = ref(false)
const revealing = ref(false)
const revealOpen = ref(false)
const revealReason = ref('')
const error = ref('')
let timer: number | null = null

const inProgress = computed(() => {
  const status = resume.value?.status
  return (
    status === 'UPLOADED' ||
    status === 'MASKED' ||
    status === 'EXTRACTED' ||
    status === 'MATCHING'
  )
})

const vacancies = computed(() => ranking.value?.items ?? [])
const displayText = computed(() => revealed.value?.text ?? resumeText.value?.maskedText ?? '')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [resumeResponse, rankingResponse] = await Promise.all([
      resumesApi.getResume({ resumeId: props.id }),
      resumeRankingApi.getResumeVacancyRanking({ resumeId: props.id }),
    ])
    resume.value = resumeResponse.data
    ranking.value = rankingResponse.data
    await loadText()
    if (inProgress.value) {
      poll()
    } else {
      stopPolling()
    }
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function loadText() {
  try {
    const response = await resumesApi.getResumeText({ resumeId: props.id })
    resumeText.value = response.data
  } catch {
    // Текст появляется не сразу после загрузки — не роняем из-за него всю страницу.
    resumeText.value = null
  }
}

async function rematch() {
  matching.value = true
  error.value = ''
  try {
    await resumeRankingApi.matchResume({ resumeId: props.id })
    success('Подбор обновлён', 'LLM не вызывается: используется сохранённый разбор')
    poll()
  } catch (e) {
    error.value = apiErrorMessage(e)
    toastError('Не удалось обновить подбор', error.value)
  } finally {
    matching.value = false
  }
}

function poll() {
  stopPolling()
  timer = window.setInterval(async () => {
    try {
      const response = await resumesApi.getResume({ resumeId: props.id })
      resume.value = response.data
      if (!inProgress.value) {
        stopPolling()
        const rankingResponse = await resumeRankingApi.getResumeVacancyRanking({
          resumeId: props.id,
        })
        ranking.value = rankingResponse.data
        await loadText()
      }
    } catch {
      stopPolling()
    }
  }, 1500)
}

function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

async function reveal(reason: string) {
  revealing.value = true
  try {
    const response = await resumesApi.revealResumeText({
      resumeId: props.id,
      revealTextRequest: { reason },
    })
    revealed.value = response.data
    revealReason.value = reason
    revealOpen.value = false
    success('Персональные данные показаны', 'Действие записано в аудит')
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось показать персональные данные', message)
  } finally {
    revealing.value = false
  }
}

function hideRevealed() {
  revealed.value = null
  revealReason.value = ''
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="space-y-5 pb-4">
    <div>
      <RouterLink
        to="/resumes"
        class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900"
      >
        <ArrowLeft :size="16" />
        К списку резюме
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

    <template v-else-if="resume">
      <article class="card p-5">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="section-label">Резюме кандидата</p>
            <h1 class="mt-1 text-2xl font-semibold tracking-tight text-slate-900">
              {{ resumeTitle(resume.createdAt, resume.fileType) }}
            </h1>
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <span
                class="rounded-md px-2 py-0.5 text-xs font-medium"
                :class="resumeStatusClass(resume.status)"
              >
                {{ resumeStatusLabel(resume.status) }}
              </span>
              <span class="badge-neutral">{{ resume.fileType.toUpperCase() }}</span>
              <span class="badge-neutral">{{ formatBytes(resume.sizeBytes) }}</span>
              <span class="badge-neutral">номер {{ shortId(resume.resumeId) }}</span>
              <span v-if="!revealed" class="badge-info">
                <ShieldCheck :size="12" />
                Слепой режим
              </span>
            </div>
            <p class="mt-2 text-xs text-slate-400">
              Загружено {{ formatDateTime(resume.createdAt) }}
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <button
              v-if="!revealed"
              class="btn-secondary"
              :disabled="revealing"
              @click="revealOpen = true"
            >
              <Loader2 v-if="revealing" :size="16" class="animate-spin" />
              <Eye v-else :size="16" />
              Показать персональные данные
            </button>
            <button v-else class="btn-secondary" @click="hideRevealed">
              <EyeOff :size="16" />
              Скрыть персональные данные
            </button>
            <button
              class="btn-secondary"
              disabled
              title="Редактирование резюме появится после поддержки API"
            >
              <Pencil :size="16" />
              Редактировать
            </button>
          </div>
        </div>

        <p class="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
          Редактирование резюме пока недоступно: в API нет обновления текста и названия. Сейчас
          можно посмотреть обезличенный текст и подобрать вакансии.
        </p>
      </article>

      <div
        v-if="error"
        class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
      >
        <span>{{ error }}</span>
        <button class="btn-secondary shrink-0" @click="load">Повторить</button>
      </div>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <article class="card overflow-hidden">
          <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
            <div>
              <h2 class="text-base font-semibold text-slate-900">
                {{ revealed ? 'Текст с персональными данными' : 'Обезличенный текст' }}
              </h2>
              <p class="mt-0.5 text-xs text-slate-400">
                {{ displayText.length.toLocaleString('ru-RU') }} символов
              </p>
            </div>
            <span v-if="revealed" class="badge-warning">PII раскрыты</span>
            <span v-else class="badge-info">
              <ShieldCheck :size="12" />
              PII скрыты
            </span>
          </div>
          <div
            v-if="revealed"
            class="border-b border-amber-100 bg-amber-50 px-5 py-3 text-xs text-amber-800"
          >
            Причина: «{{ revealReason }}». Действие записано в аудит.
          </div>
          <div
            v-if="resumeText?.warnings?.length"
            class="border-b border-amber-100 bg-amber-50 px-5 py-3 text-sm text-amber-800"
          >
            {{ resumeText.warnings.join(', ') }}
          </div>
          <div class="whitespace-pre-wrap px-5 py-4 text-sm leading-6 text-slate-700">
            {{ displayText || 'Текст резюме недоступен' }}
          </div>
        </article>

        <aside class="lg:sticky lg:top-24">
          <article class="card overflow-hidden">
            <div class="border-b border-slate-100 px-4 py-4">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-base font-semibold text-slate-900">Подходящие вакансии</h2>
                <span v-if="vacancies.length" class="badge-neutral">{{ vacancies.length }}</span>
              </div>
              <p class="mt-1 text-xs text-slate-500">
                Совпадения по сохранённому разбору резюме.
              </p>
              <button class="btn-primary mt-3 w-full" :disabled="matching || inProgress" @click="rematch">
                <Loader2 v-if="matching" :size="16" class="animate-spin" />
                <RefreshCcw v-else :size="16" />
                Обновить подбор
              </button>
            </div>

            <div
              v-if="inProgress"
              class="flex items-center gap-2 border-b border-slate-100 px-4 py-3 text-xs text-indigo-800"
            >
              <Loader2 :size="14" class="animate-spin" />
              Идёт обработка резюме. Список обновится автоматически.
            </div>

            <div v-if="!vacancies.length" class="px-4 py-8 text-center text-sm text-slate-500">
              <p>Пока нет подходящих вакансий.</p>
              <p class="mt-1 text-xs text-slate-400">
                Добавьте вакансию и обновите подбор.
              </p>
            </div>

            <ul v-else class="divide-y divide-slate-100">
              <li v-for="item in vacancies" :key="item.vacancyId" class="px-4 py-3">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="text-sm font-semibold text-slate-900">
                      {{ item.title || `Вакансия ${shortId(item.vacancyId)}` }}
                    </p>
                    <p class="mt-0.5 text-xs text-slate-500">
                      Скор {{ item.score.toFixed(1) }} · вилка
                      {{ item.scoreLow.toFixed(1) }}–{{ item.scoreHigh.toFixed(1) }}
                    </p>
                  </div>
                  <span class="shrink-0 text-xs font-semibold text-slate-400">#{{ item.rank }}</span>
                </div>
                <div class="mt-2 flex items-center justify-between gap-2">
                  <span class="truncate text-[11px] text-slate-400">
                    {{ item.flags?.length ? item.flags.join(', ') : 'без флагов' }}
                  </span>
                  <RouterLink
                    :to="`/resumes/${props.id}/vacancies/${item.vacancyId}/report`"
                    class="text-xs font-medium text-indigo-600 hover:text-indigo-700"
                  >
                    Отчёт
                  </RouterLink>
                </div>
              </li>
            </ul>
          </article>
        </aside>
      </div>
    </template>

    <RevealPiiDialog
      :open="revealOpen"
      :submitting="revealing"
      @close="revealOpen = false"
      @confirm="reveal"
    />
  </section>
</template>
