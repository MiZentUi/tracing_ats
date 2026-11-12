<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Eye, EyeOff, Loader2 } from '@lucide/vue'
import type { ResumeFullText, ResumeText, Vacancy } from '@/api/generated'
import { apiErrorMessage, resumesApi, vacanciesApi } from '@/api'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const { error: toastError } = useToast()

const resumeId = computed(() => String(route.query.resumeId ?? ''))
const vacancyId = computed(() => String(route.query.vacancyId ?? ''))
const applicationId = computed(() =>
  route.query.applicationId ? String(route.query.applicationId) : undefined,
)
const backTo = computed(() => (route.query.from ? String(route.query.from) : ''))

const vacancy = ref<Vacancy | null>(null)
const resume = ref<ResumeText | null>(null)
const revealed = ref<ResumeFullText | null>(null)
const loading = ref(true)
const revealing = ref(false)
const error = ref('')

const resumeText = computed(() => revealed.value?.text ?? resume.value?.maskedText ?? '')

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
          >{{ vacancy.source_text }}</pre>
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
          >{{ resumeText }}</pre>
          <p v-else class="text-sm text-slate-500">Текст резюме недоступен.</p>
        </div>
      </article>
    </div>
  </section>
</template>
