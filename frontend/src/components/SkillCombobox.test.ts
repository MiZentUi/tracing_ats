import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { Skill } from '@/api/dictionary'
import SkillCombobox from './SkillCombobox.vue'

const skills = [
  {
    id: 'skill-1',
    canonicalName: 'JavaScript',
    aliases: [{ raw: 'JS' }],
  },
  {
    id: 'skill-2',
    canonicalName: 'TypeScript',
    aliases: [{ raw: 'TS' }],
  },
] as unknown as Skill[]

function mountCombobox(modelValue = '') {
  return mount(SkillCombobox, {
    props: {
      modelValue,
      skills,
      inputId: 'skill-input',
      label: 'Навык',
      inputLabel: 'Навык',
    },
  })
}

describe('SkillCombobox', () => {
  it('объединяет ввод и выбор навыка в одном поле', async () => {
    const wrapper = mountCombobox()
    const input = wrapper.get('input')

    await input.trigger('focus')
    await input.setValue('Java')

    const options = wrapper.findAll('[role="option"]')
    expect(options).toHaveLength(1)
    expect(options[0].text()).toContain('JavaScript')

    await options[0].trigger('mousedown')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['skill-1'])
    expect((input.element as HTMLInputElement).value).toBe('JavaScript')
  })

  it('ищет по алиасу', async () => {
    const wrapper = mountCombobox()
    const input = wrapper.get('input')

    await input.trigger('focus')
    await input.setValue('TS')

    expect(wrapper.findAll('[role="option"]')[0].text()).toContain('TypeScript')
  })

  it('показывает выбранный навык по modelValue', () => {
    const wrapper = mountCombobox('skill-2')

    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('TypeScript')
  })
})
