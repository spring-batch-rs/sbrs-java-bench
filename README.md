# sbrs-java-bench

Java/Spring Batch reference benchmark for **[Spring Batch RS](https://crates.io/crates/spring-batch-rs)**.

Implements the same 10-million-row ETL pipeline in Java to provide a direct, apples-to-apples performance comparison with the Rust implementation.

## Benchmark Scenario

**10M financial transactions — CSV → PostgreSQL → XML → PostgreSQL**

| Step | Description |
|---|---|
| Generate | Writes 10M transaction records to a CSV file |
| Step 1 | Load CSV into PostgreSQL via chunk processing (chunk size = 1 000) |
| Step 2 | Export from PostgreSQL to XML using StAX `XMLStreamWriter` (no JAXB) |
| Step 3 | Import XML into `transactions_import` via StAX `XMLStreamReader` + JDBC |

The chunk size, connection pool size (10), and data schema are identical to the Rust benchmark for a fair comparison. Total wall-clock time includes CSV generation.

## Stack

- **Java 25** with virtual threads enabled
- **Spring Boot 4.x** + **Spring Batch 6.x**
- **StAX** (`XMLStreamWriter` / `XMLStreamReader`) for XML — no JAXB, no reflection
- **PostgreSQL** (benchmark data) + **H2** (Spring Batch metadata, in-memory)
- **Maven** for builds

## Prerequisites

- Java 25+
- Maven 3.9+
- Docker (for PostgreSQL)

```bash
# Start PostgreSQL with Docker Compose
docker compose up -d

# For reproducible results, restart with a fresh volume between runs:
docker compose down -v && docker compose up -d
```

## Usage

```bash
# Build
mvn clean package -q -DskipTests

# Run the full benchmark (generate data + Step 1 + Step 2 + Step 3)
java -Xms4g -Xmx4g -XX:+UseG1GC -XX:+AlwaysPreTouch \
  -jar target/spring-batch-benchmark-1.0.0.jar

# Run with a custom PostgreSQL URL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bench \
  java -jar target/spring-batch-benchmark-1.0.0.jar
```

## Configuration

Edit `src/main/resources/application.properties` to adjust connection settings:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/benchmark
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.hikari.maximum-pool-size=10
```

For local overrides without touching the committed config, create `application-local.properties` (gitignored):

```properties
spring.datasource.password=my-local-password
```

## Project Structure

```
src/main/
├── java/com/example/benchmark/
│   ├── BenchmarkApplication.java         # Entry point + job orchestration (3 steps)
│   ├── DataGenerator.java                # Generates 10M CSV rows
│   ├── Transaction.java                  # Domain model (JPA entity)
│   ├── TransactionProcessor.java         # EUR conversion processor
│   ├── item/
│   │   ├── TransactionXmlWriter.java     # StAX streaming XML writer (no JAXB)
│   │   └── TransactionXmlReader.java     # StAX streaming XML reader (no JAXB)
│   └── config/
│       ├── BatchConfig.java              # Step 1: CSV → PostgreSQL
│       ├── XmlExportConfig.java          # Step 2: PostgreSQL → XML
│       └── XmlImportConfig.java          # Step 3: XML → transactions_import
└── resources/
    ├── application.properties
    └── schema.sql                        # Creates transactions + transactions_import tables
```

## Benchmark Results

Measured on macOS (Apple Silicon), PostgreSQL 17-alpine in Docker, chunk size 1 000, pool size 10.
Total wall-clock time includes CSV generation.

### Overall

| Metric | Spring Batch (Java) | Spring Batch RS (Rust) | Rust advantage |
|---|---|---|---|
| **Total pipeline time** | **199.7 s** | **114.1 s** | **1.75×** faster |
| Generate CSV | 6.7 s | 1.8 s | 3.7× |
| Step 1 — CSV → PostgreSQL | 83.4 s | 38.6 s | 2.2× |
| Step 2 — PostgreSQL → XML | 32.5 s | 20.8 s | 1.6× |
| Step 3 — XML → PostgreSQL | 77.1 s | 53.0 s | 1.5× |

### Throughput (records/sec)

| Step | Java | Rust | Ratio |
|---|---|---|---|
| Step 1 — CSV → PostgreSQL | 119 964 | 259 095 | **2.2×** |
| Step 2 — PostgreSQL → XML | 307 560 | 481 773 | **1.6×** |
| Step 3 — XML → PostgreSQL | 129 671 | 188 594 | **1.5×** |
| **Average** | **50 071** | **87 610** | **1.75×** |

### Notes

- **JAXB → StAX migration**: the previous benchmark used `StaxEventItemWriter` + `Jaxb2Marshaller`
  which produced only 37 182 rec/s on Step 2. Switching to direct `XMLStreamWriter` yields
  307 560 rec/s — **8× faster** — closing the Java vs Rust gap on XML from 13.2× to 1.6×.
- Keyset pagination (`WHERE id > :last ORDER BY id LIMIT n`) is used in both Step 2 (Java)
  and Steps 2 & 3 (Rust), avoiding the O(n²) cost of `LIMIT/OFFSET` on large tables.
- Run on a fresh PostgreSQL volume; results vary by hardware and disk speed.

## Related

- **Rust implementation**: [`../sbrs-lib`](../sbrs-lib) — [spring-batch-rs on crates.io](https://crates.io/crates/spring-batch-rs)
- **Documentation**: https://spring-batch-rs.boussekeyt.dev/
