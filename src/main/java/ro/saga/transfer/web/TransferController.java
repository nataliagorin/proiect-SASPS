package ro.saga.transfer.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.saga.transfer.bank.Banks;
import ro.saga.transfer.bank.TransferRecord;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferService;
import ro.saga.transfer.observability.TransferMetrics;

import java.util.Map;
import java.util.Optional;

/**
 * REST controller for executing transfers and querying by idempotency key.
 */
@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final Banks banks;
    private final Map<String, TransferService> variants;
    private final TransferMetrics metrics;
    private final String activeVariantName;

    public TransferController(
            Banks banks,
            Map<String, TransferService> variants,
            TransferMetrics metrics,
            @Value("${app.web.variant:V1}") String activeVariantName) {
        this.banks = banks;
        this.variants = variants;
        this.metrics = metrics;
        this.activeVariantName = activeVariantName;
    }

    @PostMapping
    public ResponseEntity<?> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateTransferRequestBody body) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
        }

        TransferRequest request;
        try {
            request = new TransferRequest(
                    idempotencyKey,
                    body.fromBank(),
                    body.fromAccountId(),
                    body.toBank(),
                    body.toAccountId(),
                    body.amount());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        TransferService service = variants.get(activeVariantName);
        if (service == null) {
            log.error("Configured transfer variant '{}' not found among available beans: {}", activeVariantName, variants.keySet());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Transfer variant '" + activeVariantName + "' not configured"));
        }

        TransferService timedService = metrics.timed(activeVariantName, service);
        TransferResult result = timedService.transfer(request);

        HttpStatus status = switch (result.outcome()) {
            case COMPLETED -> HttpStatus.OK;
            case REJECTED_INSUFFICIENT_FUNDS, REJECTED_FRAUD -> HttpStatus.UNPROCESSABLE_ENTITY;
            case COMPENSATED -> HttpStatus.CONFLICT;
            case FAILED_TECHNICAL -> HttpStatus.SERVICE_UNAVAILABLE;
        };

        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/{idempotencyKey}")
    public ResponseEntity<TransferRecord> getTransfer(@PathVariable("idempotencyKey") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Search for transfer by idempotency key across Alpha Bank first, then Beta Bank
        Optional<TransferRecord> record = banks.of(Bank.ALPHA).findTransfer(idempotencyKey);
        if (record.isEmpty()) {
            record = banks.of(Bank.BETA).findTransfer(idempotencyKey);
        }

        return record.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
