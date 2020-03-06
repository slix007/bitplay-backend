package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.PosMode;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingBlocks.Ver;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import lombok.AllArgsConstructor;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private ArbitrageService arbitrageService;

    private volatile static BorderParams.PosMode theMode = BorderParams.PosMode.RIGHT_MODE;

    public BorderParams getBorderParams() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        adjBorderV2Values(borderParams);
        return borderParams;
    }

    public List<BorderTable> getBorderTableList(BorderParams borderParams) {
        adjBorderV2Values(borderParams);
        return borderParams.getBordersV2().getBorderTableList();
    }

    private void adjBorderV2Values(final BorderParams borderParams) {
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
        if (posMode == BorderParams.PosMode.LEFT_MODE) {
            return PlacingBlocks.toBitmexCont(BigDecimal.valueOf(limInUsd), isEth, cm).intValue();
        }
        return PlacingBlocks.toOkexCont(BigDecimal.valueOf(limInUsd), isEth).intValue();
    }

    int usdToCont(int limInUsd) {
        return usdToCont(limInUsd, theMode, arbitrageService.isEth(), arbitrageService.getCm());
    }


    public TradingSignal setNewBlock(TradingSignal tradingSignal, int okexBlock) {
        if (tradingSignal.okexBlock == okexBlock) {
            return tradingSignal;
        }

        final BigDecimal cm = arbitrageService.getCm();
        int block = tradingSignal.posMode == BorderParams.PosMode.RIGHT_MODE ? okexBlock :
                (BigDecimal.valueOf(okexBlock).multiply(cm)).setScale(0, RoundingMode.HALF_UP).intValue();
        final List<BigDecimal> list = Collections
                .unmodifiableList(tradingSignal.borderValueList != null ? tradingSignal.borderValueList : new ArrayList<>());
        return new BordersService.TradingSignal(tradingSignal.tradeType, block,
                tradingSignal.borderName,
                tradingSignal.borderValue + ";adjusted by affordable. okexBlock was " + tradingSignal.okexBlock,
                list,
                tradingSignal.deltaVal,
                tradingSignal.ver, tradingSignal.posMode, cm);
    }

    TradingSignal checkBordersForTests(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP,
            BigDecimal oPL,
            BigDecimal oPS) {
        final Affordable firstAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        final Affordable secondAffordable = new Affordable(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        return checkBorders(bitmexOrderBook, okexOrderBook, b_delta, o_delta, bP, oPL, oPS, true, firstAffordable, secondAffordable);
    }

    public TradingSignal checkBorders(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal b_delta, BigDecimal o_delta, BigDecimal bP, BigDecimal oPL,
            BigDecimal oPS, boolean withLogs, Affordable bitmexAffordable, Affordable okexAffordable) {
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        BigDecimal cm = placingBlocks.getCm();

        final BorderParams borderParams = getBorderParams();
        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (borderParams.getPosMode() != null) {
            theMode = borderParams.getPosMode();
        }

        final int block;
        final int pos;
        if (theMode == BorderParams.PosMode.LEFT_MODE) {
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

        BigDecimal bDeltaAffordable = null;
        BigDecimal oDeltaAffordable = null;
        if (placingBlocks.getActiveVersion() == Ver.DYNAMIC) {
            final PlBlocks btm = dynBlockMaxAffordable(DeltaName.B_DELTA, bitmexAffordable, okexAffordable);
            final PlBlocks ok = dynBlockMaxAffordable(DeltaName.O_DELTA, bitmexAffordable, okexAffordable);
            bDeltaAffordable = theMode == BorderParams.PosMode.RIGHT_MODE ? btm.getBlockOkex() : btm.getBlockBitmex();
            oDeltaAffordable = theMode == BorderParams.PosMode.RIGHT_MODE ? ok.getBlockOkex() : ok.getBlockBitmex();
        }

        TradingSignal signal = null;
        TradingSignal bbCloseSignal = bDeltaBorderClose(b_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm,
                bDeltaAffordable);
        if (bbCloseSignal != null) signal = bbCloseSignal;
        if (signal == null) {
            TradingSignal obCloseSignal = oDeltaBorderClose(o_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm,
                    oDeltaAffordable);
            if (obCloseSignal != null) signal = obCloseSignal;
        }
        if (signal == null) {
            // DELTA1_B_SELL_O_BUY
            TradingSignal bbOpenSignal = bDeltaBorderOpen(b_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm,
                    bDeltaAffordable);
            if (bbOpenSignal != null) signal = bbOpenSignal;
        }
        if (signal == null) {
            TradingSignal obOpenSignal = oDeltaBorderOpen(o_delta, block, pos, bordersV2, placingBlocks, bitmexOrderBook, okexOrderBook, withLogs, cm,
                    oDeltaAffordable);
            if (obOpenSignal != null) signal = obOpenSignal;
        }

        // Decrease by current position
        if (signal != null && signal.tradeType == TradeType.DELTA1_B_SELL_O_BUY
                && oPS.intValueExact() > 0 && signal.okexBlock > oPS.intValueExact()) {
            signal = new TradingSignal(signal.tradeType, oPS.intValueExact(), signal.borderName, signal.borderValue, signal.borderValueList,
                    signal.deltaVal,
                    placingBlocks.getActiveVersion(), cm, signal.blockOnceWarn);
        }
        if (signal != null && signal.tradeType == TradeType.DELTA2_B_BUY_O_SELL
                && oPL.intValueExact() > 0 && signal.okexBlock > oPL.intValueExact()) {
            signal = new TradingSignal(signal.tradeType, oPL.intValueExact(), signal.borderName, signal.borderValue, signal.borderValueList,
                    signal.deltaVal,
                    placingBlocks.getActiveVersion(), cm, signal.blockOnceWarn);
        }

        return signal != null ? signal : TradingSignal.none();
    }

    @SuppressWarnings("Duplicates")
    public Borders getMinBordersV2(BigDecimal bP, BigDecimal oPL, BigDecimal oPS) {
        final BorderParams borderParams = getBorderParams();
        final BordersV2 bordersV2 = borderParams.getBordersV2();

        if (borderParams.getPosMode() != null) {
            theMode = borderParams.getPosMode();
        }

        final int pos = theMode == BorderParams.PosMode.LEFT_MODE
                ? bP.intValueExact()
                : oPL.intValueExact() - oPS.intValueExact();

        BigDecimal btmMinBorder = null;
        BigDecimal okMinBorder = null;
        {
            final String borderName = "b_br_close";
            final Optional<BorderTable> b_br_close = bordersV2.getBorderTableByName(borderName);
            final List<BorderItem> btm_br_close = b_br_close.get().getBorderItemList();
            final int btm_br_close_cnt = btm_br_close.size();
            if (pos != 0) {
                int minBorderDiff = Integer.MAX_VALUE;
                for (int i = 0; i < btm_br_close_cnt; i++) {
                    final BorderItem borderItem = btm_br_close.get(i);
                    if (borderItem.getId() != 0) {
                        // value is decreasing
                        if (theMode == BorderParams.PosMode.LEFT_MODE) {
                            int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                            if (pos > 0 && pos > posLongLimit) {
                                int diff = pos - posLongLimit;
                                if (diff < minBorderDiff) {
                                    minBorderDiff = diff;
                                    btmMinBorder = borderItem.getValue();
                                }
                            } else {
                                break;
                            }
                        }
                        if (theMode == BorderParams.PosMode.RIGHT_MODE) {
                            int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                            if (pos < 0 && -pos > posShortLimit) {
                                int diff = (-pos) - posShortLimit;
                                if (diff < minBorderDiff) {
                                    minBorderDiff = diff;
                                    btmMinBorder = borderItem.getValue();
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        } // b_br_close
        {
            final String borderName = "b_br_open";
            final Optional<BorderTable> b_br_open = bordersV2.getBorderTableByName(borderName);
            final List<BorderItem> btm_br_open = b_br_open.get().getBorderItemList();
            final int btm_br_open_cnt = btm_br_open.size();
            for (int i = 0; i < btm_br_open_cnt; i++) {
                BorderItem borderItem = btm_br_open.get(i);
                if (borderItem.getId() != 0) {
                    // value is increasing
                    if (theMode == BorderParams.PosMode.RIGHT_MODE) {
                        int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                        if (pos >= 0 && pos < posLongLimit) {
                            btmMinBorder = borderItem.getValue();
                            break;
                        }
                    }
                    if (theMode == BorderParams.PosMode.LEFT_MODE) {
                        int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                        if (pos <= 0 && -pos < posShortLimit) {
                            btmMinBorder = borderItem.getValue();
                            break;
                        }
                    }
                }
            }
        } // b_br_open
        {
            final String borderName = "o_br_close";
            final Optional<BorderTable> o_br_close = bordersV2.getBorderTableByName(borderName);
            final List<BorderItem> ok_br_close = o_br_close.get().getBorderItemList();
            final int ok_br_close_cnt = ok_br_close.size();
            if (pos != 0) {
                int minBorderDiff = Integer.MAX_VALUE;
                for (int i = 0; i < ok_br_close_cnt; i++) {
                    final BorderItem borderItem = ok_br_close.get(i);
                    if (borderItem.getId() != 0) {
                        // value is decreasing
                        if (theMode == BorderParams.PosMode.RIGHT_MODE) {
                            int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                            if (pos > 0 && pos > posLongLimit) {
                                int diff = pos - posLongLimit;
                                if (diff < minBorderDiff) {
                                    minBorderDiff = diff;
                                    okMinBorder = borderItem.getValue();
                                }
                            } else {
                                break;
                            }
                        }
                        if (theMode == BorderParams.PosMode.LEFT_MODE) {
                            int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                            if (pos < 0 && -pos > posShortLimit) {
                                int diff = (-pos) - posShortLimit;
                                if (diff < minBorderDiff) {
                                    minBorderDiff = diff;
                                    okMinBorder = borderItem.getValue();
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        } // o_br_close
        {
            final String borderName = "o_br_open";
            final Optional<BorderTable> o_br_open = bordersV2.getBorderTableByName(borderName);
            final List<BorderItem> ok_br_open = o_br_open.get().getBorderItemList();
            final int ok_br_open_cnt = ok_br_open.size();
            for (int i = 0; i < ok_br_open_cnt; i++) {
                BorderItem borderItem = ok_br_open.get(i);
                if (borderItem.getId() != 0) {
                    // value is increasing
                    if (theMode == BorderParams.PosMode.LEFT_MODE) {
                        int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                        if (pos >= 0 && pos < posLongLimit) {
                            okMinBorder = borderItem.getValue();
                            break;
                        }
                    }
                    if (theMode == BorderParams.PosMode.RIGHT_MODE) {
                        int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                        if (pos <= 0 && -pos < posShortLimit) {
                            okMinBorder = borderItem.getValue();
                            break;
                        }
                    }
                }
            }

        } // o_br_open
        return (btmMinBorder != null && okMinBorder != null) ? new Borders(btmMinBorder, okMinBorder) : null;
    }

    @SuppressWarnings("Duplicates")
    private TradingSignal bDeltaBorderClose(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm, BigDecimal affordable) {
        // Bitmex border close - input data
        final String borderName = "b_br_close";
        final Optional<BorderTable> b_br_close = bordersV2.getBorderTableByName(borderName);
        if (!b_br_close.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return TradingSignal.none();
        }
        final List<BorderItem> btm_br_close = b_br_close.get().getBorderItemList();
        final int btm_br_close_cnt = btm_br_close.size();

        // Bitmex border close

        int btm_lvl_max_limit = 0; // dynamic only
        int btm_lvl_block_limit = 0; // dynamic only
        int b = 0, m = 0;
        int btm_br_close_calc_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();
        if (affordable != null) {
            borderValue.append("; affordable=").append(affordable.intValue()).append("; ");
        }

        if (pos != 0) {
            for (int i = btm_br_close_cnt - 1; i >= 0 ; i--) {
                BorderItem borderItem = btm_br_close.get(i);
                if (borderItem.getId() != 0) {
                    if (b_delta.compareTo(borderItem.getValue()) >= 0) { // >=
                        int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                        if (pos > 0 && pos > posLongLimit && theMode == BorderParams.PosMode.LEFT_MODE) {

                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (pos - block < posLongLimit) {
                                    int block_once = pos - posLongLimit;
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("b_close: block_once(%d) < 0, use absolute", block_once));
                                        block_once = Math.abs(block_once);
                                    }
                                    b = block_once;
                                } else {
                                    b = block;
                                }
                            } else { //DYNAMIC
                                b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                            }
                            m = pos - posLongLimit;
                            if (m > btm_lvl_max_limit) {
                                btm_lvl_max_limit = m;
                            }
                            final int row_block = Math.min(m, b);
                            btm_lvl_block_limit += row_block;
                            btm_br_close_calc_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);
                            if (affordable != null) {
                                btm_br_close_calc_block = Math.min(btm_br_close_calc_block, affordable.intValue());
                            }
                            if (btm_br_close_calc_block == 0) {
                                break;
                            }
                            borderValueList.add(borderItem.getValue());
                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b)
                                    .append(",full_b=").append(btm_br_close_calc_block);
                            if (m > btm_br_close_calc_block) {
                                break;
                            }
                            if (m >= block) { // do not cross more than maxBlock
                                break;
                            }
                        }

                        int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                        if (pos < 0 && -pos > posShortLimit && theMode == BorderParams.PosMode.RIGHT_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (-pos - block < posShortLimit) {
                                    int block_once = -pos - posShortLimit;
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("b_close: block_once(%d) < 0, use absolute", block_once));
                                        block_once = Math.abs(block_once);
                                    }
                                    b = block_once;
                                } else {
                                    b = block;
                                }
                            } else { //DYNAMIC
                                b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                            }
                            m = -pos - posShortLimit;
                            if (m > btm_lvl_max_limit) {
                                btm_lvl_max_limit = m;
                            }
                            final int row_block = Math.min(m, b);
                            btm_lvl_block_limit += row_block;
                            btm_br_close_calc_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);
                            if (affordable != null) {
                                btm_br_close_calc_block = Math.min(btm_br_close_calc_block, affordable.intValue());
                            }
                            if (btm_br_close_calc_block == 0) {
                                break;
                            }
                            borderValueList.add(borderItem.getValue());
                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b)
                                    .append(",full_b=").append(btm_br_close_calc_block);
                            if (m > btm_br_close_calc_block) {
                                break;
                            }
                            if (m >= block) { // do not cross more than maxBlock
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (btm_br_close_calc_block != 0) {
            StringBuilder blockOnceWarn = new StringBuilder();
            btm_br_close_calc_block = doubleCheckMaxBlock(placingBlocks, btm_br_close_calc_block, blockOnceWarn, pos);

            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, btm_br_close_calc_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList),
                    b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm, blockOnceWarn.toString());
        }

        return null;
    }

    private void writeLogs(boolean withLogs, String msg) {
        if (withLogs) {
            logger.warn(msg);
            warningLogger.warn(msg);
        }
    }

    @SuppressWarnings("Duplicates")
    private TradingSignal bDeltaBorderOpen(BigDecimal b_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm, BigDecimal affordable) {
        // Bitmex border open - input data
        final String borderName = "b_br_open";
        final Optional<BorderTable> b_br_open = bordersV2.getBorderTableByName(borderName);
        if (!b_br_open.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return TradingSignal.none();
        }
        final List<BorderItem> btm_br_open = b_br_open.get().getBorderItemList();
        final int btm_br_open_cnt = btm_br_open.size();

        // Bitmex border open

        int btm_lvl_max_limit = 0; // dynamic only
        int btm_lvl_block_limit = 0; // dynamic only
        int b = 0, m = 0;
        int btm_br_open_calc_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();
        if (affordable != null) {
            borderValue.append("; affordable=").append(affordable.intValue()).append("; ");
        }

        for (int i = 0; i < btm_br_open_cnt; i++) {
            BorderItem borderItem = btm_br_open.get(i);
            if (borderItem.getId() != 0) {

                if (b_delta.compareTo(borderItem.getValue()) >= 0) { // >=

                    int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                    if (pos >= 0 && pos < posLongLimit && theMode == BorderParams.PosMode.RIGHT_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (pos + block > posLongLimit) {
                                int block_once = posLongLimit - pos;
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("b_open: block_once(%d) < 0;", block_once));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                }
                                b = block_once;
                            } else {
                                b = block;
                            }
                        } else { // DYNAMIC {
                            // рассчитанное значение динамического шага для соответствующего барьера в таблице
                            b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                        }

                        m = posLongLimit - pos;
                        if (m > btm_lvl_max_limit) {
                            btm_lvl_max_limit = m;
                        }
                        final int row_block = Math.min(m, b);
                        btm_lvl_block_limit += row_block;
                        btm_br_open_calc_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);
                        if (affordable != null) {
                            btm_br_open_calc_block = Math.min(btm_br_open_calc_block, affordable.intValue());
                        }
                        if (btm_br_open_calc_block == 0) {
                            break;
                        }
                        borderValueList.add(borderItem.getValue());
                        borderValue.append(";").append(borderItem.toString())
                                .append(",m=").append(m)
                                .append(",b=").append(b)
                                .append(",full_b=").append(btm_br_open_calc_block);
                        if (m > btm_br_open_calc_block) {
                            break;
                        }
                        if (m >= block) { // do not cross more than maxBlock
                            break;
                        }
                    }

                    int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                    if (pos <= 0 && -pos < posShortLimit && theMode == BorderParams.PosMode.LEFT_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (-pos + block > posShortLimit) {
                                int block_once = posShortLimit - (-pos);
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("b_open: block_once(%d) < 0;", block_once));
                                    block_once = Math.abs(block_once);
                                }
                                b = block_once;
                            } else {
                                b = block;
                            }
                        } else { //DYNAMIC
                            b = funcDynBlockByBDelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                        }
                        m = posShortLimit + pos;
                        if (m > btm_lvl_max_limit) {
                            btm_lvl_max_limit = m;
                        }
                        final int row_block = Math.min(m, b);
                        btm_lvl_block_limit += row_block;
                        btm_br_open_calc_block = Math.min(btm_lvl_max_limit, btm_lvl_block_limit);
                        if (affordable != null) {
                            btm_br_open_calc_block = Math.min(btm_br_open_calc_block, affordable.intValue());
                        }
                        if (btm_br_open_calc_block == 0) {
                            break;
                        }
                        borderValueList.add(borderItem.getValue());
                        borderValue.append(";").append(borderItem.toString())
                                .append(",m=").append(m)
                                .append(",b=").append(b)
                                .append(",full_b=").append(btm_br_open_calc_block);
                        if (m > btm_br_open_calc_block) {
                            break;
                        }
                        if (m >= block) { // do not cross more than maxBlock
                            break;
                        }
                    }
                }
            }
        }

        if (btm_br_open_calc_block != 0) { // both FIXED and DYNAMIC
            StringBuilder blockOnceWarn = new StringBuilder();
            btm_br_open_calc_block = doubleCheckMaxBlock(placingBlocks, btm_br_open_calc_block, blockOnceWarn, pos);

            return new TradingSignal(TradeType.DELTA1_B_SELL_O_BUY, btm_br_open_calc_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList),
                    b_delta.toPlainString(), placingBlocks.getActiveVersion(), cm, blockOnceWarn.toString());
        }

        return null;
    }

    private int doubleCheckMaxBlock(PlacingBlocks placingBlocks, int btm_br_open_calc_block, StringBuilder blockOnceWarn, int pos) {
        int maxBlock;
        if (placingBlocks.getActiveVersion() == Ver.DYNAMIC) {
            maxBlock = theMode == PosMode.LEFT_MODE
                    ? placingBlocks.getDynMaxBlockBitmex().intValueExact()
                    : placingBlocks.getDynMaxBlockOkex().intValueExact();
        } else { // FIXED
            maxBlock = theMode == PosMode.LEFT_MODE
                    ? placingBlocks.getFixedBlockBitmex().intValueExact()
                    : placingBlocks.getFixedBlockOkex().intValueExact();
            if (btm_br_open_calc_block < maxBlock) {
                blockOnceWarn.append(String.format("block_once = %d(when fixed_block=%d); pos=%s;", btm_br_open_calc_block, maxBlock, pos));
            }
        }
        btm_br_open_calc_block = Math.min(btm_br_open_calc_block, maxBlock);
        return btm_br_open_calc_block;
    }

    private int funcDynBlockByBDelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal value, BigDecimal cm) {
        final PlBlocks bDeltaBlocks = placingBlocksService.getDynamicBlockByBDelta(bitmexOrderBook, okexOrderBook,
                value, null, cm);
        return theMode == BorderParams.PosMode.LEFT_MODE
                ? bDeltaBlocks.getBlockBitmex().intValueExact()
                : bDeltaBlocks.getBlockOkex().intValueExact();
    }

    private int funcDynBlockByODelta(OrderBook bitmexOrderBook, OrderBook okexOrderBook, BigDecimal value, BigDecimal cm) {
        final PlBlocks bDeltaBlocks = placingBlocksService.getDynamicBlockByODelta(bitmexOrderBook, okexOrderBook,
                value, null, cm);
        return theMode == BorderParams.PosMode.LEFT_MODE
                ? bDeltaBlocks.getBlockBitmex().intValueExact()
                : bDeltaBlocks.getBlockOkex().intValueExact();
    }

    @SuppressWarnings("Duplicates")
    private TradingSignal oDeltaBorderClose(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm, BigDecimal affordable) {
        // Okex border close - input data
        final String borderName = "o_br_close";
        final Optional<BorderTable> o_br_close = bordersV2.getBorderTableByName(borderName);
        if (!o_br_close.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return TradingSignal.none();
        }
        final List<BorderItem> ok_br_close = o_br_close.get().getBorderItemList();
        final int ok_br_close_cnt = ok_br_close.size();

        // Okex border close

        // dynamic only
        int ok_lvl_max_limit = 0;
        int ok_lvl_block_limit = 0;
        int b = 0, m = 0;
        int ok_br_close_calc_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();
        if (affordable != null) {
            borderValue.append("; affordable=").append(affordable.intValue()).append("; ");
        }

        if (pos != 0) {
            for (int i = ok_br_close_cnt - 1; i >= 0 ; i--) {
                BorderItem borderItem = ok_br_close.get(i);
                if (borderItem.getId() != 0) {
                    if (o_delta.compareTo(borderItem.getValue()) >= 0) {

                        int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                        if (pos > 0 && pos > posLongLimit && theMode == BorderParams.PosMode.RIGHT_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (pos - block < posLongLimit) {
                                    int block_once = pos - posLongLimit;
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("o_close: block_once(%d) < 0, use absolute", block_once));
                                        block_once = Math.abs(block_once);
                                    }
                                    b = block_once;
                                } else {
                                    b = block;
                                }
                            } else { // DYNAMIC
                                b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                            }
                            m = pos - posLongLimit;
                            if (m > ok_lvl_max_limit) {
                                ok_lvl_max_limit = m;
                            }
                            final int row_block = Math.min(m, b);
                            ok_lvl_block_limit += row_block;
                            ok_br_close_calc_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);
                            if (affordable != null) {
                                ok_br_close_calc_block = Math.min(ok_br_close_calc_block, affordable.intValue());
                            }
                            if (ok_br_close_calc_block == 0) {
                                break;
                            }
                            borderValueList.add(borderItem.getValue());
                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b)
                                    .append(",full_b=").append(ok_br_close_calc_block);
                            if (m > ok_br_close_calc_block) {
                                break;
                            }
                            if (m >= block) { // do not cross more than maxBlock
                                break;
                            }
                        }

                        int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                        if (pos < 0 && -pos > posShortLimit && theMode == BorderParams.PosMode.LEFT_MODE) {
                            if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                                if (-pos - block < posShortLimit) {
                                    int block_once = -pos - posShortLimit;
                                    if (block_once < 0) {
                                        writeLogs(withLogs, String.format("o_close: block_once(%d) < 0, use absolute", block_once));
                                        block_once = Math.abs(block_once);
                                    }
                                    b = block_once;
                                } else {
                                    b = block;
                                }
                            } else { // DYNAMIC
                                b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                            }
                            m = -pos - posShortLimit;
                            if (m > ok_lvl_max_limit) {
                                ok_lvl_max_limit = m;
                            }
                            final int row_block = Math.min(m, b);
                            ok_lvl_block_limit += row_block;
                            ok_br_close_calc_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);
                            if (affordable != null) {
                                ok_br_close_calc_block = Math.min(ok_br_close_calc_block, affordable.intValue());
                            }
                            if (ok_br_close_calc_block == 0) {
                                break;
                            }
                            borderValueList.add(borderItem.getValue());
                            borderValue.append(";").append(borderItem.toString())
                                    .append(",m=").append(m)
                                    .append(",b=").append(b)
                                    .append(",full_b=").append(ok_br_close_calc_block);
                            if (m > ok_br_close_calc_block) {
                                break;
                            }
                            if (m >= block) { // do not cross more than maxBlock
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (ok_br_close_calc_block != 0) {
            StringBuilder blockOnceWarn = new StringBuilder();
            ok_br_close_calc_block = doubleCheckMaxBlock(placingBlocks, ok_br_close_calc_block, blockOnceWarn, pos);

            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, ok_br_close_calc_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList),
                    o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm, blockOnceWarn.toString());
        }

        return null;
    }

    @SuppressWarnings("Duplicates")
    private TradingSignal oDeltaBorderOpen(BigDecimal o_delta, int block, int pos, BordersV2 bordersV2, PlacingBlocks placingBlocks, OrderBook bitmexOrderBook,
            OrderBook okexOrderBook, boolean withLogs, BigDecimal cm, BigDecimal affordable) {
        // Okex border open - input data
        final String borderName = "o_br_open";
        final Optional<BorderTable> o_br_open = bordersV2.getBorderTableByName(borderName);
        if (!o_br_open.isPresent()) {
            writeLogs(withLogs, String.format("No %s is present", borderName));
            return TradingSignal.none();
        }
        final List<BorderItem> ok_br_open = o_br_open.get().getBorderItemList();
        final int ok_br_open_cnt = ok_br_open.size();

        // Okex border open

        // dynamic only
        int ok_lvl_max_limit = 0;
        int ok_lvl_block_limit = 0;
        int b = 0; // block of crossing a borderItem by: [DYNAMIC: amount from OrderBooks] || [FIXED: block or block_once(limited to the borderItem)]
        int m = 0; // block of crossing a borderItems by pos.
        int ok_br_open_calc_block = 0;
        StringBuilder borderValue = new StringBuilder();
        List<BigDecimal> borderValueList = new ArrayList<>();
        if (affordable != null) {
            borderValue.append("; affordable=").append(affordable.intValue()).append("; ");
        }

        for (int i = 0; i < ok_br_open_cnt; i++) {
            BorderItem borderItem = ok_br_open.get(i);
            if (borderItem.getId() != 0) {
                if (o_delta.compareTo(borderItem.getValue()) >= 0) {

                    int posLongLimit = usdToCont(borderItem.getPosLongLimit());
                    if (pos >= 0 && pos < posLongLimit && theMode == BorderParams.PosMode.LEFT_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (pos + block > posLongLimit) {
                                int block_once = posLongLimit - pos;
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("o_open: block_once(%d) < 0, use absolute", block_once));
                                    block_once = Math.abs(block_once); // делается запись в логи warning, блок берется по модулю
                                }
                                b = block_once;
                            } else {
                                b = block;
                            }
                        } else { // DYNAMIC
                            b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                            //b = Math.min(b, block);
                        }
                        m = posLongLimit - pos;
                        if (m > ok_lvl_max_limit) {
                            ok_lvl_max_limit = m;
                        }
                        final int row_block = Math.min(m, b);
                        ok_lvl_block_limit += row_block;
                        ok_br_open_calc_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);
                        if (affordable != null) {
                            ok_br_open_calc_block = Math.min(ok_br_open_calc_block, affordable.intValue());
                        }
                        if (ok_br_open_calc_block == 0) {
                            break;
                        }
                        borderValueList.add(borderItem.getValue());
                        borderValue.append(";").append(borderItem.toString())
                                .append(",m=").append(m)
                                .append(",b=").append(b)
                                .append(",full_b=").append(ok_br_open_calc_block);
                        if (m > ok_br_open_calc_block) {
                            break;
                        }
                        if (m >= block) { // do not cross more than maxBlock
                            break;
                        }
                    }

                    int posShortLimit = usdToCont(borderItem.getPosShortLimit());
                    if (pos <= 0 && -pos < posShortLimit && theMode == BorderParams.PosMode.RIGHT_MODE) {
                        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
                            if (-pos + block > posShortLimit) {
                                int block_once = posShortLimit - (-pos);
                                if (block_once < 0) {
                                    writeLogs(withLogs, String.format("o_open: block_once(%d) < 0, use absolute", block_once));
                                    block_once = Math.abs(block_once);
                                }
                                b = block_once;
                            } else {
                                b = block;
                            }
                        } else { // DYNAMIC
                            b = funcDynBlockByODelta(bitmexOrderBook, okexOrderBook, borderItem.getValue(), cm);
                        }
                        m = posShortLimit + pos;
                        if (m > ok_lvl_max_limit) {
                            ok_lvl_max_limit = m;
                        }
                        final int row_block = Math.min(m, b);
                        ok_lvl_block_limit += row_block;
                        ok_br_open_calc_block = Math.min(ok_lvl_max_limit, ok_lvl_block_limit);
                        if (affordable != null) {
                            ok_br_open_calc_block = Math.min(ok_br_open_calc_block, affordable.intValue());
                        }
                        if (ok_br_open_calc_block == 0) {
                            break;
                        }
                        borderValueList.add(borderItem.getValue());
                        borderValue.append(";").append(borderItem.toString())
                                .append(",m=").append(m)
                                .append(",b=").append(b)
                                .append(",full_b=").append(ok_br_open_calc_block);
                        if (m > ok_br_open_calc_block) {
                            break;
                        }
                        if (m >= block) { // do not cross more than maxBlock
                            break;
                        }
                    }
                }
            }
        }

        if (ok_br_open_calc_block != 0) {
            StringBuilder blockOnceWarn = new StringBuilder();
            ok_br_open_calc_block = doubleCheckMaxBlock(placingBlocks, ok_br_open_calc_block, blockOnceWarn, pos);

            return new TradingSignal(TradeType.DELTA2_B_BUY_O_SELL, ok_br_open_calc_block, borderName, borderValue.toString(),
                    Collections.unmodifiableList(borderValueList),
                    o_delta.toPlainString(), placingBlocks.getActiveVersion(), cm, blockOnceWarn.toString());
        }

        return null;
    }

    private PlBlocks dynBlockMaxAffordable(DeltaName deltaRef, Affordable firstAffordable, Affordable secondAffordable) {
        BigDecimal b1 = BigDecimal.ZERO;
        BigDecimal b2 = BigDecimal.ZERO;
        final BigDecimal cm = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks().getCm();
        if (deltaRef == DeltaName.B_DELTA) {
            // b_sell, o_buy
            final BigDecimal b_sell_lim = firstAffordable.getForShort().signum() < 0 ? BigDecimal.ZERO : firstAffordable.getForShort();
            final BigDecimal o_buy_lim = secondAffordable.getForLong().signum() < 0 ? BigDecimal.ZERO : secondAffordable.getForLong();
            b1 = b_sell_lim; // blockSize1.compareTo(b_sell_lim) < 0 ? blockSize1 : b_sell_lim;
            b2 = o_buy_lim; // blockSize2.compareTo(o_buy_lim) < 0 ? blockSize2 : o_buy_lim;
        } else if (deltaRef == DeltaName.O_DELTA) {
            // buy p , sell o
            final BigDecimal b_buy_lim = firstAffordable.getForLong().signum() < 0 ? BigDecimal.ZERO : firstAffordable.getForLong();
            final BigDecimal o_sell_lim = secondAffordable.getForShort().signum() < 0 ? BigDecimal.ZERO : secondAffordable.getForShort();
            b1 = b_buy_lim; // blockSize1.compareTo(b_buy_lim) < 0 ? blockSize1 : b_buy_lim;
            b2 = o_sell_lim; // blockSize2.compareTo(o_sell_lim) < 0 ? blockSize2 : o_sell_lim;
        }

        if (b1.signum() == 0 || b2.signum() == 0) {
            b1 = BigDecimal.ZERO;
            b2 = BigDecimal.ZERO;
        } else if (b1.compareTo(b2.multiply(cm)) != 0) {
            b2 = b2.min(b1.divide(cm, 0, RoundingMode.HALF_UP));
            b1 = b2.multiply(cm).setScale(0, RoundingMode.HALF_UP);
        }

        b1 = b1.setScale(0, RoundingMode.HALF_UP);
        b2 = b2.setScale(0, RoundingMode.HALF_UP);

        String debugLog = String.format("dynBlockDecreaseByAffordable: %s, %s. bitmex %s, okex %s",
                b1, b2, firstAffordable, secondAffordable);

        return new PlBlocks(b1, b2, PlacingBlocks.Ver.DYNAMIC, debugLog);
    }

    public enum TradeType {NONE, DELTA1_B_SELL_O_BUY, DELTA2_B_BUY_O_SELL}

    public enum BorderVer {borderV1, borderV2, preliq}

    @AllArgsConstructor
    public static class Borders {

        final public BigDecimal b_border;
        final public BigDecimal o_border;
    }

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
        final public String blockOnceWarn;

        public static TradingSignal createOnBorderV1(PlacingBlocks.Ver ver, BigDecimal b_block, BigDecimal o_block, TradeType tradeType, BigDecimal deltaVal,
                BigDecimal borderValueV1) {
            return new TradingSignal(ver, b_block, o_block, tradeType,
                    deltaVal.toPlainString(), borderValueV1);
        }

        // borderV1
        private TradingSignal(PlacingBlocks.Ver ver, BigDecimal b_block, BigDecimal o_block, TradeType tradeType, String deltaVal,
                BigDecimal borderValueV1) {
            this.borderVer = BorderVer.borderV1;
            this.bitmexBlock = b_block.intValue();
            this.okexBlock = o_block.intValue();
            this.ver = ver;
            this.tradeType = tradeType;
            this.posMode = null;
            this.borderName = null;
            this.borderValue = borderValueV1.toPlainString();
            this.borderValueList = Collections.singletonList(borderValueV1);
            this.deltaVal = deltaVal;
            this.cm = null;
            this.blockOnceWarn = null;
        }

        @SuppressWarnings("Duplicates")
        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, List<BigDecimal> borderValueList,
                String deltaVal,
                Ver ver, BigDecimal cm, String blockOnceWarn) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.LEFT_MODE) { // usdInContract = 1; => min block is cm
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
            this.blockOnceWarn = blockOnceWarn;
        }

        @SuppressWarnings("Duplicates")
        TradingSignal(TradeType tradeType, int block, String borderName, String borderValue, List<BigDecimal> borderValueList,
                String deltaVal,
                Ver ver, PosMode theMode, BigDecimal cm) {
            this.tradeType = tradeType;
            if (theMode == BorderParams.PosMode.LEFT_MODE) { // usdInContract = 1; => min block is cm
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
            this.blockOnceWarn = null;
        }

        @SuppressWarnings("Duplicates")
        private TradingSignal(TradeType tradeType, int bitmexBlock, int okexBlock, Ver ver,
                PosMode posMode, String borderName, String borderValue, List<BigDecimal> borderValueList,
                String deltaVal, BigDecimal cm,
                BorderVer borderVer, String blockOnceWarn) {
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
            this.blockOnceWarn = blockOnceWarn;
        }

        public static TradingSignal none() {
            return new TradingSignal(TradeType.NONE, 0, null, null, null, null, null,
                    BigDecimal.valueOf(100), null);
        }

        TradingSignal changeBlocks(BigDecimal b_block, BigDecimal o_block) {
            return new TradingSignal(this.tradeType, b_block.intValue(), o_block.intValue(), this.ver,
                    this.posMode, this.borderName, this.borderValue, this.borderValueList, this.deltaVal, this.cm,
                    this.borderVer, this.blockOnceWarn);
        }

        public BigDecimal getMinBorder() {
            BigDecimal minBorder = null;
            if (borderValueList != null) {
                minBorder = borderValueList.stream().filter(Objects::nonNull)
                        .min(BigDecimal::compareTo).orElse(null);
            }
            return minBorder;
        }

        @Override
        public String toString() {
            final String blockOnceLine = blockOnceWarn == null || blockOnceWarn.length() == 0 ? "" : ", blockOnceWarn='" + blockOnceWarn + '\'';
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
                        ", borderValueList='" + (borderValueList != null ? Arrays.toString(borderValueList.toArray()) : "null") + '\'' +
                        ", maxBorder='" + getMaxBorder() + '\'' +
                        blockOnceLine +
                        '}';
            } else { // if (borderVer == BorderVer.borderV1 || preliq) {
                return "TradingSignal{" +
                        "borderVer =" + borderVer +
                        ", tradeType=" + tradeType +
                        ", bitmexBlock=" + bitmexBlock +
                        ", okexBlock=" + okexBlock +
                        ", ver=" + ver +
                        blockOnceLine +
                        '}';
            }
        }

        public BigDecimal getMaxBorder() {
            BigDecimal maxBorder = null;
            if (borderValueList != null) {
                maxBorder = borderValueList.stream().filter(Objects::nonNull)
                        .max(BigDecimal::compareTo).orElse(null);
            }
            return maxBorder;
        }

        public BigDecimal getDelta() {
            return deltaVal != null && deltaVal.length() > 0 ? new BigDecimal(deltaVal) : null;
        }

        public BtmFokAutoArgs toBtmFokAutoArgs() {
            final BigDecimal maxBorder = this.getMaxBorder();
            final BigDecimal delta = this.getDelta();
            return new BtmFokAutoArgs(delta, maxBorder, this.borderValue);
        }

    }

}
