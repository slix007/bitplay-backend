package com.bitplay.persistance.domain.settings;

import com.bitplay.market.okcoin.OkCoinService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okcoin.FuturesContract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@AllArgsConstructor
@Getter
public enum OkexContractType implements ContractType {

    BTC_Swap(FuturesContract.Swap, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.1), 1),
    BTC_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01), 2),
    BTC_NextWeek(FuturesContract.NextWeek, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01), 2),
    BTC_Quarter(FuturesContract.Quarter, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01), 2),
    BTC_BiQuarter(FuturesContract.BiQuarter, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.01), 2),
    ETH_Swap(FuturesContract.Swap, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.01), 2),
    ETH_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001), 3),
    ETH_NextWeek(FuturesContract.NextWeek, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001), 3),
    ETH_Quarter(FuturesContract.Quarter, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001), 3),
    ETH_BiQuarter(FuturesContract.BiQuarter, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.001), 3),
    ;

    private FuturesContract futuresContract;
    private CurrencyPair currencyPair;
    private BigDecimal tickSize;
    private Integer scale;

    /**
     * BTC or ETH.
     */
    public String getBaseTool() {
        return currencyPair.base.getCurrencyCode().toUpperCase();
    }

    /**
     * Examples: BTC0907, ETH0831
     */
    public String getContractName() {
        if (futuresContract == FuturesContract.Swap) {
            return "SWAP";
        }

        String toolName = getBaseTool();
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final String dayString = getExpString(now, false);
        return toolName + dayString;
    }

    /**
     * Examples: 190907, 190831
     */
    public String getContractNameWithYear() {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return getExpString(now, true);
    }

    String getExpString(LocalDateTime now, boolean withYear) {
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
        } else if (futuresContract == FuturesContract.BiQuarter) {
            expTime = LocalDateTime.of(2018, 9, 28, 8, 0, 0);
            final LocalDateTime plus2Weeks = now.plusDays(14);
            while (plus2Weeks.isAfter(expTime)) {
                expTime = expTime.plusDays(28 * 3 + 7);
            }
            expTime = expTime.plusDays(28 * 3 + 7);
        } else {
            throw new IllegalArgumentException("Illegal futuresContract " + futuresContract);
        }
        int monthValue = expTime.getMonthValue();
        int dayOfMonth = expTime.getDayOfMonth();
        if (withYear) {
            final String yearTwoDigits = String.valueOf(expTime.getYear()).substring(2);
            return String.format("%s%s%s",
                    yearTwoDigits,
                    monthValue < 10 ? "0" + monthValue : monthValue,
                    dayOfMonth < 10 ? "0" + dayOfMonth : dayOfMonth
            );
        }
        return String.format("%s%s",
                monthValue < 10 ? "0" + monthValue : monthValue,
                dayOfMonth < 10 ? "0" + dayOfMonth : dayOfMonth
        );
    }

    @Override
    public boolean isEth() {
        return this.name().startsWith("ETH");
    }

    @Override
    public String getMarketName() {
        return OkCoinService.NAME;
    }

    @Override
    public String getName() {
        return name();
    }

    public boolean isNotSwap() {
        return this.getFuturesContract() != FuturesContract.Swap;
    }

}
