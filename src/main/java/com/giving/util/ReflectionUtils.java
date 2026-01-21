package com.giving.util;

import java.lang.reflect.Field;

/** 
* @author yangxy
* @version 创建时间：2026年1月21日 下午3:16:21 
*/
public class ReflectionUtils {
	/**
     * 获取对象的属性值
     * @param obj 对象实例
     * @param fieldName 属性名
     * @return 属性值
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        try {
            // 1. 获取Class对象
            Class<?> clazz = obj.getClass();
            
            // 2. 获取Field对象（包括私有属性）
            Field field = getDeclaredField(clazz, fieldName);
            
            if (field == null) {
                throw new NoSuchFieldException(fieldName);
            }
            
            // 3. 设置可访问性
            field.setAccessible(true);
            
            // 4. 获取属性值
            return field.get(obj);
            
        } catch (Exception e) {
            throw new RuntimeException("获取属性值失败: " + fieldName, e);
        }
    }
    
    /**
     * 递归查找字段（包括父类）
     */
    private static Field getDeclaredField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 查找父类
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return getDeclaredField(superClass, fieldName);
            }
            return null;
        }
    }
}
