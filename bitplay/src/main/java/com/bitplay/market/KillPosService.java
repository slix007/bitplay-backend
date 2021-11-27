package com.bitplay.market;

import com.bitplay.arbitrage.posdiff.NtUsdRecoveryService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.xchange.dto.Order.OrderStatus;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        final SlackNotifications slackNotifications = marketService.getSlackNotifications();
        slackNotifications.sendNotify(NotifyType.KILLPOS_NOTIFY, msg);

        // 1) Срабатывает механизм автоматического прожатия кнопки close_all_pos mkt у соответствующей биржи;
        //2) В случае успешного первого действия, непосредственно после него срабатывает автоматическое действие recovery_nt_usd 26nv19 Кнопка recovery nt_usd со следующими особенностями:

        boolean isSuccessful = true;
        final TradeResponse tradeResponse = marketService.closeAllPos();
        final String orderId = tradeResponse.getOrderId();
        if (marketService.isOrderNullOrCancelled(orderId)) {
            isSuccessful = false;
        } else if (marketService.isOrderInProgress(orderId)) {
            marketService.cancelOrderSync(orderId, "KILLPOS:closeAllPos:Error:doCancel");
            isSuccessful = false;
        }
        final TradeResponse leftR = tradeResponse.getLeftTradeResponse();
        if (leftR != null) {
            final MarketServicePreliq leftM = marketService.getTheOtherMarket();
            if (leftM.isOrderNullOrCancelled(leftR.getOrderId())) {
                isSuccessful = false;
            } else if (leftM.isOrderInProgress(leftR.getOrderId())) {
                leftM.cancelOrderSync(orderId, "KILLPOS:closeAllPos:Error:doCancel");
                isSuccessful = false;
            }
        }

        // SUCCESS:
        //if (tradeResponse.getOrderId() != null) {

        if (marketService.getArbitrageService().areBothOkex()) {
            slackNotifications.sendNotify(NotifyType.AUTO_RECOVERY_NOTIFY,
                    String.format("%s %s no AUTO_RECOVERY (both okex)", counterForLogs, marketService.getNameWithType()));
        } else {
            slackNotifications.sendNotify(NotifyType.AUTO_RECOVERY_NOTIFY,
                    String.format("%s %s starting AUTO_RECOVERY", counterForLogs, marketService.getNameWithType()));
            doAutoRecovery();
        }

        return isSuccessful;
    }

    private void doAutoRecovery() {
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
    }
}
