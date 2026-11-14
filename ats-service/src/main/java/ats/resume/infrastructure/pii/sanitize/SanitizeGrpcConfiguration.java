package ats.resume.infrastructure.pii.sanitize;

import ats.resume.config.SanitizeProperties;
import ats.sanitize.proto.SanitizeServiceGrpc;
import ats.support.logging.GrpcClientLoggingInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** gRPC-канал к sanitize-service. Соединение ленивое: без вызова сеть не трогается. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ats.pii.sanitize", name = "enabled", havingValue = "true")
public class SanitizeGrpcConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel sanitizeChannel(SanitizeProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.getTarget())
                .usePlaintext()
                .intercept(new GrpcClientLoggingInterceptor("sanitize-service"))
                .build();
    }

    @Bean
    SanitizeServiceGrpc.SanitizeServiceBlockingStub sanitizeStub(ManagedChannel sanitizeChannel) {
        return SanitizeServiceGrpc.newBlockingStub(sanitizeChannel);
    }

    @Bean
    SanitizeClient sanitizeClient(
            SanitizeServiceGrpc.SanitizeServiceBlockingStub sanitizeStub,
            SanitizeProperties properties) {
        return new GrpcSanitizeClient(sanitizeStub, properties);
    }
}
