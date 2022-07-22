package com.kindaras.objectdatabase;

import android.util.Log;

import java.lang.reflect.Field;

public class DatabaseQueryGenerator {

    public static String getTableQueryByClass(Class<?> obj) {
        StringBuilder query = new StringBuilder();
        query.append("CREATE TABLE IF NOT EXISTS ");
        query.append(obj.getSimpleName());
        query.append(" (");
        for (Field field : obj.getDeclaredFields()) {
            query.append(getCompleteField(field));
            query.append(",");
        }
        query.delete(query.length()-1, query.length());
        query.append(");");
        return query.toString();
    }

    private static String getCompleteField(Field field) {
        return getSqlFieldName(field) + " " + getSqlType(field.getType());
    }

    private static String getSqlFieldName(Field field) {
        try {
            field.getType().getField("id");
            return "id" + field.getName();
        } catch (NoSuchFieldException e) {
            return field.getName();
        }
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
            try {
                type.getField("id");
                return "int";
            } catch (NoSuchFieldException e) {
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
            }
        }
        return null;
    }

}
