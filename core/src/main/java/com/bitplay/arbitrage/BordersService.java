package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingBlocks.Ver;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 10/11/17.
 */
@Service
public class BordersService {

    private static final Logger logger = LoggerFactory.getLogger(BordersService.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    PersistenceService persistenceService;

    @Autowired
    PlacingBlocksService placingBlocksService;

    @Autowired
    private BitmexService bitmexService;

    private volatile static BorderParams.PosMode theMode = BorderParams.PosMode.OK_MODE;

    public BorderParams getBorderParams() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        adjValuesForVolatile(borderParams);
        return borderParams;
    }

    public List<BorderTable> getBorderTableList(BorderParams borderParams) {
        adjValuesForVolatile(borderParams);
        return borderParams.getBordersV2().getBorderTableList();
    }

    private void adjValuesForVolatile(final BorderParams borderParams) {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        if (settings.getTradingModeState() != null
                && settings.getTradingModeState().getTradingMode() != null
                && settings.getTradingModeState().getTradingMode() == TradingMode.VOLATILE) {
            final List<BorderTable> borderTableList = borderParams.getBordersV2().getBorderTableList();
            for (BorderTable borderTable : borderTableList) {
                // b_br_open, b_br_close, o_br_open, o_br_close
                {
                    final BigDecimal bAddBorder = settings.getSettingsVolatileMode().getBAddBorder();
                    if (bAddBorder != null && bAddBorder.signum() > 0) {
                        if (borderTable.getBorderName().equals("b_br_open") || borderTable.getBorderName().equals("b_br_close")) {
                            for (BorderItem borderItem : borderTable.getBorderItemList()) {
                                if (borderItem.getValue() != null) {
                                    borderItem.setValue(borderItem.getValue().add(bAddBorder));
                                }
                            }
                        }
                    }
                }
                final BigDecimal oAddBorder = settings.getSettingsVolatileMode().getOAddBorder();
                if (oAddBorder != null && oAddBorder.signum() > 0) {
                    if (borderTable.getBorderName().equals("o_br_open") || borderTable.getBorderName().equals("o_br_close")) {
                        for (BorderItem borderItem : borderTable.getBorderItemList()) {
                            if (borderItem.getValue() != null) {
                                borderItem.setValue(borderItem.getValue().add(oAddBorder));
                            }
                        }
                    }
                }
            }
        }
    }

    public void adjBackValuesForVolatile(final BorderParams borderParams) {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        if (settings.getTradingModeState().getTradingMode() == TradingMode.VOLATILE) {
            final List<BorderTable> borderTableList = borderParams.getBordersV2().getBorderTableList();
            for (BorderTable borderTable : borderTableList) {
                // b_br_open, b_br_close, o_br_open, o_br_close
                {
                    final BigDecimal bAddBorder = settings.getSettingsVolatileMode().getBAddBorder();
                    if (bAddBorder != null && bAddBorder.signum() > 0) {
                        if (borderTable.getBorderName().equals("b_br_open") || borderTable.getBorderName().equals("b_br_close")) {
                            for (BorderItem borderItem : borderTable.getBorderItemList()) {
                                if (borderItem.getValue() != null) {
                                    borderItem.setValue(borderItem.getValue().subtract(bAddBorder));
                                }
                            }
                        }
                    }
                }
                final BigDecimal oAddBorder = settings.getSettingsVolatileMode().getOAddBorder();
                if (oAddBorder != null && oAddBorder.signum() > 0) {
                    if (borderTable.getBorderName().equals("o_br_open") || borderTable.getBorderName().equals("o_br_close")) {
                        for (BorderItem borderItem : borderTable.getBorderItemList()) {
                            if (borderItem.getValue() != null) {
                                borderItem.setValue(borderItem.getValue().subtract(oAddBorder));
                            }
                        }
                    }
                }
            }
        }
    }


    public static int usdToCont(int limInUsd, BorderParams.PosMode posMode, boolean isEth, BigDecimal cm) {
        if (posMode == BorderParams.PosMode.BTM_MODE) {
            return PlacingBlocks.toBitmexCont(BigDecimal.valueOf(limInUsd), isEth, cm).intValue();
        }
        return PlacingBlocks.toOkexCont(BigDecimal.valueOf(limInUsd), isEth).intValue();
    }

    int usdToCont(int limInUsd) {
        return usdToCont(limInUsd, theMode, bitmexService.getContractType().isEth(), bitmexService.getCm());
    }


    public TradingSignal setNewBlock(TradingSignal tradingSignal, int okexBlock) {
        if (tradingSignal.okexBlock == okexBlock) {
            return tradingSignal;
        }

        final BigDecimal cm = bitmexService.getCm();
        int block = tradingSignal.posMode == BorderParams.PosMode.OK_MODE ? okexBlock :
                (BigDecimal.valueOf(okexBlock).multiply(cm)).setScale(0, RoundingMode.HALF_UP).intValue();
        return new BordersService.TradingSignal(tradingSignal.tradeType, block,
                tradingSignal.borderName,
                tradingSignal.borderValue + ";adjusted by affordable. okexBlock was " + tradingSignal.okexBlock,
                Collections.unmodifiableList(tradingSignal.borderValueList != null ? tradingSignal.borderValueList : new ArrayList<>()),
                tradingSignal.deltaVal,
                tradingSignal.ver, tradingSignal.posMode, cm);
    }

    TradingSignal checkBordersForTests(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP,
            BigDecimal oPL,
            BigDecimal oPS) {
        return checkBorders(bitmexOrderBook, okexOrderBook, b_delta, o_delta, bP, oPL, oPS, true);
    }

    public TradingSignal checkBorders(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP, BigDecimal oPL,
            BigDecimal oPS, boolean withLogs) {
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        BigDecimal cm = placingBlocks.getCm();

        final BorderParams borderParams = getBorderParams();
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
        TradingSignal bbCloseSignal = bDeltaBorderClose(b_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm);
        if (bbCloseSignal != null) signal = bbCloseSignal;
        if (signal == null) {
            TradingSignal obCloseSignal = oDeltaBorderClose(o_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm);
            if (obCloseSignal != null) signal = obCloseSignal;
        }
        if (signal == null) {
            TradingSignal bbOpenSignal = bDeltaBorderOpen(b_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm);
            if (bbOpenSignal != null) signal = bbOpenSignal;
        }
        if (signal == null) {
            TradingSignal obOpenSignal = oDeltaBorderOpen(o_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm);
            if (obOpenSignal != null) signal = obOpenSignal;
        }

        // Decrease by current position
        if (signal != null && signal.tradeType == TradeType.DELTA1_B_SELL_O_BUY
                && oPS.intValueExact() > 0 && signal.okexBlock > oPS.intValueExact()) {
            signal = new TradingSignal(signal.tradeType, oPS.intValueExact(), signal.borderName, signal.borderValue, signal.borderValueList, signal.deltaVal,
                    placingBlocks.getActiveVersion(), cm);
        }
        if (signal != null && signal.tradeType == TradeType.DELTA2_B_BUY_O_SELL
                && oPL.intValueExact() > 0 && signal.okexBlock > oPL.intValueExact()) {
            signal = new TradingSignal(signal.tradeType, oPL.intValueExact(), signal.borderName, signal.borderValue, signal.borderValueList, signal.deltaVal,
                    placingBlocks.getActiveVersion(), cm);
        }

        return signal != null ? signal :
                new TradingSignal(TradeType.NONE, 0, "", "", null, "", placingBlocks.getActiveVersion(), cm);
    }

    private TradingSignal bDeltaBorderClose(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm) {
        // Bitmex border close - input data
        final String borderName = "b_br_close";
        final Optional<BorderTable> b_br_close = bordersV2.getBorderTableByName(borderName);
        if (!b_br_close.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion(), cm);
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
                BorderItem borderItem = btm_br_close.get(i);
                if (borderItem.getId() != 0) {
                    if (b_delta.compareTo(borderItem.getValue()) >= 0) { // >=
                        int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                        if (pos > 0 && pos > posLongLimit && theMode == BorderParams.PosMode.BTM_MODE) {

                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (pos - block < posLongLimit) {
                                    int block_once = pos - posLongLimit;
                                    final String warnString = String.format("block=%d; block_once = %d - %s(long);", block, pos, posLongLimit);
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                        //TODO ?? зачем
                                        block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                    } else {
                                        writeLogs(withLogs, String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        // close_pos_mode(bitmex, okex, block_once); // делаем close с шагом block_once;
                                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, borderItem.toString(),
                                                Collections.singletonList(borderItem.getValue()),
                                                b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                    }
                                } else {
                                    // close_pos_mode (bitmex, okex, block); // делаем close b_delta(delta1) с шагом block;
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }

                            } else { //DYNAMIC
                                m = pos - posLongLimit;
                                if (m > btm_lvl_max_limit)
                                    btm_lvl_max_limit = m;
                                final BigDecimal value = borderItem.getValue();
                                b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value,
                                        cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                btm_lvl_block_limit += row_block;
                                btm_br_close_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                                borderValue.append(";").append(borderItem.toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(borderItem.getValue());
                            }
                        }

                        int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                        if (pos < 0 && -pos > posShortLimit && theMode == BorderParams.PosMode.OK_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (-pos - block < posShortLimit) {
                                    int block_once = -pos - posShortLimit;
                                    final String warnString = String.format("block=%d; block_once = %d - %d(short);", block, pos, posLongLimit);
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("b_close: block_once(%d) < 0; %s", block_once, warnString));
                                        block_once = Math.abs(block_once);
                                    } else {
                                        writeLogs(withLogs, String.format("b_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        // close_pos_mode(bitmex, okex, block_once);
                                        return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, borderItem.toString(),
                                                Collections.singletonList(borderItem.getValue()),
                                                b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                    }
                                } else {
                                    // close_pos_mode (bitmex, okex, block); // делаем close с шагом block;
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else { //DYNAMIC
                                m = -pos - posShortLimit;
                                if (m > btm_lvl_max_limit)
                                    btm_lvl_max_limit = m;
                                final BigDecimal value = borderItem.getValue();
                                b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value,
                                        cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                btm_lvl_block_limit += row_block;
                                btm_br_close_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                                borderValue.append(";").append(borderItem.toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(borderItem.getValue());
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

            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, btm_br_close_dyn_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList), b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
        }

        return null;
    }

    private void writeLogs(boolean withLogs, String msg) {
        if (withLogs) {
            logger.warn(msg);
            warningLogger.warn(msg);
        }
    }

    private TradingSignal bDeltaBorderOpen(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm) {
        // Bitmex border open - input data
        final String borderName = "b_br_open";
        final Optional<BorderTable> b_br_open = bordersV2.getBorderTableByName(borderName);
        if (!b_br_open.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion(), cm);
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
            BorderItem borderItem = btm_br_open.get(i);
            if (borderItem.getId() != 0) {

                if (b_delta.compareTo(borderItem.getValue()) >= 0) { // >=

                    int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                    if (pos >= 0 && pos < posLongLimit && theMode == BorderParams.PosMode.OK_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (pos + block > posLongLimit) {
                                int block_once = posLongLimit - pos;
                                final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, posLongLimit, pos);
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                } else {
                                    writeLogs(withLogs, String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    // open_pos_mode(bitmex, okex, block_once); // делаем open с шагом block_once;
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else {
                                //open_pos_mode(bitmex, okex, block); // делаем open с шагом block;
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, borderItem.toString(),
                                        Collections.singletonList(borderItem.getValue()),
                                        b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                            }
                        } else { // DYNAMIC {
                            m = posLongLimit - pos;
                            if (m > btm_lvl_max_limit)
                                btm_lvl_max_limit = m;
                            final BigDecimal value = borderItem.getValue();
                            b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value,
                                    cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            btm_lvl_block_limit += row_block;
                            btm_br_open_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(borderItem.getValue());
                        }
                    }

                    int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                    if (pos <= 0 && -pos < posShortLimit && theMode == BorderParams.PosMode.BTM_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (-pos + block > posShortLimit) {
                                int block_once = posShortLimit - (-pos);
                                final String warnString = String
                                        .format("block=%d; block_once = %d(open,short) - %d;", block, posShortLimit, pos);
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("b_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once);
                                } else {
                                    writeLogs(withLogs, String.format("b_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    //open_pos_mode (bitmex, okex, block_once);
                                    return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block_once, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else {
                                // open_pos_mode (bitmex, okex, block) // делаем open с шагом block;
                                return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, block, borderName, borderItem.toString(),
                                        Collections.singletonList(borderItem.getValue()),
                                        b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                            }
                        } else { //DYNAMIC
                            m = posShortLimit + pos;
                            if (m > btm_lvl_max_limit)
                                btm_lvl_max_limit = m;
                            final BigDecimal value = borderItem.getValue();
                            b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, value,
                                    cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            btm_lvl_block_limit += row_block;
                            btm_br_open_dyn_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);

                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(borderItem.getValue());
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

            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, btm_br_open_dyn_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList), b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
        }

        return null;
    }

    private int funcDynBlockByBDelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal value, BigDecimal cm) {
        final PlBlocks bDeltaBlocks = placingBlocksService.getDynamicBlockByBDelta(bitmexOrderBook, okexOrderBook,
                value, null, cm);
        return theMode == BorderParams.PosMode.BTM_MODE
                ? bDeltaBlocks.getBlockBitmex().intValueExact()
                : bDeltaBlocks.getBlockOkex().intValueExact();
    }

    private int funcDynBlockByODelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal value, BigDecimal cm) {
        final PlBlocks bDeltaBlocks = placingBlocksService.getDynamicBlockByODelta(bitmexOrderBook, okexOrderBook,
                value, null, cm);
        return theMode == BorderParams.PosMode.BTM_MODE
                ? bDeltaBlocks.getBlockBitmex().intValueExact()
                : bDeltaBlocks.getBlockOkex().intValueExact();
    }

    private TradingSignal oDeltaBorderClose(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm) {
        // Okex border close - input data
        final String borderName = "o_br_close";
        final Optional<BorderTable> o_br_close = bordersV2.getBorderTableByName(borderName);
        if (!o_br_close.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion(), cm);
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
                BorderItem borderItem = ok_br_close.get(i);
                if (borderItem.getId() != 0) {
                    if (o_delta.compareTo(borderItem.getValue()) >= 0) {

                        int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                        if (pos > 0 && pos > posLongLimit && theMode == BorderParams.PosMode.OK_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (pos - block < posLongLimit) {
                                    int block_once = pos - posLongLimit;
                                    final String warnString = String.format("block=%d; block_once = %d(close,long) - %d;", block, pos,
                                            posLongLimit);
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                        block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                    } else {
                                        writeLogs(withLogs, String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        //close_pos_mode (bitmex, okex, block_once); // делаем close с шагом block_once;
                                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, borderItem.toString(),
                                                Collections.singletonList(borderItem.getValue()),
                                                o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                    }
                                } else {
                                    //close_pos_mode(bitmex, okex, block); // делаем close с шагом block;
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else { // DYNAMIC
                                m = pos - posLongLimit;
                                if (m > ok_lvl_max_limit)
                                    ok_lvl_max_limit = m;
                                final BigDecimal value = borderItem.getValue();
                                b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value,
                                        cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                ok_lvl_block_limit += row_block;
                                ok_br_close_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                                borderValue.append(";").append(borderItem.toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(borderItem.getValue());
                            }
                        }

                        int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                        if (pos < 0 && -pos > posShortLimit && theMode == BorderParams.PosMode.BTM_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (-pos - block < posShortLimit) {
                                    int block_once = -pos - posShortLimit;
                                    final String warnString = String
                                            .format("block=%d; block_once = %d - %d(close,short);", block, pos, posShortLimit);
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("o_close: block_once(%d) < 0; %s", block_once, warnString));
                                        block_once = Math.abs(block_once);
                                    } else {
                                        writeLogs(withLogs, String.format("o_close: block_once(%d) >= 0; %s", block_once, warnString));
                                        // close_pos_mode(bitmex, okex, block_once);
                                        return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, borderItem.toString(),
                                                Collections.singletonList(borderItem.getValue()),
                                                o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                    }
                                } else {
                                    // close_pos_mode(bitmex, okex, block)
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else { // DYNAMIC
                                m = -pos - posShortLimit;
                                if (m > ok_lvl_max_limit)
                                    ok_lvl_max_limit = m;
                                final BigDecimal value = borderItem.getValue();
                                b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value,
                                        cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                                final int row_block = Math.min(m, b);
                                ok_lvl_block_limit += row_block;
                                ok_br_close_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                                borderValue.append(";").append(borderItem.toString())
                                        .append(",m=").append(m)
                                        .append(",b=").append(b);
                                borderValueList.add(borderItem.getValue());
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

            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, ok_br_close_dyn_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList), o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
        }

        return null;
    }

    private TradingSignal oDeltaBorderOpen(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm) {
        // Okex border open - input data
        final String borderName = "o_br_open";
        final Optional<BorderTable> o_br_open = bordersV2.getBorderTableByName(borderName);
        if (!o_br_open.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return new TradingSignal(TradeType.NONE, 0, borderName, "", null, "", placingBlocks.getActiveVersion(), cm);
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
            BorderItem borderItem = ok_br_open.get(i);
            if (borderItem.getId() != 0) {
                if (o_delta.compareTo(borderItem.getValue()) >= 0) {

                    int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                    if (pos >= 0 && pos < posLongLimit && theMode == BorderParams.PosMode.BTM_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (pos + block > posLongLimit) {
                                int block_once = posLongLimit - pos;
                                final String warnString = String.format("block=%d; block_once = %d(open,long) - %d;", block, posLongLimit, pos);
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                } else {
                                    writeLogs(withLogs, String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    //open_pos_mode(okex, bitmex, block_once); // делаем open с шагом block_once;
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else {
                                //open_pos_mode(okex, bitmex, block); // делаем open с шагом block;
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, borderItem.toString(),
                                        Collections.singletonList(borderItem.getValue()),
                                        o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                            }
                        } else { // DYNAMIC
                            m = posLongLimit - pos;
                            if (m > ok_lvl_max_limit)
                                ok_lvl_max_limit = m;
                            final BigDecimal value = borderItem.getValue();
                            b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value,
                                    cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            ok_lvl_block_limit += row_block;
                            ok_br_open_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(borderItem.getValue());
                        }
                    }

                    int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                    if (pos <= 0 && -pos < posShortLimit && theMode == BorderParams.PosMode.OK_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (-pos + block > posShortLimit) {
                                int block_once = posShortLimit - (-pos);
                                final String warnString = String
                                        .format("block=%d; block_once = %d(open,short) - %d;", block, posShortLimit, pos);
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("o_open: block_once(%d) < 0; %s", block_once, warnString));
                                    block_once = Math.abs(block_once);
                                } else {
                                    writeLogs(withLogs, String.format("o_open: block_once(%d) >= 0; %s", block_once, warnString));
                                    // open_pos_mode(okex, bitmex, block_once);
                                    return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block_once, borderName, borderItem.toString(),
                                            Collections.singletonList(borderItem.getValue()),
                                            o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                                }
                            } else {
                                // open_pos_mode(okex, bitmex, block)
                                return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, block, borderName, borderItem.toString(),
                                        Collections.singletonList(borderItem.getValue()),
                                        o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm);
                            }
                        } else { // DYNAMIC
                            m = posShortLimit + pos;
                            if (m > ok_lvl_max_limit)
                                ok_lvl_max_limit = m;
                            final BigDecimal value = borderItem.getValue();
                            b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, value,
                                    cm); // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            final int row_block = Math.min(m, b);
                            ok_lvl_block_limit += row_block;
                            ok_br_open_dyn_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);

                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b);
                            borderValueList.add(borderItem.getValue());
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
                    borderName, borderValue.toString(), Collections.unmodifiableList(borderValueList), o_delta.toPlainString(),
                    placingBlocks.getActiveVersion(), cm);
        }

        return null;
    }

    public enum TradeType {NONE, DELTA1_B_SELL_O_BUY, DELTA2_B_BUY_O_SELL}

    public enum BorderVer {borderV1, borderV2, preliq}

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
        final public BigDecimal cm;
        final public BorderVer borderVer;

        public TradingSignal(BorderVer borderVer, PlacingBlocks.Ver ver, BigDecimal b_block, BigDecimal o_block, TradeType tradeType) {
            this.borderVer = borderVer;
            this.bitmexBlock = b_block.intValue();
            this.okexBlock = o_block.intValue();
            this.ver = ver;
            this.tradeType = tradeType;
            this.posMode = null;
            this.borderName = null;
            this.borderValue = null;
            this.borderValueList = null;
            this.deltaVal = null;
            this.cm = null;
        }

        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, List<BigDecimal> borderValueList, String deltaVal,
                PlacingBlocks.Ver ver, BigDecimal cm) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.BTM_MODE) { // usdInContract = 1; => min block is cm
                bitmexBlock = block;
                okexBlock = BigDecimal.valueOf(block).divide(cm, 0, RoundingMode.HALF_UP).intValue();
            } else { // usdInContract = cm; => min block is 1
                bitmexBlock = BigDecimal.valueOf(block).multiply(cm).setScale(0, RoundingMode.HALF_UP).intValue();
                okexBlock = block;
            }
            this.ver = ver;
            this.posMode = theMode;
            this.borderName = borderName;
            this.borderValue = borderValue;
            this.borderValueList = borderValueList;
            this.deltaVal = deltaVal;
            this.cm = cm;
            this.borderVer = BorderVer.borderV2;
        }

        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, List<BigDecimal> borderValueList, String deltaVal,
                PlacingBlocks.Ver ver, BorderParams.PosMode theMode, BigDecimal cm) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.BTM_MODE) { // usdInContract = 1; => min block is cm
                bitmexBlock = block;
                okexBlock = BigDecimal.valueOf(block).divide(cm, 0, RoundingMode.HALF_UP).intValue();
            } else { // usdInContract = cm; => min block is 1
                bitmexBlock = BigDecimal.valueOf(block).multiply(cm).setScale(0, RoundingMode.HALF_UP).intValue();
                okexBlock = block;
            }
            this.ver = ver;
            this.posMode = theMode;
            this.borderName = borderName;
            this.borderValue = borderValue;
            this.borderValueList = borderValueList;
            this.deltaVal = deltaVal;
            this.cm = cm;
            this.borderVer = BorderVer.borderV2;
        }

        private TradingSignal(TradeType tradeType, int bitmexBlock, int okexBlock, Ver ver,
                PosMode posMode, String borderName, String borderValue, List<BigDecimal> borderValueList, String deltaVal, BigDecimal cm,
                BorderVer borderVer) {
            this.tradeType = tradeType;
            this.bitmexBlock = bitmexBlock;
            this.okexBlock = okexBlock;
            this.ver = ver;
            this.posMode = posMode;
            this.borderName = borderName;
            this.borderValue = borderValue;
            this.borderValueList = borderValueList;
            this.deltaVal = deltaVal;
            this.cm = cm;
            this.borderVer = borderVer;
        }

        public static TradingSignal none() {
            return new TradingSignal(TradeType.NONE, 0, null, null, null, null, null, null);
        }

        TradingSignal changeBlocks(BigDecimal b_block, BigDecimal o_block) {
            return new TradingSignal(this.tradeType, b_block.intValue(), o_block.intValue(), this.ver,
                    this.posMode, this.borderName, this.borderValue, this.borderValueList, this.deltaVal, this.cm,
                    this.borderVer);
        }

        @Override
        public String toString() {
            if (borderVer == BorderVer.borderV2) {
                return "TradingSignal{" +
                        "borderVer =" + borderVer +
                        ", tradeType=" + tradeType +
                        ", bitmexBlock=" + bitmexBlock +
                        ", okexBlock=" + okexBlock +
                        ", cm=" + cm +
                        ", ver=" + ver +
                        ", posMode=" + posMode +
                        ", borderName='" + borderName + '\'' +
                        ", borderValue='" + borderValue + '\'' +
                        ", deltaVal='" + deltaVal + '\'' +
                        '}';
            } else { // if (borderVer == BorderVer.borderV1 || preliq) {
                return "TradingSignal{" +
                        "borderVer =" + borderVer +
                        ", tradeType=" + tradeType +
                        ", bitmexBlock=" + bitmexBlock +
                        ", okexBlock=" + okexBlock +
                        ", ver=" + ver +
                        '}';
            }
        }
    }

}
