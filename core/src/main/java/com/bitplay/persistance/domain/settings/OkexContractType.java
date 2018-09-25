package com.bitplay.persistance.domain.settings;

import info.bitrich.xchangestream.okex.dto.Tool;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okcoin.FuturesContract;

@AllArgsConstructor
@Getter
public enum OkexContractType implements ContractType {

    BTC_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01)),
    BTC_NextWeek(FuturesContract.NextWeek, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01)),
    BTC_Quarter(FuturesContract.Quarter, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01)),
    ETH_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001)),
    ETH_NextWeek(FuturesContract.NextWeek, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001)),
    ETH_Quarter(FuturesContract.Quarter, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001)),
    ;

    private FuturesContract futuresContract;
    private CurrencyPair currencyPair;
    private BigDecimal tickSize;

    public Tool getBaseTool() {
        String baseTool = currencyPair.base.getCurrencyCode();
        return Tool.valueOf(baseTool);
    }

    /**
     * Examples: BTC0907, ETH0831
     */
    public String getContractName() {
        String toolName = getBaseTool().toString().toUpperCase();
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final String dayString = getExpString(now);
        return toolName + dayString;
    }

    String getExpString(LocalDateTime now) {
        // The last four digits of a futures contract name indicate the delivery date.
        // The form is (20180928)(month,date).Contracts will be settled every Friday at 800 AM UTC (400 PM China CST).
        LocalDateTime expTime;
        if (futuresContract == FuturesContract.ThisWeek) {
            // weekly BTC0831 ->
            expTime = LocalDateTime.of(2018, 8, 31, 8, 0, 0);
            while (now.isAfter(expTime)) {
                expTime = expTime.plusDays(7);
            }
        } else if (futuresContract == FuturesContract.NextWeek) {
            // bi-weekly  - always next week! BTC0907 ->
            expTime = LocalDateTime.of(2018, 8, 31, 8, 0, 0);
            while (now.isAfter(expTime)) {
                expTime = expTime.plusDays(7);
            }
            expTime = expTime.plusDays(7);
        } else if (futuresContract == FuturesContract.Quarter) {
            // Quarterly BTC0928 ->
            expTime = LocalDateTime.of(2018, 9, 28, 8, 0, 0);
            // use next Time when bi-weekly==Quarterly
            final LocalDateTime plus2Weeks = now.plusDays(14);
            while (plus2Weeks.isAfter(expTime)) {
                expTime = expTime.plusDays(28 * 3 + 7);
            }
        } else {
            throw new IllegalArgumentException("Illegal futuresContract " + futuresContract);
        }
        int monthValue = expTime.getMonthValue();
        int dayOfMonth = expTime.getDayOfMonth();
        return String.format("%s%s",
                monthValue < 10 ? "0" + monthValue : monthValue,
                dayOfMonth < 10 ? "0" + dayOfMonth : dayOfMonth
        );
    }

    public boolean isEth() {
        return this.name().startsWith("ETH");
    }
}
