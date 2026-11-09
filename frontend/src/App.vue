<script setup lang="ts">
</script>

<template>
  <div class="min-h-screen">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
        <RouterLink to="/vacancies" class="text-lg font-semibold">Tracing ATS · Вакансии</RouterLink>
        <span class="text-sm text-slate-500">MVP · разметка требований</span>
      </div>
    </header>
    <main class="mx-auto max-w-6xl px-4 py-6">
      <RouterView />
    </main>
  </div>
</template>
