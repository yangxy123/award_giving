package com.giving.util;

/**
 * @author zzby
 * @version 创建时间： 2026/1/15 下午2:33
 */
public class TableNameUtil {
    private TableNameUtil() {}
    public static String safePrefix(String title) {
        if (title == null || !title.matches("[0-9a-zA-Z_]+")) {
            throw new IllegalArgumentException("Illegal table prefix: " + title);
        }
        return title;
    }
}
