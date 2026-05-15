package com.example.benchmark.config;

import com.example.benchmark.Transaction;
import com.example.benchmark.item.TransactionXmlReader;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Step 3 configuration: reads the exported XML file and bulk-inserts
 * into {@code transactions_import} using StAX + JDBC (chunk size = 1 000).
 */
@Configuration
public class XmlImportConfig {

    @Value("${benchmark.xml.path:#{systemProperties['java.io.tmpdir']}/transactions_export.xml}")
    private String xmlPath;

    /**
     * Reads transactions from the XML file produced by Step 2.
     */
    @Bean
    public TransactionXmlReader xmlReader() {
        return new TransactionXmlReader(xmlPath);
    }

    /**
     * Writes transactions into {@code transactions_import} via batch INSERT.
     */
    @Bean
    public JdbcBatchItemWriter<Transaction> importWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Transaction>()
            .dataSource(dataSource)
            .sql("INSERT INTO transactions_import " +
                 "(transaction_id, amount, currency, timestamp, " +
                 " account_from, account_to, status, amount_eur) " +
                 "VALUES " +
                 "(:transactionId, :amount, :currency, :timestamp, " +
                 " :accountFrom, :accountTo, :status, :amountEur)")
            .beanMapped()
            .build();
    }

    /**
     * Step 3: XML → PostgreSQL (transactions_import), chunk = 1 000.
     */
    @Bean
    public Step step3(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      TransactionXmlReader xmlReader,
                      JdbcBatchItemWriter<Transaction> importWriter) {
        return new StepBuilder("xmlToPostgresImportStep", jobRepository)
            .<Transaction, Transaction>chunk(1_000, transactionManager)
            .reader(xmlReader)
            .stream(xmlReader)
            .writer(importWriter)
            .build();
    }
}
