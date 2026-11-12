package ats.resume.infrastructure.screening;

import ats.resume.config.ScreeningProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Пул для асинхронного скрининга пачки. Запрос возвращает 202 и id пакета. */
@Configuration(proxyBeanMethods = false)
public class ScreeningExecutorConfiguration {

    @Bean(name = "screeningExecutor")
    ThreadPoolTaskExecutor screeningExecutor(ScreeningProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerThreads());
        executor.setMaxPoolSize(properties.getWorkerThreads());
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("screening-");
        executor.initialize();
        return executor;
    }
}
