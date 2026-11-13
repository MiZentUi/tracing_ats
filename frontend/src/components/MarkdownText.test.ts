import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownText from './MarkdownText.vue'

describe('MarkdownText', () => {
  it('форматирует заголовки, списки и выделение', () => {
    const wrapper = mount(MarkdownText, {
      props: { source: '# Вакансия\n\n- **Java**\n- Kafka' },
    })

    expect(wrapper.find('h1').text()).toBe('Вакансия')
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(wrapper.find('strong').text()).toBe('Java')
  })

  it('не исполняет HTML из текста', () => {
    const wrapper = mount(MarkdownText, {
      props: { source: '<script>alert("xss")</script> **безопасно**' },
    })

    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>')
    expect(wrapper.find('strong').text()).toBe('безопасно')
  })
})
