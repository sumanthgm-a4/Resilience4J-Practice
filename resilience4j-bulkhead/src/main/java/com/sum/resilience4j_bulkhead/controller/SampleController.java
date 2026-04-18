package com.sum.resilience4j_bulkhead.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sum.resilience4j_bulkhead.service.SampleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SampleController {

    private final SampleService service;

    @GetMapping("/semaphore")
    public String demonstrateSemaphoreBulkhead() {
        return service.demonstrateSemaphoreBulkhead();
    }

    @GetMapping("/bulkhead1")
    public CompletableFuture<String> demonstrateThreadPoolBulkhead1() {
        return service.demonstrateThreadPoolBulkhead1();
    }

    @GetMapping("/bulkhead2")
    public CompletableFuture<String> demonstrateThreadPoolBulkhead2() {
        return service.demonstrateThreadPoolBulkhead2();
    }
}
