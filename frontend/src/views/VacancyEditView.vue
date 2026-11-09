<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import type { ReferenceItem, SkillKind, Vacancy } from '@/api/generated'
import { apiErrorCode, apiErrorMessage, referenceApi, vacanciesApi } from '@/api'
import RequirementCard from '@/components/RequirementCard.vue'
import { kindSections, toEditable, type EditableRequirement } from '@/types'

const props = defineProps<{ id: string }>()
const router = useRouter()

const vacancy = ref<Vacancy | null>(null)
const requirements = ref<EditableRequirement[]>([])
const specialization = ref('')
const level = ref('')
const title = ref('')
const specializations = ref<ReferenceItem[]>([])
const levels = ref<ReferenceItem[]>([])
const snapshot = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const conflict = ref(false)
const savedMessage = ref('')

function serialize() {
  return JSON.stringify({
    specialization: specialization.value,
    level: level.value,
    title: title.value,
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
  specialization.value = v.specialization
  level.value = v.level
  title.value = v.title ?? ''
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

function updateRequirement(key: string, patch: Partial<EditableRequirement>) {
  const item = requirements.value.find((candidate) => candidate.key === key)
  if (item) {
    Object.assign(item, patch)
  }
}

function removeRequirement(key: string) {
  requirements.value = requirements.value.filter((item) => item.key !== key)
}

function addRequirement() {
  requirements.value.push({
    key: `new-${Date.now()}-${requirements.value.length}`,
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
  } catch (e) {
    error.value = apiErrorMessage(e)
    conflict.value = apiErrorCode(e) === 'VERSION_CONFLICT'
  } finally {
    saving.value = false
  }
}

onBeforeRouteLeave(() => {
  if (dirty.value && !saving.value) {
    return window.confirm('Есть несохранённые изменения. Перейти без сохранения?')
  }
  return true
})

onMounted(load)
</script>

<template>
  <section class="space-y-4">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 class="text-2xl font-semibold">Редактор шаблона</h1>
        <p v-if="vacancy" class="text-sm text-slate-500">
          Версия {{ vacancy.template.version }} ·
          <span
            class="rounded px-2 py-1 text-xs"
            :class="vacancy.template.is_confirmed ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'"
          >
            {{ vacancy.template.is_confirmed ? 'Подтверждён' : 'Черновик, не подтверждён' }}
          </span>
        </p>
      </div>
      <div class="flex gap-2">
        <button class="rounded border border-slate-300 px-3 py-2 text-sm" @click="router.push('/vacancies')">
          К списку
        </button>
        <button
          class="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!dirty || saving || duplicateKeys.size > 0"
          @click="save"
        >
          {{ saving ? 'Сохранение…' : 'Сохранить шаблон' }}
        </button>
      </div>
    </div>

    <p v-if="savedMessage" class="rounded bg-emerald-50 p-3 text-emerald-800">{{ savedMessage }}</p>
    <div v-if="error" class="rounded bg-red-50 p-3 text-red-700">
      {{ error }}
      <button v-if="conflict" class="ml-3 underline" @click="load">Перезагрузить</button>
    </div>

    <p v-if="loading" class="text-slate-500">Загрузка…</p>

    <template v-else-if="vacancy">
      <div class="grid gap-4 rounded border border-slate-200 bg-white p-5 sm:grid-cols-2">
        <label class="text-sm">
          Название
          <input v-model="title" class="mt-1 w-full rounded border border-slate-300 px-3 py-2" />
        </label>
        <label class="text-sm">
          Специализация
          <select v-model="specialization" class="mt-1 w-full rounded border border-slate-300 px-3 py-2">
            <option v-for="item in specializations" :key="item.code" :value="item.code">{{ item.name }}</option>
          </select>
        </label>
        <label class="text-sm">
          Уровень
          <select v-model="level" class="mt-1 w-full rounded border border-slate-300 px-3 py-2">
            <option v-for="item in levels" :key="item.code" :value="item.code">{{ item.name }}</option>
          </select>
        </label>
        <p class="self-end text-xs text-slate-500">
          Модель: {{ vacancy.template.meta?.model ?? '—' }} · промпт:
          {{ vacancy.template.meta?.prompt_version ?? '—' }}
        </p>
      </div>

      <div v-for="section in kindSections" :key="section.kind" :data-section="section.kind" class="space-y-2">
        <h2 class="text-lg font-semibold">{{ section.title }}</h2>
        <p v-if="requirementsFor(section.kind).length === 0" class="rounded border border-dashed border-slate-300 p-4 text-sm text-slate-500">
          {{ section.hint }}
        </p>
        <RequirementCard
          v-for="item in requirementsFor(section.kind)"
          :key="item.key"
          :requirement="item"
          :duplicate="duplicateKeys.has(normalizedSkill(item.skill))"
          @update="(patch) => updateRequirement(item.key, patch)"
          @remove="removeRequirement(item.key)"
        />
      </div>

      <button class="rounded border border-slate-300 px-4 py-2 text-sm" @click="addRequirement">
        Добавить требование
      </button>
    </template>
  </section>
</template>
