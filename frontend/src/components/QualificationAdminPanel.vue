<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Loader2, Plus, Search, ShieldCheck } from '@lucide/vue'
import {
  addQualificationAlias,
  createQualification,
  deleteQualificationAlias,
  listQualifications,
  setCanonicalQualificationAlias,
  type Qualification,
} from '@/api/dictionary'
import { apiErrorMessage } from '@/api'
import AppModal from '@/components/AppModal.vue'
import { useToast } from '@/composables/useToast'
import { pluralizeRu } from '@/lib/format'

const { success, error: toastError } = useToast()

const items = ref<Qualification[]>([])
const loading = ref(true)
const searching = ref(false)
const error = ref('')
const query = ref('')
const status = ref('ACTIVE')
const aliasDrafts = ref<Record<string, string>>({})

const createOpen = ref(false)
const newName = ref('')
const newType = ref('OTHER')
const createError = ref('')
const creating = ref(false)

const typeLabels: Record<string, string> = {
  PERMIT: 'Допуск',
  LICENSE: 'Лицензия',
  ATTESTATION: 'Аттестат',
  CATEGORY: 'Категория',
  OTHER: 'Другое',
}

function typeLabel(value: string): string {
  return typeLabels[value] ?? value
}

function aliasCount(count: number): string {
  return `${count} ${pluralizeRu(count, 'алиас', 'алиаса', 'алиасов')}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await listQualifications(query.value, status.value)
  } catch (exception) {
    error.value = apiErrorMessage(exception)
    toastError('Не удалось загрузить квалификации', error.value)
  } finally {
    loading.value = false
  }
}

async function search() {
  searching.value = true
  try {
    await load()
  } finally {
    searching.value = false
  }
}

function openCreate() {
  newName.value = ''
  newType.value = 'OTHER'
  createError.value = ''
  createOpen.value = true
}

async function submitCreate() {
  const name = newName.value.trim()
  if (!name || creating.value) {
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await createQualification(name, newType.value)
    createOpen.value = false
    success('Квалификация создана')
    await load()
  } catch (exception) {
    createError.value = apiErrorMessage(exception)
  } finally {
    creating.value = false
  }
}

async function bindAlias(item: Qualification) {
  const raw = (aliasDrafts.value[item.id] ?? '').trim()
  if (!raw) {
    return
  }
  try {
    await addQualificationAlias(item.id, raw)
    aliasDrafts.value[item.id] = ''
    success('Алиас добавлен')
    await load()
  } catch (exception) {
    toastError('Не удалось добавить алиас', apiErrorMessage(exception))
  }
}

async function makeCanonical(item: Qualification, aliasId: string) {
  try {
    await setCanonicalQualificationAlias(item.id, aliasId)
    success('Канонический алиас изменён')
    await load()
  } catch (exception) {
    toastError('Не удалось изменить канон', apiErrorMessage(exception))
  }
}

async function removeAlias(aliasId: string) {
  try {
    await deleteQualificationAlias(aliasId)
    success('Алиас удалён')
    await load()
  } catch (exception) {
    toastError('Не удалось удалить алиас', apiErrorMessage(exception))
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <form class="card p-3" @submit.prevent="search">
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
        <label class="relative min-w-0 flex-1">
          <Search
            :size="16"
            class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            v-model="query"
            class="input pl-9"
            placeholder="Поиск по квалификации или алиасу"
            aria-label="Поиск по квалификациям"
          />
        </label>
        <select v-model="status" class="input sm:w-40" aria-label="Статус квалификации">
          <option value="ACTIVE">Активные</option>
          <option value="DEPRECATED">В архиве</option>
        </select>
        <button type="submit" class="btn-primary shrink-0" :disabled="searching">
          <Loader2 v-if="searching" :size="16" class="animate-spin" />
          <Search v-else :size="16" />
          Найти
        </button>
        <button type="button" class="btn-secondary shrink-0" @click="openCreate">
          <Plus :size="16" />
          Добавить
        </button>
      </div>
    </form>

    <div
      v-if="error"
      class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
    >
      <span>{{ error }}</span>
      <button class="btn-secondary shrink-0" @click="load">Повторить</button>
    </div>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div v-for="index in 3" :key="index" class="card space-y-4 p-5">
        <div class="skeleton h-3 w-1/3" />
        <div class="skeleton h-6 w-2/3" />
        <div class="skeleton h-9 w-full" />
      </div>
    </div>

    <div
      v-else-if="items.length === 0"
      class="card flex flex-col items-center justify-center px-6 py-16 text-center"
    >
      <span class="grid h-14 w-14 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
        <ShieldCheck :size="26" />
      </span>
      <h2 class="mt-4 text-lg font-semibold text-slate-900">Квалификации не найдены</h2>
      <p class="mt-1 max-w-md text-sm text-slate-500">
        Добавьте группу допуска, категорию прав или другой подтверждаемый документ.
      </p>
    </div>

    <div v-else class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <article v-for="item in items" :key="item.id" class="card flex flex-col p-5">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <h2 class="text-lg font-semibold text-slate-900">{{ item.canonicalName }}</h2>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <span class="badge-neutral">{{ typeLabel(item.qualificationType) }}</span>
              <span :class="item.status === 'DEPRECATED' ? 'badge-warning' : 'badge-success'">
                {{ item.status === 'DEPRECATED' ? 'В архиве' : 'Активна' }}
              </span>
            </div>
          </div>
          <span class="badge-neutral shrink-0">{{ aliasCount(item.aliases.length) }}</span>
        </div>

        <div class="mt-4 flex flex-wrap gap-1.5">
          <div
            v-for="alias in item.aliases"
            :key="alias.id"
            class="inline-flex items-center gap-1 rounded-md bg-slate-50 px-2 py-1 text-xs"
          >
            <span :class="alias.canonical ? 'font-medium text-slate-900' : 'text-slate-600'">
              {{ alias.raw }}
            </span>
            <span v-if="alias.canonical" class="text-[10px] uppercase text-emerald-700">
              канон
            </span>
            <template v-else>
              <button
                type="button"
                class="text-indigo-600 hover:underline"
                title="Сделать каноническим алиасом"
                @click="makeCanonical(item, alias.id)"
              >
                канон
              </button>
              <button
                type="button"
                class="text-slate-400 hover:text-rose-600"
                title="Удалить алиас"
                @click="removeAlias(alias.id)"
              >
                ×
              </button>
            </template>
          </div>
          <span v-if="item.aliases.length === 0" class="text-xs text-slate-400">
            Нет алиасов
          </span>
        </div>

        <form
          class="mt-auto flex gap-2 border-t border-slate-100 pt-4"
          @submit.prevent="bindAlias(item)"
        >
          <input
            v-model="aliasDrafts[item.id]"
            class="input"
            placeholder="Новый алиас"
            :aria-label="`Новый алиас для ${item.canonicalName}`"
          />
          <button
            type="submit"
            class="btn-secondary shrink-0"
            :disabled="!(aliasDrafts[item.id] ?? '').trim()"
          >
            <Plus :size="14" />
            Добавить
          </button>
        </form>
      </article>
    </div>

    <AppModal :open="createOpen" title="Новая квалификация" @close="createOpen = false">
      <form class="space-y-4" @submit.prevent="submitCreate">
        <div>
          <label class="label" for="qualification-name">Название</label>
          <input
            id="qualification-name"
            v-model="newName"
            class="input"
            placeholder="Например, II группа по электробезопасности"
          />
        </div>
        <div>
          <label class="label" for="qualification-type">Тип</label>
          <select id="qualification-type" v-model="newType" class="input">
            <option value="PERMIT">Допуск</option>
            <option value="LICENSE">Лицензия</option>
            <option value="ATTESTATION">Аттестат</option>
            <option value="CATEGORY">Категория</option>
            <option value="OTHER">Другое</option>
          </select>
        </div>
        <p v-if="createError" class="rounded-xl bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {{ createError }}
        </p>
        <div class="flex justify-end gap-2">
          <button type="button" class="btn-secondary" @click="createOpen = false">
            Отмена
          </button>
          <button type="submit" class="btn-primary" :disabled="creating || !newName.trim()">
            <Loader2 v-if="creating" :size="16" class="animate-spin" />
            Создать
          </button>
        </div>
      </form>
    </AppModal>
  </div>
</template>
