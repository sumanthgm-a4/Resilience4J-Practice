package com.sum.caller_service.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.sum.caller_service.feign_clients.CallerClient;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallerService {

    private final CallerClient callerClient;

    private static AtomicInteger numberOfInvocations = new AtomicInteger(0);

    // RETRY + CIRCUIT BREAKER

    // Apply Resilience4J things on the caller method
    // The "name" param in the annotations is to be used in .yaml file
    @Retry(
        name = "callerServiceRetry"
        // , fallbackMethod = "fallback"
    )
    @CircuitBreaker(
        name = "callerServiceCB"
        , fallbackMethod = "fallback1"
    )
    public String failableMethod() {
        System.out.println("Calling external client...");
        return callerClient.simulateFailing();
    }

    // Should be in the same class as the annotated method
    // and should have the same name as the one mentioned in the annotation under
    // the param "fallbackMethod"
    // Catches the exception by default
    // Exceptions could be specific too - like FeignException etc
    public String fallback1(Exception e) {
        System.out.println("------------------------------- Retry + CB Fallback -------------------------------");
        return "Callee service had failed: " + e.getMessage();
    }



    // RATE LIMITER

    @RateLimiter(name = "myRateLimiter", fallbackMethod = "fallback2")
    public String demonstrateRateLimiter() {
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Number of method invocations: " + CallerService.numberOfInvocations.incrementAndGet());
        return "Sample Rate Limiter Response";
    }

    public String fallback2(Exception e) {
        System.out.println("------------------------------- Rate Limiter Fallback -------------------------------");
        return "Rate Limiter demo failed: " + e.getMessage();
    }
}
