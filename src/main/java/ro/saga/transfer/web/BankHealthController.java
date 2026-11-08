package ro.saga.transfer.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.saga.transfer.bank.BankHealthService;
import ro.saga.transfer.bank.BanksHealth;
import ro.saga.transfer.bank.HealthStatus;

@RestController
@RequestMapping("/health")
public class BankHealthController {

    private final BankHealthService bankHealthService;

    public BankHealthController(BankHealthService bankHealthService) {
        this.bankHealthService = bankHealthService;
    }

    @GetMapping("/banks")
    public ResponseEntity<BanksHealth> checkBanks() {
        BanksHealth health = bankHealthService.checkBanks();
        HttpStatus httpStatus = health.status() == HealthStatus.UP ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(health);
    }
}
