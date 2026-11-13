<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BookOpen, Check, Plus, RefreshCw, X } from '@lucide/vue'
import {
  addAlias,
  createSkill,
  listReview,
  listSkills,
  resolveReview,
  type ReviewItem,
  type Skill,
} from '@/api/dictionary'
import { apiErrorMessage } from '@/api'
import { useToast } from '@/composables/useToast'

const { success, error: toastError } = useToast()
const tab = ref<'queue' | 'skills'>('queue')
const loading = ref(false)
const review = ref<ReviewItem[]>([])
const skills = ref<Skill[]>([])
const query = ref('')
const newSkill = ref('')
const aliasDrafts = ref<Record<string, string>>({})

const pending = computed(() => review.value.filter((item) => item.status === 'PENDING'))

async function load() {
  loading.value = true
  try {
    if (tab.value === 'queue') {
      review.value = await listReview()
    } else {
      skills.value = await listSkills(query.value)
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
          Новые написания, алиасы и канонические имена. Навык попадает в скоринг только после
          подтверждения.
        </p>
      </div>
      <button class="btn-secondary" :disabled="loading" @click="load">
        <RefreshCw :size="16" />
        Обновить
      </button>
    </header>

    <div class="inline-flex rounded-xl bg-slate-100 p-1" role="tablist">
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

    <div v-else class="space-y-4">
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
  </section>
</template>
