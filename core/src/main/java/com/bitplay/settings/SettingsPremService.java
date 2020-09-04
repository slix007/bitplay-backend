package com.bitplay.settings;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.FeeSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsPremService {

    private final SettingsRepositoryService settingsRepositoryService;
    private final ArbitrageService arbitrageService;

    // Auto params
    // WRITE only in the thread "settingsVolatileAutoParamsExecutor"
    // READ from anywhere
    private volatile BigDecimal borderCrossDepth;
    private volatile BigDecimal leftAddBorder;
    private volatile BigDecimal rightAddBorder;

    private int writeToDbCounter = 0;
    private static final int WRITE_TO_DB_COUNTER_MAX = 100;

    @Async("settingsVolatileAutoParamsExecutor")
    public void updateSettings(BigDecimal borderCrossDepth, BigDecimal bAddBorder, BigDecimal oAddBorder) {
        final Settings s = settingsRepositoryService.getSettings();
        final SettingsVolatileMode v = s.getSettingsVolatileMode();
        if (borderCrossDepth != null) {
            v.setBorderCrossDepth(borderCrossDepth);
            this.borderCrossDepth = borderCrossDepth;
        }
        if (bAddBorder != null) {
            v.setBAddBorder(bAddBorder);
            this.leftAddBorder = bAddBorder;
        }
        if (oAddBorder != null) {
            v.setOAddBorder(oAddBorder);
            this.rightAddBorder = oAddBorder;
        }
        settingsRepositoryService.saveSettings(s);
    }

    @Async("settingsVolatileAutoParamsExecutor")
    @EventListener(ObChangeEvent.class)
    public void doCheckObChangeEvent() {
        writeOnInitIfNeeded();
        writeOnObChange();
    }

    private void writeOnObChange() {
        final Settings s = settingsRepositoryService.getSettings();
        final SettingsVolatileMode v = s.getSettingsVolatileMode();
        if (v.getPrem() == null
                || v.getPrem().getBcdPrem() == null
                || v.getPrem().getLeftAddBorderPrem() == null
                || v.getPrem().getRightAddBorderPrem() == null) {
            return;
        }
        final boolean haveBcdPrem = v.getActiveFields().contains(Field.BCD_prem);
        final boolean haveLPrem = v.getActiveFields().contains(Field.L_add_border_prem);
        final boolean haveRPrem = v.getActiveFields().contains(Field.R_add_border_prem);
        if (haveBcdPrem || haveLPrem || haveRPrem) {
            final OrderBook leftOb = arbitrageService.getLeftMarketService().getOrderBook();
            final BigDecimal l_ask1 = leftOb.getAsks().get(0).getLimitPrice();
            final BigDecimal l_bid1 = leftOb.getBids().get(0).getLimitPrice();
            final BigDecimal left_best_sam = (l_ask1.add(l_bid1)).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
            final OrderBook rightOb = arbitrageService.getRightMarketService().getOrderBook();
            final BigDecimal r_ask1 = rightOb.getAsks().get(0).getLimitPrice();
            final BigDecimal r_bid1 = rightOb.getBids().get(0).getLimitPrice();
            final BigDecimal right_best_sam = (r_ask1.add(r_bid1)).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
            final FeeSettings fee = s.getFeeSettings();
            final BigDecimal left_taker_com_pts = fee.getLeftTakerComRate().multiply(left_best_sam)
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            final BigDecimal right_taker_com_pts = fee.getRightTakerComRate().multiply(right_best_sam)
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            final BigDecimal sum_taker_com_pts = left_taker_com_pts.add(right_taker_com_pts);
            //Auto Border cross depth = sum_taker_com_pts + BCD_prem;
            //Auto L_add_border = sum_taker_com_pts + L_add_border_prem.
            //Auto R_add_border = sum_taker_com_pts + R_add_border_prem.
            if (haveBcdPrem) {
                borderCrossDepth = sum_taker_com_pts.add(v.getPrem().getBcdPrem());
            }
            if (haveLPrem) {
                leftAddBorder = sum_taker_com_pts.add(v.getPrem().getLeftAddBorderPrem());
            }
            if (haveRPrem) {
                rightAddBorder = sum_taker_com_pts.add(v.getPrem().getRightAddBorderPrem());
            }

            if (writeToDbCounter++ > WRITE_TO_DB_COUNTER_MAX) {
                writeToDbCounter = 0;
                settingsRepositoryService.updateVolatileAutoBorders(
                        haveBcdPrem ? borderCrossDepth : null,
                        haveLPrem ? leftAddBorder : null,
                        haveRPrem ? rightAddBorder : null
                );
            }
        }
    }

    private void writeOnInitIfNeeded() {
        if (borderCrossDepth == null || leftAddBorder == null || rightAddBorder == null) {
            final SettingsVolatileMode v = settingsRepositoryService.getSettings().getSettingsVolatileMode();
            borderCrossDepth = v.getBorderCrossDepth();
            leftAddBorder = v.getBAddBorder();
            rightAddBorder = v.getOAddBorder();
        }
    }

    public BigDecimal getBorderCrossDepth() {
        return borderCrossDepth != null
                ? borderCrossDepth
                : settingsRepositoryService.getSettings().getSettingsVolatileMode().getBorderCrossDepth();
    }

    public BigDecimal getLeftAddBorder() {
        return leftAddBorder != null
                ? leftAddBorder
                : settingsRepositoryService.getSettings().getSettingsVolatileMode().getBAddBorder();
    }

    public BigDecimal getRightAddBorder() {
        return rightAddBorder != null
                ? rightAddBorder
                : settingsRepositoryService.getSettings().getSettingsVolatileMode().getOAddBorder();
    }
}
