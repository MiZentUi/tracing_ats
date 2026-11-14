package ats.vacancy.api;

import ats.api.HhApi;
import ats.gateway.model.HhPublication;
import ats.gateway.model.HhWebhookEvent;
import ats.resume.infrastructure.hh.HhWebhookService;
import ats.vacancy.hh.HhPublicationService;
import ats.vacancy.hh.HhPublicationView;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HhController implements HhApi {

    private final HhPublicationService publicationService;
    private final HhWebhookService webhookService;

    @Override
    public ResponseEntity<HhPublication> getHhPublication(UUID vacancyId) {
        return ResponseEntity.ok(toModel(publicationService.get(vacancyId)));
    }

    @Override
    public ResponseEntity<HhPublication> publishVacancyToHh(UUID vacancyId) {
        return ResponseEntity.accepted().body(toModel(publicationService.publish(vacancyId)));
    }

    @Override
    public ResponseEntity<Void> archiveHhPublication(UUID vacancyId) {
        publicationService.archive(vacancyId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> receiveHhWebhook(String token, HhWebhookEvent hhWebhookEvent) {
        webhookService.receive(token, hhWebhookEvent);
        return ResponseEntity.accepted().build();
    }

    private HhPublication toModel(HhPublicationView view) {
        HhPublication model =
                new HhPublication()
                        .status(HhPublication.StatusEnum.valueOf(view.status().name()))
                        .hhVacancyId(view.hhVacancyId())
                        .hhUrl(view.hhUrl())
                        .errorCode(view.errorCode());
        if (view.publishedAt() != null) {
            model.publishedAt(view.publishedAt().atOffset(ZoneOffset.UTC));
        }
        return model;
    }
}
