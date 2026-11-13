package ats.vacancy.dictionary;

import ats.dictionary.proto.DictionaryServiceGrpc;
import ats.support.logging.GrpcClientLoggingInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Канал к dictionary-service. Соединение ленивое: без вызова сеть не трогается. */
@Configuration(proxyBeanMethods = false)
public class DictionaryGrpcConfiguration {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel dictionaryChannel(DictionaryProperties properties) {
        return ManagedChannelBuilder.forTarget(properties.getTarget())
                .usePlaintext()
                .intercept(new GrpcClientLoggingInterceptor("dictionary-service"))
                .build();
    }

    @Bean
    DictionaryServiceGrpc.DictionaryServiceBlockingStub dictionaryStub(
            ManagedChannel dictionaryChannel) {
        return DictionaryServiceGrpc.newBlockingStub(dictionaryChannel);
    }
}
