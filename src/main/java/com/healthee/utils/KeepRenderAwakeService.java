package com.healthee.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class KeepRenderAwakeService {

    private static final String RENDER_HEALTH_URL =
            "https://healthee-fruit-juice-backend.onrender.com"
                    + "/api/v1/utilities/public/health";

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);

        return new RestTemplate(factory);
    }

    // First ping after one minute, then every 13 minutes.
    @Scheduled(
            fixedRate = 13,
            initialDelay = 1,
            timeUnit = TimeUnit.MINUTES
    )
    public void pingRenderHealthEndpoint() {
        log.info("🍍 Sending Render keep-alive ping.");

        try {
            String response = restTemplate.getForObject(
                    RENDER_HEALTH_URL,
                    String.class
            );

            log.info(
                    "✅ Render keep-alive ping successful: {}",
                    response
            );
        } catch (RestClientResponseException exception) {
            log.info(
                    "⚠️ Render keep-alive ping failed | HTTP status={}",
                    exception.getStatusCode().value()
            );
        } catch (RestClientException exception) {
            log.info(
                    "⚠️ Render keep-alive ping failed | reason={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}