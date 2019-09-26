package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.events.NtUsdCheckEvent;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PosDiffPortionsService {

    @Autowired
    private PosDiffService posDiffService;

    /**
     * Runs on each pos change (bitmex on posDiffService event; okex each 200ms).
     */
    @Async("ntUsdSignalCheckExecutor")
    @EventListener(NtUsdCheckEvent.class)
    public void doCheck() {

//        final BigDecimal dcMainSet = posDiffService.getDcMainSet();
//        log.info("doCheck. dcMainSet=" + dcMainSet);
    }

}
