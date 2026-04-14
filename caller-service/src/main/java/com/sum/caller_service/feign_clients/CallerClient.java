package com.sum.caller_service.feign_clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "caller-client", url = "http://localhost:9090")
public interface CallerClient {

    @GetMapping("/fail")
    public String simulateFailing();
}
