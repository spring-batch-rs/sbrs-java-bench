package com.example.benchmark.item;

import com.example.benchmark.Transaction;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Streams transactions to an XML file using {@link XMLStreamWriter}.
 *
 * <p>Writes a {@code <transactions>} root element containing one
 * {@code <transaction>} element per record. No JAXB reflection.
 *
 * <p>Implements {@link ItemStreamWriter} so Spring Batch can open/close
 * the underlying file stream via the step lifecycle.
 */
public class TransactionXmlWriter implements ItemStreamWriter<Transaction> {

    private final String path;
    private XMLStreamWriter xmlWriter;
    private BufferedOutputStream out;

    public TransactionXmlWriter(String path) {
        this.path = path;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            out = new BufferedOutputStream(new FileOutputStream(path), 256 * 1024);
            try {
                xmlWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(out, "UTF-8");
                xmlWriter.writeStartDocument("UTF-8", "1.0");
                xmlWriter.writeStartElement("transactions");
            } catch (XMLStreamException e) {
                try { out.close(); } catch (IOException ignored) { /* best-effort */ }
                throw new ItemStreamException("Cannot open XML output: " + path, e);
            }
        } catch (IOException e) {
            throw new ItemStreamException("Cannot open XML output: " + path, e);
        }
    }

    @Override
    public void write(Chunk<? extends Transaction> items) throws ItemStreamException, XMLStreamException {
        if (xmlWriter == null) {
            throw new ItemStreamException("Writer not opened — call open() before write()");
        }
        for (Transaction t : items) {
            xmlWriter.writeStartElement("transaction");

            xmlWriter.writeStartElement("transaction_id");
            xmlWriter.writeCharacters(t.getTransactionId());
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("amount");
            xmlWriter.writeCharacters(Double.toString(t.getAmount()));
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("currency");
            xmlWriter.writeCharacters(t.getCurrency());
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("timestamp");
            xmlWriter.writeCharacters(t.getTimestamp());
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("account_from");
            xmlWriter.writeCharacters(t.getAccountFrom());
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("account_to");
            xmlWriter.writeCharacters(t.getAccountTo());
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("status");
            xmlWriter.writeCharacters(t.getStatus());
            xmlWriter.writeEndElement();

            xmlWriter.writeStartElement("amount_eur");
            xmlWriter.writeCharacters(Double.toString(t.getAmountEur()));
            xmlWriter.writeEndElement();

            xmlWriter.writeEndElement(); // </transaction>
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // no state to checkpoint
    }

    @Override
    public void close() throws ItemStreamException {
        if (xmlWriter != null) {
            try {
                xmlWriter.writeEndElement(); // </transactions>
                xmlWriter.writeEndDocument();
                xmlWriter.flush();
                xmlWriter.close();
            } catch (XMLStreamException e) {
                throw new ItemStreamException("Cannot close XML writer", e);
            }
        }
        if (out != null) {
            try { out.close(); } catch (IOException e) { /* best-effort */ }
        }
    }
}
