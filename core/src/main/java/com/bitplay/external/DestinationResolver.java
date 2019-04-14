package com.bitplay.external;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DestinationResolver {

    @Autowired
    private HostResolver hostResolver;

    private final static List<String> testServers = Arrays.asList("658", "660", "661");
    private final static List<String> prodServers = Arrays.asList("659", "662", "667", "668", "669");

    private final static List<String> prodServersCoordinator = Arrays.asList("659", "662", "667", "668", "669", "661"); // all prodServers + 661
    private final static List<String> serversTraderActive = Arrays.asList("659", "662", "668", "669");
    private final static List<String> serversTraderActiveEx = Arrays.asList("661", "667");


    // Trader active: сервера 662, 669, 659.
    //Список алармов:
    //1. Сделалась любая коррекция / подгонка.
    //2. Bitmex дошли до stop! по X-rate limits.
    //3. Запрос на ребут по зависанию стакана.
    //4. Бан на бирже (HTTP status was not ok: 403).
    //5. DQL < DQL_open_min
    //6. Совершился любой Preliq
    //7. Любой флаг перешел в Stopped
    //8. Любой флаг перешел в FORBIDDEN
    //9. BusyIsBusy for 6 min любой биржи, Arbitrage state reset.
    //10. Любая из бирж Outside limits
    //11. Stop all actions (MDC и signal limit)
    //12. Last price deviation
    //13. Reconnect / resubscribe Bitmex.
    // UPDATE1: Добавить в Trader active, All test и All prod alarm: началась любая сделка по сигналу ( по кнопке не надо).
    private final static EnumSet<NotifyType> traderActive = EnumSet.of(
            NotifyType.CORR_NOTIFY, NotifyType.ADJ_NOTIFY,
            NotifyType.BITMEX_X_RATE_LIMIT,
            NotifyType.REBOOT_TIMESTAMP_OLD,
            NotifyType.BITMEX_BAN_403, NotifyType.OKEX_BAN_403,
            NotifyType.BITMEX_DQL_OPEN_MIN, NotifyType.OKEX_DQL_OPEN_MIN,
            NotifyType.PRELIQ,
            NotifyType.STOPPED,
            NotifyType.FORBIDDEN,
            NotifyType.BUSY_6_MIN,
            NotifyType.BITMEX_OUTSIDE_LIMITS, NotifyType.OKEX_OUTSIDE_LIMITS,
            NotifyType.STOP_ALL_ACTIONS_BY_MDC_TIMER,
            NotifyType.LAST_PRICE_DEVIATION,
            NotifyType.BITMEX_RECONNECT,
            NotifyType.TRADE_SIGNAL,
            NotifyType.ROUND_IS_NOT_DONE
    );
    //2) Trader passive: сервера 662, 669, 659, 667, 668
    //1. Stop all actions (MDC и signal limit)
    //2. Preliq
    //3. Forbidden
    //4. Любая коррекция (только коррекция, подгонка - не аларм).
    //5. Бан на бирже (HTTP status was not ok: 403)
    //6. Last price deviation
    //7. Флаг Stopped
    private final static EnumSet<NotifyType> traderPassive = EnumSet.of(
            NotifyType.STOP_ALL_ACTIONS_BY_MDC_TIMER,
            NotifyType.PRELIQ,
            NotifyType.FORBIDDEN,
            NotifyType.CORR_NOTIFY,
            NotifyType.BITMEX_BAN_403, NotifyType.OKEX_BAN_403,
            NotifyType.LAST_PRICE_DEVIATION,
            NotifyType.STOPPED
    );

    //3) Coordinator: сервера 662, 669, 659,
    //1. Bitmex дошли до stop! по X-rate limits.
    //2. Запрос на ребут по зависанию стакана.
    //3. Бан на бирже (HTTP status was not ok: 403).
    //4. DQL < DQL_open_min
    //5. Совершился любой Preliq
    //6. Любой флаг перешел в Stopped
    //7. Любой флаг перешел в FORBIDDEN
    //8. Любая из бирж Outside limits
    //9. Last price deviation
    //10. Reconnect / resubscribe Bitmex.
    //11. e_best bitmex / e_best okex = res; 0.4 < res < 1
    private final static EnumSet<NotifyType> coordinator = EnumSet.of(
            NotifyType.BITMEX_X_RATE_LIMIT,
            NotifyType.REBOOT_TIMESTAMP_OLD,
            NotifyType.BITMEX_BAN_403, NotifyType.OKEX_BAN_403,
            NotifyType.BITMEX_DQL_OPEN_MIN, NotifyType.OKEX_DQL_OPEN_MIN,
            NotifyType.PRELIQ,
            NotifyType.STOPPED,
            NotifyType.FORBIDDEN,
            NotifyType.BITMEX_OUTSIDE_LIMITS, NotifyType.OKEX_OUTSIDE_LIMITS,
            NotifyType.LAST_PRICE_DEVIATION,
            NotifyType.BITMEX_RECONNECT,
            NotifyType.E_BEST_VIOLATION
    );


    private final static String LOCAL_CHANNEL = "app-local";
    //    private final static String TEST_CHANNEL_NIGHT = "app-test-night";
    private final static String TRADER_ACTIVE_CHANNEL = "trader-active";
    private final static String TRADER_ACTIVE_EX_CHANNEL = "trader-active-661-667";
    private final static String TRADER_PASSIVE_CHANNEL = "trader-passive";
    private final static String COORDINATOR_CHANNEL = "coordinator";
    private final static String ALL_PROD_CHANNEL = "all-prod";
    private final static String ALL_TEST_CHANNEL = "all-test";

    ToObj defineWhereToSend(NotifyType notifyType) {
        final String hostLabel = hostResolver.getHostname();
        final List<String> channels = new ArrayList<>();
        if (hostLabel.equals(HostResolver.LOCALHOST) || hostLabel.equals(HostResolver.UNKNOWN)) { // local development workaround
            channels.add(LOCAL_CHANNEL);
        } else if (testServers.contains(hostLabel)) {
            channels.add(ALL_TEST_CHANNEL);
        } else if (prodServers.contains(hostLabel)) {
            channels.add(ALL_PROD_CHANNEL);
        }

        if (serversTraderActive.contains(hostLabel) && traderActive.contains(notifyType)) {
            channels.add(TRADER_ACTIVE_CHANNEL);
        }
        if (serversTraderActiveEx.contains(hostLabel) && traderActive.contains(notifyType)) {
            channels.add(TRADER_ACTIVE_EX_CHANNEL);
        }

        if (prodServers.contains(hostLabel) && traderPassive.contains(notifyType)) {
            channels.add(TRADER_PASSIVE_CHANNEL);
        }

        // coordinator
        if (prodServersCoordinator.contains(hostLabel) && coordinator.contains(notifyType)) {
            channels.add(COORDINATOR_CHANNEL);
        }

        return new ToObj(channels, hostLabel);
    }

    @AllArgsConstructor
    @Getter
    static class ToObj {

        private List<String> channels;
        private String hostLabel;
    }

}
