package com.sum.caller_service.service;

import org.springframework.stereotype.Service;

import com.sum.caller_service.feign_clients.CallerClient;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallerService {

    private final CallerClient callerClient;

    // Apply Resilience4J things on the caller method
    // The "name" param in the annotations is to be used in .yaml file
    @Retry(
        name = "callerServiceRetry"
        // , fallbackMethod = "fallback"
    )
    @CircuitBreaker(
        name = "callerServiceRetry"
        , fallbackMethod = "fallback"
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
    public String fallback(Exception e) {
        System.out.println("------------------------------- Fallback -------------------------------");
        return "Callee service had failed: " + e.getMessage();
    }
}
