<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { VacancyList } from '@/api/generated'
import { apiErrorMessage, vacanciesApi } from '@/api'

const loading = ref(true)
const error = ref('')
const page = ref<VacancyList | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await vacanciesApi.listVacancies({ limit: 50, offset: 0 })
    page.value = response.data
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(load)
</script>

<template>
  <section class="space-y-4">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">Вакансии</h1>
      <RouterLink
        to="/vacancies/new"
        class="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
      >
        Создать вакансию
      </RouterLink>
    </div>

    <p v-if="loading" class="text-slate-500">Загрузка…</p>
    <p v-else-if="error" class="rounded bg-red-50 p-3 text-red-700">{{ error }}</p>

    <div v-else-if="page && page.items.length === 0" class="rounded border border-dashed border-slate-300 p-10 text-center">
      <p class="text-slate-600">Вакансий пока нет.</p>
      <RouterLink to="/vacancies/new" class="mt-2 inline-block text-indigo-700 hover:underline">
        Создать первую вакансию
      </RouterLink>
    </div>

    <div v-else-if="page" class="overflow-x-auto rounded border border-slate-200 bg-white">
      <table class="min-w-full text-left text-sm">
        <thead class="border-b border-slate-200 bg-slate-50 text-slate-600">
          <tr>
            <th class="px-4 py-3">Название</th>
            <th class="px-4 py-3">Специализация</th>
            <th class="px-4 py-3">Уровень</th>
            <th class="px-4 py-3">Статус</th>
            <th class="px-4 py-3">Требований</th>
            <th class="px-4 py-3">Дата</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in page.items" :key="item.id" class="border-b border-slate-100 last:border-0">
            <td class="px-4 py-3">
              <RouterLink :to="`/vacancies/${item.id}`" class="font-medium text-indigo-700 hover:underline">
                {{ item.title }}
              </RouterLink>
            </td>
            <td class="px-4 py-3">{{ item.specialization }}</td>
            <td class="px-4 py-3">{{ item.level }}</td>
            <td class="px-4 py-3">
              <span
                class="rounded px-2 py-1 text-xs"
                :class="item.is_confirmed ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'"
              >
                {{ item.is_confirmed ? 'Подтверждена' : 'Черновик' }}
              </span>
            </td>
            <td class="px-4 py-3">{{ item.requirements_count }}</td>
            <td class="px-4 py-3">{{ formatDate(item.created_at) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
