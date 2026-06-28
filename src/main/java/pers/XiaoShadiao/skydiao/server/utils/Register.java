package pers.XiaoShadiao.skydiao.server.utils;

import java.lang.reflect.Field;
import java.util.function.Consumer;

public class Register {

    public static <T> void execRegister(Class<?> targetClassFields, Class<T> instanceClass, Consumer<T> consumer) {
        for (Field field : targetClassFields.getDeclaredFields()) {
            if(instanceClass.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                try {
                    consumer.accept((T) field.get(null));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new RuntimeException("Error executing registering", e);
                }
            }
        }
    }

}
