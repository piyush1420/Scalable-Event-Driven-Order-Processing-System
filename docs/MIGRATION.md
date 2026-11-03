# How the services were ported to Java

The services started as Go prototypes, one homework at a time (HW5–HW8 of CS6650). This repo merges
them into one project and rewrites every service in **Java 21 + Spring Boot 3.5**. The goal was a
*faithful* port: same endpoints, same JSON, same error messages, same timings, same edge cases.

## 1. Dependency analysis (done before writing any Java)

| Original file | Libraries it used | Depends on | Used by | Java replacement |
|---|---|---|---|---|
| `inventory-service/main.go` | Gin, `sync/atomic`, `math/rand` | nothing | product-api, resilience test | Spring MVC, `AtomicLong`/`AtomicReference`, `ThreadLocalRandom` |
| `product-api/main.go` | Gin, `sync.Map`, `sync.Mutex`, `net/http` client | inventory-service (HTTP JSON) | ALB, Locust search test | `ConcurrentHashMap`, `ReentrantLock`, `java.net.http.HttpClient`, `Semaphore` |
| `order-receiver/main.go` | Gin, aws-sdk-go v1 (SNS), uuid | SNS topic | processors (message contract), Locust order test | AWS SDK for Java v2 (`sns`), `UUID`, fair `Semaphore(1)` |
| `order-processor/main.go` | aws-sdk-go v1 (SQS), Gin (health only) | SQS queue, receiver's message format | — | SDK v2 (`sqs` + Apache HTTP client), virtual threads, `SmartLifecycle` |
| `lambda-processor/main.go` | aws-lambda-go | SNS event, receiver's message format | — | `aws-lambda-java-core` + `aws-lambda-java-events` (no Spring, fast start) |
| `shopping-cart/main.go` | Gin, `database/sql`, go-sql-driver/mysql | MySQL | HW8 test scripts | JDBC + HikariCP + MySQL Connector/J (same SQL) |
| `shopping-cart-dynamo/main.go` | Gin, aws-sdk-go-v2 (DynamoDB, attributevalue) | DynamoDB table | HW8 test scripts | SDK v2 `dynamodb` with an explicit attribute codec |

Porting order followed the folders and, inside a homework, the dependencies: inventory → product-api;
order-receiver (produces messages) → order-processor → lambda-processor (consume them);
MySQL cart → DynamoDB cart.

## 2. How correctness was checked

1. **Unit and HTTP tests** for every service (`./mvnw verify`: 188 tests). Slow parts (the 3–10 s
   sleeps, randomness, clocks, AWS, the database) are injected, so the tests run in seconds.
2. **Differential tests**: the original Go binary and the new Java service were started side by side
   and sent the *same* requests, including bad input (`+7`, `1.5`, `"5"`, `null`, empty bodies,
   2 KB keys, numbers too big for MySQL `INT`). Status codes and JSON bodies were compared:

   | Service | Cases | Result |
   |---|---|---|
   | inventory-service | 23 | identical (incl. 10 s hang in crashed mode) |
   | product-api | 47 | identical (incl. 2 s fail-fast, circuit opening on the 5th failure) |
   | order-receiver | 16 | identical except a wrong HTTP method (405 vs 404, see §4); SNS messages have the same fields in the same order |
   | order-processor | 2 runs, 13 messages | same orders processed, same ones rejected, queues emptied |
   | lambda-processor | 5 records in the AWS Lambda runtime emulator | identical log lines |
   | shopping-cart (MySQL 8.4) | 37 | identical, even the auto-increment item IDs |
   | shopping-cart-dynamo (DynamoDB Local) | 21 | identical; same stored attribute types |

3. **The original Python scripts** (MM2 resilience test, HW8 MySQL/DynamoDB scripts) were run
   unchanged against the Java services and behave as in the reports.

## 3. Behaviour that was kept on purpose

- **Validation order and messages**, e.g. the cart checks the ID, then the body, then the values.
- **Whole-number parsing**: only an optional sign and ASCII digits (`+3` is accepted, `1.5`, ` 1`
  and non-ASCII digits are not), 64-bit range.
- **JSON binding**: keys match case-insensitively, unknown keys are ignored, no type coercion
  (`"5"` is not a number, `5` is not a string, `1.5` is not an integer), `null` leaves the zero
  value, an empty body is an error. Queue messages must be exactly one JSON value.
- **Search** looks at the first 101 generated products and returns at most 20 (12 matches for
  `alpha`); `search_time` uses the same compact format (`823.75µs`).
- **Circuit breaker**: 5 failures → OPEN, more than 15 s after the last failure → HALF_OPEN,
  3 successes → CLOSED, one failure → OPEN; the bulkhead is checked first.
- **Fail rate** in `/stats` rounds exact ties to even (`0.25` → `0.2%`).
- **SQL**: the same schema, LEFT JOIN, `ON DUPLICATE KEY UPDATE` and transaction steps, each with
  its own error message.
- **DynamoDB**: one item per cart, eventually consistent reads, read-modify-write on add.

## 4. Intentional differences

| Topic | Before | Now | Why |
|---|---|---|---|
| Wrong HTTP method on a known path | 404 | 405 | standard Spring behaviour |
| Unknown path body | plain text | JSON error | standard Spring behaviour |
| Inventory address | hard-coded `localhost:8081` | `INVENTORY_URL` (default `http://localhost:8081`) | lets Docker Compose run it as a separate container |
| MySQL settings | `DB_DSN` | `DB_URL`, `DB_USER`, `DB_PASSWORD` | JDBC style |
| Whole-number prices in orders | `10` | `10.0` | same number, Java formatting |
| Order `created_at` | server's local zone | always UTC (`…Z`) | both are valid RFC 3339 |
| Lambda log lines | newline added by `log.Printf` | newline added explicitly | otherwise CloudWatch merges lines |
| Task size | 0.25 vCPU / 512 MB | 0.5 vCPU / 1 GB (products task 1 vCPU / 2 GB) | JVM memory needs |
| Lambda cold start | ~70 ms measured in HW7 | expect more with the JVM | consider Lambda SnapStart |

## 5. Infrastructure changes while merging

- One VPC, one ECS cluster, **one load balancer** with path rules instead of one per homework.
- Inventory runs as a **sidecar** in the product task, so `localhost:8081` works on AWS too
  (HW5 never deployed it).
- **One order consumer at a time** (`order_consumer = "ecs"` or `"lambda"`): HW7 subscribed both
  to SNS, so every order was processed twice.
- RDS **MySQL 8.4** (8.0 left standard support in July 2026); the password has no default and
  must be set in `terraform.tfvars` (HW8 had `changeme123` in the code).
- Image tags come from a hash of each service's sources, so a code change rolls out new tasks.
- AWS provider 6.x, Docker provider 4.x (BuildKit), ECR repositories can be destroyed with images.
