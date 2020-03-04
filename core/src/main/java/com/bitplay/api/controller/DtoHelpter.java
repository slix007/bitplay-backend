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
        try {
            for (Field f : inObj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                final Object inObjFieldValue = f.get(inObj);
                if (inObjFieldValue != null) {
                    final String name = f.getName();
                    final Field toUpdateF = toUpdate.getClass().getDeclaredField(name);
                    toUpdateF.setAccessible(true);
                    if (f.getType().getTypeName().startsWith("com.bitplay.persistance.domain")) {
                        Object toUpdateObjFieldValue = toUpdateF.get(toUpdate);
                        updateNotNullFieldsWithNested(inObjFieldValue, toUpdateObjFieldValue);
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
