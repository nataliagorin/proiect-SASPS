package ro.saga.transfer.bank;

import org.springframework.stereotype.Component;
import ro.saga.transfer.common.Bank;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** {@link BankOperations} by {@link Bank}, so variants do not branch on Alpha/Beta. */
@Component
public class Banks {

    private final Map<Bank, BankOperations> operations = new EnumMap<>(Bank.class);

    public Banks(List<BankOperations> all) {
        all.forEach(bank -> operations.put(bank.bank(), bank));
        for (Bank bank : Bank.values()) {
            if (!operations.containsKey(bank)) {
                throw new IllegalStateException("No BankOperations for " + bank.displayName());
            }
        }
    }

    public BankOperations of(Bank bank) {
        return operations.get(bank);
    }
}
