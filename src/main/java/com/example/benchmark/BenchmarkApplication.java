package com.example.benchmark;

import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point for the Spring Batch Java benchmark.
 *
 * <p>Runs a three-step ETL pipeline:
 * <ol>
 *   <li>Step 1 — reads 10M transactions from CSV, converts currencies to EUR,
 *       normalises statuses, and bulk-inserts into PostgreSQL (chunk = 1 000)</li>
 *   <li>Step 2 — reads PostgreSQL and exports to XML using StAX (chunk = 1 000)</li>
 *   <li>Step 3 — reads XML and bulk-inserts into transactions_import (chunk = 1 000)</li>
 * </ol>
 *
 * <p>Total wall-clock time includes CSV generation.
 */
@SpringBootApplication
public class BenchmarkApplication {

    private static final long TOTAL_RECORDS = 10_000_000L;

    @Value(
        "${benchmark.csv.path:#{systemProperties['java.io.tmpdir']}/transactions.csv}"
    )
    private String csvPath;

    public static void main(String[] args) {
        SpringApplication.run(BenchmarkApplication.class, args);
    }

    /**
     * Defines the benchmark job: Step 1, Step 2, then Step 3.
     */
    @Bean
    public Job benchmarkJob(
        JobRepository jobRepository,
        Step step1,
        Step step2,
        Step step3
    ) {
        return new JobBuilder("transactionBenchmarkJob", jobRepository)
            .start(step1)
            .next(step2)
            .next(step3)
            .build();
    }

    /**
     * Runs the benchmark on application startup:
     * truncates tables, generates CSV (included in total time),
     * executes all three steps, and prints a metrics summary.
     */
    @Bean
    public ApplicationRunner benchmarkRunner(
        JobLauncher jobLauncher,
        Job benchmarkJob,
        DataSource dataSource
    ) {
        return args -> {
            System.err.println(
                "╔══════════════════════════════════════════════════════════╗"
            );
            System.err.println(
                "║  Spring Batch Java — 10M Transaction Benchmark           ║"
            );
            System.err.println(
                "╚══════════════════════════════════════════════════════════╝"
            );
            System.err.println();

            // Clean previous run
            try (var conn = dataSource.getConnection();
                 var stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE TABLE transactions");
                stmt.execute("TRUNCATE TABLE transactions_import");
            }

            // Total wall time includes CSV generation
            long totalStart = System.currentTimeMillis();

            // Generate CSV data
            System.err.printf(
                "[Generate] Writing %,d rows to %s …%n",
                TOTAL_RECORDS,
                csvPath
            );
            long genStart = System.currentTimeMillis();
            DataGenerator.generate(csvPath, TOTAL_RECORDS);
            System.err.printf(
                "[Generate] Done in %.1fs%n%n",
                (System.currentTimeMillis() - genStart) / 1000.0
            );

            // Run batch job
            JobExecution execution = jobLauncher.run(
                benchmarkJob,
                new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters()
            );

            long totalMs = System.currentTimeMillis() - totalStart;

            // Print per-step metrics
            for (StepExecution step : execution.getStepExecutions()) {
                long stepMs = ChronoUnit.MILLIS.between(
                    step.getStartTime(),
                    step.getEndTime()
                );
                double throughput =
                    stepMs > 0 ? step.getWriteCount() / (stepMs / 1000.0) : 0;
                System.err.printf(
                    "[%s] read=%,d  write=%,d  skip=%d  duration=%.1fs  throughput=%.0f rec/s%n",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    stepMs / 1000.0,
                    throughput
                );
            }

            System.err.println();
            System.err.println(
                "╔══════════════════════════════════════════════════════════╗"
            );
            System.err.println(
                "║  BENCHMARK SUMMARY                                       ║"
            );
            System.err.println(
                "╠══════════════════════════════════════════════════════════╣"
            );
            System.err.printf(
                "║  Job status              : %s%n",
                execution.getStatus()
            );
            System.err.printf(
                "║  Total wall-clock time   : %.1fs  (incl. CSV generation)%n",
                totalMs / 1000.0
            );
            System.err.printf(
                "║  Records processed       : %,d%n",
                TOTAL_RECORDS
            );
            System.err.printf(
                "║  Average throughput      : %.0f rec/s%n",
                totalMs > 0 ? TOTAL_RECORDS / (totalMs / 1000.0) : 0
            );
            System.err.println(
                "╚══════════════════════════════════════════════════════════╝"
            );
        };
    }
}
