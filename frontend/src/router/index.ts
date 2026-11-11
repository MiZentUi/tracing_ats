import { createRouter, createWebHistory } from 'vue-router'
import VacancyListView from '@/views/VacancyListView.vue'
import VacancyCreateView from '@/views/VacancyCreateView.vue'
import VacancyEditView from '@/views/VacancyEditView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/vacancies' },
    { path: '/vacancies', name: 'vacancies', component: VacancyListView },
    { path: '/vacancies/new', name: 'vacancy-create', component: VacancyCreateView },
    { path: '/vacancies/:id', name: 'vacancy-edit', component: VacancyEditView, props: true },
  ],
})

export default router
