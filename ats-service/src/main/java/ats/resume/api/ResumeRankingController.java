package ats.resume.api;

import ats.api.ResumeRankingApi;
import ats.gateway.model.ResumeVacancyRankingItem;
import ats.gateway.model.ResumeVacancyRankingResponse;
import ats.gateway.model.ScreeningBatchAccepted;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.resume.infrastructure.ranking.ResumeRankingService;
import ats.resume.infrastructure.screening.ResumeMatchService;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Направление «вакансии под резюме»: рейтинг и ручной пересчёт. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResumeRankingController implements ResumeRankingApi {

    private final ResumeRankingService rankingService;
    private final ResumeMatchService matchService;
    private final ResumeRepository resumeRepository;

    @Override
    public ResponseEntity<ResumeVacancyRankingResponse> getResumeVacancyRanking(UUID resumeId) {
        ResumeRankingService.RankingView view = rankingService.ranking(resumeId, null);
        ResumeVacancyRankingResponse response =
                new ResumeVacancyRankingResponse()
                        .resumeId(view.resumeId())
                        .phase(ResumeVacancyRankingResponse.PhaseEnum.valueOf(view.phase()))
                        .generatedAt(view.generatedAt().atOffset(ZoneOffset.UTC))
                        .items(
                                view.items().stream()
                                        .map(
                                                item ->
                                                        new ResumeVacancyRankingItem()
                                                                .rank(item.rank())
                                                                .vacancyId(item.vacancyId())
                                                                .title(item.title())
                                                                .relevance(item.relevance())
                                                                .score(item.score())
                                                                .scoreLow(item.scoreLow())
                                                                .scoreHigh(item.scoreHigh())
                                                                .flags(item.flags()))
                                        .toList());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ScreeningBatchAccepted> matchResume(UUID resumeId) {
        if (!resumeRepository.existsById(resumeId)) {
            return ResponseEntity.notFound().build();
        }
        UUID batchId = matchService.start(resumeId);
        return ResponseEntity.accepted()
                .body(new ScreeningBatchAccepted().batchId(batchId).status("PENDING"));
    }
}
