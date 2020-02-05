package com.bitplay.market;

import com.bitplay.arbitrage.posdiff.NtUsdRecoveryService;
import com.bitplay.market.model.TradeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class KillPosService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final MarketServicePreliq marketService;

    public void doKillPos(String counterForLogs) {
        final String msg = String.format("%s %s KILLPOS", counterForLogs, marketService.getName());
        log.error(msg);
        warningLogger.error(msg);
        marketService.getTradeLogger().info(msg);

        // 1) Срабатывает механизм автоматического прожатия кнопки close_all_pos mkt у соответствующей биржи;
        //2) В случае успешного первого действия, непосредственно после него срабатывает автоматическое действие recovery_nt_usd 26nv19 Кнопка recovery nt_usd со следующими особенностями:

        final TradeResponse tradeResponse = marketService.closeAllPos();
        if (tradeResponse.getOrderId() != null) {
            final NtUsdRecoveryService ntUsdRecoveryService = marketService.getArbitrageService().getNtUsdRecoveryService();

            final Future<String> stringFuture = ntUsdRecoveryService.tryRecoveryAfterKillPos(marketService.getName());
            String res;
            try {
                res = stringFuture.get(10, TimeUnit.SECONDS);
                marketService.getTradeLogger().info(res);
                log.info(res);
            } catch (Exception e) {
                marketService.getTradeLogger().info("recovery_nt_usd Error:" + e.getMessage());
                log.error("recovery_nt_usd Error", e);
            }

        }
    }
}
