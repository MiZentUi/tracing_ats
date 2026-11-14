<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ArrowLeft, LockKeyholeOpen } from '@lucide/vue'
import { CandidateMessageKind } from '@/api/generated'
import type {
  ApplicationReport,
  ReportDecisionRequestDecisionEnum,
  RevealedCandidate,
} from '@/api/generated'
import { apiErrorMessage, reportsApi, resumesApi, vacanciesApi } from '@/api'
import AppModal from '@/components/AppModal.vue'
import CandidateMessagesPanel from '@/components/CandidateMessagesPanel.vue'
import RevealPiiDialog from '@/components/RevealPiiDialog.vue'
import TextComparison from '@/components/TextComparison.vue'
import { useToast } from '@/composables/useToast'
import { pluralizeRu, requirementsCount } from '@/lib/format'
import { kindLabel } from '@/lib/labels'

const route = useRoute()
const { success, error: toastError } = useToast()

const report = ref<ApplicationReport | null>(null)
const revealed = ref<RevealedCandidate | null>(null)
const vacancyText = ref('')
const resumeText = ref('')
const revealedResumeText = ref('')
const resumeRevealOpen = ref(false)
const resumeRevealing = ref(false)
const decisionOpen = ref(false)
const decisionValue = ref<ReportDecisionRequestDecisionEnum>('SHORTLISTED')
const decisionComment = ref('')
const decisionError = ref('')
const savingDecision = ref(false)
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
const comparisonResumeText = computed(() => revealedResumeText.value || resumeText.value)
const decisionApplicationId = computed(() => report.value?.applicationId ?? '')
const decisionResumeId = computed(() => report.value?.resumeId ?? '')
const decisionVacancyId = computed(() => report.value?.vacancyId ?? '')
const decisionHasMessageTarget = computed(
  () =>
    Boolean(decisionApplicationId.value) ||
    Boolean(decisionResumeId.value && decisionVacancyId.value),
)
const decisionPhase = computed<'PRELIMINARY' | 'FINAL'>(() =>
  report.value?.phase === 'PRELIMINARY' ? 'PRELIMINARY' : 'FINAL',
)
const decisionHasGaps = computed(() => Boolean(report.value?.gaps?.length))
const decisionMessageKind = computed<CandidateMessageKind>(() =>
  decisionValue.value === 'NOT_SHORTLISTED'
    ? CandidateMessageKind.Rejection
    : CandidateMessageKind.InterviewInvitation,
)

const decisionOptions: Array<{ value: ReportDecisionRequestDecisionEnum; label: string }> = [
  { value: 'SHORTLISTED', label: 'Собеседование' },
  { value: 'NOT_SHORTLISTED', label: 'Отказ' },
]

const hrDecisionLabels: Record<string, string> = {
  SHORTLISTED: 'Собеседование',
  NOT_SHORTLISTED: 'Отказ',
  BORDERLINE: 'На границе',
}

function hrDecisionLabel(value: string): string {
  return hrDecisionLabels[value] ?? value
}

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

const statusLegend = [
  { status: 'COVERED', description: 'Требование подтверждено цитатой из резюме.' },
  { status: 'PARTIAL', description: 'Требование подтверждено частично.' },
  { status: 'GAP', description: 'Подтверждение не найдено или цитата не прошла проверку.' },
  {
    status: 'NOT_STATED',
    description: 'В резюме нет сведений. Это не означает, что навыка или опыта нет.',
  },
  { status: 'ABSENT', description: 'Резюме указывает на несоответствие требованию.' },
]

function tenureText(tenure: number | null | undefined): string {
  if (tenure === null || tenure === undefined || tenure <= 0) {
    return '—'
  }
  return `${tenure} ${pluralizeRu(tenure, 'год', 'года', 'лет')}`
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
    if (axios.isAxiosError(e) && e.response?.status === 404) {
      error.value = 'Отчёт пока недоступен: скоринг ещё не завершён или завершился ошибкой.'
    } else {
      error.value = apiErrorMessage(e)
    }
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

async function revealResume(reason: string) {
  const value = report.value
  if (!value?.resumeId) {
    return
  }
  resumeRevealing.value = true
  try {
    const response = await resumesApi.revealResumeText({
      resumeId: value.resumeId,
      revealTextRequest: {
        reason,
        applicationId: value.applicationId ?? undefined,
      },
    })
    revealedResumeText.value = response.data.text
    resumeRevealOpen.value = false
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось показать персональные данные', message)
  } finally {
    resumeRevealing.value = false
  }
}

function hideResumeReveal() {
  revealedResumeText.value = ''
}

function openDecision() {
  decisionValue.value =
    report.value?.hrDecision === 'NOT_SHORTLISTED' ? 'NOT_SHORTLISTED' : 'SHORTLISTED'
  decisionComment.value = report.value?.hrComment ?? ''
  decisionError.value = ''
  decisionOpen.value = true
}

async function saveDecision() {
  const value = report.value
  if (!value?.resumeId || !value.vacancyId || savingDecision.value) {
    return
  }
  const comment = decisionComment.value.trim()
  if (comment.length < 3) {
    decisionError.value = 'Добавьте комментарий: минимум 3 символа'
    return
  }
  savingDecision.value = true
  decisionError.value = ''
  try {
    await reportsApi.saveReportDecision({
      resumeId: value.resumeId,
      vacancyId: value.vacancyId,
      reportDecisionRequest: {
        decision: decisionValue.value,
        comment,
      },
    })
    decisionOpen.value = false
    success('Решение сохранено')
    await load()
  } catch (e) {
    decisionError.value = apiErrorMessage(e)
  } finally {
    savingDecision.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-slate-900">Отчёт о соответствии</h1>
        <p v-if="report" class="mt-1 text-sm text-slate-500">
          {{ requirementsCount(report.requirements?.length ?? 0) }}
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
            {{ report.hrDecision ? hrDecisionLabel(report.hrDecision) : 'Не выбрано' }}
          </p>
          <p v-if="report.hrComment" class="mt-1 text-sm text-slate-500">
            {{ report.hrComment }}
          </p>
          <button class="btn-secondary mt-3 px-3 py-1.5 text-xs" @click="openDecision">
            {{ report.hrDecision ? 'Изменить решение' : 'Выбрать решение' }}
          </button>
        </div>
      </div>

      <section
        v-if="report.questions?.length || report.risks?.length"
        class="space-y-4"
      >
        <div class="grid gap-4 md:grid-cols-2">
          <div v-if="report.questions?.length" class="card p-5">
            <p class="text-xs uppercase tracking-wide text-slate-500">Рекомендации и вопросы</p>
            <ul class="mt-3 space-y-2 text-sm text-slate-700">
              <li v-for="item in report.questions" :key="item" class="flex gap-2">
                <span class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-500" />
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

          <div v-if="report.risks?.length" class="card p-5">
            <p class="text-xs uppercase tracking-wide text-slate-500">Риски</p>
            <ul class="mt-3 space-y-2 text-sm text-slate-700">
              <li v-for="item in report.risks" :key="item" class="flex gap-2">
                <span class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

        </div>
      </section>

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

      <div class="card p-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <p class="text-sm font-semibold text-slate-900">Как читать статусы</p>
          <span class="text-xs text-slate-400">«Не указано» ≠ «отсутствует»</span>
        </div>
        <ul class="mt-3 grid gap-2 text-xs sm:grid-cols-2">
          <li v-for="item in statusLegend" :key="item.status" class="flex items-start gap-2">
            <span
              class="shrink-0 rounded-md border px-2 py-0.5 font-medium"
              :class="statusClass[item.status]"
            >
              {{ statusLabel[item.status] }}
            </span>
            <span class="text-slate-600">{{ item.description }}</span>
          </li>
        </ul>
      </div>

      <div class="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        <table class="w-full min-w-[760px] text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th class="px-4 py-3">Требование</th>
              <th class="px-4 py-3">Тип</th>
              <th class="px-4 py-3">Опыт</th>
              <th class="px-4 py-3">Статус</th>
              <th class="px-4 py-3">Подтверждение</th>
              <th class="px-4 py-3">Комментарий</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="item in report.requirements" :key="item.requirementId">
              <td class="px-4 py-3 font-medium text-slate-900">{{ item.text }}</td>
              <td class="px-4 py-3 text-slate-500">{{ kindLabel(item.kind) }}</td>
              <td class="whitespace-nowrap px-4 py-3 text-slate-600">
                {{ tenureText(item.tenure) }}
              </td>
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
        :resume-text="comparisonResumeText"
        :requirements="report.requirements"
        :resume-id="report.resumeId"
        :resume-revealed="!!revealedResumeText"
        :revealing="resumeRevealing"
        @reveal="resumeRevealOpen = true"
        @hide="hideResumeReveal"
      />
    </template>

    <RevealPiiDialog
      :open="revealOpen"
      :submitting="revealing"
      @close="revealOpen = false"
      @confirm="reveal"
    />

    <RevealPiiDialog
      :open="resumeRevealOpen"
      :submitting="resumeRevealing"
      @close="resumeRevealOpen = false"
      @confirm="revealResume"
    />

    <AppModal
      :open="decisionOpen"
      title="Решение по отчёту"
      description="Выберите решение и добавьте комментарий."
      width="lg"
      @close="decisionOpen = false"
    >
      <div class="space-y-4">
        <div>
          <label class="label" for="report-decision">Решение</label>
          <select id="report-decision" v-model="decisionValue" class="input">
            <option v-for="option in decisionOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div>
          <label class="label" for="report-comment">Комментарий</label>
          <textarea
            id="report-comment"
            v-model="decisionComment"
            class="textarea min-h-0"
            rows="2"
            maxlength="2000"
            placeholder="Почему выбрано это решение"
          />
        </div>

        <CandidateMessagesPanel
          v-if="decisionOpen && decisionHasMessageTarget"
          :application-id="decisionApplicationId"
          :resume-id="decisionResumeId"
          :vacancy-id="decisionVacancyId"
          :phase="decisionPhase"
          :kind="decisionMessageKind"
          :has-gaps="decisionHasGaps"
        />

        <p v-if="decisionError" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {{ decisionError }}
        </p>
        <div class="flex justify-end gap-2">
          <button class="btn-secondary" :disabled="savingDecision" @click="decisionOpen = false">
            Отмена
          </button>
          <button class="btn-primary" :disabled="savingDecision" @click="saveDecision">
            {{ savingDecision ? 'Сохраняем…' : 'Сохранить' }}
          </button>
        </div>
      </div>
    </AppModal>
  </section>
</template>
