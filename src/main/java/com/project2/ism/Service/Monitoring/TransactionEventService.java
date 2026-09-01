package com.project2.ism.Service.Monitoring;

import com.project2.ism.Enum.TransactionEventStatus;
import com.project2.ism.Enum.TransactionSourceType;
import com.project2.ism.Model.Monitoring.TransactionEvent;
import com.project2.ism.Repository.TransactionEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single write-side entry point for the Transaction Monitoring feature.
 * Every money-movement service (Payout, BBPS, Settlement, Wallet Adjustment)
 * calls recordEvent(...) at each status change; this is what lets a future
 * payment rail plug into monitoring with one call instead of a bespoke
 * integration.
 *
 * Deliberately fails soft: a monitoring write/eval problem must never break
 * the real money-movement flow it's observing.
 */
@Service
public class TransactionEventService {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventService.class);

    private final TransactionEventRepository transactionEventRepository;
    private final RuleEvaluationService ruleEvaluationService;

    public TransactionEventService(TransactionEventRepository transactionEventRepository,
                                    RuleEvaluationService ruleEvaluationService) {
        this.transactionEventRepository = transactionEventRepository;
        this.ruleEvaluationService = ruleEvaluationService;
    }

    public void recordEvent(TransactionSourceType sourceType, Long sourceId, String initiatorType,
                             Long initiatorId, BigDecimal amount, TransactionEventStatus status, String metadata) {
        recordEvent(sourceType, sourceId, initiatorType, initiatorId, amount, status, metadata, null);
    }

    // Overload for sources that carry a card network (settlement/commission
    // from a card-present transaction). Existing callers that have no card
    // involved keep using the shorter overload above unchanged.
    public void recordEvent(TransactionSourceType sourceType, Long sourceId, String initiatorType,
                             Long initiatorId, BigDecimal amount, TransactionEventStatus status, String metadata,
                             String cardBrand) {
        try {
            TransactionEvent event = new TransactionEvent();
            event.setSourceType(sourceType);
            event.setSourceId(sourceId);
            event.setInitiatorType(initiatorType);
            event.setInitiatorId(initiatorId);
            event.setAmount(amount);
            event.setStatus(status);
            event.setMetadata(metadata);
            event.setCardBrand(cardBrand);
            event.setOccurredAt(LocalDateTime.now());

            event = transactionEventRepository.save(event);

            log.debug("Recorded transaction event id={} source={}:{} initiator={}:{} status={} cardBrand={}",
                    event.getId(), sourceType, sourceId, initiatorType, initiatorId, status, cardBrand);

            ruleEvaluationService.evaluateInstantRules(event);

        } catch (Exception e) {
            // Monitoring must never take down the payment flow it's watching.
            log.error("Failed to record transaction event. source={}:{} initiator={}:{} status={}: {}",
                    sourceType, sourceId, initiatorType, initiatorId, status, e.getMessage(), e);
        }
    }
}
