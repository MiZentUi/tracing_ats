<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, Loader2, RefreshCcw } from '@lucide/vue'
import type { ResumeSummary, ResumeVacancyRankingResponse } from '@/api/generated'
import { apiErrorMessage, resumeRankingApi, resumesApi } from '@/api'
import { useToast } from '@/composables/useToast'

const props = defineProps<{ id: string }>()
const { success, error: toastError } = useToast()

const resume = ref<ResumeSummary | null>(null)
const ranking = ref<ResumeVacancyRankingResponse | null>(null)
const loading = ref(true)
const matching = ref(false)
const error = ref('')
let timer: number | null = null

const inProgress = computed(() => {
  const status = resume.value?.status
  return status === 'MASKED' || status === 'EXTRACTED' || status === 'MATCHING'
})

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
    if (resume.value.status === 'MATCHED' || resume.value.status === 'MATCH_FAILED') {
      stopPolling()
    } else {
      poll()
    }
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function rematch() {
  matching.value = true
  error.value = ''
  try {
    await resumeRankingApi.matchResume({ resumeId: props.id })
    success('Пересчёт запущен', 'LLM не вызывается: используется сохранённый разбор')
    poll()
  } catch (e) {
    error.value = apiErrorMessage(e)
    toastError('Не удалось запустить пересчёт', error.value)
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
      if (response.data.status === 'MATCHED' || response.data.status === 'MATCH_FAILED') {
        stopPolling()
        const rankingResponse = await resumeRankingApi.getResumeVacancyRanking({
          resumeId: props.id,
        })
        ranking.value = rankingResponse.data
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

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Вакансии под резюме</p>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">
          Рейтинг вакансий
        </h1>
        <p class="mt-1 text-sm text-slate-500">
          Резюме {{ props.id.slice(0, 8) }}… · статус {{ resume?.status ?? '—' }}
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <RouterLink class="btn-secondary" to="/analyses">
          <ArrowLeft :size="16" />
          Все разборы
        </RouterLink>
        <RouterLink
          class="btn-secondary"
          :to="{ path: '/match', query: { mode: 'vacancies' } }"
        >
          Загрузить ещё
        </RouterLink>
        <button class="btn-primary" :disabled="matching" @click="rematch">
          <Loader2 v-if="matching" :size="16" class="animate-spin" />
          <RefreshCcw v-else :size="16" />
          Пересчитать без LLM
        </button>
      </div>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div
      v-if="inProgress"
      class="flex items-center gap-3 rounded-2xl border border-indigo-200 bg-indigo-50/60 px-4 py-3 text-sm text-indigo-800"
    >
      <Loader2 :size="16" class="animate-spin" />
      Считаем соответствие по вакансиям. Рейтинг обновится автоматически.
    </div>

    <div
      v-if="resume?.warnings?.length"
      class="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"
    >
      {{ resume.warnings.join(', ') }}
    </div>

    <div v-if="loading && !ranking" class="text-sm text-slate-500">Загрузка…</div>
    <div
      v-else-if="!ranking?.items?.length"
      class="rounded-3xl border border-dashed border-slate-300 px-6 py-12 text-center text-sm text-slate-500"
    >
      Пока нет оценённых вакансий. Убедитесь, что в системе есть вакансии.
    </div>
    <div v-else class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <table class="w-full text-left text-sm">
        <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th class="px-4 py-3">#</th>
            <th class="px-4 py-3">Вакансия</th>
            <th class="px-4 py-3">Скор</th>
            <th class="px-4 py-3">Вилка</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="item in ranking.items" :key="item.vacancyId">
            <td class="px-4 py-3 font-semibold text-slate-900">{{ item.rank }}</td>
            <td class="px-4 py-3">{{ item.title ?? item.vacancyId.slice(0, 8) + '…' }}</td>
            <td class="px-4 py-3">{{ item.score.toFixed(1) }}</td>
            <td class="px-4 py-3 text-slate-500">
              {{ item.scoreLow.toFixed(1) }}–{{ item.scoreHigh.toFixed(1) }}
            </td>
            <td class="px-4 py-3 text-right">
              <RouterLink
                class="text-sm font-medium text-indigo-600 hover:text-indigo-700"
                :to="`/resumes/${props.id}/vacancies/${item.vacancyId}/report`"
              >
                Отчёт
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
