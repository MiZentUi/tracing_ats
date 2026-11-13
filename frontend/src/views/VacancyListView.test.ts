import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VacancyListView from './VacancyListView.vue'

const listVacancies = vi.fn()
const deleteVacancy = vi.fn()

vi.mock('@/api', () => ({
  vacanciesApi: {
    listVacancies: (...args: unknown[]) => listVacancies(...args),
    deleteVacancy: (...args: unknown[]) => deleteVacancy(...args),
  },
  referenceApi: {
    listSpecializations: () =>
      Promise.resolve({
        data: [
          { code: 'backend', name: 'Backend', it: true },
          { code: 'analytics', name: 'Аналитика', it: false },
        ],
      }),
    listLevels: () => Promise.resolve({ data: [{ code: 'middle', name: 'Middle' }] }),
  },
  apiErrorMessage: () => 'Ошибка',
}))

function page() {
  return {
    total: 3,
    items: [
      {
        id: 'vacancy-1',
        title: 'Backend-разработчик',
        specialization: 'backend',
        level: 'middle',
        status: 'DRAFT',
        version: 1,
        is_confirmed: false,
        requirements_count: 3,
        created_at: '2026-11-10T00:00:00Z',
      },
      {
        id: 'vacancy-2',
        title: 'Аналитик данных',
        specialization: 'analytics',
        level: 'none',
        status: 'DRAFT',
        version: 2,
        is_confirmed: false,
        requirements_count: 5,
        created_at: '2026-11-11T00:00:00Z',
      },
      {
        id: 'vacancy-3',
        title: 'Шаблон Backend Senior',
        specialization: 'backend',
        level: 'middle',
        status: 'ACTIVE',
        version: 4,
        is_confirmed: true,
        requirements_count: 8,
        created_at: '2026-11-12T00:00:00Z',
      },
    ],
  }
}

function mountList() {
  return mount(VacancyListView, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        teleport: true,
      },
    },
  })
}

describe('VacancyListView', () => {
  beforeEach(() => {
    listVacancies.mockReset()
    deleteVacancy.mockReset()
    listVacancies.mockResolvedValue({ data: page() })
    deleteVacancy.mockResolvedValue({ data: undefined })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('показывает вакансии карточками и фильтрует по кнопке «Найти»', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.findAll('table')).toHaveLength(0)
    expect(wrapper.text()).toContain('Backend-разработчик')
    expect(wrapper.text()).toContain('Аналитик данных')
    expect(wrapper.text()).toContain('middle')

    await wrapper.get('input[aria-label="Поиск вакансий"]').setValue('Аналит')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Аналитик данных')
    expect(wrapper.text()).not.toContain('Backend-разработчик')
  })

  it('открывает фильтры отдельной кнопкой', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.find('#filter-specialization').exists()).toBe(false)
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Фильтры'))
      ?.trigger('click')

    expect(wrapper.find('#filter-specialization').exists()).toBe(true)
    expect(wrapper.find('#filter-level').exists()).toBe(true)
  })

  it('по умолчанию не показывает шаблоны и умеет включать их фильтром', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.text()).not.toContain('Шаблон Backend Senior')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Фильтры'))
      ?.trigger('click')
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Шаблон Backend Senior')
  })

  it('удаляет вакансию', async () => {
    const wrapper = mountList()
    await flushPromises()

    await wrapper.get('button[title="Удалить вакансию"]').trigger('click')
    await flushPromises()
    const confirmButton = wrapper
      .findAll('button')
      .find((button) => button.text().trim() === 'Удалить')
    await confirmButton?.trigger('click')
    await flushPromises()

    expect(deleteVacancy).toHaveBeenCalledWith({ id: 'vacancy-1' })
    expect(listVacancies).toHaveBeenCalledTimes(2)
  })
})
