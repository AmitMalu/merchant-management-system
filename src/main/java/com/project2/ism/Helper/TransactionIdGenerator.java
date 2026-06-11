package com.project2.ism.Helper;

import org.hibernate.generator.EventType;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionIdGenerator implements BeforeExecutionGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TransactionIdGenerator.class);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmmss");

    // Thread-safe counter with better overflow handling
    private static final AtomicInteger counter = new AtomicInteger(1);

    // Configurable timezone - could be moved to properties
    private static final ZoneId TIMEZONE = ZoneId.systemDefault();

    // Maximum counter value before reset (999 gives 3 digits)
    private static final int MAX_COUNTER = 999;

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
                           Object currentValue, EventType eventType) {

        // If value already exists, don't regenerate
        if (currentValue != null) {
            return currentValue;
        }

        try {
            String timestamp = ZonedDateTime.now(TIMEZONE).format(DATE_FORMATTER);
            int nextCounter = getNextCounter();

            String idString = timestamp + String.format("%03d", nextCounter);
            Long transactionId = Long.parseLong(idString);

            logger.debug("Generated transaction ID: {}", transactionId);
            return transactionId;

        } catch (Exception e) {
            logger.error("Failed to generate transaction ID", e);
            // Fallback to timestamp only
            return Long.parseLong(ZonedDateTime.now(TIMEZONE).format(DATE_FORMATTER) + "001");
        }
    }

    /**
     * Get next counter value with proper overflow handling
     */
    private int getNextCounter() {
        return counter.updateAndGet(current -> {
            int next = current + 1;
            return next > MAX_COUNTER ? 1 : next;
        });
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    // Testing and monitoring methods
    public static void resetCounter() {
        counter.set(1);
        logger.info("Transaction ID counter reset to 1");
    }

    public static int getCurrentCounter() {
        return counter.get();
    }

    // Method to preview what the next ID would look like
    public static Long previewNextId() {
        String timestamp = ZonedDateTime.now(TIMEZONE).format(DATE_FORMATTER);
        int nextCounter = counter.get() % MAX_COUNTER + 1;
        return Long.parseLong(timestamp + String.format("%03d", nextCounter));
    }
}