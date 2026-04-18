package com.sum.callee_service.controller;

import java.time.Duration;

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

    @GetMapping("/bulkhead")
    public String bulkheadDemoMethod1() {
        System.out.println("Method is being executed");
        System.out.println(Thread.currentThread().getName());
        try {
            Thread.sleep(Duration.ofSeconds(2));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("Done");
        return "Done";
    }
}
