# Resilience4j — Practical + Deep Dive Guide

---

# 🔁 PART 1 — RETRY

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

# 🔌 PART 2 — CIRCUIT BREAKER

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

---

# 🚦 PART 4 - RATE LIMITER

## ⚡ Core Idea
A **RateLimiter** controls how many requests can execute in a given time window.

```
limit-for-period: 5
limit-refresh-period: 10s
```

👉 Means:
- Only **5 executions allowed every 10 seconds**
- After 10s → permits reset

---

## 🔑 What is a Permit?

A **permit** = permission to execute your method.

```
permit = "you are allowed to run now"
```

- 5 permits → only 5 requests can run in that window
- Once used → NOT returned when thread finishes
- Reset only on next window

---

## ❗ Key Rule (MOST IMPORTANT)

```
Permits are NOT released when threads finish
Permits reset ONLY with time
```

---

## 🧠 Thread Behavior

Each request (thread) does:

```
try acquire permit
    ↓
if success → execute method
if fail → wait (timeout-duration)
    ↓
if still no permit → fallback()
```

---

## ⚙️ Your Code

```java
@RateLimiter(name = "myRateLimiter", fallbackMethod = "fallback2")
public String demonstrateRateLimiter() {
    try {
        Thread.sleep(Duration.ofSeconds(1));
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    System.out.println("Number of method invocations: " 
        + CallerService.numberOfInvocations.incrementAndGet());
    return "Sample Rate Limiter Response";
}

public String fallback2(Exception e) {
    System.out.println("------------------------------- Rate Limiter Fallback -------------------------------");
    return "Rate Limiter demo failed: " + e.getMessage();
}
```

---

## ⚙️ Config

```yaml
rateLimiter:
  instances:
    myRateLimiter:
      limit-for-period: 5
      limit-refresh-period: 10s
      timeout-duration: 3s
```

---

## 🧪 Mental Model

Each thread has:

```
arrival_time
deadline = arrival_time + timeout
```

And it asks:

```
Did I get a permit before my deadline?
```

---

## 📊 Timeline Example

### Setup:
- 11 requests
- limit = 5
- timeout = 3s
- window = 10s

### Flow:

```mermaid
sequenceDiagram
    participant R1 as Req1-5
    participant R2 as Req6-11
    participant RL as RateLimiter

    R1->>RL: acquire permit
    RL-->>R1: granted
    R1->>R1: execute (1s)

    R2->>RL: acquire permit
    RL-->>R2: no permits

    R2->>R2: wait (timeout=3s)

    R2-->>RL: timeout expired
    RL-->>R2: fallback
```

---

## 🧠 Why Some Requests Succeed Later

Because:

```
Threads can "wait" for future permits
```

If they survive long enough → they execute

If not → fallback

---

## 🔥 Negative Permits (Internal)

```
0 → no permits
-1 → one thread waiting
-2 → two threads waiting
```

👉 Means:
threads are reserving future execution slots


---

## ⚡ ApacheBench (-c)

```bash
ab -n 11 -c 5 <url>
```

👉 Means:
- Only 5 concurrent requests
- Others are delayed

So requests are NOT truly simultaneous

---

## 🔥 Real Behavior

```
Requests are spread across time windows
```

So:

```
Success = permits_per_window × windows_reached
```

---

## ⚠️ Timeout Purpose

```
timeout-duration = max wait time for a permit
```

- small → fail fast
- large → wait longer
- too large → thread pile-up risk

---

## 🧠 Thread Lifecycle
```mermaid
flowchart TD
    A[Request arrives] --> B[Try acquire permit]
    
    B -->|Permit available| C[Execute method]
    B -->|No permit| D[Wait up to timeout]

    D -->|Permit becomes available| C
    D -->|Timeout expires| E[Fallback executed]
```
---

## 🔥 Key Insights

### 1. Not concurrency control
RateLimiter ≠ Semaphore

### 2. Time-based, not execution-based
Completion doesn’t free permits (only the window refresh does)

### 3. Each request is independent
No batches internally

### 4. It's a race against time
Not a queue

---

## ⚡ One-Liners

- Permit = one execution slot in time window
- Timeout = how long you're willing to wait
- Fallback = thread lost the race
- RateLimiter = time-based token system

---

## 🧠 Final Mental Model

```
Threads race against:
    - permit availability
    - timeout deadline
    - window reset
```

---

## 🧃 Analogy

Juice shop:

- 5 juices per 10 min
- people can wait (timeout)
- if they wait long enough → get juice
- else → leave (fallback)

---

# ⏳ PART 5 - TIME LIMITER

## 🧠 What TimeLimiter Does

TimeLimiter ensures that a caller **does not wait longer than a
configured duration**.

-   It does **NOT** guarantee stopping execution
-   It **times out the caller**, not the worker thread

------------------------------------------------------------------------

## ⚙️ Configuration (Spring Boot YAML)

``` yaml
resilience4j:
  timelimiter:
    instances:
      myTimeLimiter:
        timeoutDuration: 3s
        cancelRunningFuture: true
```

### Key Properties

-   `timeoutDuration`: Max wait time
-   `cancelRunningFuture`: Calls `future.cancel(true)` on timeout

------------------------------------------------------------------------

## 🧵 How It Works

1.  Method returns `CompletableFuture`
2.  TimeLimiter starts timer
3.  If task completes in time → success
4.  If not:
    -   Throws `TimeoutException`
    -   Calls `future.cancel(true)` (if enabled)

------------------------------------------------------------------------

## 🔁 Execution Flow

``` mermaid
sequenceDiagram
    participant Caller
    participant TimeLimiter
    participant WorkerThread

    Caller->>TimeLimiter: Call async method
    TimeLimiter->>WorkerThread: Start task
    TimeLimiter->>TimeLimiter: Start timer (3s)

    alt finishes before timeout
        WorkerThread-->>TimeLimiter: Result
        TimeLimiter-->>Caller: Success
    else timeout occurs
        TimeLimiter-->>Caller: TimeoutException / fallback
        TimeLimiter->>WorkerThread: cancel(true)
    end
```

------------------------------------------------------------------------

## ⚠️ Important: Cancellation is Cooperative

`future.cancel(true)`: - Sends interrupt signal - DOES NOT force kill
thread

### Correct handling

``` java
try {
    Thread.sleep(5000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return "Cancelled";
}
```

### Wrong handling

``` java
catch (InterruptedException e) {
    // ignore
}
```

------------------------------------------------------------------------

## ❗ Common Pitfalls

### 1. Using wrong fallback signature

❌ Incorrect:

``` java
public void fallback()
```

✅ Correct:

``` java
public CompletableFuture<String> fallback(Throwable t)
```

------------------------------------------------------------------------

### 2. Using commonPool for blocking tasks

Default:

``` java
CompletableFuture.supplyAsync(...)
```

Uses: - ForkJoinPool.commonPool()

❌ Not suitable for blocking calls

------------------------------------------------------------------------

### 3. Ignoring interrupts

Leads to: - Threads continue running - Unexpected completions

------------------------------------------------------------------------

## ✅ Best Practices

-   Use custom executor:

``` java
ExecutorService executor = Executors.newFixedThreadPool(5);

// Usage
CompletableFuture.supplyAsync(() -> methodThatTakesTime(), executor);
```

-   Handle interrupts properly
-   Combine with:
    -   Bulkhead (limit threads)
    -   CircuitBreaker (stop failures)

------------------------------------------------------------------------

## 🧠 Mental Model

TimeLimiter says: \> "I will not wait longer than X time"

NOT: \> "I will stop the work"

------------------------------------------------------------------------

## 🧾 Summary

  Aspect           | Behavior
  -----------------| ----------------------------
  Timeout          | Stops waiting
  Cancellation     | Best-effort interrupt
  Thread stopping  | Not guaranteed
  Return type      | CompletableFuture required
  Fallback         | Must match signature

------------------------------------------------------------------------

## 🔥 Final Insight

TimeLimiter controls **latency**, not **execution**.

---

# 🚢 PART 6 - BULKHEADS

## 🔥 What is a Bulkhead?

- A **Bulkhead** isolates parts of your system to prevent failure in one part from cascading to others.
- A **Bulkhead** isolates resources of the system so that one part of the system can't use up all the resources.

Think:

> “Limit how many requests can use this resource at once.”

💡 In this setup, the bulkhead implementation is kept in a **separate Spring Boot project named `resilience4j-bulkhead`**, placed in the same directory as other services.
This reflects real-world usage where bulkheads provide **isolation between services/modules**, not just methods.

---

## ⚠️ Compatibility Note

This setup is **not fully compatible with Spring Boot 4 (yet)**.
Resilience4j support is currently stable with Spring Boot 3.x, so using Boot 4 may lead to:

* Annotation issues
* AOP not triggering correctly
* Unexpected fallback behavior

👉 Stick to Spring Boot 3.x for consistent behavior.

---

## 🧠 How Bulkhead Prevents Failure Cascading

### ❌ Without Bulkhead

```text
Service A → Service B (slow)
          → threads get stuck
          → thread pool exhausted
          → Service A stops responding ❌
```

* All threads get blocked
* Entire service becomes unresponsive
* One failure spreads everywhere

---

### ✅ With Bulkhead

```yaml
Service B bulkhead:
  maxThreadPoolSize: 5
  queueCapacity: 2
```

---

### 🔄 What happens now

```text
Incoming: 100 requests

→ Only 5 run
→ 2 wait
→ Remaining get rejected (fallback)
```

---

### 💡 Result

| Component             | Status            |
| --------------------- | ----------------- |
| Calls to slow service | partially failing |
| Main service          | STILL WORKING ✅   |
| Other endpoints       | NOT affected ✅    |

---

## 🧱 Ship Analogy (Why “Bulkhead”?)

```text
[Compartment A] | [Compartment B] | [Compartment C]
```

If one fills with water:

* It stays contained
* Ship does NOT sink

---

## 🔄 Visual

```mermaid
flowchart LR
    A[Incoming Requests] --> B{Bulkhead}

    B -->|Allowed| C[Call Slow Service]
    B -->|Rejected| D[Fallback Response]

    C --> E[Limited Impact]
    D --> F[Fast Failure]
```

---

## 🔑 Core Mechanisms

### 1. Resource Limiting

* Limits threads / concurrent calls
* Prevents total exhaustion

---

### 2. Fast Failure

Instead of:

```text
wait → timeout → crash
```

You get:

```text
reject → fallback → continue
```

---

### 3. Isolation

Different dependencies get different limits:

```text
DB → bulkhead(5 threads)
API → bulkhead(10 threads)
Cache → bulkhead(20 threads)
```

👉 If DB fails:

* Only DB calls suffer
* API & Cache keep working

---

## 🧠 One-liner

> A bulkhead doesn’t stop failure — it **contains it** so it doesn’t spread.

---

# ⚙️ Types of Bulkheads

## 1️⃣ Semaphore Bulkhead

### 🧠 Idea

Limits **number of concurrent executions**.

* Uses **same calling thread**
* No queue (unless wait duration > 0)
* Simple and lightweight

---

### ⚙️ Config (example)

```yaml
resilience4j:
  bulkhead:
    instances:
      mySemaphoreBulkhead:
        maxConcurrentCalls: 7
        maxWaitDuration: 0s
```

---

### 🔄 Working

```mermaid
flowchart LR
    A[Incoming Requests] --> B{Permits Available?}
    B -->|Yes| C[Execute Method]
    B -->|No| D[Fallback]
```

---

### 🧪 Behavior Example

* Max calls = 7
* Incoming = 10

| Result   | Count |
| -------- | ----- |
| Executed | 7     |
| Rejected | 3     |

---

### ⚠️ Key Points

* No separate threads
* Fast and low overhead
* Can block if wait duration > 0

---

## 2️⃣ ThreadPool Bulkhead

### 🧠 Idea

Isolates execution using a **separate thread pool + queue**

👉 This is where the **true “bulkhead isolation”** happens:

* One overloaded component **does NOT consume all threads**
* Other parts of the system remain unaffected

---

### ⚙️ Config (example)

```yaml
resilience4j:
  threadPoolBulkhead:
    instances:
      myThreadPoolBulkhead1:
        coreThreadPoolSize: 3
        maxThreadPoolSize: 5
        queueCapacity: 2
```

---

### 🔄 Working

```mermaid
flowchart LR
    A[Incoming Requests] --> B{Thread Available?}
    B -->|Yes| C[Run in Pool]
    B -->|No| D{Queue Full?}
    D -->|No| E[Add to Queue]
    D -->|Yes| F[Fallback]
```

---

### 🧪 Capacity Calculation

```text
Total Capacity = maxThreadPoolSize + queueCapacity
               = 5 + 2
               = 7
```

---

### 🧪 Behavior Example

* Incoming = 10

| Stage           | Count |
| --------------- | ----- |
| Running Threads | 5     |
| Queued          | 2     |
| Rejected        | 3     |

---

### ⏱ Execution Timeline

```mermaid
gantt
    title ThreadPool Bulkhead Execution
    dateFormat  s
    axisFormat %S

    section Running
    Req1 :0, 3
    Req2 :0, 3
    Req3 :0, 3
    Req4 :0, 3
    Req5 :0, 3

    section Queued
    Req6 :3, 6
    Req7 :3, 6

    section Rejected
    Req8 :0, 0
    Req9 :0, 0
    Req10 :0, 0
```

---

## 🧠 Important Rules

### ✅ For Semaphore Bulkhead

* Works with normal methods (`String`, etc.)
* Uses calling thread
* No async required

---

### ✅ For ThreadPool Bulkhead

* Must return:

```text
CompletableFuture<T>
```

* Uses internal thread pool
* Supports queueing

---

### ❗ Fallback Rules (Critical)

Fallback must match:

* Same return type
* Same parameters + `Throwable`

---

### Example

```java
// For synchronous
String fallback(Throwable t)

// For async
CompletableFuture<String> fallback(Throwable t)
```

---

## ⚖️ Semaphore vs ThreadPool

| Feature       | Semaphore     | ThreadPool    |
| ------------- | ------------- | ------------- |
| Thread usage  | Caller thread | Separate pool |
| Queue         | ❌             | ✅             |
| Async support | ❌             | ✅             |
| Overhead      | Low           | Higher        |
| Isolation     | Limited       | Strong        |

---

## 🔥 When to Use What

### Use Semaphore when:

* Fast operations
* Low latency required
* Minimal overhead needed

---

### Use ThreadPool when:

* External calls (DB, API)
* Slow operations
* Need isolation between services

---

## 🧪 Testing Behavior (ApacheBench)

Example:

```bash
ab -n 10 -c 10 http://localhost:8080/bulkhead1
```

---

## Expected Outcomes

### Semaphore

* Limited concurrency
* Some fallbacks

### ThreadPool

* Controlled execution
* Queue used
* Some fallbacks
* Multi-wave execution

---

## 🏁 Final Takeaways

* Bulkhead ≠ rate limiter
* It controls **concurrent access**, not request rate
* ThreadPool bulkhead provides **true isolation**
* Semaphore bulkhead provides **lightweight protection**

---

## ⚡ Mental Model

```mermaid
flowchart TD
    A[Requests] --> B{Bulkhead Type}

    B -->|Semaphore| C[Limit Concurrent Calls]
    B -->|ThreadPool| D[Thread Pool + Queue]

    C --> E[Execute or Reject]
    D --> F[Execute / Queue / Reject]
```

---

## 🧠 One-liner

> Semaphore = **limit access**
> ThreadPool = **isolate + control execution**

---

## 🔄 Difference vs Other Resilience4j Components
### 🔁 Retry
- Re-attempts failed calls
### 🔌 CircuitBreaker
- Stops calls when failures high
### 🚦 RateLimiter
- Controls traffic volume, not failures
### ⏳ TimeLimiter
- Times out the caller thread
### 🚢 Bulkhead
- Isolates parts of your service