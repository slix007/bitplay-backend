package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.PlacingBlocks;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
@Service
public class PlacingBlocksService {

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    public PlBlocks getPlacingBlocks(OrderBook bitmexOrderBook, OrderBook okexOrderBook) {
        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();
        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
            return new PlBlocks(placingBlocks.getDynMaxBlockBitmex(), placingBlocks.getDynMaxBlockOkex());
        } else if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.DYNAMIC) {
            return new PlBlocks(placingBlocks.getDynMaxBlockBitmex(), placingBlocks.getDynMaxBlockOkex());
        }
        throw new IllegalStateException("Unhandled PlacsingBlocks version");
    }

    private PlBlocks getDynamicBlock(BigDecimal placingBlock, OrderBook orderBook) {
        // b_delta = b_ob_bid_qu[1] - ok_ob_ask_qu[1]
       /* if (b_si == true)
        {
            int i = 1;
            int k = 1;
            int b_obt = b_ob; // order book, целый класс
            int ok_obt = ok_ob; //order book, целый класс
            int b_block = 0;
            int b_d = b_delta; // b_delta = b_ob_bid_qu[1] - ok_ob_ask_qu[1]
            while (b_d >= b_border && b_max_block > b_block ) do
            {
                am = b_ob_bid_am[i] - ok_ob_ask_am[k]*100;
                if ( am > 0 )
                {
                    b_block += ok_ob_ask_am[k]*100;
                    b_ob_bid_am[i] = am;
                    ok_ob_ask_am[k] = 0;
                    k++;
                }
                if ( am < 0)
                {
                    b_block += b_ob_bid_am[i];
                    b_ob_bid_am[i] = 0;
                    ok_ob_ask_am[k] = -am;
                    i++;
                }
                if ( am == 0)
                {
                    b_block += b_ob_bid_am[i];
                    b_ob_bid_am[i] = 0;
                    ok_ob_ask_am[k] = 0;
                    i++;
                    k++;
                }
                b_d = b_ob_bid_qu[i] - ok_ob_ask_qu[k];
            }
            b_block = min(b_max_block, b_block);
        }
*/

        return null;
    }


}
