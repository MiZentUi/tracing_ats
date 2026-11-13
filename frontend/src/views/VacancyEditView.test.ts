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
    deleteVacancy: vi.fn(),
  },
  referenceApi: {
    listSpecializations: () =>
      Promise.resolve({ data: [{ code: 'backend', name: 'Backend', it: true }] }),
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

function confirmedVacancy(version = 2): Vacancy {
  const draft = vacancy()
  return {
    ...draft,
    status: 'ACTIVE',
    template: { ...draft.template, version, is_confirmed: true },
  }
}

async function mountEditor() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/vacancies', component: { template: '<div />' } },
      { path: '/vacancies/:id', name: 'view', component: { template: '<div />' } },
      { path: '/vacancies/:id/edit', name: 'edit', component: VacancyEditView, props: true },
    ],
  })
  await router.push('/vacancies/vacancy-1/edit')
  await router.isReady()
  const wrapper = mount(VacancyEditView, {
    props: { id: 'vacancy-1' },
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return wrapper
}

function skillInputs(wrapper: Awaited<ReturnType<typeof mountEditor>>) {
  return wrapper
    .findAll('input[aria-label="Название навыка"]')
    .map((input) => (input.element as HTMLInputElement).value)
}

function buttonWithText(wrapper: Awaited<ReturnType<typeof mountEditor>>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().trim() === text)
}

describe('VacancyEditView', () => {
  beforeEach(() => {
    errorCode = null
    getVacancy.mockResolvedValue({ data: vacancy() })
    updateVacancyTemplate.mockReset()
  })

  it('открывается на доске и показывает три колонки', async () => {
    const wrapper = await mountEditor()
    const columnSkills = (kind: string) =>
      wrapper
        .get(`[data-column="${kind}"]`)
        .findAll('input[aria-label="Название навыка"]')
        .map((input) => (input.element as HTMLInputElement).value)

    expect(columnSkills('MUST')).toContain('JavaScript')
    expect(columnSkills('NICE')).toContain('Kafka')
    expect(columnSkills('OPTIONAL')).toContain('Docker')
    expect(wrapper.findAll('[data-testid="board-card"]')).toHaveLength(3)
  })

  it('в режиме списка не меняет порядок при смене типа', async () => {
    const wrapper = await mountEditor()
    await buttonWithText(wrapper, 'Список')?.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-requirement-key]')).toHaveLength(3)
    expect(skillInputs(wrapper)).toEqual(['JavaScript', 'Kafka', 'Docker'])

    await wrapper.findAll('[data-requirement-key]')[0].get('select').setValue('OPTIONAL')
    await flushPromises()

    expect(skillInputs(wrapper)).toEqual(['JavaScript', 'Kafka', 'Docker'])
  })

  it('добавляет требование в конкретную колонку доски', async () => {
    const wrapper = await mountEditor()
    const niceColumn = wrapper.get('[data-column="NICE"]')
    await niceColumn
      .findAll('button')
      .find((button) => button.text().trim() === 'Добавить')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-testid="board-card"]')).toHaveLength(4)
    expect(wrapper.get('[data-column="NICE"]').findAll('[data-testid="board-card"]')).toHaveLength(2)
  })

  it('сохраняет изменения отдельной кнопкой', async () => {
    updateVacancyTemplate.mockResolvedValue({ data: confirmedVacancy() })
    const wrapper = await mountEditor()

    await wrapper.get('#editor-title').setValue('Backend Senior')
    await buttonWithText(wrapper, 'Сохранить')?.trigger('click')
    await flushPromises()

    expect(updateVacancyTemplate).toHaveBeenCalledTimes(1)
    expect(updateVacancyTemplate.mock.calls[0][0].updateTemplateRequest.confirm).toBe(false)
    expect(wrapper.text()).toContain('Изменения сохранены')
  })

  it('отправляет изменённый текст вакансии', async () => {
    updateVacancyTemplate.mockResolvedValue({ data: confirmedVacancy() })
    const wrapper = await mountEditor()

    await wrapper.get('[data-testid="vacancy-source-text"]').setValue('Новый текст вакансии')
    await buttonWithText(wrapper, 'Сохранить')?.trigger('click')
    await flushPromises()

    expect(updateVacancyTemplate.mock.calls[0][0].updateTemplateRequest.source_text).toBe(
      'Новый текст вакансии',
    )
  })

  it('использует вакансию как шаблон после подтверждения', async () => {
    updateVacancyTemplate.mockResolvedValue({ data: confirmedVacancy(3) })
    const wrapper = await mountEditor()

    await wrapper.get('#editor-title').setValue('Backend Senior')
    await buttonWithText(wrapper, 'Использовать как шаблон')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Использовать как шаблон?')
    await buttonWithText(wrapper, 'Использовать')?.trigger('click')
    await flushPromises()

    expect(updateVacancyTemplate).toHaveBeenCalledTimes(1)
    expect(updateVacancyTemplate.mock.calls[0][0].updateTemplateRequest.confirm).toBe(true)
  })

  it('показывает 409 и предлагает перезагрузить', async () => {
    errorCode = 'VERSION_CONFLICT'
    updateVacancyTemplate.mockRejectedValue(new Error('conflict'))
    const wrapper = await mountEditor()

    await wrapper.get('#editor-title').setValue('Backend Senior')
    await buttonWithText(wrapper, 'Сохранить')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Шаблон изменён в другом месте')
    expect(buttonWithText(wrapper, 'Перезагрузить')).toBeTruthy()
  })
})
