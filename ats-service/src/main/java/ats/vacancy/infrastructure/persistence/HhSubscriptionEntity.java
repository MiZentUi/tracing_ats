package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Активная подписка на вебхуки hh.ru. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "hh_subscription")
public class HhSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subscription_id", nullable = false, length = 64)
    private String subscriptionId;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "employer_id", length = 64)
    private String employerId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
