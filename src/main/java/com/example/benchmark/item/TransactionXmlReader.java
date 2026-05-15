package com.example.benchmark.item;

import com.example.benchmark.Transaction;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Reads transactions from an XML file produced by {@link TransactionXmlWriter}.
 *
 * <p>Parses {@code <transaction>} elements one at a time using {@link XMLStreamReader}.
 * No JAXB, no reflection — pure pull-parser iteration.
 */
public class TransactionXmlReader implements ItemStreamReader<Transaction> {

    private final String path;
    private XMLStreamReader xmlReader;
    private BufferedInputStream in;

    public TransactionXmlReader(String path) {
        this.path = path;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            in = new BufferedInputStream(new FileInputStream(path), 256 * 1024);
            try {
                XMLInputFactory factory = XMLInputFactory.newFactory();
                factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
                xmlReader = factory.createXMLStreamReader(in, "UTF-8");
            } catch (XMLStreamException e) {
                try { in.close(); } catch (IOException ignored) { /* best-effort */ }
                throw new ItemStreamException("Cannot open XML input: " + path, e);
            }
        } catch (IOException e) {
            throw new ItemStreamException("Cannot open XML input: " + path, e);
        }
    }

    @Override
    public Transaction read() throws XMLStreamException {
        while (xmlReader.hasNext()) {
            int event = xmlReader.next();
            if (event == XMLStreamConstants.START_ELEMENT
                    && "transaction".equals(xmlReader.getLocalName())) {
                return parseTransaction();
            }
        }
        return null; // end of file
    }

    private Transaction parseTransaction() throws XMLStreamException {
        Transaction t = new Transaction();
        String currentElement = null;

        while (xmlReader.hasNext()) {
            int event = xmlReader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT ->
                    currentElement = xmlReader.getLocalName();
                case XMLStreamConstants.CHARACTERS -> {
                    if (currentElement == null) break;
                    String text = xmlReader.getText();
                    switch (currentElement) {
                        case "transaction_id" -> t.setTransactionId(text);
                        case "amount"         -> t.setAmount(Double.parseDouble(text));
                        case "currency"       -> t.setCurrency(text);
                        case "timestamp"      -> t.setTimestamp(text);
                        case "account_from"   -> t.setAccountFrom(text);
                        case "account_to"     -> t.setAccountTo(text);
                        case "status"         -> t.setStatus(text);
                        case "amount_eur"     -> t.setAmountEur(Double.parseDouble(text));
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if ("transaction".equals(xmlReader.getLocalName())) {
                        return t;
                    }
                    currentElement = null;
                }
            }
        }
        return t;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // no state to checkpoint
    }

    @Override
    public void close() throws ItemStreamException {
        if (xmlReader != null) {
            try { xmlReader.close(); } catch (XMLStreamException e) { /* best-effort */ }
        }
        if (in != null) {
            try { in.close(); } catch (IOException e) { /* best-effort */ }
        }
    }
}
