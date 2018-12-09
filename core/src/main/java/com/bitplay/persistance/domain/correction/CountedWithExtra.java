package com.bitplay.persistance.domain.correction;

@SuppressWarnings("Duplicates")
public abstract class CountedWithExtra {

    abstract void incFailed();

    abstract void incSuccessful();

    protected void incTotalCount() {
        inProgress = true;
    }

    protected void incTotalCountExtra() {
        inProgressExtra = true;
    }

    private boolean inProgress;

    private boolean inProgressExtra;

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

    public boolean tryIncFailedExtra() {
        boolean changed = false;
        if (inProgressExtra) {
            this.incFailed();
            inProgressExtra = false;
            changed = true;
        }
        return changed;
    }

    public boolean tryIncSuccessfulExtra() {
        boolean changed = false;
        if (inProgressExtra) {
            this.incSuccessful();
            inProgressExtra = false;
            changed = true;
        }
        return changed;
    }


}
