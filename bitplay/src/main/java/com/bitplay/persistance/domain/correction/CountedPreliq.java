package com.bitplay.persistance.domain.correction;

import com.bitplay.market.bitmex.BitmexService;

public abstract class CountedPreliq {

    abstract void incFailed();

    abstract void incSuccessful();

    protected void incTotalCount(String marketName) {
        if (marketName.equals(BitmexService.NAME)) {
            inProgressBitmex = true;
        } else {
            inProgressOkex = true;
        }
    }

    private boolean inProgressBitmex;
    private boolean inProgressOkex;

//    public boolean tryIncOnFinish(boolean stillViolated) {
//        return stillViolated ? tryIncFailed() : tryIncSuccessful();
//    }

    public boolean tryIncFailed(String marketName) {
        boolean changed = false;
        if (marketName.equals(BitmexService.NAME)) {
            if (inProgressBitmex) {
                this.incFailed();
                inProgressBitmex = false;
                changed = true;
            }
        } else {
            if (inProgressOkex) {
                this.incFailed();
                inProgressOkex = false;
                changed = true;
            }
        }
        return changed;
    }

    public boolean tryIncSuccessful(String marketName) {
        boolean changed = false;
        if (marketName.equals(BitmexService.NAME)) {
            if (inProgressBitmex) {
                this.incSuccessful();
                inProgressBitmex = false;
                changed = true;
            }
        } else {
            if (inProgressOkex) {
                this.incSuccessful();
                inProgressOkex = false;
                changed = true;
            }
        }
        return changed;
    }


}
