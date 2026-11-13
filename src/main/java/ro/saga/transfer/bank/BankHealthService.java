package ro.saga.transfer.bank;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;
import ro.saga.transfer.config.BetaBankDataSourceConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BankHealthService {

    private final Map<String, DataSource> dataSourcesByBankName;

    public BankHealthService(
            @Qualifier(AlphaBankDataSourceConfig.DATA_SOURCE) DataSource alphaBankDataSource,
            @Qualifier(BetaBankDataSourceConfig.DATA_SOURCE) DataSource betaBankDataSource) {
        Map<String, DataSource> dataSources = new LinkedHashMap<>();
        dataSources.put(AlphaBankDataSourceConfig.BANK_NAME, alphaBankDataSource);
        dataSources.put(BetaBankDataSourceConfig.BANK_NAME, betaBankDataSource);
        this.dataSourcesByBankName = dataSources;
    }

    public BanksHealth checkBanks() {
        Map<String, BankHealth> healthByBankName = new LinkedHashMap<>();
        dataSourcesByBankName.forEach((bankName, dataSource) -> healthByBankName.put(bankName, check(dataSource)));
        return BanksHealth.of(healthByBankName);
    }

    // The pool validates every connection before handing it out, so obtaining one proves the bank is reachable.
    private BankHealth check(DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            return BankHealth.up();
        } catch (SQLException exception) {
            return BankHealth.down(exception.getMessage());
        }
    }
}
