package com.sum.resilience4j_bulkhead.service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

@Service
public class SampleService {

    @Bulkhead(name = "mySemaphoreBulkhead", type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "fallback")
    public String demonstrateSemaphoreBulkhead() {
        System.out.println("Method started...............");
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            // TODO: handle exception
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("Method executed..............");
        return "Done";
    } 

    @Bulkhead(name = "myThreadPoolBulkhead1", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "fallback1")
    public CompletableFuture<String> demonstrateThreadPoolBulkhead1() {
        System.out.println("Method started...............");
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("Method executed..............");

        return CompletableFuture.completedFuture("Done");
    } 

    @Bulkhead(name = "myThreadPoolBulkhead2", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "fallback1")
    public CompletableFuture<String> demonstrateThreadPoolBulkhead2() {
        System.out.println("Method started...............");
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.println("Method executed..............");

        return CompletableFuture.completedFuture("Done");
    } 

    public String fallback(Throwable t) {
        System.out.println("FALLBACK.....................");
        return "Fallback called: " + t.getMessage();
    }

    public CompletableFuture<String> fallback1(Throwable t) {
        System.out.println("FALLBACK.....................");
        return CompletableFuture.completedFuture("Fallback: " + t.getMessage());
    }
}
