package com.bitplay.external;

import static org.junit.Assert.*;

import org.junit.Test;

public class DestinationResolverByFileTest {

    @Test
    public void defineWhereToSend() {
        String l2 = "cor   ,  adj, plq ;  ,";
        final String[] shortTypes = l2.split("\\s*,\\s*");
        System.out.println(shortTypes);
        for (int i = 0; i < shortTypes.length; i++) {
            System.out.println(shortTypes[i] + '=' + shortTypes[i].length());
        }


    }
}