package com.sum.caller_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sum.caller_service.service.CallerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CallerController {

    private final CallerService callerService;

    @GetMapping("/fail")
    public String simualteFailing() {
        return callerService.failableMethod();
    }

    @GetMapping("/rate-limit")
    public String simulateRateLimiter() {
        return callerService.demonstrateRateLimiter();
    }

    @GetMapping("/time-limit")
    public String simulateSlowMethod() {
        return callerService.simulateSlowMethod().join();
    }

    @GetMapping("/semaphore-bulkhead")
    public String simulateSemaphoreBulkhead() {
        return callerService.simulateSemaphoreBulkhead();
    }

    @GetMapping("/threadpool-bulkhead-1")
    public String simulateThreadPoolBulkhead1() {
        return callerService.callExternal1();
    }

    @GetMapping("/threadpool-bulkhead-2")
    public String simulateThreadPoolBulkhead2() {
        return callerService.callExternal2().join();
    }

}
