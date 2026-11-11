package ats.resume.infrastructure.scoring;

import ats.resume.config.ScoringGrpcProperties;
import ats.scoring.proto.ScoringServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** gRPC-канал к scoring-service. Соединение ленивое: без вызова сеть не трогается. */
@Configuration(proxyBeanMethods = false)
public class ScoringGrpcConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel scoringChannel(ScoringGrpcProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.getTarget()).usePlaintext().build();
    }

    @Bean
    ScoringServiceGrpc.ScoringServiceBlockingStub scoringStub(ManagedChannel scoringChannel) {
        return ScoringServiceGrpc.newBlockingStub(scoringChannel);
    }
}
