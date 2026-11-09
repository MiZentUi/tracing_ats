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
  apiErrorMessage: () => 'Ошибка',
}))

function page() {
  return {
    total: 1,
    items: [
      {
        id: 'vacancy-1',
        title: 'Backend без грейда',
        specialization: 'backend',
        level: 'none',
        status: 'DRAFT',
        version: 1,
        is_confirmed: false,
        requirements_count: 3,
        created_at: '2026-11-10T00:00:00Z',
      },
    ],
  }
}

function mountList() {
  return mount(VacancyListView, {
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
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

  it('показывает «Без уровня» и удаляет вакансию', async () => {
    const wrapper = mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('Без уровня')

    const deleteButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Удалить'))
    await deleteButton?.trigger('click')
    await flushPromises()

    expect(deleteVacancy).toHaveBeenCalledWith({ id: 'vacancy-1' })
    expect(listVacancies).toHaveBeenCalledTimes(2)
  })
})
