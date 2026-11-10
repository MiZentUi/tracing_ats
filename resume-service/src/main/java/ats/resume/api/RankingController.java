package ats.resume.api;

import ats.resume.api.generated.RankingApi;
import ats.resume.api.model.RankingItem;
import ats.resume.api.model.RankingResponse;
import ats.resume.infrastructure.ranking.RankingService;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RankingController implements RankingApi {

    private final RankingService rankingService;

    @Override
    public ResponseEntity<RankingResponse> getRanking(UUID vacancyId, String phase) {
        RankingService.RankingView view = rankingService.ranking(vacancyId, phase);
        RankingResponse response =
                new RankingResponse()
                        .vacancyId(view.vacancyId())
                        .phase(RankingResponse.PhaseEnum.valueOf(view.phase()))
                        .generatedAt(view.generatedAt().atOffset(ZoneOffset.UTC))
                        .items(
                                view.items().stream()
                                        .map(
                                                item ->
                                                        new RankingItem()
                                                                .rank(item.rank())
                                                                .applicationId(item.applicationId())
                                                                .score(item.score())
                                                                .scoreLow(item.scoreLow())
                                                                .scoreHigh(item.scoreHigh())
                                                                .capApplied(item.capApplied())
                                                                .decision(
                                                                        RankingItem.DecisionEnum
                                                                                .valueOf(
                                                                                        item
                                                                                                .decision()))
                                                                .flags(item.flags()))
                                        .toList());
        return ResponseEntity.ok(response);
    }
}
