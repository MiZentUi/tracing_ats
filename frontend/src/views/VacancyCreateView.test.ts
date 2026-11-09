import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'
import VacancyCreateView from './VacancyCreateView.vue'

const createVacancy = vi.fn()

vi.mock('@/api', () => ({
  vacanciesApi: {
    createVacancy: (...args: unknown[]) => createVacancy(...args),
  },
  referenceApi: {
    listSpecializations: () => Promise.resolve({ data: [{ code: 'backend', name: 'Backend' }] }),
    listLevels: () => Promise.resolve({ data: [{ code: 'middle', name: 'Middle' }] }),
  },
  apiErrorMessage: () => 'Ошибка',
  apiErrorCode: () => null,
}))

async function mountCreate() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/vacancies/new', component: VacancyCreateView },
      { path: '/vacancies/:id', component: { template: '<div />' } },
    ],
  })
  await router.push('/vacancies/new')
  await router.isReady()
  const wrapper = mount(VacancyCreateView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('VacancyCreateView', () => {
  it('блокирует повторную отправку и показывает индикатор', async () => {
    let resolveRequest: (value: unknown) => void = () => {}
    createVacancy.mockImplementation(
      () => new Promise((resolve) => (resolveRequest = resolve)),
    )
    const wrapper = await mountCreate()

    await wrapper.findAll('select')[0].setValue('backend')
    await wrapper.findAll('select')[1].setValue('middle')
    await wrapper.get('textarea').setValue('Java')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const button = wrapper.get('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Разбор может занять до пары минут')

    resolveRequest({ data: { vacancy_id: 'vacancy-1' } })
    await flushPromises()
  })

  it('не отправляет форму без текста', async () => {
    const wrapper = await mountCreate()
    await wrapper.findAll('select')[0].setValue('backend')
    await wrapper.findAll('select')[1].setValue('middle')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })
})
