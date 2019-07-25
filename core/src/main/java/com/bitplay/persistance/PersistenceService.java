package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.ExchangePair;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.MarketDocument;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode.Field;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.persistance.repository.BorderParamsRepository;
import com.bitplay.persistance.repository.CorrParamsRepository;
import com.bitplay.persistance.repository.DeltaParamsRepository;
import com.bitplay.persistance.repository.LiqParamsRepository;
import com.bitplay.persistance.repository.SwapParamsRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 6/16/17.
 */
@Service
public class PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private LiqParamsRepository liqParamsRepository;

    @Autowired
    private DeltaParamsRepository deltaParamsRepository;

    @Autowired
    private SwapParamsRepository swapParamsRepository;

    @Autowired
    private CorrParamsRepository corrParamsRepository;

    @Autowired
    private BorderParamsRepository borderParamsRepository;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private OrderRepositoryService orderRepositoryService;

    @Autowired
    private MongoTemplate mongoTemplate;

    public void saveGuiParams(GuiParams guiParams) {
        mongoTemplate.save(guiParams);
    }

    public GuiParams fetchGuiParams() {
        GuiParams guiParams = mongoTemplate.findById(1L, GuiParams.class);

        return guiParams;
    }

    public void saveGuiLiqParams(GuiLiqParams guiLiqParams) {
        mongoTemplate.save(guiLiqParams);
    }

    public GuiLiqParams fetchGuiLiqParams() {
        GuiLiqParams guiLiqParams = mongoTemplate.findById(3L, GuiLiqParams.class);
        return guiLiqParams;
    }

    public void saveLastPriceDeviation(LastPriceDeviation lastPriceDeviation) {
        mongoTemplate.save(lastPriceDeviation);
    }

    public LastPriceDeviation fetchLastPriceDeviation() {
        return mongoTemplate.findById(4L, LastPriceDeviation.class);
    }


    public void saveLiqParams(LiqParams liqParams, String marketName) {
        setMarketDocumentName(liqParams, marketName);
        liqParamsRepository.save(liqParams);
    }

    public LiqParams fetchLiqParams(String marketName) {
        return liqParamsRepository.findFirstByMarketName(marketName);
    }

    public void storeDeltaParams(DeltaParams deltaParams) {
        deltaParams.setId(1L);
        deltaParams.setName("InstantDelta");
        deltaParamsRepository.save(deltaParams);
    }

    public DeltaParams fetchDeltaParams() {
        return deltaParamsRepository.findFirstByDocumentId(1L);
    }

    public synchronized void saveSwapParams(SwapParams swapParams, String marketName) {
        setMarketDocumentName(swapParams, marketName);
        swapParamsRepository.save(swapParams);
    }

    private void setMarketDocumentName(MarketDocument swapParams, String marketName) {
        long id = 1L;
        if (marketName.equals("bitmex")) {
            id = 2L;
        }
        if (marketName.equals("okcoin")) {
            id = 3L;
        }
        swapParams.setId(id);
        swapParams.setMarketName(marketName);
    }

    public synchronized SwapParams fetchSwapParams(String marketName) {
        final SwapParams first = swapParamsRepository.findFirstByMarketName(marketName);
        return first == null ? SwapParams.createDefault() : first;
    }

    public void saveCorrParams(CorrParams corrParams) {
        corrParamsRepository.save(corrParams);
    }

    public CorrParams fetchCorrParams() {
        CorrParams corrParams = corrParamsRepository.findFirstByExchangePair(ExchangePair.BITMEX_OKEX);

        // transient fields
        BigDecimal cm = bitmexService.getCm();
        boolean isEth = bitmexService.getContractType() != null && bitmexService.getContractType().isEth();
        corrParams.getCorr().setIsEth(isEth);
        corrParams.getCorr().setCm(cm);
        corrParams.getPreliq().setIsEth(isEth);
        corrParams.getPreliq().setCm(cm);

        // volatile mode params
        final Settings settings = settingsRepositoryService.getSettings();
        if (settings.getTradingModeState().getTradingMode() == TradingMode.VOLATILE) {
            final SettingsVolatileMode vm = settings.getSettingsVolatileMode();
            if (vm.getActiveFields().contains(Field.corr_adj)) {
                if (vm.getCorrMaxTotalCount() != null) {
                    corrParams.getCorr().setMaxTotalCount(vm.getCorrMaxTotalCount());
                }
                if (vm.getAdjMaxTotalCount() != null) {
                    corrParams.getAdj().setMaxTotalCount(vm.getAdjMaxTotalCount());
                }
            }
        }

        return corrParams;
    }

    public BorderParams fetchBorders() {
        return borderParamsRepository.findOne(1L);
    }

    public void saveBorderParams(BorderParams borderParams) {
        borderParamsRepository.save(borderParams);
    }

    public SettingsRepositoryService getSettingsRepositoryService() {
        return settingsRepositoryService;
    }

    public OrderRepositoryService getOrderRepositoryService() {
        return orderRepositoryService;
    }

    public void resetSettingsPreset() {
        final Settings settings = settingsRepositoryService.getSettings();
        settings.setCurrentPreset("");
        settingsRepositoryService.saveSettings(settings);
    }
}
