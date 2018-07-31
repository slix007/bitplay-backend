package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;
import com.bitplay.arbitrage.BordersCalcScheduler;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderDelta.DeltaCalcType;
import com.bitplay.persistance.domain.borders.BorderItem;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderTable;
import com.bitplay.persistance.domain.borders.BordersV1;
import com.bitplay.persistance.domain.borders.BordersV2;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 10/6/17.
 */
@RestController
@RequestMapping("/borders")
public class BordersEndpoint {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private BordersCalcScheduler bordersCalcScheduler;

    @Autowired
    private DeltaRepositoryService deltaRepositoryService;

    @Autowired
    private DeltasCalcService deltasCalcService;

    @RequestMapping(value = "/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public BorderParams getBorders() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        // changeset for BordersV2: new params
        if (borderParams.getBordersV2().getMaxLvl() == null || borderParams.getRecalcPeriodSec() == null) {
            createDefaultParams2(borderParams);
        }
        return borderParams;
    }

    @RequestMapping(value = "/tables", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BorderTable> getBorderTables() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        return (borderParams != null && borderParams.getBordersV2() != null)
                ? borderParams.getBordersV2().getBorderTableList() : new ArrayList<>();
    }

    @RequestMapping(value = "/create-default", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public BorderParams setDefaultBorders() {
        final BorderParams borderParams = createDefaultBorders();

        persistenceService.saveBorderParams(borderParams);

        return persistenceService.fetchBorders();
    }

    public static BorderParams createDefaultBorders() {
        final List<BorderTable> borders = new ArrayList<>();
        final List<BorderItem> borderBtmClose = new ArrayList<>();
        borderBtmClose.add(new BorderItem(1, BigDecimal.valueOf(-21), 500, 500));
        borderBtmClose.add(new BorderItem(2, BigDecimal.valueOf(-15), 300, 350));
        borderBtmClose.add(new BorderItem(3, BigDecimal.valueOf(-10), 200, 250));
        borderBtmClose.add(new BorderItem(4, BigDecimal.valueOf(-5), 100, 100));
        borders.add(new BorderTable("b_br_close", borderBtmClose));
        final List<BorderItem> borderBtmOpen = new ArrayList<>();
        borderBtmOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 100, 100));
        borderBtmOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 250, 250));
        borderBtmOpen.add(new BorderItem(3, BigDecimal.valueOf(35), 350, 350));
        borders.add(new BorderTable("b_br_open", borderBtmOpen));
        final List<BorderItem> borderOkexClose = new ArrayList<>();
        borderOkexClose.add(new BorderItem(1, BigDecimal.valueOf(-21), 500, 500));
        borderOkexClose.add(new BorderItem(2, BigDecimal.valueOf(-15), 300, 350));
        borderOkexClose.add(new BorderItem(3, BigDecimal.valueOf(-10), 200, 250));
        borderOkexClose.add(new BorderItem(4, BigDecimal.valueOf(-5), 100, 100));
        borders.add(new BorderTable("o_br_close", borderOkexClose));
        final List<BorderItem> borderOkexOpen = new ArrayList<>();
        borderOkexOpen.add(new BorderItem(1, BigDecimal.valueOf(20), 100, 100));
        borderOkexOpen.add(new BorderItem(2, BigDecimal.valueOf(30), 250, 250));
        borderOkexOpen.add(new BorderItem(3, BigDecimal.valueOf(35), 350, 350));
        borders.add(new BorderTable("o_br_open", borderOkexOpen));

        final BorderParams borderParams = new BorderParams(BorderParams.Ver.V2, new BordersV1(), new BordersV2(borders));
        createDefaultParams2(borderParams);
        borderParams.setBorderDelta(BorderDelta.createDefault());
        return borderParams;
    }

    private static void createDefaultParams2(final BorderParams borderParams) {
        borderParams.setRecalcPeriodSec(3600);
        final BordersV2 bordersV2 = borderParams.getBordersV2();
        bordersV2.setMaxLvl(7);
        bordersV2.setAutoBaseLvl(false);
        bordersV2.setBaseLvlCnt(4);
        bordersV2.setBaseLvlType(BordersV2.BaseLvlType.B_OPEN);
        bordersV2.setStep(20);
        bordersV2.setGapStep(20);
        bordersV2.setbAddDelta(BigDecimal.valueOf(20));
        bordersV2.setOkAddDelta(BigDecimal.valueOf(20));
    }

    @RequestMapping(value = "/tables", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson saveBorders(@RequestBody List<BorderTable> borderTableList) {
        if (borderTableList == null || borderTableList.size() == 0) {
            return new ResultJson("Wrong request", "Request body should have borderTables. " + borderTableList);
        }

        final BorderParams borderParams = persistenceService.fetchBorders();

        for (BorderTable updatedTable : borderTableList) {
            final List<BorderTable> currentList = borderParams.getBordersV2().getBorderTableList();
            currentList.replaceAll(borderTable -> {

                if (updatedTable.getBorderName().equals(borderTable.getBorderName())) return updatedTable;

                return borderTable;
            });

        }

        persistenceService.saveBorderParams(borderParams);

        return new ResultJson("OK", "");
    }

    @RequestMapping(value = "/settings", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson updateBordersSettings(@RequestBody BordersSettings update) {
        final BorderParams bP = persistenceService.fetchBorders();

        String respDetails = "";
        try {
            if (update.version != null) {
                bP.setActiveVersion(BorderParams.Ver.valueOf(update.version));
                respDetails = "ver: " + update.version;
            }
            if (update.posMode != null) {
                bP.setPosMode(BorderParams.PosMode.valueOf(update.posMode));
                respDetails += " posMode: " + update.posMode;
            }
            if (update.recalcPeriodSec != null) {
                final Integer periodSec = Integer.valueOf(update.recalcPeriodSec);
                bP.setRecalcPeriodSec(periodSec);
                deltasCalcService.setBorderDelta(bP.getBorderDelta());
                boolean isRecalcEveryNewDelta = bP.getBorderDelta().getDeltaCalcType().isEveryNewDelta();
                bordersCalcScheduler.resetTimerToRecalc(periodSec, isRecalcEveryNewDelta);
                respDetails = update.recalcPeriodSec;
            }
            if (update.borderV1SumDelta != null) {
                final BigDecimal sumDelta = new BigDecimal(update.borderV1SumDelta);
                bP.getBordersV1().setSumDelta(sumDelta);
                respDetails = bP.getBordersV1().getSumDelta().toPlainString();
            }
            if (update.doResetDeltaHistPer != null) {
                deltasCalcService.resetDeltasCache(bP.getBorderDelta(), true);
                respDetails = "OK";
            }

            if (update.borderDelta != null) {
                if (update.borderDelta.getDeltaSmaCalcOn() != null) {
                    bP.getBorderDelta().setDeltaSmaCalcOn(update.borderDelta.getDeltaSmaCalcOn());
                    respDetails = bP.getBorderDelta().getDeltaSmaCalcOn().toString();
                    deltasCalcService.resetDeltasCache(bP.getBorderDelta(), true);
                }

                if (update.borderDelta.getDeltaCalcType() != null) {
                    DeltaCalcType before = bP.getBorderDelta().getDeltaCalcType();
                    DeltaCalcType after = update.borderDelta.getDeltaCalcType();
                    bP.getBorderDelta().setDeltaCalcType(after);

                    if (before.isEveryNewDelta() || after.isEveryNewDelta()) {
                        boolean isRecalcEveryNewDelta = bP.getBorderDelta().getDeltaCalcType().isEveryNewDelta();

                        bordersCalcScheduler.resetTimerToRecalc(bP.getRecalcPeriodSec(), isRecalcEveryNewDelta);
                    }

                    respDetails += update.borderDelta.getDeltaCalcType().toString();
                }
                if (update.borderDelta.getDeltaCalcPast() != null) {
                    final Integer histPerUpdate = update.borderDelta.getDeltaCalcPast();
                    final boolean shouldClearData = deltasCalcService.isDataResetNeeded(histPerUpdate);
                    bP.getBorderDelta().setDeltaCalcPast(histPerUpdate);
                    deltasCalcService.resetDeltasCache(bP.getBorderDelta(), shouldClearData);

                    respDetails += update.borderDelta.getDeltaCalcPast().toString();
                }
                if (update.borderDelta.getDeltaSaveType() != null) {
                    bP.getBorderDelta().setDeltaSaveType(update.borderDelta.getDeltaSaveType());
                    respDetails += update.borderDelta.getDeltaSaveType().toString();
                }
                if (update.borderDelta.getDeltaSaveDev() != null) {
                    bP.getBorderDelta().setDeltaSaveDev(update.borderDelta.getDeltaSaveDev());
                    respDetails += update.borderDelta.getDeltaSaveDev().toString();
                }
                if (update.borderDelta.getDeltaSavePerSec() != null) {
                    bP.getBorderDelta().setDeltaSavePerSec(update.borderDelta.getDeltaSavePerSec());
                    respDetails += update.borderDelta.getDeltaSavePerSec().toString();
                }

                deltaRepositoryService.recreateSavingListener(bP.getBorderDelta());
            }

            deltasCalcService.setBorderDelta(bP.getBorderDelta());

        } catch (Exception e) {
            return new ResultJson("Wrong version", e.getMessage());
        }

        persistenceService.saveBorderParams(bP);

        return new ResultJson(respDetails, respDetails);
    }

    private static class BordersSettings {
        public String version;
        public String posMode;
        public String recalcPeriodSec;
        public String borderV1SumDelta;
        public String doResetDeltaHistPer;
        public BorderDelta borderDelta;
    }

    @RequestMapping(value = "/settingsV2", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson updateBordersSettingsV2(@RequestBody BordersV2 update) {
        final BorderParams bP = persistenceService.fetchBorders();
        final BordersV2 bordersV2 = bP.getBordersV2();

        String result = "";
        try {
            if (update.getMaxLvl() != null) {
                bordersV2.setMaxLvl(update.getMaxLvl());
                result = bordersV2.getMaxLvl().toString();
            }
            if (update.getAutoBaseLvl() != null) {
                bordersV2.setAutoBaseLvl(update.getAutoBaseLvl());
                result = bordersV2.getAutoBaseLvl().toString();
            }
            if (update.getBaseLvlCnt() != null) {
                bordersV2.setBaseLvlCnt(update.getBaseLvlCnt());
                result = bordersV2.getBaseLvlCnt().toString();
            }
            if (update.getBaseLvlType() != null) {
                bordersV2.setBaseLvlType(update.getBaseLvlType());
                result = bordersV2.getBaseLvlType().toString();
            }
            if (update.getStep() != null) {
                bordersV2.setStep(update.getStep());
                result = bordersV2.getStep().toString();
            }
            if (update.getGapStep() != null) {
                bordersV2.setGapStep(update.getGapStep());
                result = bordersV2.getGapStep().toString();
            }
            if (update.getbAddDelta() != null) {
                bordersV2.setbAddDelta(update.getbAddDelta());
                result = bordersV2.getbAddDelta().toString();
            }
            if (update.getOkAddDelta() != null) {
                bordersV2.setOkAddDelta(update.getOkAddDelta());
                result = bordersV2.getOkAddDelta().toString();
            }
        } catch (Exception e) {
            return new ResultJson("Wrong request", e.getMessage());
        }

        persistenceService.saveBorderParams(bP);

        return new ResultJson(result, result);
    }

}
