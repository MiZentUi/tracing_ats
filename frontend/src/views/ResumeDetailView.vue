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
import AppModal from '@/components/AppModal.vue'
import RevealPiiDialog from '@/components/RevealPiiDialog.vue'
import { useToast } from '@/composables/useToast'
import { resumeDisplayTitle } from '@/lib/format'
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
const pendingEdit = ref(false)
const editOpen = ref(false)
const editTitle = ref('')
const editText = ref('')
const editSubmitting = ref(false)
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
    resumeText.value = null
  }
}

async function rematch() {
  matching.value = true
  error.value = ''
  try {
    await resumeRankingApi.matchResume({ resumeId: props.id })
    success('Подбор обновлён')
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

function startEdit() {
  editTitle.value = resume.value?.title ?? ''
  if (revealed.value?.text) {
    editText.value = revealed.value.text
    editOpen.value = true
    return
  }
  pendingEdit.value = true
  revealOpen.value = true
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
    if (pendingEdit.value) {
      editText.value = response.data.text
      editOpen.value = true
      pendingEdit.value = false
    }
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось показать персональные данные', message)
  } finally {
    revealing.value = false
  }
}

function closeReveal() {
  revealOpen.value = false
  pendingEdit.value = false
}

function hideRevealed() {
  revealed.value = null
  revealReason.value = ''
  editOpen.value = false
}

function cancelEdit() {
  editOpen.value = false
  hideRevealed()
}

async function saveEdit() {
  if (!resume.value || editSubmitting.value) {
    return
  }
  const currentTitle = resume.value.title ?? ''
  const title = editTitle.value.trim()
  const textChanged = editText.value !== (revealed.value?.text ?? '')
  const titleChanged = title !== currentTitle

  if (!titleChanged && !textChanged) {
    cancelEdit()
    return
  }
  if (textChanged && revealReason.value.trim().length < 3) {
    error.value = 'Для изменения текста нужна причина раскрытия'
    return
  }

  editSubmitting.value = true
  error.value = ''
  try {
    const response = await resumesApi.updateResume({
      resumeId: props.id,
      updateResumeRequest: {
        title: title || null,
        ...(textChanged ? { text: editText.value, reason: revealReason.value } : {}),
      },
    })
    resume.value = response.data
    if (textChanged) {
      ranking.value = null
      resumeText.value = null
    }
    editOpen.value = false
    hideRevealed()
    success('Резюме сохранено')
    await load()
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось сохранить резюме', message)
  } finally {
    editSubmitting.value = false
  }
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
      <div class="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <div class="space-y-5">
          <article class="card p-5">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div class="min-w-0">
                <h1 class="text-2xl font-semibold tracking-tight text-slate-900">
                  {{ resumeDisplayTitle(resume.title, resume.createdAt) }}
                </h1>
                <span
                  class="mt-2 inline-flex rounded-md px-2 py-0.5 text-xs font-medium"
                  :class="resumeStatusClass(resume.status)"
                >
                  {{ resumeStatusLabel(resume.status) }}
                </span>
              </div>
              <button class="btn-secondary shrink-0" :disabled="inProgress" @click="startEdit">
                <Pencil :size="16" />
                Редактировать
              </button>
            </div>
          </article>

          <article class="card overflow-hidden">
            <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <h2 class="text-base font-semibold text-slate-900">Текст резюме</h2>
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
        </div>

        <aside class="lg:sticky lg:top-24">
          <article class="card overflow-hidden">
            <div class="border-b border-slate-100 px-4 py-4">
              <div class="flex items-center justify-between gap-2">
                <h2 class="text-base font-semibold text-slate-900">Подходящие вакансии</h2>
                <span v-if="vacancies.length" class="badge-neutral">{{ vacancies.length }}</span>
              </div>
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
              Идёт обработка резюме.
            </div>

            <div v-if="!vacancies.length" class="px-4 py-8 text-center text-sm text-slate-500">
              <p>Пока нет подходящих вакансий.</p>
            </div>

            <ul v-else class="divide-y divide-slate-100">
              <li v-for="item in vacancies" :key="item.vacancyId" class="px-4 py-3">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="text-sm font-semibold text-slate-900">
                      {{ item.title || 'Вакансия' }}
                    </p>
                    <p class="mt-0.5 text-xs text-slate-500">
                      Скор {{ item.score.toFixed(1) }}
                    </p>
                  </div>
                  <span class="shrink-0 text-xs font-semibold text-slate-400">#{{ item.rank }}</span>
                </div>
                <div class="mt-2 flex justify-end">
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

      <div
        v-if="error"
        class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
      >
        <span>{{ error }}</span>
        <button class="btn-secondary shrink-0" @click="load">Повторить</button>
      </div>
    </template>

    <RevealPiiDialog
      :open="revealOpen"
      :submitting="revealing"
      @close="closeReveal"
      @confirm="reveal"
    />

    <AppModal
      :open="editOpen"
      title="Редактировать резюме"
      description="Обновите название или текст резюме."
      @close="cancelEdit"
    >
      <div class="space-y-4">
        <div>
          <label class="label" for="resume-title">Название</label>
          <input id="resume-title" v-model="editTitle" class="input" maxlength="255" />
        </div>
        <div>
          <label class="label" for="resume-text">Текст резюме</label>
          <textarea
            id="resume-text"
            v-model="editText"
            class="textarea min-h-72"
          />
        </div>
        <p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {{ error }}
        </p>
        <div class="flex justify-end gap-2">
          <button class="btn-secondary" :disabled="editSubmitting" @click="cancelEdit">
            Отмена
          </button>
          <button class="btn-primary" :disabled="editSubmitting" @click="saveEdit">
            <Loader2 v-if="editSubmitting" :size="16" class="animate-spin" />
            <ShieldCheck v-else :size="16" />
            {{ editSubmitting ? 'Сохраняем…' : 'Сохранить' }}
          </button>
        </div>
      </div>
    </AppModal>
  </section>
</template>
