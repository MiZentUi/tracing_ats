package ats.resume.infrastructure.vacancy;

import ats.resume.config.VacancyGrpcProperties;
import ats.vacancy.proto.VacancyInternalServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Канал к vacancy-service. Соединение ленивое, вызовы ограничены дедлайном в адаптере. */
@Configuration(proxyBeanMethods = false)
public class VacancyGrpcConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel vacancyChannel(VacancyGrpcProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.getTarget()).usePlaintext().build();
    }

    @Bean
    VacancyInternalServiceGrpc.VacancyInternalServiceBlockingStub vacancyStub(
            ManagedChannel vacancyChannel) {
        return VacancyInternalServiceGrpc.newBlockingStub(vacancyChannel);
    }
}
