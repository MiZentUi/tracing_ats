package ats.resume.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Отдельный пул для обработки вебхуков hh: HTTP-ответ не ждёт LLM и скоринг. */
@Configuration(proxyBeanMethods = false)
public class HhExecutorConfiguration {

    @Bean("hhExecutor")
    ThreadPoolTaskExecutor hhExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("hh-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
