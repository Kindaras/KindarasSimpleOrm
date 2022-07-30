package com.kindaras.objectdatabase;

import android.util.Log;

import java.lang.reflect.Field;
import java.sql.SQLException;

public class DatabaseQueryGenerator {

    public static String getTableQueryByClass(Class<?> obj) throws SQLException {
        StringBuilder query = new StringBuilder();
        query.append("CREATE TABLE IF NOT EXISTS ");
        query.append(obj.getSimpleName());
        query.append(" (");
        for (Field field : obj.getDeclaredFields()) {
            if (field.getDeclaredAnnotation(Ignored.class) == null) {
                query.append(getCompleteField(field));
                if (field.getDeclaredAnnotation(PrimaryKey.class) != null)
                    query.append(" PRIMARY KEY NOT NULL");
                if (field.getDeclaredAnnotation(AutoIncrement.class) != null && (!field.getType().getSimpleName().equals("int") && !field.getType().getSimpleName().equals("Integer")))
                    throw new SQLException("Column not INTEGER can't be AUTOINCREMENT");
                if (field.getDeclaredAnnotation(AutoIncrement.class) != null && field.getDeclaredAnnotation(PrimaryKey.class) == null)
                    throw new SQLException("Column not PRIMARY KEY can't be AUTOINCREMENT");
                query.append(",");
            }
        }
        query.delete(query.length()-1, query.length());
        query.append(");");
        return query.toString();
    }

    private static String getCompleteField(Field field) {
        return field.getName() + " " + getSqlType(field.getType());
    }

    private static String getSqlType(Class<?> type) {
        Log.e("return", type.getSimpleName());
        if (type.isEnum()) {
            if (type.getMethods()[0].getReturnType().equals(String.class))
                return "text";
            else if (type.getMethods()[0].getReturnType().equals(Integer.class))
                return "integer";
            else if (type.getMethods()[0].getReturnType().getSimpleName().equals("int"))
                return "integer";
            else if (type.getMethods()[0].getReturnType().getSimpleName().equalsIgnoreCase("byte"))
                return "integer";
            else if (type.getMethods()[0].getReturnType().getSimpleName().equalsIgnoreCase("byte[]"))
                return "blob";
        } else {
            if (!type.isPrimitive()) {
                for (Field f : type.getDeclaredFields()) {
                    if (f.isAnnotationPresent(PrimaryKey.class)) {
                        return getSqlTypeText(f.getType());
                    }
                }
                return getSqlTypeText(type);
            } else {
                return getSqlTypeText(type);
            }
        }
        return null;
    }

    private static String getSqlTypeText(Class<?> type) {
        if (type.getSimpleName().equals("Integer") || type.getSimpleName().equals("int"))
            return "integer";
        if (type.getSimpleName().equals("String"))
            return "text";
        if (type.getSimpleName().equalsIgnoreCase("boolean"))
            return "integer";
        if (type.getSimpleName().equalsIgnoreCase("byte"))
            return "integer";
        if (type.getSimpleName().equalsIgnoreCase("byte[]"))
            return "blob";
        if (type.getSimpleName().equalsIgnoreCase("double") || type.getSimpleName().equalsIgnoreCase("float"))
            return "real";
        if (type.getSimpleName().equalsIgnoreCase("LocalDate"))
            return "date";
        if (type.getSimpleName().equalsIgnoreCase("LocalDateTime"))
            return "datetime";
        else
            return "text";
    }

}
