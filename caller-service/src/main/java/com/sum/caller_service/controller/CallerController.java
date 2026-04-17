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
}
