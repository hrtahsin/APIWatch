package com.hasan.apiwatch.config;

import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "apiwatch.demo-data.enabled", havingValue = "true")
public class DemoDataConfig {

    @Bean
    ApplicationRunner seedDemoServices(
            MonitoredServiceRepository repository,
            @Value("${apiwatch.demo-data.base-url}") String baseUrl
    ) {
        return args -> {
            List<MonitoredService> demoServices = List.of(
                    service("Local Healthy API", baseUrl + "/api/mock/healthy", 2000, 3),
                    service("Local Slow API", baseUrl + "/api/mock/slow", 2000, 3),
                    service("Local Failing API", baseUrl + "/api/mock/failing", 2000, 2),
                    service("GitHub API", "https://api.github.com", 3000, 3),
                    service(
                            "JSONPlaceholder",
                            "https://jsonplaceholder.typicode.com/posts/1",
                            3000,
                            3
                    )
            );
            demoServices.stream()
                    .filter(service -> !repository.existsByNameIgnoreCase(service.getName()))
                    .forEach(repository::save);
        };
    }

    private MonitoredService service(
            String name,
            String url,
            int timeoutMs,
            int failureThreshold
    ) {
        MonitoredService service = new MonitoredService();
        service.setName(name);
        service.setUrl(url);
        service.setMethod(HttpMethodType.GET);
        service.setExpectedStatusCode(200);
        service.setTimeoutMs(timeoutMs);
        service.setFailureThreshold(failureThreshold);
        service.setActive(true);
        return service;
    }
}
