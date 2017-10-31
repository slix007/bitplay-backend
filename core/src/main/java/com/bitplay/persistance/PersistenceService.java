package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.Counters;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.MarketDocument;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.repository.BorderParamsRepository;
import com.bitplay.persistance.repository.CountersRepository;
import com.bitplay.persistance.repository.DeltaParamsRepository;
import com.bitplay.persistance.repository.GuiParamsRepository;
import com.bitplay.persistance.repository.LiqParamsRepository;
import com.bitplay.persistance.repository.SwapParamsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 6/16/17.
 */
@Service
public class PersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);

    @Autowired
    private GuiParamsRepository guiParamsRepository;

    @Autowired
    private LiqParamsRepository liqParamsRepository;

    @Autowired
    private DeltaParamsRepository deltaParamsRepository;

    @Autowired
    private SwapParamsRepository swapParamsRepository;

    @Autowired
    private CountersRepository countersRepository;

    @Autowired
    private BorderParamsRepository borderParamsRepository;

    public void saveGuiParams(GuiParams deltas) {
        deltas.setId(1L);
        guiParamsRepository.save(deltas);
    }

    public GuiParams fetchGuiParams() {
        return guiParamsRepository.findFirstByDocumentId(1L);
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
        return first == null ? new SwapParams() : first;
    }

    public void saveCounters(Counters counters) {
        counters.setId(1L);
        countersRepository.save(counters);
    }

    public Counters fetchCounters() {
        final Counters firstByDocumentId = countersRepository.findFirstByDocumentId(1L);
        return firstByDocumentId == null ? new Counters() : firstByDocumentId;
    }

    public BorderParams fetchBorders() {
        return borderParamsRepository.findOne(1L);
    }

    public void saveBorderParams(BorderParams borderParams) {
        borderParamsRepository.save(borderParams);
    }
}
