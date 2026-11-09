<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { ReferenceItem } from '@/api/generated'
import { apiErrorMessage, referenceApi, vacanciesApi } from '@/api'

const router = useRouter()
const specializations = ref<ReferenceItem[]>([])
const levels = ref<ReferenceItem[]>([])
const specialization = ref('')
const level = ref('')
const title = ref('')
const mode = ref<'text' | 'file'>('text')
const text = ref('')
const file = ref<File | null>(null)
const loading = ref(false)
const error = ref('')

const canSubmit = computed(
  () =>
    !loading.value &&
    specialization.value !== '' &&
    level.value !== '' &&
    (mode.value === 'text' ? text.value.trim() !== '' : file.value !== null),
)

onMounted(async () => {
  try {
    const [specs, levelList] = await Promise.all([
      referenceApi.listSpecializations(),
      referenceApi.listLevels(),
    ])
    specializations.value = specs.data
    levels.value = levelList.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
})

function onFile(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0] ?? null
  file.value = selected
}

async function submit() {
  if (!canSubmit.value) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = await vacanciesApi.createVacancy({
      specialization: specialization.value,
      level: level.value,
      title: title.value.trim() || undefined,
      text: mode.value === 'text' ? text.value : undefined,
      file: mode.value === 'file' ? file.value : undefined,
    })
    await router.push(`/vacancies/${response.data.vacancy_id}`)
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-3xl space-y-4">
    <h1 class="text-2xl font-semibold">Создать вакансию</h1>

    <p v-if="error" class="rounded bg-red-50 p-3 text-red-700">{{ error }}</p>

    <form class="space-y-4 rounded border border-slate-200 bg-white p-5" @submit.prevent="submit">
      <div class="grid gap-4 sm:grid-cols-2">
        <label class="text-sm">
          Специализация
          <select v-model="specialization" class="mt-1 w-full rounded border border-slate-300 px-3 py-2" required>
            <option value="" disabled>Выберите</option>
            <option v-for="item in specializations" :key="item.code" :value="item.code">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label class="text-sm">
          Уровень
          <select v-model="level" class="mt-1 w-full rounded border border-slate-300 px-3 py-2" required>
            <option value="" disabled>Выберите</option>
            <option v-for="item in levels" :key="item.code" :value="item.code">
              {{ item.name }}
            </option>
          </select>
        </label>
      </div>

      <label class="block text-sm">
        Название (необязательно)
        <input v-model="title" class="mt-1 w-full rounded border border-slate-300 px-3 py-2" />
      </label>

      <div class="flex gap-4 text-sm">
        <label class="flex items-center gap-2">
          <input v-model="mode" type="radio" value="text" /> Вставить текст
        </label>
        <label class="flex items-center gap-2">
          <input v-model="mode" type="radio" value="file" /> Загрузить файл
        </label>
      </div>

      <label v-if="mode === 'text'" class="block text-sm">
        Текст вакансии
        <textarea
          v-model="text"
          rows="12"
          class="mt-1 w-full rounded border border-slate-300 px-3 py-2 font-mono text-xs"
          placeholder="Вставьте описание вакансии"
        />
      </label>

      <label v-else class="block text-sm">
        Файл PDF, DOCX, TXT или MD
        <input type="file" accept=".pdf,.docx,.txt,.md" class="mt-1 block w-full" @change="onFile" />
      </label>

      <div class="flex items-center gap-3">
        <button
          type="submit"
          :disabled="!canSubmit"
          class="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          Разобрать
        </button>
        <span v-if="loading" class="text-sm text-slate-600" role="status">
          Разбор может занять до пары минут…
        </span>
      </div>
    </form>
  </section>
</template>
