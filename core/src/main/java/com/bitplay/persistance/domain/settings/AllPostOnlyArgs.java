package com.bitplay.persistance.domain.settings;

import com.bitplay.arbitrage.dto.ArbType;
import lombok.Data;

/**
 * Created by Sergey Shurmin on 10/5/19.
 */
@Data
public class AllPostOnlyArgs {

    private OkexPostOnlyArgs right;
    private OkexPostOnlyArgs left;

    public static AllPostOnlyArgs defaults() {
        final AllPostOnlyArgs newObj = new AllPostOnlyArgs();
        newObj.left = OkexPostOnlyArgs.defaults();
        newObj.right = OkexPostOnlyArgs.defaults();
        return newObj;
    }


    public OkexPostOnlyArgs get(ArbType arbType) {
        if (arbType == ArbType.LEFT) {
            return left;
        }
        return right;
    }
}
