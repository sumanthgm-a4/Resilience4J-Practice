# Resilience4j — Practical + Deep Dive Guide

---

# 🧩 PART 1 — RETRY (ISOLATED)

## 📌 What Retry Does

Retry handles **temporary/transient failures** by re-attempting a failed operation.

👉 Think:

> "Maybe it’ll work if I try again"

---

## ⚙️ Retry Configuration

```yaml
resilience4j:
  retry:
    instances:
      callerServiceRetry:
        max-attempts: 3
        wait-duration: 2s
```

---

## 🔄 Retry Flow

```mermaid
sequenceDiagram
    participant Client
    participant Service
    participant Retry
    participant Method
    participant Feign

    Client->>Service: Request
    Service->>Retry: execute
    Retry->>Method: call
    Method->>Feign: HTTP call
    Feign-->>Method: fail
    Method-->>Retry: exception

    Retry->>Method: retry #1
    Method->>Feign: call
    Feign-->>Method: fail

    Retry->>Method: retry #2
    Method->>Feign: call
    Feign-->>Method: fail

    Retry-->>Service: invoke fallback
    Service-->>Client: fallback response
```

---

## ⚡ Retry Behavior

* Total attempts = **3 (1 original + 2 retries)**
* Wait time between retries = **2s**
* Retries on configured exceptions (default: all RuntimeExceptions)
* After max attempts → fallback (if defined) or exception propagates

---

## 🧠 Internal Mechanism

```mermaid
graph LR
    A[Call] --> B{Success?}
    B -->|Yes| C[Return Response]
    B -->|No| D{Attempts Left?}
    D -->|Yes| E[Wait]
    E --> A
    D -->|No| F[Throw Exception]
```

---

## 🚨 Limitations of Retry

* Can **spam failing service**
* Adds **latency**
* No awareness of system health

---

# 🔌 PART 2 — CIRCUIT BREAKER (ISOLATED)

## 📌 What Circuit Breaker Does

Circuit Breaker stops calls when failures are frequent.

👉 Think:

> "Stop trying, system is broken"

---

## ⚙️ Circuit Breaker Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      callerServiceRetry:
        sliding-window-size: 3
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 2
```

---

## 🔄 Circuit Breaker Flow

```mermaid
sequenceDiagram
    participant Client
    participant Service
    participant CB
    participant Method
    participant Feign

    Client->>Service: Request
    Service->>CB: check state

    alt CLOSED
        CB->>Method: allow call
        Method->>Feign: HTTP call

        alt Success
            Feign-->>Method: success
            CB-->>Service: record success
            Service-->>Client: response
        else Failure
            Feign-->>Method: failure
            CB-->>Service: record failure
            Service-->>Client: error/fallback
        end

    else OPEN
        CB-->>Service: short-circuit
        Service-->>Client: fallback

    else HALF_OPEN
        CB->>Method: trial call
        Method->>Feign: HTTP call

        alt Success
            Feign-->>Method: success
            CB-->>Service: close circuit
            Service-->>Client: response
        else Failure
            Feign-->>Method: failure
            CB-->>Service: reopen circuit
            Service-->>Client: fallback
        end
    end
```

---

## 🔌 State Machine

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN : Failure threshold exceeded
    OPEN --> HALF_OPEN : wait-duration elapsed
    HALF_OPEN --> CLOSED : success
    HALF_OPEN --> OPEN : failure
```

---

## ⚡ Circuit Breaker Behavior

* Monitors last **N calls (sliding window)**
* Opens when **failure % > threshold**
* Blocks all calls when OPEN
* Allows limited test calls in HALF_OPEN

---

## 🧠 Internal Mechanism

```mermaid
graph LR
    A[Incoming Request] --> B{State?}
    B -->|CLOSED| C[Allow Call]
    B -->|OPEN| D[Reject Immediately]
    B -->|HALF_OPEN| E[Limited Calls]

    C --> F[Record Success/Failure]
    E --> F
    F --> G{Threshold Breached?}
    G -->|Yes| D
    G -->|No| C
```

---

## 🚨 Benefits

* Prevents system overload
* Enables **fast failure (zero latency)**
* Protects downstream services

---

# ⚡ PART 3 — COMBINING RETRY + CIRCUIT BREAKER

## 🧠 Key Idea

> Retry = handle temporary failures     
> Circuit Breaker = handle repeated failures

---

## ⚠️ Execution Order (Critical)

```java
@Retry(name = "callerServiceRetry")
@CircuitBreaker(name = "callerServiceRetry", fallbackMethod = "fallback")
public String failableMethod() {
    return callerClient.simulateFailing();
}
```

Default Resilience4J aspect order:
* ```Retry( CircuitBreaker( RateLimiter( TimeLimiter( Bulkhead( function)))))```
* Override it so that the CB fallback is given after Retry tries again and again and fails
```yaml
resilience4j:
    retry:
        retryAspectOrder: 2
        # Rest of the config
    circuitbreaker:
        circuitBreakerAspectOrder: 1
        # Rest of the config
```

---

## 🔥 AOP Proxy Chain

```mermaid
graph TD
    A[Client Request] --> B[Spring Proxy Layer]
    B --> C[CircuitBreaker Aspect]
    C --> D[Retry Aspect]
    D --> E[Actual Method]
    E --> F[Feign Call]
```

---

## 🔄 Combined Flow

```mermaid
sequenceDiagram
    participant Client
    participant Proxy
    participant CB
    participant Retry
    participant Method
    participant Feign

    Client->>Proxy: request
    Proxy->>CB: check state

    alt OPEN
        CB-->>Proxy: short-circuit
        Proxy-->>Client: fallback

    else CLOSED or HALF_OPEN
        CB->>Retry: execute

        Retry->>Method: call
        Method->>Feign: HTTP call
        Feign-->>Method: failure

        Retry->>Method: retry attempts
        Method->>Feign: call again
        Feign-->>Method: failure

        Retry-->>CB: final failure

        alt Threshold reached
            CB-->>Proxy: open circuit
            Proxy-->>Client: fallback
        else Below threshold
            CB-->>Proxy: propagate failure
            Proxy-->>Client: error/fallback
        end
    end
```

---

## ⚡ Combined Behavior

* Retry happens **first**
* Circuit Breaker sees **final result**
* After enough failures → Circuit opens
* When OPEN:

  * ❌ No Retry
  * ❌ No Feign call
  * ✅ Immediate fallback

## How the failures are actually recorded in the window of CB here?
* CB records the whole result of the 3 retry attempts as one:
    * E.g.: Retry [fail, fail, fail] (3 fails) --> CB [FAIL] (1 FAIL)
    * And 50% of 3 (~2) such failures in the window OPENs the circuit
* Other CB recording scenarios here:
    * E.g.: Retry [success] (1 success) --> CB [SUCCESS] (1 SUCCESS)
    * E.g.: Retry [fail, success] (1 fail, 1 success) --> CB [SUCCESS] (1 SUCCESS)
---

## ⏱️ Timing Behavior

* OPEN → stays for **10s**
* Then HALF_OPEN
* Allows **2 test calls**
* Decides whether to:
  * CLOSE ✅
  * OPEN again ❌

---

## 🧠 Final Mental Model

```mermaid
graph LR
    A[Request] --> B[Retry Attempts]
    B --> C{Still failing?}
    C -->|Yes| D[Circuit Breaker counts failure]
    D --> E{Threshold reached?}
    E -->|Yes| F[OPEN Circuit]
    F --> G[Fast fallback]
```

---

## ⚡ Key Takeaways

* Retry = "Try again"
* Circuit Breaker = "Stop trying"
* Retry is **outer layer**
* Circuit Breaker is **inner layer**
* Order matters due to **AOP proxy chain**
* When OPEN → system fails **fast and safely**

---

## 🧠 Golden Rule

> "If you don't understand the proxy chain, you don't understand Resilience4j."
