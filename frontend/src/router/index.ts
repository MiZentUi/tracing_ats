import { createRouter, createWebHistory } from 'vue-router'
import VacancyListView from '@/views/VacancyListView.vue'
import VacancyCreateView from '@/views/VacancyCreateView.vue'
import VacancyEditView from '@/views/VacancyEditView.vue'
import MatchView from '@/views/MatchView.vue'
import AnalysesView from '@/views/AnalysesView.vue'
import VacancyCandidatesView from '@/views/VacancyCandidatesView.vue'
import ResumeRankingView from '@/views/ResumeRankingView.vue'
import ResumeTextView from '@/views/ResumeTextView.vue'
import CompareView from '@/views/CompareView.vue'
import ReportView from '@/views/ReportView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/vacancies' },
    { path: '/vacancies', name: 'vacancies', component: VacancyListView },
    { path: '/vacancies/new', name: 'vacancy-create', component: VacancyCreateView },
    { path: '/vacancies/:id', name: 'vacancy-edit', component: VacancyEditView, props: true },
    { path: '/match', name: 'match', component: MatchView },
    { path: '/analyses', name: 'analyses', component: AnalysesView },
    {
      path: '/vacancies/:id/candidates',
      name: 'vacancy-candidates',
      component: VacancyCandidatesView,
      props: true,
    },
    { path: '/resumes/:id', name: 'resume-ranking', component: ResumeRankingView, props: true },
    {
      path: '/resumes/:id/text',
      name: 'resume-text',
      component: ResumeTextView,
      props: true,
    },
    {
      path: '/compare',
      name: 'compare',
      component: CompareView,
    },
    {
      path: '/applications/:id/report',
      name: 'application-report',
      component: ReportView,
    },
    {
      path: '/resumes/:resumeId/vacancies/:vacancyId/report',
      name: 'resume-vacancy-report',
      component: ReportView,
    },
  ],
})

export default router
