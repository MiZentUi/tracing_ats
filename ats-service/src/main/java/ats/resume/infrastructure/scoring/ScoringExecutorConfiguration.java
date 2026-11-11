package ats.resume.infrastructure.scoring;

import ats.resume.config.ScoringProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Отдельный пул: скоринг при загрузке не должен ждать скрининга пачки. */
@Configuration(proxyBeanMethods = false)
public class ScoringExecutorConfiguration {

    @Bean(name = "scoringExecutor")
    ThreadPoolTaskExecutor scoringExecutor(ScoringProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerThreads());
        executor.setMaxPoolSize(properties.getWorkerThreads());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("scoring-");
        executor.initialize();
        return executor;
    }
}
