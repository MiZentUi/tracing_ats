<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  Copy,
  LoaderCircle,
  Plus,
  Save,
  Search,
  Sparkles,
  Trash2,
} from 'lucide-vue-next'
import type { ReferenceItem, SkillKind, Vacancy } from '@/api/generated'
import { apiErrorCode, apiErrorMessage, referenceApi, vacanciesApi } from '@/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import RequirementCard from '@/components/RequirementCard.vue'
import { useToast } from '@/composables/useToast'
import { kindSections, levelLabel, toEditable, type EditableRequirement } from '@/types'

const props = defineProps<{ id: string }>()
const router = useRouter()
const { success, info, error: toastError } = useToast()

const vacancy = ref<Vacancy | null>(null)
const requirements = ref<EditableRequirement[]>([])
const title = ref('')
const specialization = ref('')
const level = ref('none')
const specializations = ref<ReferenceItem[]>([])
const levels = ref<ReferenceItem[]>([])
const snapshot = ref('')
const loading = ref(true)
const saving = ref(false)
const deleting = ref(false)
const deleteOpen = ref(false)
const error = ref('')
const conflict = ref(false)
const savedMessage = ref('')
const sourceSearch = ref('')
const highlightedKey = ref<string | null>(null)

const canConfirmDraft = computed(
  () => !!vacancy.value && !vacancy.value.template.is_confirmed && !saving.value && !deleting.value,
)

const sourceText = computed(() => vacancy.value?.source_text ?? '')
const sourceChars = computed(() => sourceText.value.length.toLocaleString('ru-RU'))

const highlightedSource = computed(() => {
  const query = sourceSearch.value.trim()
  if (!query || !sourceText.value) {
    return [{ text: sourceText.value, match: false }]
  }
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const parts = sourceText.value.split(new RegExp(`(${escaped})`, 'gi'))
  return parts.map((text) => ({ text, match: text.toLowerCase() === query.toLowerCase() }))
})

function serialize() {
  return JSON.stringify({
    title: title.value,
    specialization: specialization.value,
    level: level.value,
    requirements: requirements.value.map((item) => ({
      id: item.id,
      skill: item.skill,
      kind: item.kind,
      yearsRequired: item.yearsRequired,
      quote: item.quote,
      clarityWarning: item.clarityWarning,
    })),
  })
}

const dirty = computed(() => snapshot.value !== '' && serialize() !== snapshot.value)

function apply(v: Vacancy) {
  vacancy.value = v
  title.value = v.title ?? ''
  specialization.value = v.specialization
  level.value = v.level
  requirements.value = v.template.requirements.map(toEditable)
  snapshot.value = serialize()
}

async function load() {
  loading.value = true
  error.value = ''
  conflict.value = false
  try {
    const [vacancyResponse, specs, levelList] = await Promise.all([
      vacanciesApi.getVacancy({ id: props.id }),
      referenceApi.listSpecializations(),
      referenceApi.listLevels(),
    ])
    apply(vacancyResponse.data)
    specializations.value = specs.data
    levels.value = levelList.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function requirementsFor(kind: SkillKind) {
  return requirements.value.filter((item) => item.kind === kind)
}

function normalizedSkill(skill: string | null) {
  return (skill ?? '').trim().toLowerCase()
}

const duplicateKeys = computed(() => {
  const counts = new Map<string, number>()
  for (const item of requirements.value) {
    const key = normalizedSkill(item.skill)
    if (key !== '') {
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
  }
  return new Set([...counts.entries()].filter(([, count]) => count > 1).map(([key]) => key))
})

async function revealRequirement(key: string, focusSkill = false) {
  highlightedKey.value = key
  await nextTick()
  const element = document.querySelector(`[data-requirement-key="${key}"]`)
  element?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  if (focusSkill) {
    const input = element?.querySelector<HTMLInputElement>('input[aria-label="Название навыка"]')
    input?.focus({ preventScroll: true })
  }
  window.setTimeout(() => {
    if (highlightedKey.value === key) {
      highlightedKey.value = null
    }
  }, 1800)
}

async function updateRequirement(key: string, patch: Partial<EditableRequirement>) {
  const item = requirements.value.find((candidate) => candidate.key === key)
  if (!item) {
    return
  }
  const previousKind = item.kind
  Object.assign(item, patch)

  if (patch.kind && patch.kind !== previousKind) {
    const target = kindSections.find((section) => section.kind === patch.kind)
    const name = item.skill?.trim() || item.quote?.slice(0, 40) || 'Требование'
    info('Тип изменён', `«${name}» перенесено в «${target?.title ?? patch.kind}»`)
    await revealRequirement(key)
  }
}

function removeRequirement(key: string) {
  requirements.value = requirements.value.filter((item) => item.key !== key)
}

async function addRequirement() {
  const key = `new-${Date.now()}-${requirements.value.length}`
  requirements.value.push({
    key,
    id: null,
    skill: '',
    skillId: null,
    normalizationStatus: 'NEW',
    kind: 'MUST',
    kindSource: 'HR',
    llmKind: null,
    yearsRequired: null,
    quote: null,
    clarityWarning: null,
    history: null,
  })
  info('Добавлено требование', 'Заполните название навыка')
  await revealRequirement(key, true)
}

async function save() {
  if (!vacancy.value || saving.value) {
    return
  }
  if (requirements.value.length === 0) {
    error.value = 'Нужно хотя бы одно требование'
    return
  }
  if (requirements.value.some((item) => !item.skill?.trim() && !item.quote)) {
    error.value = 'У требования должен быть навык или цитата'
    return
  }
  const wasDraft = !vacancy.value.template.is_confirmed
  saving.value = true
  error.value = ''
  savedMessage.value = ''
  conflict.value = false
  try {
    const response = await vacanciesApi.updateVacancyTemplate({
      id: props.id,
      updateTemplateRequest: {
        base_version: vacancy.value.template.version,
        specialization: specialization.value,
        level: level.value,
        title: title.value.trim() || null,
        requirements: requirements.value.map((item) => ({
          id: item.id,
          skill: item.skill?.trim() || null,
          kind: item.kind,
          years_required: item.yearsRequired,
          quote: item.quote,
          clarity_warning: item.clarityWarning,
        })),
      },
    })
    apply(response.data)
    savedMessage.value = `Сохранено, версия ${response.data.template.version}`
    success(
      wasDraft ? 'Черновик подтверждён' : 'Шаблон сохранён',
      `Версия ${response.data.template.version} · ${response.data.template.requirements.length} требований`,
    )
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    conflict.value = apiErrorCode(e) === 'VERSION_CONFLICT'
    toastError('Не удалось сохранить шаблон', message)
  } finally {
    saving.value = false
  }
}

async function confirmDeleteVacancy() {
  if (deleting.value) {
    return
  }
  deleting.value = true
  try {
    await vacanciesApi.deleteVacancy({ id: props.id })
    deleteOpen.value = false
    success('Вакансия удалена', 'Её вклад убран из истории типов')
    await router.push('/vacancies')
  } catch (e) {
    toastError('Не удалось удалить вакансию', apiErrorMessage(e))
  } finally {
    deleting.value = false
  }
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

onBeforeRouteLeave(() => {
  if (dirty.value && !saving.value && !deleting.value) {
    return window.confirm('Есть несохранённые изменения. Перейти без сохранения?')
  }
  return true
})

onMounted(load)
</script>

<template>
  <section class="space-y-5 pb-4">
    <div>
      <RouterLink to="/vacancies" class="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 transition hover:text-slate-900">
        <ArrowLeft :size="16" />
        К списку вакансий
      </RouterLink>
    </div>

    <div v-if="loading" class="card space-y-4 p-6">
      <div class="skeleton h-8 w-1/2" />
      <div class="skeleton h-4 w-1/3" />
      <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div class="space-y-4">
          <div class="skeleton h-28 w-full" />
          <div class="skeleton h-40 w-full" />
          <div class="skeleton h-40 w-full" />
        </div>
        <div class="skeleton h-80 w-full" />
      </div>
    </div>

    <template v-else-if="vacancy">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="truncate text-3xl font-semibold tracking-tight text-slate-900">
              {{ title || 'Без названия' }}
            </h1>
            <span :class="vacancy.template.is_confirmed ? 'badge-success' : 'badge-warning'">
              <CheckCircle2 v-if="vacancy.template.is_confirmed" :size="12" />
              {{ vacancy.template.is_confirmed ? 'Подтверждена' : 'Черновик' }}
            </span>
            <span class="badge-neutral">Версия {{ vacancy.template.version }}</span>
          </div>
          <p class="mt-1 text-sm text-slate-500">
            {{ vacancy.specialization }} · {{ levelLabel(vacancy.level) }} ·
            {{ vacancy.template.meta?.model ?? 'модель не указана' }} ·
            {{ vacancy.template.meta?.prompt_version ?? 'промпт не указан' }}
          </p>
        </div>
      </div>

      <div v-if="savedMessage" class="flex items-center gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
        <CheckCircle2 :size="16" />
        {{ savedMessage }}
      </div>

      <div v-if="error" class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
        <span class="flex items-center gap-2">
          <AlertTriangle :size="16" />
          {{ error }}
        </span>
        <button v-if="conflict" class="btn-secondary shrink-0" @click="load">Перезагрузить</button>
      </div>

      <div v-if="duplicateKeys.size > 0" class="flex items-center gap-2 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
        <AlertTriangle :size="16" />
        Найдены дубликаты навыков. Исправьте их, чтобы сохранить шаблон.
      </div>

      <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_24rem] lg:items-start">
        <div class="space-y-4">
          <div class="card p-5">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="text-base font-semibold text-slate-900">Параметры шаблона</h2>
                <p class="text-sm text-slate-500">Смена специализации или уровня создаст новую версию.</p>
              </div>
            </div>
            <div class="mt-4 grid gap-4 sm:grid-cols-2">
              <div class="sm:col-span-2">
                <label class="label" for="editor-title">Название</label>
                <input id="editor-title" v-model="title" class="input" />
              </div>
              <div>
                <label class="label" for="editor-specialization">Специализация</label>
                <select id="editor-specialization" v-model="specialization" class="input">
                  <option v-for="item in specializations" :key="item.code" :value="item.code">{{ item.name }}</option>
                </select>
              </div>
              <div>
                <label class="label" for="editor-level">Уровень</label>
                <select id="editor-level" v-model="level" class="input">
                  <option value="none">Без уровня</option>
                  <option v-for="item in levels" :key="item.code" :value="item.code">{{ item.name }}</option>
                </select>
              </div>
            </div>
          </div>

          <div
            v-for="section in kindSections"
            :key="section.kind"
            :data-section="section.kind"
            class="card overflow-hidden"
          >
            <div class="flex items-center gap-2 border-b border-slate-100 px-5 py-3">
              <span
                class="h-2.5 w-2.5 rounded-full"
                :class="section.kind === 'MUST' ? 'bg-rose-400' : section.kind === 'NICE' ? 'bg-indigo-400' : 'bg-emerald-400'"
              />
              <h2 class="text-base font-semibold text-slate-900">{{ section.title }}</h2>
              <span class="badge-neutral">{{ requirementsFor(section.kind).length }}</span>
            </div>
            <div class="space-y-3 p-4">
              <p v-if="requirementsFor(section.kind).length === 0" class="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-400">
                {{ section.hint }}
              </p>
              <div
                v-for="item in requirementsFor(section.kind)"
                :key="item.key"
                :data-requirement-key="item.key"
                class="rounded-2xl transition-all duration-300"
                :class="highlightedKey === item.key ? 'ring-2 ring-indigo-300 ring-offset-2' : ''"
              >
                <RequirementCard
                  :requirement="item"
                  :duplicate="duplicateKeys.has(normalizedSkill(item.skill))"
                  @update="(patch) => updateRequirement(item.key, patch)"
                  @remove="removeRequirement(item.key)"
                />
              </div>
            </div>
          </div>

          <button type="button" class="btn-secondary w-full border-dashed" @click="addRequirement">
            <Plus :size="16" />
            Добавить требование
          </button>
        </div>

        <aside class="card order-first overflow-hidden lg:order-none lg:sticky lg:top-24">
          <div class="border-b border-slate-100 px-4 py-3">
            <div class="flex items-center justify-between gap-2">
              <h2 class="text-sm font-semibold text-slate-900">Текст вакансии</h2>
              <button class="icon-btn" title="Скопировать текст" @click="copySource">
                <Copy :size="15" />
              </button>
            </div>
            <p class="mt-0.5 text-xs text-slate-400">{{ sourceChars }} символов</p>
            <label class="relative mt-3 block">
              <Search :size="14" class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input v-model="sourceSearch" class="input py-2 pl-8 text-xs" placeholder="Найти в тексте" />
            </label>
          </div>
          <div
            data-testid="vacancy-source-text"
            class="max-h-72 overflow-auto whitespace-pre-wrap px-4 py-3 text-sm leading-6 text-slate-700 lg:max-h-[68vh]"
          >
            <template v-if="sourceText">
              <template v-for="(part, index) in highlightedSource" :key="index">
                <mark v-if="part.match" class="rounded bg-amber-200 px-0.5 text-slate-900">{{ part.text }}</mark>
                <span v-else>{{ part.text }}</span>
              </template>
            </template>
            <span v-else class="text-slate-400">Текст вакансии недоступен</span>
          </div>
        </aside>
      </div>

      <div class="sticky bottom-3 z-20">
        <div class="card flex flex-wrap items-center gap-2 p-3 shadow-xl backdrop-blur">
          <span class="mr-auto flex items-center gap-2 px-1 text-xs font-medium" :class="dirty ? 'text-amber-700' : 'text-slate-400'">
            <span class="h-2 w-2 rounded-full" :class="dirty ? 'bg-amber-400' : 'bg-slate-300'" />
            {{ dirty ? 'Есть несохранённые изменения' : 'Изменений нет' }}
          </span>
          <button class="btn-danger" :disabled="deleting || saving" @click="deleteOpen = true">
            <Trash2 :size="16" />
            Удалить
          </button>
          <button
            v-if="canConfirmDraft"
            class="btn-success"
            :disabled="saving || duplicateKeys.size > 0"
            @click="save"
          >
            <Sparkles :size="16" />
            {{ saving ? 'Подтверждение…' : 'Подтвердить черновик' }}
          </button>
          <button
            class="btn-primary"
            :disabled="!dirty || saving || duplicateKeys.size > 0"
            @click="save"
          >
            <LoaderCircle v-if="saving" :size="16" class="animate-spin" />
            <Save v-else :size="16" />
            {{ saving ? 'Сохранение…' : 'Сохранить шаблон' }}
          </button>
        </div>
      </div>
    </template>

    <ConfirmDialog
      :open="deleteOpen"
      title="Удалить вакансию?"
      message="Вакансия будет удалена вместе с версиями требований. Её вклад исчезнет из истории типов. Это действие необратимо."
      confirm-label="Удалить"
      danger
      @cancel="deleteOpen = false"
      @confirm="confirmDeleteVacancy"
    />
  </section>
</template>
