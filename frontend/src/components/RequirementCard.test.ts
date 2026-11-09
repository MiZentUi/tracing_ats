import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RequirementCard from './RequirementCard.vue'
import type { EditableRequirement } from '@/types'

function requirement(overrides: Partial<EditableRequirement> = {}): EditableRequirement {
  return {
    key: 'req-1',
    id: 'req-1',
    skill: 'JavaScript',
    skillId: 'skill-1',
    normalizationStatus: 'MATCHED',
    kind: 'MUST',
    kindSource: 'HISTORY',
    llmKind: 'NICE',
    yearsRequired: 3,
    quote: 'опыт работы с JS от 3 лет',
    clarityWarning: null,
    history: { must: 4, nice: 1, optional: 0 },
    ...overrides,
  }
}

describe('RequirementCard', () => {
  it('показывает источник, историю и расхождение с LLM', () => {
    const wrapper = mount(RequirementCard, { props: { requirement: requirement() } })
    expect(wrapper.text()).toContain('История')
    expect(wrapper.text()).toContain('MUST')
    expect(wrapper.text()).toContain('80%')
    expect(wrapper.text()).toContain('NICE')
    expect(wrapper.text()).toContain('20%')
    expect(wrapper.text()).not.toContain('OPTIONAL')
    expect(wrapper.text()).toContain('история HR переопределила тип')
    expect(wrapper.text()).toContain('LLM предлагала')
    expect(wrapper.text()).toContain('из словаря')
  })

  it('эмитит смену типа', async () => {
    const wrapper = mount(RequirementCard, { props: { requirement: requirement() } })
    await wrapper.get('button[data-kind="OPTIONAL"]').trigger('click')
    const emitted = wrapper.emitted('update')
    expect(emitted?.[0]).toEqual([{ kind: 'OPTIONAL' }])
  })

  it('показывает расплывчатое требование без навыка', () => {
    const wrapper = mount(RequirementCard, {
      props: {
        requirement: requirement({
          skill: null,
          skillId: null,
          normalizationStatus: 'NEW',
          kind: 'NICE',
          kindSource: 'LLM',
          llmKind: 'NICE',
          history: null,
          clarityWarning: 'Уточните нагрузку',
        }),
      },
    })
    expect(wrapper.text()).toContain('Расплывчатое требование')
    expect(wrapper.text()).toContain('Уточните нагрузку')
  })
})
