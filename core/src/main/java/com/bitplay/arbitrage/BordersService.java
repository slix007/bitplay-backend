package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.BorderTable;
import com.bitplay.persistance.domain.BordersV2;
import com.bitplay.persistance.domain.settings.PlacingBlocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Created by Sergey Shurmin on 10/11/17.
 */
@Service
public class BordersService {
    private static final Logger logger = LoggerFactory.getLogger(BordersService.class);
//    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    @Autowired
    PersistenceService persistenceService;

    private volatile static BorderParams.PosMode theMode = BorderParams.PosMode.OK_MODE;

    public TradingSignal checkBorders(BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP, BigDecimal oPL, BigDecimal oPS) {
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();

        final BorderParams borderParams = persistenceService.fetchBorders();
        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (borderParams.getPosMode() != null) {
            theMode = borderParams.getPosMode();
        }

        final int block;
        final int pos;
        if (theMode == BorderParams.PosMode.BTM_MODE) {
//            block = guiParams.getBlock1().intValueExact();
            block = placingBlocks.getFixedBlockBitmex().intValueExact();
            pos = bP.intValueExact();
        } else {
//            block = guiParams.getBlock2().intValueExact();
            block = placingBlocks.getFixedBlockOkex().intValueExact();
            pos = oPL.intValueExact() - oPS.intValueExact();
        }

        TradingSignal bbCloseSignal = bDeltaBorderClose(b_delta, block, pos, bordersV2);
        if (bbCloseSignal != null) return bbCloseSignal;
        TradingSignal obCloseSignal = oDeltaBorderClose(o_delta, block, pos, bordersV2);
        if (obCloseSignal != null) return obCloseSignal;

        TradingSignal bbOpenSignal = bDeltaBorderOpen(b_delta, block, pos, bordersV2);
        if (bbOpenSignal != null) return bbOpenSignal;
        TradingSignal obOpenSignal = oDeltaBorderOpen(o_delta, block, pos, bordersV2);
        if (obOpenSignal != null) return obOpenSignal;


        return new TradingSignal(TradeType.NONE, 0, "", "", "");
    }

    private TradingSignal bDeltaBorderClose(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2) {
        // Bitmex border close - input data
        final String borderName = "b_br_close";
        final Optional<BorderTable> b_br_close = bordersV2.getBorderTableByName(borderName);
        if (!b_br_close.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", "");
        }
        final List<BorderItem> btm_br_close = b_br_close.get().getBorderItemList();
        final int btm_br_close_cnt = btm_br_close.size();

        // Bitmex border close
        if (pos != 0) {
            for (int i = 0; i < btm_br_close_cnt; i++) {
                if (btm_br_close.get(i).getId() != 0) {
                    if (b_delta.compareTo(btm_br_close.get(i).getValue()) >= 0) { // >=
                        if (pos > 0 && pos > btm_br_close.get(i).getPosLongLimit()) {
                            if (pos - block < btm_br_close.get(i).getPosLongLimit()) {
                                int block_once = pos - btm_br_close.get(i).getPosLongLimit();
                                final String warnString = String.format("block=%d; block_once = %d - %s(long);", block, pos, btm_br_close.get(i).getPosLongLimit());
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                    //TODO ?? зачем
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                } else {
                                    warningLogger.warn(String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
                                    // close_pos_mode(bitmex, okex, block_once); // делаем close с шагом block_once;
                                    if (theMode == BorderParams.PosMode.BTM_MODE) { // pos > 0
                                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_close.get(i).toString(), b_delta.toPlainString());
                                    }
                                }
                            } else {
                                // close_pos_mode (bitmex, okex, block); // делаем close b_delta(delta1) с шагом block;
                                if (theMode == BorderParams.PosMode.BTM_MODE) { // pos > 0
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_close.get(i).toString(), b_delta.toPlainString());
                                }
                            }
                        }

                        if (pos < 0 && -pos > btm_br_close.get(i).getPosShortLimit()) {
                            if (-pos - block < btm_br_close.get(i).getPosShortLimit()) {
                                int block_once = -pos - btm_br_close.get(i).getPosShortLimit();
                                final String warnString = String.format("block=%d; block_once = %d - %d(short);", block, pos, btm_br_close.get(i).getPosLongLimit());
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once);
                                } else {
                                    warningLogger.warn(String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
                                    // close_pos_mode(bitmex, okex, block_once);
                                    if (theMode == BorderParams.PosMode.OK_MODE) { // pos < 0
                                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_close.get(i).toString(), b_delta.toPlainString());
                                    }
                                }
                            } else {
                                // close_pos_mode (bitmex, okex, block); // делаем close с шагом block;
                                if (theMode == BorderParams.PosMode.OK_MODE) { // pos < 0
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_close.get(i).toString(), b_delta.toPlainString());
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private TradingSignal bDeltaBorderOpen(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2) {
        // Bitmex border open - input data
        final String borderName = "b_br_open";
        final Optional<BorderTable> b_br_open = bordersV2.getBorderTableByName(borderName);
        if (!b_br_open.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", "");
        }
        final List<BorderItem> btm_br_open = b_br_open.get().getBorderItemList();
        final int btm_br_open_cnt = btm_br_open.size();

        // Bitmex border open
        for (int i = 0; i < btm_br_open_cnt; i++) {
            if (btm_br_open.get(i).getId() != 0) {

                if (b_delta.compareTo(btm_br_open.get(i).getValue()) >= 0) { // >=

                    if (pos >= 0 && pos < btm_br_open.get(i).getPosLongLimit()) {
                        if (pos + block > btm_br_open.get(i).getPosLongLimit()) {
                            int block_once = btm_br_open.get(i).getPosLongLimit() - pos;
                            final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, btm_br_open.get(i).getPosLongLimit(), pos);
                            if (block_once < 0) {
                                warningLogger.warn(String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                            } else {
                                warningLogger.warn(String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                                // open_pos_mode(bitmex, okex, block_once); // делаем open с шагом block_once;
                                if (theMode == BorderParams.PosMode.OK_MODE) { // pos > 0
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_open.get(i).toString(), b_delta.toPlainString());
                                }
                            }
                        } else {
                            //open_pos_mode(bitmex, okex, block); // делаем open с шагом block;
                            if (theMode == BorderParams.PosMode.OK_MODE) { // pos > 0
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_open.get(i).toString(), b_delta.toPlainString());
                            }
                        }
                    }

                    if (pos <= 0 && -pos < btm_br_open.get(i).getPosShortLimit()) {
                        if (-pos + block > btm_br_open.get(i).getPosShortLimit()) {
                            int block_once = btm_br_open.get(i).getPosShortLimit() - (-pos);
                            final String warnString = String.format("block=%d; block_once = %d(open,short) - %d;", block, btm_br_open.get(i).getPosShortLimit(), pos);
                            if (block_once < 0) {
                                warningLogger.warn(String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once);
                            } else {
                                warningLogger.warn(String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                                //open_pos_mode (bitmex, okex, block_once);
                                if (theMode == BorderParams.PosMode.BTM_MODE) { // pos < 0
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_open.get(i).toString(), b_delta.toPlainString());
                                }
                            }
                        } else {
                            // open_pos_mode (bitmex, okex, block) // делаем open с шагом block;
                            if (theMode == BorderParams.PosMode.BTM_MODE) { // pos < 0
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_open.get(i).toString(), b_delta.toPlainString());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private TradingSignal oDeltaBorderClose(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2) {
        // Okex border close - input data
        final String borderName = "o_br_close";
        final Optional<BorderTable> o_br_close = bordersV2.getBorderTableByName(borderName);
        if (!o_br_close.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", "");
        }
        final List<BorderItem> ok_br_close = o_br_close.get().getBorderItemList();
        final int ok_br_close_cnt = ok_br_close.size();

        // Okex border close
        if (pos != 0) {
            for (int i = 0; i < ok_br_close_cnt; i++) {
                if (ok_br_close.get(i).getId() != 0) {
                    if (o_delta.compareTo(ok_br_close.get(i).getValue()) >= 0) {
                        if (pos > 0 && pos > ok_br_close.get(i).getPosLongLimit()) {
                            if (pos - block < ok_br_close.get(i).getPosLongLimit()) {
                                int block_once = pos - ok_br_close.get(i).getPosLongLimit();
                                final String warnString = String.format("block=%d; block_once = %d(close,long) - %d;", block, pos, ok_br_close.get(i).getPosLongLimit());
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                } else {
                                    warningLogger.warn(String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                    //close_pos_mode (bitmex, okex, block_once); // делаем close с шагом block_once;
                                    if (theMode == BorderParams.PosMode.OK_MODE) { // pos > 0
                                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_close.get(i).toString(), o_delta.toPlainString());
                                    }
                                }
                            } else {
                                //close_pos_mode(bitmex, okex, block); // делаем close с шагом block;
                                if (theMode == BorderParams.PosMode.OK_MODE) { // pos > 0
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_close.get(i).toString(), o_delta.toPlainString());
                                }
                            }
                        }
                        if (pos < 0 && -pos > ok_br_close.get(i).getPosShortLimit()) {
                            if (-pos - block < ok_br_close.get(i).getPosShortLimit()) {
                                int block_once = -pos - ok_br_close.get(i).getPosShortLimit();
                                final String warnString = String.format("block=%d; block_once = %d - %d(close,short);", block, pos, ok_br_close.get(i).getPosShortLimit());
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once);
                                } else {
                                    warningLogger.warn(String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                    // close_pos_mode(bitmex, okex, block_once);
                                    if (theMode == BorderParams.PosMode.BTM_MODE) { // pos < 0
                                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_close.get(i).toString(), o_delta.toPlainString());
                                    }
                                }
                            } else {
                                // close_pos_mode(bitmex, okex, block)
                                if (theMode == BorderParams.PosMode.BTM_MODE) { // pos < 0
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_close.get(i).toString(), o_delta.toPlainString());
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private TradingSignal oDeltaBorderOpen(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2) {
        // Okex border open - input data
        final String borderName = "o_br_open";
        final Optional<BorderTable> o_br_open = bordersV2.getBorderTableByName(borderName);
        if (!o_br_open.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", "");
        }
        final List<BorderItem> ok_br_open = o_br_open.get().getBorderItemList();
        final int ok_br_open_cnt = ok_br_open.size();

        // Okex border open
        for (int i = 0; i < ok_br_open_cnt; i++) {
            if (ok_br_open.get(i).getId() != 0) {
                if (o_delta.compareTo(ok_br_open.get(i).getValue()) >= 0) {
                    if (pos >= 0 && pos < ok_br_open.get(i).getPosLongLimit()) {
                        if (pos + block > ok_br_open.get(i).getPosLongLimit()) {
                            int block_once = ok_br_open.get(i).getPosLongLimit() - pos;
                            final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, ok_br_open.get(i).getPosLongLimit(), pos);
                            if (block_once < 0) {
                                warningLogger.warn(String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                            } else {
                                warningLogger.warn(String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                                //open_pos_mode(okex, bitmex, block_once); // делаем open с шагом block_once;
                                if (theMode == BorderParams.PosMode.BTM_MODE) { // pos > 0
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_open.get(i).toString(), o_delta.toPlainString());
                                }
                            }
                        } else {
                            //open_pos_mode(okex, bitmex, block); // делаем open с шагом block;
                            if (theMode == BorderParams.PosMode.BTM_MODE) { // pos > 0
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_open.get(i).toString(), o_delta.toPlainString());
                            }
                        }
                    }
                    if (pos <= 0 && -pos < ok_br_open.get(i).getPosShortLimit()) {
                        if (-pos + block > ok_br_open.get(i).getPosShortLimit()) {
                            int block_once = ok_br_open.get(i).getPosShortLimit() - (-pos);
                            final String warnString = String.format("block=%d; block_once = %d(open,short) - %d;", block, ok_br_open.get(i).getPosShortLimit(), pos);
                            if (block_once < 0) {
                                warningLogger.warn(String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once);
                            } else {
                                warningLogger.warn(String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                                // open_pos_mode(okex, bitmex, block_once);
                                if (theMode == BorderParams.PosMode.OK_MODE) { // pos < 0
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_open.get(i).toString(), o_delta.toPlainString());
                                }
                            }
                        } else {
                            // open_pos_mode(okex, bitmex, block)
                            if (theMode == BorderParams.PosMode.OK_MODE) { // pos < 0
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_open.get(i).toString(), o_delta.toPlainString());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    enum TradeType {NONE, DELTA1_B_SELL_O_BUY, DELTA2_B_BUY_O_SELL}

    public static class TradingSignal {
        // params for the signal
        final public TradeType tradeType;
        final public int bitmexBlock;
        final public int okexBlock;

        // params for logging
        final public BorderParams.PosMode posMode;
        final public String borderName;
        final public String borderValue;
        final public String deltaVal;

        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, String deltaVal) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.BTM_MODE) { // usdInContract = 1; => min block is 100
                bitmexBlock = block;
                okexBlock = block / 100;
            } else { // usdInContract = 100; => min block is 1
                bitmexBlock = block * 100;
                okexBlock = block;
            }
            this.posMode = theMode;
            this.borderName = borderName;
            this.borderValue = borderValue;
            this.deltaVal = deltaVal;
        }

        @Override
        public String toString() {
            return "TradingSignal{" +
                    "tradeType=" + tradeType +
                    ", bitmexBlock=" + bitmexBlock +
                    ", okexBlock=" + okexBlock +
                    ", posMode=" + posMode +
                    ", borderName='" + borderName + '\'' +
                    ", borderVal='" + borderValue + '\'' +
                    ", deltaCalcVal='" + deltaVal + '\'' +
                    '}';
        }
    }

}
