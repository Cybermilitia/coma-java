package com.bip.coma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
public class CoturnHealthcheckerApplication {

    @Value("${system.scheduler.poolsize:10}")
    int schedulerPoolSize;

	public static void main(String[] args) {
		SpringApplication.run(CoturnHealthcheckerApplication.class, args);
		
	}
	/*Per proxy group / proxy run syncronization method by using that Pool size. So this is a limitation for number of proxy group.*/
	@Bean
    public TaskScheduler taskScheduler() {
        final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("health-checker-");
        return scheduler;
    }

}
