package com.sum.callee_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FailingSimulatorController {

    @GetMapping("/fail")
    public String simulateFailingService() {
        
        if (Math.random() < 0.8) {      // This service fails 70% of the time
            throw new RuntimeException("Failed the service intentionally");
        }

        return "Returned success from the failable service";
    }
}
