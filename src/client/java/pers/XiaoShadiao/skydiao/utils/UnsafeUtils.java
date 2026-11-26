package pers.XiaoShadiao.skydiao.utils;

import java.lang.reflect.Field;

public class UnsafeUtils {

    private static final Class<?> unsafeClass;
    private static final Object unsafeObj;

    // 全部用反射, 不然神秘IDEA和编译器会疯狂爆出警告。
    // 确保没有用到import sun.misc.Unsafe或直接申明字段/变量类型为sun.misc.Unsafe

    static {
        try {
            unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafeObj = unsafeField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void applyValueToStaticField(Field field, Object value) throws Throwable {
//            Unsafe unsafe = (Unsafe) unsafeObj;
//
//            long staticFieldOffset = unsafe.staticFieldOffset(field);
//            Object staticFieldBase = unsafe.staticFieldBase(field);
//            unsafe.putObject(staticFieldBase, staticFieldOffset, value);
        long staticFieldOffset = (long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafeObj, field);
        unsafeClass.getMethod("putObject", Object.class, long.class, Object.class).invoke(unsafeObj, field.getDeclaringClass(), staticFieldOffset, value);
    }

    public static void applyValueToField(Field field, Object instance, Object value) throws Throwable {
//        Unsafe unsafe = (Unsafe) unsafeObj;
//
//        long fieldOffset = unsafe.objectFieldOffset(field);
//        unsafe.putObject(instance, fieldOffset, value);
        long fieldOffset = (long) unsafeClass.getMethod("objectFieldOffset", Field.class).invoke(unsafeObj, field);
        unsafeClass.getMethod("putObject", Object.class, long.class, Object.class).invoke(unsafeObj, instance, fieldOffset, value);
    }

    public static Object bypass(Object obj) {
        Object o = new Object() {
            public Object holder;
        };
        try {
            Field holder = o.getClass().getDeclaredField("holder");
            applyValueToField(holder, o, obj);
            holder.setAccessible(true);
            return holder.get(o);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
