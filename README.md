# sbrs-java-bench

Java/Spring Batch reference benchmark for **[Spring Batch RS](https://crates.io/crates/spring-batch-rs)**.

Implements the same 10-million-row ETL pipeline in Java to provide a direct, apples-to-apples performance comparison with the Rust implementation.

## Benchmark Scenario

**10M financial transactions — CSV → PostgreSQL → XML**

| Step | Description |
|---|---|
| 1 | Generate or read 10M transaction records from a CSV file |
| 2 | Load into PostgreSQL via Spring Batch chunk processing (chunk size = 1 000) |
| 3 | Export from PostgreSQL to a compressed XML file |

The chunk size, connection pool size (10), and data schema are identical to the Rust benchmark for a fair comparison.

## Stack

- **Java 25** with virtual threads enabled
- **Spring Boot 4.x** + **Spring Batch 6.x**
- **PostgreSQL** (benchmark data) + **H2** (Spring Batch metadata, in-memory)
- **JAXB** for XML export
- **Maven** for builds

## Prerequisites

- Java 25+
- Maven 3.9+
- PostgreSQL running locally (default: `localhost:5432`)

```bash
# Start PostgreSQL with Docker
docker run -d \
  --name bench-pg \
  -e POSTGRES_DB=benchmark \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:17-alpine
```

## Usage

```bash
# Build
mvn clean package -q

# Run the full benchmark (generate data + Step 1 + Step 2)
mvn spring-boot:run

# Run with a custom PostgreSQL URL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bench mvn spring-boot:run

# Run the fat JAR directly
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
│   ├── BenchmarkApplication.java   # Entry point + job orchestration
│   ├── DataGenerator.java          # Generates 10M CSV rows
│   ├── Transaction.java            # Domain model
│   ├── TransactionProcessor.java   # EUR conversion processor
│   └── config/
│       ├── BatchConfig.java        # Step 1: CSV → PostgreSQL
│       └── XmlExportConfig.java    # Step 2: PostgreSQL → XML
└── resources/
    ├── application.properties
    └── schema.sql                  # Creates the transactions table
```

## Related

- **Rust implementation**: [`../sbrs-lib`](../sbrs-lib) — [spring-batch-rs on crates.io](https://crates.io/crates/spring-batch-rs)
- **Documentation**: https://spring-batch-rs.boussekeyt.dev/
