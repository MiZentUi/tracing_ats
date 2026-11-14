<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as d3 from 'd3'
import { Link2, Loader2, Plus, RefreshCw, Trash2 } from '@lucide/vue'
import {
  createRelation,
  deleteRelation,
  listRelations,
  listSkills,
  type Relation,
  type Skill,
} from '@/api/dictionary'
import { apiErrorMessage } from '@/api'
import { useToast } from '@/composables/useToast'
import SkillCombobox from './SkillCombobox.vue'

const { success, error: toastError } = useToast()

interface GraphNode extends d3.SimulationNodeDatum {
  id: string
  label: string
}

interface GraphEdge extends d3.SimulationLinkDatum<GraphNode> {
  leftSkillId: string
  rightSkillId: string
  kind: string
}

const relations = ref<Relation[]>([])
const skills = ref<Skill[]>([])
const loading = ref(true)
const error = ref('')
const selectedId = ref('')
const leftId = ref('')
const rightId = ref('')
const kind = ref('RELATED')
const saving = ref(false)
const svgRef = ref<SVGSVGElement | null>(null)
let simulation: d3.Simulation<GraphNode, GraphEdge> | null = null

const kindLabels: Record<string, string> = {
  SAME_GROUP: 'Одна группа',
  RELATED: 'Похожий',
  PARENT: 'Родитель',
  CHILD: 'Дочерний',
}

const kindClasses: Record<string, string> = {
  SAME_GROUP: 'text-indigo-600',
  RELATED: 'text-emerald-600',
  PARENT: 'text-amber-600',
  CHILD: 'text-sky-600',
}

const skillNames = computed(() => {
  const result = new Map<string, string>()
  for (const skill of skills.value) {
    result.set(skill.id, skill.canonicalName)
  }
  return result
})

const nodes = computed<GraphNode[]>(() => {
  const ids = new Set<string>()
  for (const relation of relations.value) {
    ids.add(relation.leftSkillId)
    ids.add(relation.rightSkillId)
  }
  return [...ids].map((id) => ({
    id,
    label: skillNames.value.get(id) ?? id,
  }))
})

const edges = computed<GraphEdge[]>(() =>
  relations.value.map((relation) => ({
    leftSkillId: relation.leftSkillId,
    rightSkillId: relation.rightSkillId,
    kind: relation.kind,
    source: relation.leftSkillId,
    target: relation.rightSkillId,
  })),
)

const selectedRelations = computed(() =>
  selectedId.value
    ? relations.value.filter(
        (relation) =>
          relation.leftSkillId === selectedId.value || relation.rightSkillId === selectedId.value,
      )
    : [],
)

const canCreate = computed(
  () => leftId.value && rightId.value && leftId.value !== rightId.value && !saving.value,
)

function kindLabel(value: string): string {
  return kindLabels[value] ?? value
}

function kindClass(value: string): string {
  return kindClasses[value] ?? 'text-slate-500'
}

function nodeClass(id: string): string {
  if (selectedId.value === id) {
    return 'fill-slate-900 stroke-slate-900'
  }
  if (
    selectedId.value &&
    selectedRelations.value.some(
      (relation) => relation.leftSkillId === id || relation.rightSkillId === id,
    )
  ) {
    return 'fill-indigo-500 stroke-indigo-600'
  }
  return 'fill-white stroke-slate-400'
}

function shortLabel(label: string): string {
  return label.length > 28 ? `${label.slice(0, 27)}…` : label
}

function nodeRadius(id: string): number {
  return selectedId.value === id ? 11 : 9
}

function updateSelection() {
  const element = svgRef.value
  if (!element) {
    return
  }
  const root = d3.select(element)
  root
    .selectAll<SVGCircleElement, GraphNode>('circle[data-node]')
    .attr('class', (node) => nodeClass(node.id))
    .attr('r', (node) => nodeRadius(node.id))

  const selected = selectedId.value
  root
    .selectAll<SVGLineElement, GraphEdge>('line[data-edge]')
    .attr('opacity', (edge) => {
      if (!selected) {
        return 0.55
      }
      return edge.leftSkillId === selected || edge.rightSkillId === selected ? 0.8 : 0.08
    })
    .attr('stroke-width', (edge) => {
      if (!selected) {
        return 1.5
      }
      return edge.leftSkillId === selected || edge.rightSkillId === selected ? 2.5 : 1
    })
}

function renderGraph() {
  simulation?.stop()
  const element = svgRef.value
  if (!element) {
    return
  }
  const graphNodes = nodes.value.map((node) => ({ ...node }))
  const graphEdges = edges.value.map((edge) => ({ ...edge }))
  if (graphNodes.length === 0) {
    d3.select(element).selectAll('*').remove()
    return
  }

  const width = 900
  const height = 560
  const svg = d3.select(element)
  svg.selectAll('*').remove()
  svg.on('click', () => {
    selectedId.value = ''
  })
  const root = svg.append('g')

  const link = root
    .append('g')
    .selectAll<SVGLineElement, GraphEdge>('line')
    .data(graphEdges)
    .join('line')
    .attr('data-edge', '')
    .attr('stroke', 'currentColor')
    .attr('stroke-width', 1.5)
    .attr('opacity', 0.55)
    .attr('class', (edge) => kindClass(edge.kind))

  const node = root
    .append('g')
    .selectAll<SVGGElement, GraphNode>('g')
    .data(graphNodes)
    .join('g')
    .attr('data-node', (item) => item.id)
    .attr('class', 'cursor-pointer')
    .on('click', (event, item) => {
      event.stopPropagation()
      selectedId.value = selectedId.value === item.id ? '' : item.id
    })

  node
    .append('circle')
    .attr('data-node', (item) => item.id)
    .attr('r', 9)
    .attr('stroke-width', 2)
    .attr('class', (item) => nodeClass(item.id))

  node
    .append('text')
    .attr('y', -15)
    .attr('text-anchor', 'middle')
    .attr('class', 'fill-slate-700 text-[11px] font-medium')
    .text((item) => shortLabel(item.label))

  simulation = d3
    .forceSimulation<GraphNode>(graphNodes)
    .force(
      'link',
      d3
        .forceLink<GraphNode, GraphEdge>(graphEdges)
        .id((item) => item.id)
        .distance(130)
        .strength(0.55),
    )
    .force('charge', d3.forceManyBody<GraphNode>().strength(-420))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collide', d3.forceCollide<GraphNode>().radius(48))
    .on('tick', () => {
      link
        .attr('x1', (edge) => (edge.source as GraphNode).x ?? 0)
        .attr('y1', (edge) => (edge.source as GraphNode).y ?? 0)
        .attr('x2', (edge) => (edge.target as GraphNode).x ?? 0)
        .attr('y2', (edge) => (edge.target as GraphNode).y ?? 0)
      node.attr(
        'transform',
        (item) => `translate(${item.x ?? 0},${item.y ?? 0})`,
      )
    })

  node.call(
    d3
      .drag<SVGGElement, GraphNode>()
      .on('start', (event, item) => {
        if (!event.active) {
          simulation?.alphaTarget(0.3).restart()
        }
        item.fx = item.x
        item.fy = item.y
      })
      .on('drag', (event, item) => {
        item.fx = event.x
        item.fy = event.y
      })
      .on('end', (event, item) => {
        if (!event.active) {
          simulation?.alphaTarget(0)
        }
        item.fx = null
        item.fy = null
      }),
  )

  updateSelection()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [relationItems, skillItems] = await Promise.all([
      listRelations(500),
      listSkills('', '', 'ACTIVE', 500),
    ])
    relations.value = relationItems
    skills.value = skillItems
  } catch (exception) {
    error.value = apiErrorMessage(exception)
    toastError('Не удалось загрузить связи', error.value)
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canCreate.value) {
    return
  }
  saving.value = true
  try {
    await createRelation(leftId.value, rightId.value, kind.value)
    success('Связь добавлена')
    leftId.value = ''
    rightId.value = ''
    await load()
  } catch (exception) {
    toastError('Не удалось добавить связь', apiErrorMessage(exception))
  } finally {
    saving.value = false
  }
}

async function remove(relation: Relation) {
  try {
    await deleteRelation(relation.leftSkillId, relation.rightSkillId)
    success('Связь удалена')
    await load()
  } catch (exception) {
    toastError('Не удалось удалить связь', apiErrorMessage(exception))
  }
}

watch(
  [loading, nodes, edges],
  async () => {
    if (loading.value) {
      return
    }
    await nextTick()
    renderGraph()
  },
  { deep: false },
)

watch(selectedId, updateSelection)

onMounted(load)
onBeforeUnmount(() => simulation?.stop())
</script>

<template>
  <div class="space-y-4">
    <div
      v-if="error"
      class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800"
    >
      <span>{{ error }}</span>
      <button class="btn-secondary shrink-0" @click="load">Повторить</button>
    </div>

    <div v-if="loading" class="card flex h-72 items-center justify-center">
      <Loader2 :size="24" class="animate-spin text-slate-400" />
    </div>

    <template v-else>
      <div class="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
        <div class="card min-w-0 p-4">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
            <div>
              <h2 class="text-base font-semibold text-slate-900">Граф связей</h2>
              <p class="mt-0.5 text-xs text-slate-500">
                Нажмите на навык, чтобы подсветить его связи. Узлы можно перетаскивать.
              </p>
            </div>
            <button class="btn-secondary" @click="load">
              <RefreshCw :size="14" />
              Обновить
            </button>
          </div>

          <div
            v-if="nodes.length === 0"
            class="flex flex-col items-center justify-center px-6 py-16 text-center"
          >
            <span class="grid h-14 w-14 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
              <Link2 :size="26" />
            </span>
            <h3 class="mt-4 text-lg font-semibold text-slate-900">Связей пока нет</h3>
            <p class="mt-1 max-w-md text-sm text-slate-500">
              Добавьте связь между навыками, чтобы показать, что они относятся к одной группе
              или близки по смыслу.
            </p>
          </div>

          <div v-else class="mt-3 overflow-hidden rounded-lg border border-slate-100 bg-slate-50/40">
            <svg
              ref="svgRef"
              viewBox="0 0 900 560"
              class="h-auto w-full"
              role="img"
              aria-label="Граф связей навыков"
            />
          </div>

          <div class="mt-3 flex flex-wrap gap-3 border-t border-slate-100 pt-3 text-xs text-slate-500">
            <span v-for="(label, value) in kindLabels" :key="value" class="inline-flex items-center gap-1.5">
              <span class="h-0.5 w-4 rounded-full bg-current" :class="kindClass(value)" />
              {{ label }}
            </span>
          </div>
        </div>

        <aside class="card space-y-4 p-5">
          <div>
            <h2 class="text-base font-semibold text-slate-900">Новая связь</h2>
            <p class="mt-1 text-sm text-slate-500">
              Связь не объединяет навыки: она задаёт класс похожести.
            </p>
          </div>
          <form class="space-y-3" @submit.prevent="submit">
            <SkillCombobox
              v-model="leftId"
              input-id="relation-left"
              label="Первый навык"
              input-label="Первый навык"
              :skills="skills"
            />
            <SkillCombobox
              v-model="rightId"
              input-id="relation-right"
              label="Второй навык"
              input-label="Второй навык"
              :skills="skills"
            />
            <div>
              <label class="label" for="relation-kind">Тип связи</label>
              <select id="relation-kind" v-model="kind" class="input">
                <option v-for="(label, value) in kindLabels" :key="value" :value="value">
                  {{ label }}
                </option>
              </select>
            </div>
            <button type="submit" class="btn-primary w-full" :disabled="!canCreate">
              <Loader2 v-if="saving" :size="16" class="animate-spin" />
              <Plus v-else :size="16" />
              Добавить связь
            </button>
          </form>

          <div v-if="selectedId" class="border-t border-slate-100 pt-4">
            <p class="text-sm font-medium text-slate-700">
              Связи «{{ skillNames.get(selectedId) ?? selectedId }}»
            </p>
            <p v-if="selectedRelations.length === 0" class="mt-2 text-sm text-slate-500">
              У навыка пока нет связей.
            </p>
            <ul v-else class="mt-2 space-y-2">
              <li
                v-for="relation in selectedRelations"
                :key="`${relation.leftSkillId}:${relation.rightSkillId}`"
                class="flex items-center gap-2 text-sm text-slate-600"
              >
                <span class="h-1.5 w-1.5 rounded-full bg-current" :class="kindClass(relation.kind)" />
                <span class="min-w-0 flex-1 truncate">
                  {{
                    skillNames.get(
                      relation.leftSkillId === selectedId
                        ? relation.rightSkillId
                        : relation.leftSkillId,
                    ) ?? '—'
                  }}
                </span>
                <span class="text-xs text-slate-400">{{ kindLabel(relation.kind) }}</span>
              </li>
            </ul>
          </div>
        </aside>
      </div>

      <div class="card overflow-hidden">
        <div class="border-b border-slate-100 px-4 py-3">
          <h2 class="text-base font-semibold text-slate-900">Все связи</h2>
        </div>
        <div v-if="relations.length === 0" class="px-4 py-8 text-center text-sm text-slate-500">
          Связей пока нет.
        </div>
        <div v-else class="overflow-x-auto">
          <table class="w-full min-w-[640px] text-sm">
            <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th class="px-4 py-2 text-left font-medium">Первый навык</th>
                <th class="px-4 py-2 text-left font-medium">Второй навык</th>
                <th class="px-4 py-2 text-left font-medium">Тип</th>
                <th class="px-4 py-2 text-right font-medium">Действие</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="relation in relations" :key="`${relation.leftSkillId}:${relation.rightSkillId}`">
                <td class="px-4 py-3 text-slate-700">
                  {{ skillNames.get(relation.leftSkillId) ?? '—' }}
                </td>
                <td class="px-4 py-3 text-slate-700">
                  {{ skillNames.get(relation.rightSkillId) ?? '—' }}
                </td>
                <td class="px-4 py-3">
                  <span class="rounded-md border border-slate-200 px-2 py-1 text-xs" :class="kindClass(relation.kind)">
                    {{ kindLabel(relation.kind) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-right">
                  <button
                    type="button"
                    class="icon-btn text-slate-400 hover:text-rose-600"
                    title="Удалить связь"
                    @click="remove(relation)"
                  >
                    <Trash2 :size="15" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
