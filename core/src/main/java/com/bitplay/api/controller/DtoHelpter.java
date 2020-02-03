package com.bitplay.api.controller;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

@Slf4j
public class DtoHelpter {

    public static void updateNotNullFields(Object inObj, Object toUpdate) {
        try {
            for (Field f : inObj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                final Object value = f.get(inObj);
                if (value != null) {
                    final String name = f.getName();
                    final Field toUpdateF = toUpdate.getClass().getDeclaredField(name);
                    toUpdateF.setAccessible(true);
                    toUpdateF.set(toUpdate, value);
                    toUpdateF.setAccessible(false);
                }
                f.setAccessible(false);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("", e);
        }
    }


}
