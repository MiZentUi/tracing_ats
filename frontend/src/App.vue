<script setup lang="ts">
import { BriefcaseBusiness, Sparkles } from '@lucide/vue'
import ToastHost from '@/components/ToastHost.vue'
</script>

<template>
  <div class="min-h-screen">
    <header class="sticky top-0 z-30 border-b border-slate-200/80 bg-white/80 backdrop-blur-xl">
      <div class="mx-auto flex max-w-7xl items-center gap-6 px-4 py-3 sm:px-6">
        <RouterLink to="/vacancies" class="flex items-center gap-3">
          <span class="grid h-10 w-10 place-items-center rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-lg shadow-indigo-500/20">
            <BriefcaseBusiness :size="20" />
          </span>
          <span>
            <span class="block text-sm font-semibold tracking-tight text-slate-900">Tracing ATS</span>
            <span class="block text-xs text-slate-500">Вакансии и шаблоны</span>
          </span>
        </RouterLink>

        <nav class="hidden items-center gap-1 sm:flex">
          <RouterLink
            to="/vacancies"
            class="rounded-xl px-3 py-2 text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
            active-class="bg-slate-100 !text-slate-900"
          >
            Вакансии
          </RouterLink>
        </nav>

        <div class="ml-auto flex items-center gap-2">
          <span class="badge-info hidden sm:inline-flex">
            <Sparkles :size="12" />
            MVP
          </span>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <RouterView />
    </main>

    <ToastHost />
  </div>
</template>
