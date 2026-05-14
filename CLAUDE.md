# CLAUDE.md — sbrs-java-bench

Java/Spring Batch reference benchmark for **Spring Batch RS**.  
Used to measure and compare batch processing performance between Spring Batch (Java) and spring-batch-rs (Rust).

> **Source**: Benchmark code originates from `sbrs-lib/benchmark/java/` and is maintained here as a standalone repo.

## Benchmark Scenario

**10 million transaction ETL pipeline** — CSV → PostgreSQL → XML

1. Generate or read 10M transaction records from CSV
2. Load into PostgreSQL via Spring Batch chunk processing
3. Export from PostgreSQL to XML

This mirrors the same workload implemented in `sbrs-lib` (Rust), allowing a direct apples-to-apples comparison.

## Stack

- **Java 25**
- **Spring Boot 4.x** + **Spring Batch**
- **PostgreSQL** (via JDBC)
- **Maven** (build tool)

## Essential Commands

```bash
# Build
mvn clean package

# Run benchmark (requires PostgreSQL running)
mvn spring-boot:run

# Run with custom PostgreSQL URL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/bench mvn spring-boot:run
```

## Configuration

Edit `src/main/resources/application.properties` to adjust:
- Database connection URL, user, password
- Chunk size
- Input CSV path

## Project Structure

```
src/main/
├── java/com/example/benchmark/
│   ├── BenchmarkApplication.java   # Entry point
│   ├── Transaction.java            # Domain model
│   ├── TransactionProcessor.java   # Item processor
│   ├── DataGenerator.java          # Test data generator
│   └── config/
│       ├── BatchConfig.java        # Main batch job configuration
│       └── XmlExportConfig.java    # XML export step
└── resources/
    ├── application.properties
    └── schema.sql                  # PostgreSQL schema
```

## Relationship to sbrs-lib

The Rust equivalent of this benchmark lives in `../sbrs-lib`. When modifying the benchmark scenario (chunk size, data volume, processing logic), apply the equivalent change to both repos so comparisons remain valid.
