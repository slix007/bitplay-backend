package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderItem;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.BorderTable;
import com.bitplay.persistance.domain.BordersV2;
import com.bitplay.persistance.domain.settings.PlacingBlocks;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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

    @Autowired
    PlacingBlocksService placingBlocksService;

    private volatile static BorderParams.PosMode theMode = BorderParams.PosMode.OK_MODE;

    public TradingSignal setNewBlock(TradingSignal tradingSignal, int okexBlock) {
        if (tradingSignal.okexBlock == okexBlock) {
            return tradingSignal;
        }

        int block = tradingSignal.posMode == BorderParams.PosMode.OK_MODE ? okexBlock : okexBlock * 100;
        return new BordersService.TradingSignal(tradingSignal.tradeType, block,
                tradingSignal.borderName,
                tradingSignal.borderValue + ";adjusted by affordable. okexBlock was " + tradingSignal.okexBlock,
                Collections.unmodifiableList(tradingSignal.borderValueList),
                tradingSignal.deltaVal,
                tradingSignal.ver, tradingSignal.posMode);
    }

    public TradingSignal checkBorders(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP, BigDecimal oPL, BigDecimal oPS) {
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();

        final BorderParams borderParams = persistenceService.fetchBorders();
        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (borderParams.getPosMode() != null) {
            theMode = borderParams.getPosMode();
        }

        final int block;
        final int pos;
        if (theMode == BorderParams.PosMode.BTM_MODE) {
            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                block = placingBlocks.getFixedBlockBitmex().intValueExact();
            } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
                block = placingBlocks.getDynMaxBlockBitmex().intValueExact(); // Dynamic block step 1
            } else {
                throw new IllegalStateException("Unhandled placingBlocks version: " + placingBlocks.getActiveVersion());
            }
            pos = bP.intValueExact();
        } else {
            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                block = placingBlocks.getFixedBlockOkex().intValueExact();
            } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
                block = placingBlocks.getDynMaxBlockOkex().intValueExact(); // Dynamic block step 1
            } else {
                throw new IllegalStateException("Unhandled placingBlocks version: " + placingBlocks.getActiveVersion());
            }
            pos = oPL.intValueExact() - oPS.intValueExact();
        }

        TradingSignal signal = null;
        TradingSignal bbCloseSignal = bDeltaBorderClose(b_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook);
        if (bbCloseSignal != null) signal = bbCloseSignal;
        if (signal == null) {
            TradingSignal obCloseSignal = oDeltaBorderClose(o_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook);
            if (obCloseSignal != null) signal = obCloseSignal;
        }
        if (signal == null) {
            TradingSignal bbOpenSignal = bDeltaBorderOpen(b_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook);
            if (bbOpenSignal != null) signal = bbOpenSignal;
        }
        if (signal == null) {
            TradingSignal obOpenSignal = oDeltaBorderOpen(o_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook);
            if (obOpenSignal != null) signal = obOpenSignal;
        }

        // Decrease by current position
        if (signal != null && signal.tradeType == TradeType.DELTA1_B_SELL_O_BUY
                && oPS.intValueExact() > 0 && signal.okexBlock > oPS.intValueExact()) {
            signal = new TradingSignal(signal.tradeType, oPS.intValueExact(), signal.borderName, signal.borderValue, signal.borderValueList, signal.deltaVal, placingBlocks.getActiveVersion());
        }
        if (signal != null && signal.tradeType == TradeType.DELTA2_B_BUY_O_SELL
                && oPL.intValueExact() > 0 && signal.okexBlock > oPL.intValueExact()) {
            signal = new TradingSignal(signal.tradeType, oPL.intValueExact(), signal.borderName, signal.borderValue, signal.borderValueList, signal.deltaVal, placingBlocks.getActiveVersion());
        }

        return signal != null ? signal :
                new TradingSignal(TradeType.NONE, 0, "", "", null, "", placingBlocks.getActiveVersion());
    }

    private TradingSignal bDeltaBorderClose(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook, OrderBook okexOrderBook) {
        // Bitmex border close - input data
        final String borderName = "b_br_close";
        final Optional<BorderTable> b_br_close = bordersV2.getBorderTableByName(borderName);
        if (!b_br_close.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion());
        }
        final List<BorderItem> btm_br_close = b_br_close.get().getBorderItemList();
        final int btm_br_close_cnt = btm_br_close.size();

        // Bitmex border close

        int btm_lvl_max_limit = 0; // dynamic only
        int btm_lvl_block_limit = 0; // dynamic only
        int b = 0, m = 0;
        int btm_br_close_dyn_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();

        if (pos != 0) {
            for (int i = 0; i < btm_br_close_cnt; i++) {
                if (btm_br_close.get(i).getId() != 0) {
                    if (b_delta.compareTo(btm_br_close.get(i).getValue()) >= 0) { // >=
                        if (pos > 0 && pos > btm_br_close.get(i).getPosLongLimit() && theMode == BorderParams.PosMode.BTM_MODE) {

                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
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
                                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_close.get(i).toString(),
                                                Collections.singletonList(btm_br_close.get(i).getValue()),
                                                b_delta.toPlainString(), placingBlocks.getActiveVersion());
                                    }
                                } else {
                                    // close_pos_mode (bitmex, okex, block); // делаем close b_delta(delta1) с шагом block;
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_close.get(i).toString(),
                                            Collections.singletonList(btm_br_close.get(i).getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }

                            } else { //DYNAMIC
                                m = pos - btm_br_close.get(i).getPosLongLimit();
                                if (m > btm_lvl_max_limit)
                                    btm_lvl_max_limit = m;
                                final BigDecimal value = btm_br_close.get(i).getValue();
                                b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                btm_lvl_block_limit += row_block;
                                btm_br_close_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                                borderValue.append(";").append(btm_br_close.get(i).toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(btm_br_close.get(i).getValue());
                            }
                        }

                        if (pos < 0 && -pos > btm_br_close.get(i).getPosShortLimit() && theMode == BorderParams.PosMode.OK_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (-pos - block < btm_br_close.get(i).getPosShortLimit()) {
                                    int block_once = -pos - btm_br_close.get(i).getPosShortLimit();
                                    final String warnString = String.format("block=%d; block_once = %d - %d(short);", block, pos, btm_br_close.get(i).getPosLongLimit());
                                    if (block_once < 0) {
                                        warningLogger.warn(String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                        block_once = Math.abs(block_once);
                                    } else {
                                        warningLogger.warn(String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        // close_pos_mode(bitmex, okex, block_once);
                                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_close.get(i).toString(),
                                                Collections.singletonList(btm_br_close.get(i).getValue()),
                                                b_delta.toPlainString(), placingBlocks.getActiveVersion());
                                    }
                                } else {
                                    // close_pos_mode (bitmex, okex, block); // делаем close с шагом block;
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_close.get(i).toString(),
                                            Collections.singletonList(btm_br_close.get(i).getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else { //DYNAMIC
                                m = -pos - btm_br_close.get(i).getPosShortLimit();
                                if (m > btm_lvl_max_limit)
                                    btm_lvl_max_limit = m;
                                final BigDecimal value = btm_br_close.get(i).getValue();
                                b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                btm_lvl_block_limit += row_block;
                                btm_br_close_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                                borderValue.append(";").append(btm_br_close.get(i).toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(btm_br_close.get(i).getValue());
                            }
                        }
                    }
                }
            }
        }

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC && btm_br_close_dyn_block != 0) {
            int maxBlock = theMode == BorderParams.PosMode.BTM_MODE
                    ? placingBlocks.getDynMaxBlockBitmex().intValueExact()
                    : placingBlocks.getDynMaxBlockOkex().intValueExact();
            btm_br_close_dyn_block = Math.min(btm_br_close_dyn_block, maxBlock);

            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, btm_br_close_dyn_block,
                    borderName, borderValue.toString(), Collections.unmodifiableList(borderValueList), b_delta.toPlainString(), placingBlocks.getActiveVersion());
        }

        return null;
    }

    private TradingSignal bDeltaBorderOpen(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook, OrderBook okexOrderBook) {
        // Bitmex border open - input data
        final String borderName = "b_br_open";
        final Optional<BorderTable> b_br_open = bordersV2.getBorderTableByName(borderName);
        if (!b_br_open.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion());
        }
        final List<BorderItem> btm_br_open = b_br_open.get().getBorderItemList();
        final int btm_br_open_cnt = btm_br_open.size();

        // Bitmex border open

        int btm_lvl_max_limit = 0; // dynamic only
        int btm_lvl_block_limit = 0; // dynamic only
        int b = 0, m = 0;
        int btm_br_open_dyn_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();

        for (int i = 0; i < btm_br_open_cnt; i++) {
            if (btm_br_open.get(i).getId() != 0) {

                if (b_delta.compareTo(btm_br_open.get(i).getValue()) >= 0) { // >=

                    if (pos >= 0 && pos < btm_br_open.get(i).getPosLongLimit() && theMode == BorderParams.PosMode.OK_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (pos + block > btm_br_open.get(i).getPosLongLimit()) {
                                int block_once = btm_br_open.get(i).getPosLongLimit() - pos;
                                final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, btm_br_open.get(i).getPosLongLimit(), pos);
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                } else {
                                    warningLogger.warn(String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    // open_pos_mode(bitmex, okex, block_once); // делаем open с шагом block_once;
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_open.get(i).toString(),
                                            Collections.singletonList(btm_br_open.get(i).getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else {
                                //open_pos_mode(bitmex, okex, block); // делаем open с шагом block;
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_open.get(i).toString(),
                                        Collections.singletonList(btm_br_open.get(i).getValue()),
                                        b_delta.toPlainString(), placingBlocks.getActiveVersion());
                            }
                        } else { // DYNAMIC {
                            m = btm_br_open.get(i).getPosLongLimit() - pos;
                            if (m > btm_lvl_max_limit)
                                btm_lvl_max_limit = m;
                            final BigDecimal value = btm_br_open.get(i).getValue();
                            b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            btm_lvl_block_limit += row_block;
                            btm_br_open_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                            borderValue.append(";").append(btm_br_open.get(i).toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(btm_br_open.get(i).getValue());
                        }
                    }

                    if (pos <= 0 && -pos < btm_br_open.get(i).getPosShortLimit() && theMode == BorderParams.PosMode.BTM_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (-pos + block > btm_br_open.get(i).getPosShortLimit()) {
                                int block_once = btm_br_open.get(i).getPosShortLimit() - (-pos);
                                final String warnString = String.format("block=%d; block_once = %d(open,short) - %d;", block, btm_br_open.get(i).getPosShortLimit(), pos);
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once);
                                } else {
                                    warningLogger.warn(String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    //open_pos_mode (bitmex, okex, block_once);
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, btm_br_open.get(i).toString(),
                                            Collections.singletonList(btm_br_open.get(i).getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else {
                                // open_pos_mode (bitmex, okex, block) // делаем open с шагом block;
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, btm_br_open.get(i).toString(),
                                        Collections.singletonList(btm_br_open.get(i).getValue()),
                                        b_delta.toPlainString(), placingBlocks.getActiveVersion());
                            }
                        } else { //DYNAMIC
                            m = btm_br_open.get(i).getPosShortLimit() + pos;
                            if (m > btm_lvl_max_limit)
                                btm_lvl_max_limit = m;
                            final BigDecimal value = btm_br_open.get(i).getValue();
                            b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            btm_lvl_block_limit += row_block;
                            btm_br_open_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                            borderValue.append(";").append(btm_br_open.get(i).toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(btm_br_open.get(i).getValue());
                        }
                    }
                }
            }
        }

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC && btm_br_open_dyn_block != 0) {
            int maxBlock = theMode == BorderParams.PosMode.BTM_MODE
                    ? placingBlocks.getDynMaxBlockBitmex().intValueExact()
                    : placingBlocks.getDynMaxBlockOkex().intValueExact();
            btm_br_open_dyn_block = Math.min(btm_br_open_dyn_block, maxBlock);

            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, btm_br_open_dyn_block,
                    borderName, borderValue.toString(), Collections.unmodifiableList(borderValueList), b_delta.toPlainString(), placingBlocks.getActiveVersion());
        }

        return null;
    }

    private int funcDynBlockByBDelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal value) {
        final PlBlocks bDeltaBlocks = placingBlocksService.getDynamicBlockByBDelta(bitmexOrderBook, okexOrderBook,
                value, null);
        return theMode == BorderParams.PosMode.BTM_MODE
                ? bDeltaBlocks.getBlockBitmex().intValueExact()
                : bDeltaBlocks.getBlockOkex().intValueExact();
    }

    private int funcDynBlockByODelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal value) {
        final PlBlocks bDeltaBlocks = placingBlocksService.getDynamicBlockByODelta(bitmexOrderBook, okexOrderBook,
                value, null);
        return theMode == BorderParams.PosMode.BTM_MODE
                ? bDeltaBlocks.getBlockBitmex().intValueExact()
                : bDeltaBlocks.getBlockOkex().intValueExact();
    }

    private TradingSignal oDeltaBorderClose(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook, OrderBook okexOrderBook) {
        // Okex border close - input data
        final String borderName = "o_br_close";
        final Optional<BorderTable> o_br_close = bordersV2.getBorderTableByName(borderName);
        if (!o_br_close.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion());
        }
        final List<BorderItem> ok_br_close = o_br_close.get().getBorderItemList();
        final int ok_br_close_cnt = ok_br_close.size();

        // Okex border close

        // dynamic only
        int ok_lvl_max_limit = 0;
        int ok_lvl_block_limit = 0;
        int b = 0, m = 0;
        int ok_br_close_dyn_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();

        if (pos != 0) {
            for (int i = 0; i < ok_br_close_cnt; i++) {
                if (ok_br_close.get(i).getId() != 0) {
                    if (o_delta.compareTo(ok_br_close.get(i).getValue()) >= 0) {

                        if (pos > 0 && pos > ok_br_close.get(i).getPosLongLimit() && theMode == BorderParams.PosMode.OK_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (pos - block < ok_br_close.get(i).getPosLongLimit()) {
                                    int block_once = pos - ok_br_close.get(i).getPosLongLimit();
                                    final String warnString = String.format("block=%d; block_once = %d(close,long) - %d;", block, pos, ok_br_close.get(i).getPosLongLimit());
                                    if (block_once < 0) {
                                        warningLogger.warn(String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                        block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                    } else {
                                        warningLogger.warn(String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        //close_pos_mode (bitmex, okex, block_once); // делаем close с шагом block_once;
                                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_close.get(i).toString(),
                                                Collections.singletonList(ok_br_close.get(i).getValue()),
                                                o_delta.toPlainString(), placingBlocks.getActiveVersion());
                                    }
                                } else {
                                    //close_pos_mode(bitmex, okex, block); // делаем close с шагом block;
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_close.get(i).toString(),
                                            Collections.singletonList(ok_br_close.get(i).getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else { // DYNAMIC
                                m = pos - ok_br_close.get(i).getPosLongLimit();
                                if (m > ok_lvl_max_limit)
                                    ok_lvl_max_limit = m;
                                final BigDecimal value = ok_br_close.get(i).getValue();
                                b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                ok_lvl_block_limit += row_block;
                                ok_br_close_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                                borderValue.append(";").append(ok_br_close.get(i).toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(ok_br_close.get(i).getValue());
                            }
                        }

                        if (pos < 0 && -pos > ok_br_close.get(i).getPosShortLimit() && theMode == BorderParams.PosMode.BTM_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (-pos - block < ok_br_close.get(i).getPosShortLimit()) {
                                    int block_once = -pos - ok_br_close.get(i).getPosShortLimit();
                                    final String warnString = String.format("block=%d; block_once = %d - %d(close,short);", block, pos, ok_br_close.get(i).getPosShortLimit());
                                    if (block_once < 0) {
                                        warningLogger.warn(String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                        block_once = Math.abs(block_once);
                                    } else {
                                        warningLogger.warn(String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        // close_pos_mode(bitmex, okex, block_once);
                                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_close.get(i).toString(),
                                                Collections.singletonList(ok_br_close.get(i).getValue()),
                                                o_delta.toPlainString(), placingBlocks.getActiveVersion());
                                    }
                                } else {
                                    // close_pos_mode(bitmex, okex, block)
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_close.get(i).toString(),
                                            Collections.singletonList(ok_br_close.get(i).getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else { // DYNAMIC
                                m = -pos - ok_br_close.get(i).getPosShortLimit();
                                if (m > ok_lvl_max_limit)
                                    ok_lvl_max_limit = m;
                                final BigDecimal value = ok_br_close.get(i).getValue();
                                b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                ok_lvl_block_limit += row_block;
                                ok_br_close_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                                borderValue.append(";").append(ok_br_close.get(i).toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(ok_br_close.get(i).getValue());
                            }
                        }
                    }
                }
            }
        }

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC && ok_br_close_dyn_block != 0) {
            int maxBlock = theMode == BorderParams.PosMode.BTM_MODE
                    ? placingBlocks.getDynMaxBlockBitmex().intValueExact()
                    : placingBlocks.getDynMaxBlockOkex().intValueExact();
            ok_br_close_dyn_block = Math.min(ok_br_close_dyn_block, maxBlock);

            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, ok_br_close_dyn_block,
                    borderName, borderValue.toString(), Collections.unmodifiableList(borderValueList), o_delta.toPlainString(), placingBlocks.getActiveVersion());
        }

        return null;
    }

    private TradingSignal oDeltaBorderOpen(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook, OrderBook okexOrderBook) {
        // Okex border open - input data
        final String borderName = "o_br_open";
        final Optional<BorderTable> o_br_open = bordersV2.getBorderTableByName(borderName);
        if (!o_br_open.isPresent()) {
            logger.warn(String.format("No %s is present", borderName));
            warningLogger.warn(String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion());
        }
        final List<BorderItem> ok_br_open = o_br_open.get().getBorderItemList();
        final int ok_br_open_cnt = ok_br_open.size();

        // Okex border open

        // dynamic only
        int ok_lvl_max_limit = 0;
        int ok_lvl_block_limit = 0;
        int b = 0, m = 0;
        int ok_br_open_dyn_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();

        for (int i = 0; i < ok_br_open_cnt; i++) {
            if (ok_br_open.get(i).getId() != 0) {
                if (o_delta.compareTo(ok_br_open.get(i).getValue()) >= 0) {

                    if (pos >= 0 && pos < ok_br_open.get(i).getPosLongLimit() && theMode == BorderParams.PosMode.BTM_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (pos + block > ok_br_open.get(i).getPosLongLimit()) {
                                int block_once = ok_br_open.get(i).getPosLongLimit() - pos;
                                final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, ok_br_open.get(i).getPosLongLimit(), pos);
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                } else {
                                    warningLogger.warn(String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    //open_pos_mode(okex, bitmex, block_once); // делаем open с шагом block_once;
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_open.get(i).toString(),
                                            Collections.singletonList(ok_br_open.get(i).getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else {
                                //open_pos_mode(okex, bitmex, block); // делаем open с шагом block;
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_open.get(i).toString(),
                                        Collections.singletonList(ok_br_open.get(i).getValue()),
                                        o_delta.toPlainString(), placingBlocks.getActiveVersion());
                            }
                        } else { // DYNAMIC
                            m = ok_br_open.get(i).getPosLongLimit() - pos;
                            if (m > ok_lvl_max_limit)
                                ok_lvl_max_limit = m;
                            final BigDecimal value = ok_br_open.get(i).getValue();
                            b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            ok_lvl_block_limit += row_block;
                            ok_br_open_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                            borderValue.append(";").append(ok_br_open.get(i).toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(ok_br_open.get(i).getValue());
                        }
                    }

                    if (pos <= 0 && -pos < ok_br_open.get(i).getPosShortLimit() && theMode == BorderParams.PosMode.OK_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (-pos + block > ok_br_open.get(i).getPosShortLimit()) {
                                int block_once = ok_br_open.get(i).getPosShortLimit() - (-pos);
                                final String warnString = String.format("block=%d; block_once = %d(open,short) - %d;", block, ok_br_open.get(i).getPosShortLimit(), pos);
                                if (block_once < 0) {
                                    warningLogger.warn(String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once);
                                } else {
                                    warningLogger.warn(String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    // open_pos_mode(okex, bitmex, block_once);
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, ok_br_open.get(i).toString(),
                                            Collections.singletonList(ok_br_open.get(i).getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion());
                                }
                            } else {
                                // open_pos_mode(okex, bitmex, block)
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, ok_br_open.get(i).toString(),
                                        Collections.singletonList(ok_br_open.get(i).getValue()),
                                        o_delta.toPlainString(), placingBlocks.getActiveVersion());
                            }
                        } else { // DYNAMIC
                            m = ok_br_open.get(i).getPosShortLimit() + pos;
                            if (m > ok_lvl_max_limit)
                                ok_lvl_max_limit = m;
                            final BigDecimal value = ok_br_open.get(i).getValue();
                            b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            ok_lvl_block_limit += row_block;
                            ok_br_open_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                            borderValue.append(";").append(ok_br_open.get(i).toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(ok_br_open.get(i).getValue());
                        }
                    }
                }
            }
        }

        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC && ok_br_open_dyn_block != 0) {
            int maxBlock = theMode == BorderParams.PosMode.BTM_MODE
                    ? placingBlocks.getDynMaxBlockBitmex().intValueExact()
                    : placingBlocks.getDynMaxBlockOkex().intValueExact();
            ok_br_open_dyn_block = Math.min(ok_br_open_dyn_block, maxBlock);

            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, ok_br_open_dyn_block,
                    borderName, borderValue.toString(), Collections.unmodifiableList(borderValueList), o_delta.toPlainString(), placingBlocks.getActiveVersion());
        }

        return null;
    }

    enum TradeType {NONE, DELTA1_B_SELL_O_BUY, DELTA2_B_BUY_O_SELL}

    public static class TradingSignal {
        // params for the signal
        final public TradeType tradeType;
        final public int bitmexBlock;
        final public int okexBlock;
        final public PlacingBlocks.Ver ver;

        // params for logging
        final public BorderParams.PosMode posMode;
        final public String borderName;
        final public String borderValue;
        final public List<BigDecimal> borderValueList;
        final public String deltaVal;

        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, List<BigDecimal> borderValueList, String deltaVal, PlacingBlocks.Ver ver) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.BTM_MODE) { // usdInContract = 1; => min block is 100
                bitmexBlock = block;
                okexBlock = block / 100;
            } else { // usdInContract = 100; => min block is 1
                bitmexBlock = block * 100;
                okexBlock = block;
            }
            this.ver = ver;
            this.posMode = theMode;
            this.borderName = borderName;
            this.borderValue = borderValue;
            this.borderValueList = borderValueList;
            this.deltaVal = deltaVal;
        }

        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, List<BigDecimal> borderValueList, String deltaVal, PlacingBlocks.Ver ver, BorderParams.PosMode theMode) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.BTM_MODE) { // usdInContract = 1; => min block is 100
                bitmexBlock = block;
                okexBlock = block / 100;
            } else { // usdInContract = 100; => min block is 1
                bitmexBlock = block * 100;
                okexBlock = block;
            }
            this.ver = ver;
            this.posMode = theMode;
            this.borderName = borderName;
            this.borderValue = borderValue;
            this.borderValueList = borderValueList;
            this.deltaVal = deltaVal;
        }

        @Override
        public String toString() {
            return "TradingSignal{" +
                    "tradeType=" + tradeType +
                    ", bitmexBlock=" + bitmexBlock +
                    ", okexBlock=" + okexBlock +
                    ", ver=" + ver +
                    ", posMode=" + posMode +
                    ", borderName='" + borderName + '\'' +
                    ", borderValue='" + borderValue + '\'' +
                    ", deltaVal='" + deltaVal + '\'' +
                    '}';
        }
    }

}
