<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BookOpen, Check, Plus, RefreshCw, Sparkles, X } from '@lucide/vue'
import {
  addAlias,
  approveGeneration,
  createGenerationJob,
  createSkill,
  listGenerationJobs,
  listReview,
  listSkills,
  rejectGeneration,
  resolveReview,
  setCanonical,
  type GenerationJob,
  type ReviewItem,
  type Skill,
} from '@/api/dictionary'
import { apiErrorMessage } from '@/api'
import { useToast } from '@/composables/useToast'

const { success, error: toastError } = useToast()
const tab = ref<'queue' | 'skills' | 'generation'>('queue')
const loading = ref(false)
const review = ref<ReviewItem[]>([])
const skills = ref<Skill[]>([])
const query = ref('')
const newSkill = ref('')
const aliasDrafts = ref<Record<string, string>>({})

const jobs = ref<GenerationJob[]>([])
const activeJob = ref<GenerationJob | null>(null)
const specialization = ref('')
const generationNote = ref('')
const maxSkills = ref(30)
const generating = ref(false)
const selectedSkills = ref<Record<string, boolean>>({})
const selectedAliases = ref<Record<string, boolean>>({})

const pending = computed(() => review.value.filter((item) => item.status === 'PENDING'))
const selectedCount = computed(
  () => Object.values(selectedSkills.value).filter(Boolean).length,
)

async function load() {
  loading.value = true
  try {
    if (tab.value === 'queue') {
      review.value = await listReview()
    } else if (tab.value === 'skills') {
      skills.value = await listSkills(query.value)
    } else {
      jobs.value = await listGenerationJobs()
      if (!activeJob.value && jobs.value.length > 0) {
        selectJob(jobs.value[0])
      }
    }
  } catch (exception) {
    toastError('Словарь недоступен', apiErrorMessage(exception))
  } finally {
    loading.value = false
  }
}

async function bind(item: ReviewItem, skillId: string) {
  try {
    await resolveReview(item.id, 'ALIAS', skillId)
    success('Алиас добавлен')
    await load()
  } catch (exception) {
    toastError('Не удалось привязать', apiErrorMessage(exception))
  }
}

async function createFromReview(item: ReviewItem) {
  try {
    await resolveReview(item.id, 'NEW_SKILL', undefined, item.samples[0] ?? item.key)
    success('Навык создан')
    await load()
  } catch (exception) {
    toastError('Не удалось создать навык', apiErrorMessage(exception))
  }
}

async function reject(item: ReviewItem) {
  try {
    await resolveReview(item.id, 'REJECT')
    success('Запись отклонена')
    await load()
  } catch (exception) {
    toastError('Не удалось отклонить', apiErrorMessage(exception))
  }
}

async function create() {
  if (!newSkill.value.trim()) return
  try {
    await createSkill(newSkill.value.trim())
    newSkill.value = ''
    success('Навык создан')
    await load()
  } catch (exception) {
    toastError('Не удалось создать навык', apiErrorMessage(exception))
  }
}

async function bindAlias(skill: Skill) {
  const raw = (aliasDrafts.value[skill.id] ?? '').trim()
  if (!raw) return
  try {
    await addAlias(skill.id, raw)
    aliasDrafts.value[skill.id] = ''
    success('Алиас добавлен')
    await load()
  } catch (exception) {
    toastError('Не удалось добавить алиас', apiErrorMessage(exception))
  }
}

async function makeCanonical(skill: Skill, aliasId: string) {
  try {
    await setCanonical(skill.id, aliasId)
    success('Канонический алиас изменён')
    await load()
  } catch (exception) {
    toastError('Не удалось изменить канон', apiErrorMessage(exception))
  }
}

function initSelection(job: GenerationJob) {
  const nextSkills: Record<string, boolean> = {}
  const nextAliases: Record<string, boolean> = {}
  for (const skill of job.skills) {
    nextSkills[skill.id] = !skill.conflictSkillId && skill.status === 'DRAFT'
    for (const alias of skill.aliases) {
      nextAliases[alias.id] = !alias.conflictSkillId && alias.status === 'DRAFT'
    }
  }
  selectedSkills.value = nextSkills
  selectedAliases.value = nextAliases
}

function selectJob(job: GenerationJob) {
  activeJob.value = job
  initSelection(job)
}

async function startGeneration() {
  if (!specialization.value.trim()) return
  generating.value = true
  try {
    const job = await createGenerationJob({
      specialization: specialization.value.trim(),
      note: generationNote.value.trim() || undefined,
      maxSkills: maxSkills.value,
    })
    activeJob.value = job
    initSelection(job)
    jobs.value = await listGenerationJobs()
    success('Черновики готовы', 'Проверьте навыки и алиасы перед подтверждением')
  } catch (exception) {
    toastError('Не удалось сгенерировать', apiErrorMessage(exception))
  } finally {
    generating.value = false
  }
}

async function approve() {
  if (!activeJob.value) return
  const accepted = activeJob.value.skills
    .filter((skill) => selectedSkills.value[skill.id] && !skill.conflictSkillId)
    .map((skill) => ({
      draftId: skill.id,
      canonical: skill.canonical,
      skillType: skill.skillType,
      targetSkillId: skill.targetSkillId,
      aliases: skill.aliases
        .filter((alias) => selectedAliases.value[alias.id] && !alias.conflictSkillId)
        .map((alias) => alias.text),
    }))
  if (accepted.length === 0) {
    toastError('Ничего не выбрано', 'Отметьте хотя бы один навык')
    return
  }
  try {
    const job = await approveGeneration(activeJob.value.id, accepted)
    activeJob.value = job
    initSelection(job)
    jobs.value = await listGenerationJobs()
    success('Навыки добавлены в словарь')
  } catch (exception) {
    toastError('Не удалось подтвердить', apiErrorMessage(exception))
  }
}

async function rejectJob() {
  if (!activeJob.value) return
  try {
    const job = await rejectGeneration(activeJob.value.id)
    activeJob.value = job
    jobs.value = await listGenerationJobs()
    success('Черновики отклонены')
  } catch (exception) {
    toastError('Не удалось отклонить', apiErrorMessage(exception))
  }
}

onMounted(load)
</script>

<template>
  <section class="space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 class="flex items-center gap-2 text-2xl font-semibold text-slate-900">
          <BookOpen :size="24" />
          Словарь навыков
        </h1>
        <p class="mt-1 text-sm text-slate-500">
          Новые написания, алиасы, канонические имена и генерация навыков по специальности.
        </p>
      </div>
      <button class="btn-secondary" :disabled="loading" @click="load">
        <RefreshCw :size="16" />
        Обновить
      </button>
    </header>

    <div class="inline-flex flex-wrap rounded-xl bg-slate-100 p-1" role="tablist">
      <button
        class="rounded-lg px-4 py-2 text-sm font-medium"
        :class="tab === 'queue' ? 'bg-white shadow-sm' : 'text-slate-600'"
        @click="tab = 'queue'; load()"
      >
        Очередь NEW ({{ pending.length }})
      </button>
      <button
        class="rounded-lg px-4 py-2 text-sm font-medium"
        :class="tab === 'skills' ? 'bg-white shadow-sm' : 'text-slate-600'"
        @click="tab = 'skills'; load()"
      >
        Навыки
      </button>
      <button
        class="rounded-lg px-4 py-2 text-sm font-medium"
        :class="tab === 'generation' ? 'bg-white shadow-sm' : 'text-slate-600'"
        @click="tab = 'generation'; load()"
      >
        Генерация
      </button>
    </div>

    <div v-if="loading" class="card text-sm text-slate-500">Загрузка…</div>

    <div v-else-if="tab === 'queue'" class="space-y-3">
      <div v-if="pending.length === 0" class="card text-sm text-slate-500">
        Очередь пуста: все новые навыки разобраны.
      </div>
      <article v-for="item in pending" :key="item.id" class="card space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-mono text-sm font-semibold text-slate-900">{{ item.key }}</span>
          <span class="badge-neutral">{{ item.occurrences }} вхождений</span>
          <span v-if="item.samples.length" class="text-xs text-slate-500">
            {{ item.samples.join(' · ') }}
          </span>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="candidate in item.candidates"
            :key="candidate.skillId"
            class="btn-secondary"
            @click="bind(item, candidate.skillId)"
          >
            <Check :size="14" />
            Похоже на {{ candidate.canonicalName }}
            ({{ Math.round(candidate.confidence * 100) }}%)
          </button>
          <button class="btn-secondary" @click="createFromReview(item)">
            <Plus :size="14" />
            Создать навык
          </button>
          <button class="btn-secondary text-rose-600" @click="reject(item)">
            <X :size="14" />
            Отклонить
          </button>
        </div>
      </article>
    </div>

    <div v-else-if="tab === 'skills'" class="space-y-4">
      <div class="flex flex-wrap gap-2">
        <input v-model="query" class="input" placeholder="Поиск по навыку или алиасу" @keyup.enter="load" />
        <input v-model="newSkill" class="input" placeholder="Новый навык" @keyup.enter="create" />
        <button class="btn-primary" @click="create">
          <Plus :size="16" />
          Создать
        </button>
      </div>

      <article v-for="skill in skills" :key="skill.id" class="card space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-semibold text-slate-900">{{ skill.canonicalName }}</span>
          <span class="badge-neutral">{{ skill.skillType }}</span>
          <span v-if="skill.status === 'DEPRECATED'" class="badge-warning">deprecated</span>
        </div>
        <div class="flex flex-wrap gap-1">
          <span
            v-for="alias in skill.aliases"
            :key="alias.id"
            class="badge-neutral"
            :class="alias.canonical ? 'badge-success' : ''"
          >
            {{ alias.raw }}
            <template v-if="alias.canonical"> · канон</template>
            <button
              v-if="!alias.canonical"
              class="ml-1 text-[10px] underline"
              @click="makeCanonical(skill, alias.id)"
            >
              сделать каноном
            </button>
          </span>
        </div>
        <div class="flex gap-2">
          <input
            v-model="aliasDrafts[skill.id]"
            class="input"
            placeholder="Новый алиас"
            @keyup.enter="bindAlias(skill)"
          />
          <button class="btn-secondary" @click="bindAlias(skill)">
            <Plus :size="14" />
            Добавить
          </button>
        </div>
      </article>
    </div>

    <div v-else class="space-y-4">
      <div class="card space-y-3">
        <div class="flex flex-wrap gap-2">
          <input
            v-model="specialization"
            class="input min-w-56 flex-1"
            placeholder="Специальность, например «электрик»"
            @keyup.enter="startGeneration"
          />
          <input v-model.number="maxSkills" type="number" min="1" max="50" class="input w-28" />
          <button class="btn-primary" :disabled="generating || !specialization.trim()" @click="startGeneration">
            <Sparkles :size="16" />
            {{ generating ? 'Генерируем…' : 'Сгенерировать' }}
          </button>
        </div>
        <input
          v-model="generationNote"
          class="input"
          placeholder="Примечание для LLM: регион, отрасль, что не нужно"
        />
        <p class="text-xs text-slate-500">
          LLM предложит навыки и алиасы. В словарь они попадут только после подтверждения.
        </p>
      </div>

      <div v-if="jobs.length" class="flex flex-wrap gap-2">
        <button
          v-for="job in jobs"
          :key="job.id"
          class="btn-secondary text-xs"
          :class="activeJob?.id === job.id ? 'ring-2 ring-indigo-300' : ''"
          @click="selectJob(job)"
        >
          {{ job.specialization }} · {{ job.status }} · {{ job.skills.length }}
        </button>
      </div>

      <article v-if="activeJob" class="card space-y-4">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-semibold text-slate-900">{{ activeJob.specialization }}</span>
          <span class="badge-neutral">{{ activeJob.status }}</span>
          <span v-if="activeJob.model" class="text-xs text-slate-500">{{ activeJob.model }}</span>
          <span class="ml-auto text-xs text-slate-500">
            выбрано навыков: {{ selectedCount }}
          </span>
        </div>
        <p v-if="activeJob.error" class="text-sm text-rose-600">{{ activeJob.error }}</p>

        <article
          v-for="skill in activeJob.skills"
          :key="skill.id"
          class="rounded-xl border border-slate-200 p-3"
        >
          <label class="flex flex-wrap items-center gap-2">
            <input
              v-model="selectedSkills[skill.id]"
              type="checkbox"
              :disabled="!!skill.conflictSkillId || skill.status !== 'DRAFT'"
            />
            <span class="font-medium text-slate-900">{{ skill.canonical }}</span>
            <span class="badge-neutral">{{ skill.skillType }}</span>
            <span v-if="skill.targetSkillName" class="badge-success">
              к существующему: {{ skill.targetSkillName }}
            </span>
            <span v-if="skill.conflictSkillName" class="badge-warning">
              конфликт: {{ skill.conflictSkillName }}
            </span>
          </label>
          <div class="mt-2 flex flex-wrap gap-2">
            <label
              v-for="alias in skill.aliases"
              :key="alias.id"
              class="flex items-center gap-1 rounded-lg bg-slate-50 px-2 py-1 text-xs"
              :class="alias.conflictSkillName ? 'text-rose-600' : 'text-slate-700'"
            >
              <input
                v-model="selectedAliases[alias.id]"
                type="checkbox"
                :disabled="!!alias.conflictSkillId || alias.status !== 'DRAFT'"
              />
              {{ alias.text }}
              <span v-if="alias.conflictSkillName">↔ {{ alias.conflictSkillName }}</span>
            </label>
          </div>
        </article>

        <div class="flex flex-wrap gap-2">
          <button class="btn-primary" :disabled="selectedCount === 0" @click="approve">
            <Check :size="16" />
            Подтвердить выбранное
          </button>
          <button class="btn-secondary text-rose-600" @click="rejectJob">
            <X :size="16" />
            Отклонить черновик
          </button>
        </div>
      </article>
    </div>
  </section>
</template>
