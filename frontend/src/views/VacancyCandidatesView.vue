<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Loader2, Play, RefreshCcw } from '@lucide/vue'
import type {
  RankingResponse,
  ScreeningBatchStatus,
  Vacancy,
} from '@/api/generated'
import { apiErrorMessage, rankingApi, screeningApi, vacanciesApi } from '@/api'
import { useToast } from '@/composables/useToast'

const props = defineProps<{ id: string }>()
const { success, error: toastError } = useToast()

const vacancy = ref<Vacancy | null>(null)
const ranking = ref<RankingResponse | null>(null)
const phase = ref<'PRELIMINARY' | 'FINAL'>('FINAL')
const loading = ref(true)
const starting = ref(false)
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
    success('Скрининг запущен')
    pollBatch()
  } catch (e) {
    error.value = apiErrorMessage(e)
    toastError('Не удалось запустить скрининг', error.value)
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
  phase.value = value
  await load()
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="text-xs font-medium uppercase tracking-wide text-indigo-600">Кандидаты под вакансию</p>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">
          {{ vacancy?.title ?? 'Вакансия' }}
        </h1>
        <p class="mt-1 text-sm text-slate-500">
          {{ vacancy?.specialization }} · {{ vacancy?.level }}
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <RouterLink
          class="btn-secondary"
          :to="{ path: '/match', query: { mode: 'candidates', vacancyId: props.id } }"
        >
          Загрузить резюме
        </RouterLink>
        <button class="btn-primary" :disabled="starting || running" @click="startScreening">
          <Loader2 v-if="starting || running" :size="16" class="animate-spin" />
          <Play v-else :size="16" />
          {{ running ? 'Скрининг идёт…' : 'Запустить скрининг' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div v-if="batch" class="rounded-2xl border border-slate-200 bg-white px-4 py-3">
      <div class="flex items-center justify-between text-sm text-slate-700">
        <span>Пакет {{ batch.status }}</span>
        <span>{{ batch.processed }} / {{ batch.total }} (ошибок: {{ batch.failed }})</span>
      </div>
      <div class="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
        <div class="h-full bg-indigo-500 transition-all" :style="{ width: `${progress}%` }" />
      </div>
    </div>

    <div class="flex items-center justify-between">
      <div class="inline-flex rounded-2xl border border-slate-200 bg-white p-1">
        <button
          v-for="value in (['PRELIMINARY', 'FINAL'] as const)"
          :key="value"
          class="rounded-xl px-3 py-1.5 text-sm font-medium"
          :class="phase === value ? 'bg-slate-900 text-white' : 'text-slate-600 hover:bg-slate-100'"
          @click="switchPhase(value)"
        >
          {{ value === 'PRELIMINARY' ? 'Предварительный' : 'Итоговый' }}
        </button>
      </div>
      <button class="btn-secondary" :disabled="loading" @click="load">
        <RefreshCcw :size="16" />
        Обновить
      </button>
    </div>

    <div v-if="loading" class="text-sm text-slate-500">Загрузка…</div>
    <div
      v-else-if="!ranking?.items?.length"
      class="rounded-3xl border border-dashed border-slate-300 px-6 py-12 text-center text-sm text-slate-500"
    >
      Пока нет оценённых кандидатов. Загрузите резюме и запустите скрининг.
    </div>
    <div v-else class="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <table class="w-full text-left text-sm">
        <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th class="px-4 py-3">#</th>
            <th class="px-4 py-3">Скор</th>
            <th class="px-4 py-3">Вилка</th>
            <th class="px-4 py-3">Решение</th>
            <th class="px-4 py-3"></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr v-for="item in ranking.items" :key="item.applicationId">
            <td class="px-4 py-3 font-semibold text-slate-900">{{ item.rank }}</td>
            <td class="px-4 py-3">{{ item.score.toFixed(1) }}</td>
            <td class="px-4 py-3 text-slate-500">
              {{ item.scoreLow.toFixed(1) }}–{{ item.scoreHigh.toFixed(1) }}
            </td>
            <td class="px-4 py-3">
              <span class="rounded-full bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">
                {{ item.decision }}
              </span>
            </td>
            <td class="px-4 py-3 text-right">
              <RouterLink
                class="text-sm font-medium text-indigo-600 hover:text-indigo-700"
                :to="`/applications/${item.applicationId}/report`"
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
