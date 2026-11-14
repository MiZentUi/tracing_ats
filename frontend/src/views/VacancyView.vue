<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Copy, ExternalLink, Loader2, Pencil, RefreshCcw, Send, Users } from '@lucide/vue'
import type {
  HhPublication,
  RankingItem,
  RankingResponse,
  Specialization,
  Vacancy,
} from '@/api/generated'
import { apiErrorMessage, hhApi, rankingApi, referenceApi, vacanciesApi } from '@/api'
import MarkdownText from '@/components/MarkdownText.vue'
import { useToast } from '@/composables/useToast'
import { decisionLabel } from '@/lib/labels'
import { pluralizeRu, requirementsCount, resumeDisplayTitle } from '@/lib/format'
import { levelLabel } from '@/types'

const props = defineProps<{ id: string }>()
const { success, error: toastError } = useToast()

const vacancy = ref<Vacancy | null>(null)
const finalRanking = ref<RankingResponse | null>(null)
const preliminaryRanking = ref<RankingResponse | null>(null)
const specializations = ref<Specialization[]>([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const hhPublication = ref<HhPublication | null>(null)
const publishingToHh = ref(false)
const hhError = ref('')

const candidates = computed(() => {
  const byResume = new Map<string, RankingItem>()
  for (const item of preliminaryRanking.value?.items ?? []) {
    const key = item.resumeId ?? item.applicationId ?? `rank-${item.rank}`
    const existing = byResume.get(key)
    if (!existing || (!existing.applicationId && item.applicationId)) {
      byResume.set(key, item)
    }
  }
  for (const item of finalRanking.value?.items ?? []) {
    const key = item.resumeId ?? item.applicationId ?? `rank-${item.rank}`
    byResume.set(key, item)
  }
  return [...byResume.values()].sort((left, right) => right.score - left.score)
})

const sourceText = computed(() => vacancy.value?.source_text ?? '')

const hhStatusLabel = computed(() => {
  switch (hhPublication.value?.status) {
    case 'PUBLISHED':
      return 'Опубликована'
    case 'PENDING':
      return 'Публикуется'
    case 'FAILED':
      return 'Ошибка публикации'
    case 'ARCHIVED':
      return 'Снята с публикации'
    default:
      return 'Не опубликована'
  }
})

async function publishToHh() {
  if (publishingToHh.value) {
    return
  }
  publishingToHh.value = true
  hhError.value = ''
  try {
    const response = await hhApi.publishVacancyToHh({ vacancyId: props.id })
    hhPublication.value = response.data
    if (response.data.status === 'PUBLISHED') {
      success('Вакансия опубликована на hh.ru')
    } else {
      hhError.value = 'Публикация не завершена. Проверьте статус позже.'
    }
  } catch (e) {
    hhError.value = apiErrorMessage(e)
    toastError('Не удалось опубликовать на hh.ru', hhError.value)
  } finally {
    publishingToHh.value = false
  }
}

const specializationName = computed(
  () =>
    specializations.value.find((item) => item.code === vacancy.value?.specialization)?.name ??
    vacancy.value?.specialization ??
    '—',
)

function candidateTitle(item: RankingItem): string {
  if (item.resumeTitle?.trim()) {
    return item.resumeTitle
  }
  if (item.resumeCreatedAt) {
    return resumeDisplayTitle(null, item.resumeCreatedAt)
  }
  return 'Резюме кандидата'
}

function isFinal(item: RankingItem): boolean {
  const key = item.resumeId ?? item.applicationId
  return (finalRanking.value?.items ?? []).some(
    (candidate) => (candidate.resumeId ?? candidate.applicationId) === key,
  )
}

function reportLink(item: RankingItem): string | null {
  if (item.applicationId) {
    const phase = isFinal(item) ? '' : '?phase=PRELIMINARY'
    return `/applications/${item.applicationId}/report${phase}`
  }
  if (item.resumeId) {
    return `/resumes/${item.resumeId}/vacancies/${props.id}/report`
  }
  return null
}

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

async function load(initial = true) {
  if (initial) {
    loading.value = true
  } else {
    refreshing.value = true
  }
  error.value = ''
  try {
    const vacancyResponse = await vacanciesApi.getVacancy({ id: props.id })
    vacancy.value = vacancyResponse.data
    const [finalResponse, preliminaryResponse] = await Promise.all([
      rankingApi.getRanking({ vacancyId: props.id, phase: 'FINAL' }).catch(() => null),
      rankingApi.getRanking({ vacancyId: props.id, phase: 'PRELIMINARY' }).catch(() => null),
    ])
    finalRanking.value = finalResponse?.data ?? null
    preliminaryRanking.value = preliminaryResponse?.data ?? null
    try {
      const hhResponse = await hhApi.getHhPublication({ vacancyId: props.id })
      hhPublication.value = hhResponse.data
    } catch {
      hhPublication.value = null
    }
    try {
      const specs = await referenceApi.listSpecializations()
      specializations.value = specs.data
    } catch {
      // Без справочника показываем код специализации.
    }
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function refreshCandidates() {
  await load(false)
  success('Подбор обновлён')
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

onMounted(() => load())
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
        <button class="btn-secondary shrink-0" @click="load()">Повторить</button>
      </div>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <div class="space-y-5">
          <article class="card p-5">
            <dl class="grid gap-4 text-sm sm:grid-cols-3">
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Специализация</dt>
                <dd class="mt-1 font-medium text-slate-900">{{ specializationName }}</dd>
              </div>
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Уровень</dt>
                <dd class="mt-1 font-medium text-slate-900">{{ levelLabel(vacancy.level) }}</dd>
              </div>
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Требуемый стаж</dt>
                <dd class="mt-1 font-medium text-slate-900">
                  {{
                    vacancy.experience_years == null
                      ? 'Не указан'
                      : `${vacancy.experience_years} ${pluralizeRu(vacancy.experience_years, 'год', 'года', 'лет')}`
                  }}
                </dd>
              </div>
              <div>
                <dt class="text-xs uppercase tracking-wide text-slate-400">Требований</dt>
                <dd class="mt-1 font-medium text-slate-900">
                  {{ requirementsCount(vacancy.template.requirements.length) }}
                </dd>
              </div>
            </dl>
          </article>

          <article v-if="!vacancy.template.is_confirmed" class="card p-5">
            <h2 class="text-base font-semibold text-slate-900">Шаблон ещё не подтверждён</h2>
            <p class="mt-1 text-sm text-slate-500">
              Сейчас это черновик: требования {{ requirementsCount(vacancy.template.requirements.length) }}.
              Проверьте их и разложите по важности — после подтверждения появится публикация на hh.ru.
            </p>
            <RouterLink :to="`/vacancies/${props.id}/edit`" class="btn-primary mt-4">
              Разложить требования и подтвердить
            </RouterLink>
          </article>

          <article v-else class="card p-5">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 class="text-base font-semibold text-slate-900">hh.ru</h2>
                <p class="mt-1 text-sm text-slate-500">{{ hhStatusLabel }}</p>
              </div>
              <a
                v-if="hhPublication?.hh_url"
                :href="hhPublication.hh_url"
                target="_blank"
                rel="noreferrer"
                class="btn-secondary px-3 py-1.5 text-xs"
              >
                <ExternalLink :size="14" />
                Открыть на hh.ru
              </a>
            </div>
            <p v-if="hhPublication?.hh_vacancy_id" class="mt-3 text-xs text-slate-400">
              hh vacancy id: {{ hhPublication.hh_vacancy_id }}
            </p>
            <p v-if="hhError" class="mt-3 text-sm text-red-700">{{ hhError }}</p>
            <button
              v-if="hhPublication?.status !== 'PUBLISHED'"
              class="btn-primary mt-4"
              :disabled="publishingToHh"
              @click="publishToHh"
            >
              <Loader2 v-if="publishingToHh" :size="16" class="animate-spin" />
              <Send v-else :size="16" />
              {{ publishingToHh ? 'Публикуем…' : 'Опубликовать на hh.ru' }}
            </button>
          </article>

          <article class="card">
            <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
              <h2 class="text-base font-semibold text-slate-900">Текст вакансии</h2>
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

        <aside class="lg:sticky lg:top-24">
          <article class="card overflow-hidden">
            <div class="border-b border-slate-100 px-4 py-4">
              <div class="flex items-center justify-between gap-2">
                <h2 class="flex items-center gap-2 text-base font-semibold text-slate-900">
                  <Users :size="17" class="text-indigo-600" />
                  Кандидаты
                </h2>
                <span v-if="candidates.length" class="badge-neutral">{{ candidates.length }}</span>
              </div>
              <button
                class="btn-primary mt-3 w-full"
                :disabled="refreshing"
                @click="refreshCandidates"
              >
                <Loader2 v-if="refreshing" :size="16" class="animate-spin" />
                <RefreshCcw v-else :size="16" />
                Подобрать кандидатов
              </button>
            </div>

            <div v-if="!candidates.length" class="px-4 py-8 text-center text-sm text-slate-500">
              <p>Пока нет подходящих резюме.</p>
              <p class="mt-1 text-xs text-slate-400">
                Добавьте резюме в разделе «Резюме» — подбор появится автоматически.
              </p>
            </div>

            <ul v-else class="divide-y divide-slate-100">
              <li v-for="(item, index) in candidates" :key="item.resumeId ?? item.applicationId ?? index" class="px-4 py-3">
                <div class="flex items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="flex items-center gap-2 truncate text-sm font-semibold text-slate-900">
                      <span class="truncate">{{ candidateTitle(item) }}</span>
                      <span
                        v-if="item.source === 'HH'"
                        class="shrink-0 rounded-md bg-red-50 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-red-700"
                        title="Отклик пришёл через вебхук hh.ru"
                      >
                        hh.ru
                      </span>
                    </p>
                    <p class="mt-0.5 text-xs text-slate-500">
                      Скор {{ item.score.toFixed(1) }}
                    </p>
                  </div>
                  <span class="shrink-0 text-xs font-semibold text-slate-400">#{{ index + 1 }}</span>
                </div>
                <div class="mt-2 flex items-center justify-between gap-2">
                  <span
                    v-if="item.decision"
                    class="rounded-md px-2 py-0.5 text-[11px] font-medium"
                    :class="decisionClass(item.decision)"
                  >
                    {{ decisionLabel(item.decision) }}
                  </span>
                  <span v-else />
                  <RouterLink
                    v-if="reportLink(item)"
                    :to="reportLink(item) ?? '/'"
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
  </section>
</template>
