import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Relation, Skill } from '@/api/dictionary'
import RelationGraphPanel from './RelationGraphPanel.vue'

const listRelations = vi.fn()
const listSkills = vi.fn()

vi.mock('@/api/dictionary', () => ({
  listRelations: (...args: unknown[]) => listRelations(...args),
  listSkills: (...args: unknown[]) => listSkills(...args),
  createRelation: vi.fn(),
  deleteRelation: vi.fn(),
}))

vi.mock('@/api', () => ({
  apiErrorMessage: () => 'Ошибка',
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}))

const skills = [
  { id: 'skill-1', canonicalName: 'JavaScript', aliases: [] },
  { id: 'skill-2', canonicalName: 'TypeScript', aliases: [] },
] as unknown as Skill[]

const relations = [
  {
    leftSkillId: 'skill-1',
    rightSkillId: 'skill-2',
    kind: 'RELATED',
  },
] as unknown as Relation[]

describe('RelationGraphPanel', () => {
  beforeEach(() => {
    listRelations.mockResolvedValue(relations)
    listSkills.mockResolvedValue(skills)
  })

  it('рисует граф связей через d3', async () => {
    const wrapper = mount(RelationGraphPanel)

    await flushPromises()
    await nextTick()
    await nextTick()

    const graph = wrapper.find('svg[aria-label="Граф связей навыков"]')
    expect(graph.exists()).toBe(true)
    expect(graph.attributes('viewBox')).toBe('0 0 1200 800')
    expect(wrapper.findAll('circle[data-node]')).toHaveLength(2)
    expect(wrapper.findAll('line[data-edge]')).toHaveLength(1)
    expect(wrapper.text()).toContain('JavaScript')
    expect(wrapper.text()).toContain('TypeScript')
    expect(wrapper.text()).toContain('Уместить')

    await wrapper.get('#relation-kind').setValue('PARENT')
    await nextTick()
    expect(wrapper.text()).toContain('Родительский навык')
    expect(wrapper.text()).toContain('Дочерний навык')

    await wrapper.get('[data-testid="fit-graph"]').trigger('click')

    wrapper.unmount()
  })
})
