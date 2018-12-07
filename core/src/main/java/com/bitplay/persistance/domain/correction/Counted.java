package com.bitplay.persistance.domain.correction;

import org.springframework.data.annotation.Transient;

public abstract class Counted {

    abstract void incFailed();

    abstract void incSuccessful();

    protected void incTotalCount() {
        inProgress = true;
    }

    @Transient
    private boolean inProgress = false;

    public boolean tryIncOnFinish(boolean stillViolated) {
        return stillViolated ? tryIncFailed() : tryIncSuccessful();
    }

    public boolean tryIncFailed() {
        boolean changed = false;
        if (inProgress) {
            this.incFailed();
            inProgress = false;
            changed = true;
        }
        return changed;
    }

    public boolean tryIncSuccessful() {
        boolean changed = false;
        if (inProgress) {
            this.incSuccessful();
            inProgress = false;
            changed = true;
        }
        return changed;
    }


}
