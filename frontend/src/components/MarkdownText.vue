<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps<{ source: string }>()

// html: false обязателен: текст вакансии приходит извне и не должен исполнять HTML.
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })
const rendered = computed(() => (props.source.trim() ? markdown.render(props.source) : ''))
</script>

<template>
  <div v-if="rendered" class="markdown-body" v-html="rendered" />
  <p v-else class="text-sm text-slate-400">Текст недоступен</p>
</template>

<style scoped>
.markdown-body {
  font-size: 0.9375rem;
  line-height: 1.7;
  color: #334155;
  overflow-wrap: anywhere;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 1.4em 0 0.5em;
  font-weight: 600;
  line-height: 1.3;
  color: #0f172a;
}

.markdown-body :deep(h1:first-child),
.markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child) {
  margin-top: 0;
}

.markdown-body :deep(h1) {
  font-size: 1.5rem;
}

.markdown-body :deep(h2) {
  font-size: 1.25rem;
}

.markdown-body :deep(h3) {
  font-size: 1.0625rem;
}

.markdown-body :deep(p) {
  margin: 0.65em 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.65em 0;
  padding-left: 1.4rem;
}

.markdown-body :deep(ul) {
  list-style: disc;
}

.markdown-body :deep(ol) {
  list-style: decimal;
}

.markdown-body :deep(li) {
  margin: 0.25em 0;
}

.markdown-body :deep(li > p) {
  margin: 0;
}

.markdown-body :deep(a) {
  color: #4f46e5;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: #0f172a;
}

.markdown-body :deep(code) {
  border-radius: 0.25rem;
  background: #f1f5f9;
  padding: 0.1rem 0.3rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85em;
}

.markdown-body :deep(pre) {
  margin: 0.8em 0;
  overflow-x: auto;
  border-radius: 0.5rem;
  background: #f8fafc;
  padding: 0.75rem 1rem;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-body :deep(blockquote) {
  margin: 0.8em 0;
  border-left: 3px solid #c7d2fe;
  padding-left: 1rem;
  color: #475569;
}

.markdown-body :deep(hr) {
  margin: 1.25em 0;
  border: 0;
  border-top: 1px solid #e2e8f0;
}

.markdown-body :deep(table) {
  margin: 0.8em 0;
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 0.4rem 0.6rem;
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f8fafc;
  font-weight: 600;
}

</style>
