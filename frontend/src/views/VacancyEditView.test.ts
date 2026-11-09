import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Vacancy } from '@/api/generated'
import VacancyEditView from './VacancyEditView.vue'

const getVacancy = vi.fn()
const updateVacancyTemplate = vi.fn()
let errorCode: string | null = null

vi.mock('@/api', () => ({
  vacanciesApi: {
    getVacancy: (...args: unknown[]) => getVacancy(...args),
    updateVacancyTemplate: (...args: unknown[]) => updateVacancyTemplate(...args),
  },
  referenceApi: {
    listSpecializations: () => Promise.resolve({ data: [{ code: 'backend', name: 'Backend' }] }),
    listLevels: () => Promise.resolve({ data: [{ code: 'middle', name: 'Middle' }] }),
  },
  apiErrorMessage: () => 'Шаблон изменён в другом месте. Перезагрузите данные',
  apiErrorCode: () => errorCode,
}))

function vacancy(): Vacancy {
  return {
    vacancy_id: 'vacancy-1',
    specialization: 'backend',
    level: 'middle',
    title: 'Backend',
    status: 'DRAFT',
    source_text: 'Обязательно:\n- JavaScript\n- Kafka\n- Docker',
    template: {
      version: 1,
      is_confirmed: false,
      meta: { model: 'mock/mock-model', prompt_version: 'vacancy-template.v2', dropped_requirements: 0 },
      requirements: [
        {
          id: 'req-1',
          skill: 'JavaScript',
          skill_id: 'skill-1',
          normalization_status: 'MATCHED',
          kind: 'MUST',
          kind_source: 'LLM',
          llm_kind: 'MUST',
          quote: 'JS',
        },
        {
          id: 'req-2',
          skill: 'Kafka',
          skill_id: 'skill-2',
          normalization_status: 'MATCHED',
          kind: 'NICE',
          kind_source: 'LLM',
          llm_kind: 'NICE',
          quote: 'Kafka',
        },
        {
          id: 'req-3',
          skill: 'Docker',
          skill_id: 'skill-3',
          normalization_status: 'MATCHED',
          kind: 'OPTIONAL',
          kind_source: 'LLM',
          llm_kind: 'OPTIONAL',
          quote: 'Docker',
        },
      ],
    },
  }
}

async function mountEditor() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/vacancies', component: { template: '<div />' } },
      { path: '/vacancies/:id', name: 'edit', component: VacancyEditView, props: true },
    ],
  })
  await router.push('/vacancies/vacancy-1')
  await router.isReady()
  const wrapper = mount(VacancyEditView, {
    props: { id: 'vacancy-1' },
    global: { plugins: [router] },
  })
  await flushPromises()
  return wrapper
}

describe('VacancyEditView', () => {
  beforeEach(() => {
    errorCode = null
    getVacancy.mockResolvedValue({ data: vacancy() })
    updateVacancyTemplate.mockReset()
  })

  it('группирует требования и переносит карточку при смене типа', async () => {
    const wrapper = await mountEditor()
    expect(wrapper.text()).toContain('Обязательные')
    expect(wrapper.text()).toContain('Желательные')
    expect(wrapper.text()).toContain('Научим / не требуется')
    expect(wrapper.get('[data-testid="vacancy-source-text"]').text()).toContain('JavaScript')

    const selects = wrapper.findAll('select[aria-label="Тип требования"]')
    expect(selects).toHaveLength(3)
    await selects[0].setValue('OPTIONAL')
    await flushPromises()

    const optionalSection = wrapper.get('[data-section="OPTIONAL"]')
    const skillInputs = optionalSection
      .findAll('input[aria-label="Название навыка"]')
      .map((input) => (input.element as HTMLInputElement).value)
    expect(skillInputs).toContain('JavaScript')
    expect(skillInputs).toContain('Docker')
  })

  it('блокирует сохранение без изменений и разблокирует после правки', async () => {
    const wrapper = await mountEditor()
    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Сохранить шаблон'))
    expect(saveButton?.attributes('disabled')).toBeDefined()

    await wrapper.get('input[aria-label="Название навыка"]').setValue('Java')
    expect(saveButton?.attributes('disabled')).toBeUndefined()
  })

  it('показывает 409 и предлагает перезагрузить', async () => {
    errorCode = 'VERSION_CONFLICT'
    updateVacancyTemplate.mockRejectedValue(new Error('conflict'))
    const wrapper = await mountEditor()
    await wrapper.get('input[aria-label="Название навыка"]').setValue('Java')
    const saveButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Сохранение') || button.text().includes('Сохранить шаблон'))
    await saveButton?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Шаблон изменён в другом месте')
    expect(wrapper.findAll('button').some((button) => button.text().includes('Перезагрузить'))).toBe(true)
  })
})
