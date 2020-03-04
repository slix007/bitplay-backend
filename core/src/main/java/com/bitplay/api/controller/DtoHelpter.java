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

    public static void updateNotNullFieldsWithNested(Object inObj, Object toUpdate) {
        updateNotNullFieldsWithNested(inObj, toUpdate, 0);
    }

    public static void updateNotNullFieldsWithNested(Object inObj, Object toUpdate, int lvl) {
        int maxLvl = 3;
        lvl++;

        try {
            for (Field f : inObj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                final Object inObjFieldValue = f.get(inObj);
                if (inObjFieldValue != null) {
                    final String name = f.getName();
                    final Field toUpdateF = toUpdate.getClass().getDeclaredField(name);
                    toUpdateF.setAccessible(true);
                    if (toUpdateF.getType().isEnum()) {
                        toUpdateF.set(toUpdate, inObjFieldValue);
                    } else if (f.getType().getTypeName().startsWith("com.bitplay.persistance.domain") && lvl <= maxLvl) {
                        Object toUpdateObjFieldValue = toUpdateF.get(toUpdate);
                        updateNotNullFieldsWithNested(inObjFieldValue, toUpdateObjFieldValue, lvl);
                    } else {
                        toUpdateF.set(toUpdate, inObjFieldValue);
                    }
                    toUpdateF.setAccessible(false);
                }
                f.setAccessible(false);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("", e);
        }
    }


}
