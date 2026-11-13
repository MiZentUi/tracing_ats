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

async function mountEditor() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/vacancies', component: { template: '<div />' } },
      { path: '/vacancies/:id', name: 'edit', component: VacancyEditView, props: true },
      { path: '/vacancies/:id/candidates', component: { template: '<div />' } },
    ],
  })
  await router.push('/vacancies/vacancy-1')
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

  it('показывает плоский список и не меняет порядок при смене типа', async () => {
    const wrapper = await mountEditor()
    expect(wrapper.get('[data-testid="vacancy-source-text"]').text()).toContain('JavaScript')
    expect(wrapper.findAll('[data-requirement-key]')).toHaveLength(3)
    expect(skillInputs(wrapper)).toEqual(['JavaScript', 'Kafka', 'Docker'])

    await wrapper.findAll('[data-requirement-key]')[0].get('select').setValue('OPTIONAL')
    await flushPromises()

    expect(skillInputs(wrapper)).toEqual(['JavaScript', 'Kafka', 'Docker'])
  })

  it('добавляет требование и подсвечивает его для пользователя', async () => {
    const wrapper = await mountEditor()
    await buttonWithText(wrapper, 'Добавить требование')?.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-requirement-key]')).toHaveLength(4)
    const highlighted = wrapper
      .findAll('[data-requirement-key]')
      .find((node) => node.classes().includes('ring-2'))
    expect(highlighted).toBeTruthy()
  })

  it('устанавливает черновик как шаблон после подтверждения', async () => {
    const draft = vacancy()
    const confirmed: Vacancy = {
      ...draft,
      status: 'ACTIVE',
      template: { ...draft.template, version: 2, is_confirmed: true },
    }
    updateVacancyTemplate.mockResolvedValue({ data: confirmed })
    const wrapper = await mountEditor()

    await wrapper.get('input[aria-label="Название навыка"]').setValue('Java')
    await buttonWithText(wrapper, 'Установить как шаблон')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Установить требования как шаблон?')
    await buttonWithText(wrapper, 'Установить')?.trigger('click')
    await flushPromises()

    expect(updateVacancyTemplate).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Шаблон установлен, версия 2')
    expect(buttonWithText(wrapper, 'Сохранить изменения')).toBeTruthy()
    expect(buttonWithText(wrapper, 'Установить как шаблон')).toBeUndefined()
  })

  it('переносит карточку между колонками на доске', async () => {
    const wrapper = await mountEditor()
    await buttonWithText(wrapper, 'Доска')?.trigger('click')
    await flushPromises()

    const mustColumn = wrapper.get('[data-column="MUST"]')
    const javaCard = mustColumn.findAll('[data-testid="board-card"]')[0]
    await javaCard.get('select').setValue('OPTIONAL')
    await flushPromises()

    const optionalColumn = wrapper.get('[data-column="OPTIONAL"]')
    expect(optionalColumn.text()).toContain('JavaScript')
    expect(optionalColumn.text()).toContain('Docker')
  })

  it('показывает 409 и предлагает перезагрузить', async () => {
    errorCode = 'VERSION_CONFLICT'
    updateVacancyTemplate.mockRejectedValue(new Error('conflict'))
    const wrapper = await mountEditor()
    await wrapper.get('input[aria-label="Название навыка"]').setValue('Java')
    await buttonWithText(wrapper, 'Установить как шаблон')?.trigger('click')
    await flushPromises()
    await buttonWithText(wrapper, 'Установить')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Шаблон изменён в другом месте')
    expect(buttonWithText(wrapper, 'Перезагрузить')).toBeTruthy()
  })
})
