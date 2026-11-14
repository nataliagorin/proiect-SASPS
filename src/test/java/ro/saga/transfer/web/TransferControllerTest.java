package ro.saga.transfer.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.observability.TransferMetrics;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class TransferControllerTest extends AbstractBankIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private LedgerService ledger;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void resetData() {
        ledger.resetData();
    }

    @Test
    void postTransfer_whenValid_returns200AndCompletes() {
        var response = mockMvc.post()
                .uri("/transfers")
                .header("Idempotency-Key", "web-valid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fromBank": "ALPHA",
                          "fromAccountId": 1,
                          "toBank": "BETA",
                          "toAccountId": 2,
                          "amount": 100.00
                        }
                        """);

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.outcome").isEqualTo("COMPLETED");
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");

        // Verify metrics recorded
        double count = meterRegistry.get(TransferMetrics.TIMER)
                .tag("variant", "V1")
                .tag("outcome", "COMPLETED")
                .timer()
                .count();
        assertThat(count).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void postTransfer_missingIdempotencyKey_returns400() {
        var response = mockMvc.post()
                .uri("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fromBank": "ALPHA",
                          "fromAccountId": 1,
                          "toBank": "BETA",
                          "toAccountId": 2,
                          "amount": 100.00
                        }
                        """);

        assertThat(response).hasStatus4xxClientError();
        assertThat(response).bodyJson().extractingPath("$.error").asString().contains("Idempotency-Key");
    }

    @Test
    void postTransfer_sameBank_returns400() {
        var response = mockMvc.post()
                .uri("/transfers")
                .header("Idempotency-Key", "web-same-bank")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fromBank": "ALPHA",
                          "fromAccountId": 1,
                          "toBank": "ALPHA",
                          "toAccountId": 2,
                          "amount": 100.00
                        }
                        """);

        assertThat(response).hasStatus4xxClientError();
        assertThat(response).bodyJson().extractingPath("$.error").asString().contains("different banks");
    }

    @Test
    void postTransfer_insufficientFunds_returns422() {
        var response = mockMvc.post()
                .uri("/transfers")
                .header("Idempotency-Key", "web-insufficient")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fromBank": "ALPHA",
                          "fromAccountId": 1,
                          "toBank": "BETA",
                          "toAccountId": 2,
                          "amount": 5000.00
                        }
                        """);

        assertThat(response).hasStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response).bodyJson().extractingPath("$.outcome").isEqualTo("REJECTED_INSUFFICIENT_FUNDS");
    }

    @Test
    void getTransfer_whenExists_returns200() {
        // First create transfer
        var postResponse = mockMvc.post()
                .uri("/transfers")
                .header("Idempotency-Key", "web-query-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fromBank": "ALPHA",
                          "fromAccountId": 1,
                          "toBank": "BETA",
                          "toAccountId": 2,
                          "amount": 50.00
                        }
                        """);
        assertThat(postResponse).hasStatusOk();

        // Then query
        var response = mockMvc.get().uri("/transfers/web-query-1");
        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.idempotencyKey").isEqualTo("web-query-1");
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    void getTransfer_whenNotFound_returns404() {
        var response = mockMvc.get().uri("/transfers/non-existent-key");
        assertThat(response).hasStatus(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
