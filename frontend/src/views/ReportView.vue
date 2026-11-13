<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, LockKeyholeOpen } from '@lucide/vue'
import type { ApplicationReport, RevealedCandidate } from '@/api/generated'
import { apiErrorMessage, reportsApi, resumesApi, vacanciesApi } from '@/api'
import RevealPiiDialog from '@/components/RevealPiiDialog.vue'
import TextComparison from '@/components/TextComparison.vue'
import { useToast } from '@/composables/useToast'
import { decisionLabel, kindLabel } from '@/lib/labels'

const route = useRoute()
const { success, error: toastError } = useToast()

const report = ref<ApplicationReport | null>(null)
const revealed = ref<RevealedCandidate | null>(null)
const vacancyText = ref('')
const resumeText = ref('')
const loading = ref(true)
const revealing = ref(false)
const revealOpen = ref(false)
const error = ref('')

const applicationId = computed(() => route.params.id as string | undefined)
const resumeId = computed(() => route.params.resumeId as string | undefined)
const vacancyId = computed(() => route.params.vacancyId as string | undefined)
const phase = computed(() =>
  String(route.query.phase ?? '').toUpperCase() === 'PRELIMINARY' ? 'PRELIMINARY' : 'FINAL',
)

const analysisLink = computed(() => {
  const value = report.value
  if (value?.applicationId && value.vacancyId) {
    return `/vacancies/${value.vacancyId}`
  }
  if (value?.resumeId) {
    return `/resumes/${value.resumeId}`
  }
  if (value?.vacancyId) {
    return `/vacancies/${value.vacancyId}`
  }
  return null
})

const analysisLabel = computed(() => (report.value?.applicationId ? 'К вакансии' : 'К резюме'))

const statusLabel: Record<string, string> = {
  COVERED: 'Подтверждено',
  PARTIAL: 'Частично',
  GAP: 'Пробел',
  NOT_STATED: 'Не указано',
  ABSENT: 'Отсутствует',
}

const statusClass: Record<string, string> = {
  COVERED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PARTIAL: 'bg-amber-50 text-amber-700 border-amber-200',
  GAP: 'bg-rose-50 text-rose-700 border-rose-200',
  NOT_STATED: 'bg-slate-100 text-slate-600 border-slate-200',
  ABSENT: 'bg-rose-50 text-rose-700 border-rose-200',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (applicationId.value) {
      const response = await reportsApi.getApplicationReport({
        applicationId: applicationId.value,
        phase: phase.value,
      })
      report.value = response.data
    } else if (resumeId.value && vacancyId.value) {
      const response = await reportsApi.getResumeVacancyReport({
        resumeId: resumeId.value,
        vacancyId: vacancyId.value,
      })
      report.value = response.data
    }
    await loadTexts()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function loadTexts() {
  const value = report.value
  if (!value) {
    return
  }
  const tasks: Array<Promise<void>> = []
  if (value.resumeId) {
    tasks.push(
      resumesApi
        .getResumeText({ resumeId: value.resumeId })
        .then((response) => {
          resumeText.value = response.data.maskedText
        })
        .catch(() => undefined),
    )
  }
  if (value.vacancyId) {
    tasks.push(
      vacanciesApi
        .getVacancy({ id: value.vacancyId })
        .then((response) => {
          vacancyText.value = response.data.source_text ?? ''
        })
        .catch(() => undefined),
    )
  }
  await Promise.all(tasks)
}

async function reveal(reason: string) {
  if (!applicationId.value) {
    return
  }
  revealing.value = true
  try {
    const response = await reportsApi.revealCandidate({
      applicationId: applicationId.value,
      revealRequest: { reason },
    })
    revealed.value = response.data
    revealOpen.value = false
    success('Персональные данные показаны')
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
}

onMounted(load)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Отчёт о соответствии</h1>
        <p v-if="report" class="mt-1 text-sm text-slate-500">
          {{ report.requirements?.length ?? 0 }} требований
        </p>
      </div>
      <div class="flex items-center gap-2">
        <RouterLink v-if="analysisLink" :to="analysisLink" class="btn-secondary">
          <ArrowLeft :size="16" />
          {{ analysisLabel }}
        </RouterLink>
        <button
          v-if="applicationId && !revealed"
          class="btn-secondary"
          :disabled="revealing"
          @click="revealOpen = true"
        >
          <LockKeyholeOpen :size="16" />
          Показать персональные данные
        </button>
        <button v-else-if="revealed" class="btn-secondary" @click="hideRevealed">
          Скрыть персональные данные
        </button>
      </div>
    </header>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{{ error }}</p>

    <div v-if="loading" class="text-sm text-slate-500">Загрузка…</div>

    <template v-else-if="report">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="rounded-xl border border-slate-200 bg-white p-5">
          <p class="text-xs uppercase tracking-wide text-slate-500">Скор</p>
          <p class="mt-2 text-3xl font-semibold text-slate-900">{{ report.score.toFixed(1) }}</p>
        </div>
        <div class="rounded-xl border border-slate-200 bg-white p-5">
          <p class="text-xs uppercase tracking-wide text-slate-500">Решение</p>
          <p class="mt-2 text-lg font-semibold text-slate-900">
            {{ report.decision ? decisionLabel(report.decision) : '—' }}
          </p>
        </div>
      </div>

      <div
        v-if="revealed"
        class="rounded-xl border border-emerald-200 bg-emerald-50/60 p-5 text-sm text-emerald-900"
      >
        <dl class="grid gap-1 sm:grid-cols-2">
          <div v-if="revealed.fullName"><dt class="text-xs uppercase">Имя</dt><dd>{{ revealed.fullName }}</dd></div>
          <div v-if="revealed.email"><dt class="text-xs uppercase">Email</dt><dd>{{ revealed.email }}</dd></div>
          <div v-if="revealed.phone"><dt class="text-xs uppercase">Телефон</dt><dd>{{ revealed.phone }}</dd></div>
          <div v-if="revealed.githubUrl"><dt class="text-xs uppercase">GitHub</dt><dd>{{ revealed.githubUrl }}</dd></div>
        </dl>
      </div>

      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <table class="w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th class="px-4 py-3">Требование</th>
              <th class="px-4 py-3">Тип</th>
              <th class="px-4 py-3">Статус</th>
              <th class="px-4 py-3">Подтверждение</th>
              <th class="px-4 py-3">Комментарий</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in report.requirements" :key="item.requirementId">
              <td class="px-4 py-3 font-medium text-slate-900">{{ item.text }}</td>
              <td class="px-4 py-3 text-slate-500">{{ kindLabel(item.kind) }}</td>
              <td class="px-4 py-3">
                <span
                  class="rounded-md border px-2 py-1 text-xs font-medium"
                  :class="statusClass[item.status] ?? 'border-slate-200 bg-slate-100 text-slate-600'"
                >
                  {{ statusLabel[item.status] ?? item.status }}
                </span>
              </td>
              <td class="px-4 py-3 text-slate-600">
                <span v-if="item.evidenceQuote">«{{ item.evidenceQuote }}»</span>
                <span v-else class="text-slate-400">—</span>
              </td>
              <td class="px-4 py-3 text-slate-500">{{ item.gap ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <TextComparison
        v-if="report.resumeId && report.vacancyId"
        :vacancy-text="vacancyText"
        :resume-text="resumeText"
        :requirements="report.requirements"
      />
    </template>

    <RevealPiiDialog
      :open="revealOpen"
      :submitting="revealing"
      @close="revealOpen = false"
      @confirm="reveal"
    />
  </section>
</template>
