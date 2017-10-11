package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.BorderTable;
import com.bitplay.persistance.domain.BordersV2;
import com.bitplay.persistance.domain.GuiParams;

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
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    @Autowired
    PersistenceService persistenceService;
    private PosMode theMode = PosMode.OK_MODE;

    public TradingSignal checkBorders(BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP, BigDecimal oPL, BigDecimal oPS) {
        final GuiParams guiParams = persistenceService.fetchGuiParams();

        final BorderParams borderParams = persistenceService.fetchBorders();
        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (theMode == PosMode.BTM_MODE) {
            final int block = guiParams.getBlock2().intValueExact();
            final int pos = bP.intValueExact();
            TradingSignal bbCloseSignal = bitmexBorderClose(b_delta, block, pos, bordersV2);
            if (bbCloseSignal != null) return bbCloseSignal;
            TradingSignal bbOpenSignal = bitmexBorderOpen(b_delta, block, pos, bordersV2);
            if (bbOpenSignal != null) return bbOpenSignal;
        } else {
            final int block = guiParams.getBlock1().intValueExact();
            final int pos = oPL.intValueExact() - oPS.intValueExact();
            TradingSignal obCloseSignal = okexBorderClose(o_delta, block, pos, bordersV2);
            if (obCloseSignal != null) return obCloseSignal;
            TradingSignal obOpenSignal = okexBorderOpen(o_delta, block, pos, bordersV2);
            if (obOpenSignal != null) return obOpenSignal;
        }

        return new TradingSignal(TradeType.NONE, 0);
    }

    private TradingSignal bitmexBorderClose(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2) {
        // Bitmex border close - input data
        final Optional<BorderTable> b_br_close = bordersV2.getBorderTableByName("b_br_close");
        if (!b_br_close.isPresent()) {
            logger.warn(String.format("No %s is present", "b_br_close"));
            warningLogger.warn(String.format("No %s is present", "b_br_close"));
            return new TradingSignal(TradeType.NONE, 0);
        }
        final List<BorderItem> btm_br_close = b_br_close.get().getBorderItemList();
        final int btm_br_close_cnt = btm_br_close.size();

        // Bitmex border close
        if (pos != 0) {
            for (int i = 0; i < btm_br_close_cnt; i++) {
                if (b_delta.intValue() >= btm_br_close.get(i).getValue()) {
                    if (pos > 0 && pos > btm_br_close.get(i).getPosLongLimit()) {
                        if (pos - block < btm_br_close.get(i).getPosLongLimit()) {
                            int block_once = pos - btm_br_close.get(i).getPosLongLimit();
                            final String warnString = String.format("block=%d; block_once = %d - %s(long);", block, pos, btm_br_close.get(i).getPosLongLimit());
                            if (block_once < 0) {
                                deltasLogger.warn(String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                //TODO ?? зачем
                                block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                            } else {
                                deltasLogger.warn(String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
//                                  close_pos_mode(bitmex, okex, block_once); // делаем close с шагом block_once;
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once);
                            }
                        } else {
//                            close_pos_mode (bitmex, okex, block); // делаем close с шагом block;
                            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block);
                        }
                    }

                    if (pos < 0 && -pos > btm_br_close.get(i).getPosShortLimit()) {
                        if (-pos - block < btm_br_close.get(i).getPosShortLimit()) {
                            int block_once = -pos - btm_br_close.get(i).getPosShortLimit();
                            final String warnString = String.format("block=%d; block_once = %d - %d(short);", block, pos, btm_br_close.get(i).getPosLongLimit());
                            if (block_once < 0) {
                                deltasLogger.warn(String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once);
                            } else {
                                deltasLogger.warn(String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
//                            close_pos_mode(bitmex, okex, block_once);
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once);
                            }
                        } else {
//                            close_pos_mode (bitmex, okex, block); // делаем close с шагом block;
                            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block);
                        }
                    }
                }
            }
        }
        return null;
    }

    private TradingSignal bitmexBorderOpen(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2) {
        // Bitmex border open - input data
        final Optional<BorderTable> b_br_open = bordersV2.getBorderTableByName("b_br_open");
        if (!b_br_open.isPresent()) {
            logger.warn(String.format("No %s is present", "b_br_open"));
            warningLogger.warn(String.format("No %s is present", "b_br_open"));
            return new TradingSignal(TradeType.NONE, 0);
        }
        final List<BorderItem> btm_br_open = b_br_open.get().getBorderItemList();
        final int btm_br_open_cnt = btm_br_open.size();

        // Bitmex border open
        for (int i = 0; i < btm_br_open_cnt; i++) {
            if (b_delta.intValueExact() >= btm_br_open.get(i).getValue()) {

                if (pos >= 0 && pos < btm_br_open.get(i).getPosLongLimit()) {
                    if (pos + block > btm_br_open.get(i).getPosLongLimit()) {
                        int block_once = btm_br_open.get(i).getPosLongLimit() - pos;
                        final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, btm_br_open.get(i).getPosLongLimit(), pos);
                        if (block_once < 0) {
                            deltasLogger.warn(String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                            block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                        } else {
                            deltasLogger.warn(String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                            // open_pos_mode(bitmex, okex, block_once); // делаем open с шагом block_once;
                            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once);
                        }
                    } else {
                        //open_pos_mode(bitmex, okex, block); // делаем open с шагом block;
                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block);
                    }
                }

                if (pos < 0 && -pos < btm_br_open.get(i).getPosShortLimit()) {
                    if (-pos + block > btm_br_open.get(i).getPosShortLimit()) {
                        int block_once = btm_br_open.get(i).getPosShortLimit() - (-pos);
                        final String warnString = String.format("block=%d; block_once = %d(open,short) - %d;", block, btm_br_open.get(i).getPosShortLimit(), pos);
                        if (block_once < 0) {
                            deltasLogger.warn(String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                            block_once = Math.abs(block_once);
                        } else {
                            deltasLogger.warn(String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                            //open_pos_mode (bitmex, okex, block_once);
                            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once);
                        }
                    } else {
                        // open_pos_mode (bitmex, okex, block) // делаем open с шагом block;
                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block);
                    }
                }
            }
        }
        return null;
    }

    private TradingSignal okexBorderClose(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2) {
        // Okex border close - input data
        final Optional<BorderTable> o_br_close = bordersV2.getBorderTableByName("o_br_close");
        if (!o_br_close.isPresent()) {
            logger.warn(String.format("No %s is present", "o_br_close"));
            warningLogger.warn(String.format("No %s is present", "o_br_close"));
            return new TradingSignal(TradeType.NONE, 0);
        }
        final List<BorderItem> ok_br_close = o_br_close.get().getBorderItemList();
        final int ok_br_close_cnt = ok_br_close.size();

        // Okex border close
        if (pos != 0) {
            for (int i = 0; i < ok_br_close_cnt; i++) {
                if (o_delta.intValueExact() >= ok_br_close.get(i).getValue()) {
                    if (pos > 0 && pos > ok_br_close.get(i).getPosLongLimit()) {
                        if (pos - block < ok_br_close.get(i).getPosLongLimit()) {
                            int block_once = pos - ok_br_close.get(i).getPosLongLimit();
                            final String warnString = String.format("block=%d; block_once = %d(close,long) - %d;", block, pos, ok_br_close.get(i).getPosLongLimit());
                            if (block_once < 0) {
                                deltasLogger.warn(String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                            } else {
                                deltasLogger.warn(String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                //close_pos_mode (bitmex, okex, block_once); // делаем close с шагом block_once;
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once);
                            }
                        } else {
                            //close_pos_mode(bitmex, okex, block); // делаем close с шагом block;
                            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block);
                        }
                    }
                    if (pos < 0 && -pos > ok_br_close.get(i).getPosShortLimit()) {
                        if (-pos - block < ok_br_close.get(i).getPosShortLimit()) {
                            int block_once = -pos - ok_br_close.get(i).getPosShortLimit();
                            final String warnString = String.format("block=%d; block_once = %d - %d(close,short);", block, pos, ok_br_close.get(i).getPosShortLimit());
                            if (block_once < 0) {
                                deltasLogger.warn(String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                block_once = Math.abs(block_once);
                            } else {
                                deltasLogger.warn(String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                // close_pos_mode(bitmex, okex, block_once);
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once);
                            }
                        } else {
                            // close_pos_mode(bitmex, okex, block)
                            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block);
                        }
                    }
                }
            }
        }
        return null;
    }

    private TradingSignal okexBorderOpen(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2) {
        // Okex border open - input data
        final Optional<BorderTable> o_br_open = bordersV2.getBorderTableByName("o_br_open");
        if (!o_br_open.isPresent()) {
            logger.warn(String.format("No %s is present", "o_br_open"));
            warningLogger.warn(String.format("No %s is present", "o_br_open"));
            return new TradingSignal(TradeType.NONE, 0);
        }
        final List<BorderItem> ok_br_open = o_br_open.get().getBorderItemList();
        final int ok_br_open_cnt = ok_br_open.size();

        // Okex border open
        for (int i = 0; i < ok_br_open_cnt; i++) {
            if (o_delta.intValueExact() >= ok_br_open.get(i).getValue()) {
                if (pos >= 0 && pos < ok_br_open.get(i).getPosLongLimit()) {
                    if (pos + block > ok_br_open.get(i).getPosLongLimit()) {
                        int block_once = ok_br_open.get(i).getPosLongLimit() - pos;
                        final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, ok_br_open.get(i).getPosLongLimit(), pos);
                        if (block_once < 0) {
                            deltasLogger.warn(String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                            block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                        } else {
                            deltasLogger.warn(String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                            //open_pos_mode(okex, bitmex, block_once); // делаем open с шагом block_once;
                            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once);
                        }
                    } else {
                        //open_pos_mode(okex, bitmex, block); // делаем open с шагом block;
                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block);
                    }
                }
                if (pos < 0 && -pos < ok_br_open.get(i).getPosShortLimit()) {
                    if (-pos + block > ok_br_open.get(i).getPosShortLimit()) {
                        int block_once = ok_br_open.get(i).getPosShortLimit() - (-pos);
                        final String warnString = String.format("block=%d; block_once = %d(open,short) - %d;", block, ok_br_open.get(i).getPosShortLimit(), pos);
                        if (block_once < 0) {
                            deltasLogger.warn(String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                            block_once = Math.abs(block_once);
                        } else {
                            deltasLogger.warn(String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                            // open_pos_mode(okex, bitmex, block_once);
                            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once);
                        }
                    } else {
                        // open_pos_mode(okex, bitmex, block)
                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block);
                    }
                }
            }
        }
        return null;
    }

    enum PosMode {BTM_MODE, OK_MODE}

    enum TradeType {NONE, DELTA1_B_SELL_O_BUY, DELTA2_B_BUY_O_SELL}

    public static class TradingSignal {
        final public TradeType tradeType;
        final public int block;

        public TradingSignal(TradeType tradeType, int block) {
            this.tradeType = tradeType;
            this.block = block;
        }
    }

}
