package com.example.benchmark.config;

import com.example.benchmark.Transaction;
import com.example.benchmark.item.TransactionXmlWriter;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Step 2 configuration: reads all transactions from PostgreSQL (paginated)
 * and writes to an XML file using StAX streaming (chunk size = 1 000).
 */
@Configuration
public class XmlExportConfig {

    @Value("${benchmark.xml.path:#{systemProperties['java.io.tmpdir']}/transactions_export.xml}")
    private String xmlPath;

    /**
     * Reads transactions from PostgreSQL using keyset-based pagination.
     */
    @Bean
    public JdbcPagingItemReader<Transaction> postgresReader(DataSource dataSource) throws Exception {
        return new JdbcPagingItemReaderBuilder<Transaction>()
            .name("postgresTransactionReader")
            .dataSource(dataSource)
            .selectClause("SELECT transaction_id, amount, currency, timestamp, " +
                          "account_from, account_to, status, amount_eur")
            .fromClause("FROM transactions")
            .sortKeys(Map.of("transaction_id", Order.ASCENDING))
            .rowMapper((rs, rowNum) -> {
                Transaction t = new Transaction();
                t.setTransactionId(rs.getString("transaction_id"));
                t.setAmount(rs.getDouble("amount"));
                t.setCurrency(rs.getString("currency"));
                t.setTimestamp(rs.getString("timestamp"));
                t.setAccountFrom(rs.getString("account_from"));
                t.setAccountTo(rs.getString("account_to"));
                t.setStatus(rs.getString("status"));
                t.setAmountEur(rs.getDouble("amount_eur"));
                return t;
            })
            .pageSize(1_000)
            .build();
    }

    /**
     * Writes transactions to XML using StAX — no JAXB, no reflection.
     */
    @Bean
    public TransactionXmlWriter xmlWriter() {
        return new TransactionXmlWriter(xmlPath);
    }

    /**
     * Step 2: PostgreSQL → XML (chunk = 1 000).
     */
    @Bean
    public Step step2(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      JdbcPagingItemReader<Transaction> postgresReader,
                      TransactionXmlWriter xmlWriter) {
        return new StepBuilder("postgrestoXmlStep", jobRepository)
            .<Transaction, Transaction>chunk(1_000, transactionManager)
            .reader(postgresReader)
            .writer(xmlWriter)
            .stream(xmlWriter)
            .build();
    }
}
