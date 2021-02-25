package com.bitplay.market;

import com.bitplay.arbitrage.posdiff.NtUsdRecoveryService;
import com.bitplay.market.model.TradeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class KillPosService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final MarketServicePreliq marketService;

    public boolean doKillPos(String counterForLogs) {
        final String msg = String.format("%s %s KILLPOS", counterForLogs, marketService.getNameWithType());
        log.error(msg);
        warningLogger.error(msg);
        marketService.getTradeLogger().info(msg);

        // 1) Срабатывает механизм автоматического прожатия кнопки close_all_pos mkt у соответствующей биржи;
        //2) В случае успешного первого действия, непосредственно после него срабатывает автоматическое действие recovery_nt_usd 26nv19 Кнопка recovery nt_usd со следующими особенностями:

        final TradeResponse tradeResponse = marketService.closeAllPos();
        final String orderId = tradeResponse.getOrderId();
        if (orderId == null ||
                (marketService.getOpenOrders().stream()
                        .filter(fplayOrder -> orderId.equals(fplayOrder.getOrderId()))
                        .allMatch(fplayOrder -> fplayOrder.getOrderDetail().getOrderStatus() == OrderStatus.CANCELED))) {
            // FAIL:
            return false;
        } else if (marketService.getOpenOrders().stream()
                .filter(fplayOrder -> orderId.equals(fplayOrder.getOrderId()))
                .anyMatch(fplayOrder -> (
                                fplayOrder.getOrderDetail().getOrderStatus() == OrderStatus.NEW
                                        || fplayOrder.getOrderDetail().getOrderStatus() == OrderStatus.PENDING_NEW
                                        || fplayOrder.getOrderDetail().getOrderStatus() == OrderStatus.PARTIALLY_FILLED
                        )
                )) {
            // cancel the order:
            marketService.cancelOrderSync(orderId, "KILLPOS:closeAllPos:Error:doCancel");
            return false;
        }

        // SUCCESS:
        //if (tradeResponse.getOrderId() != null) {
        final NtUsdRecoveryService ntUsdRecoveryService = marketService.getArbitrageService().getNtUsdRecoveryService();

        final Future<String> stringFuture = ntUsdRecoveryService.tryRecoveryAfterKillPos(marketService);
        String res;
        try {
            res = stringFuture.get(10, TimeUnit.SECONDS);
            marketService.getTradeLogger().info(res);
            log.info(res);
        } catch (Exception e) {
            marketService.getTradeLogger().info("recovery_nt_usd Error:" + e.getMessage());
            log.error("recovery_nt_usd Error", e);
        }

        return true;
    }
}
