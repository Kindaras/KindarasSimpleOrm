package com.kindaras.objectdatabase;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbHelper {
    private SQLiteDatabase db;
    private Database dbUtil;
    private static DbHelper dbHelper;

    private final List<String> tables = new ArrayList<>();
    private final Map<String, List<String>> structure = new HashMap<>();
    private int dbVersion;
    private int oldDbVersion;

    @SuppressLint("Range")
    private DbHelper(Context context, String name, int version) {
        open(context, name, version);
        dbVersion = version;
        dbHelper = this;
        oldDbVersion = dbUtil.version;
        Cursor c = rawQuery("select name from sqlite_master where type = 'table' and name not like 'sqlite_%' and name != 'android_metadata'");
        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    String table_name = c.getString(c.getColumnIndex("name"));
                    tables.add(table_name);
                    if (oldDbVersion != dbVersion) {
                        Cursor c1 = rawQuery("select name from pragma_table_info('" + table_name + "')");
                        List<String> columns = new ArrayList<>();
                        if (c1 != null) {
                            if (c1.moveToFirst()) {
                                do {
                                    columns.add(c1.getString(c.getColumnIndex("name")));
                                } while (c1.moveToNext());
                            }
                            c1.close();
                        }
                        structure.put(table_name, columns);
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
    }

    public static DbHelper getDb(Context context, String name, int version) {
        if (dbHelper == null) {
            dbHelper = new DbHelper(context.getApplicationContext(), name, version);
        }
        return dbHelper;
    }

    private void open(Context context, String name, int version) {
        dbUtil = new Database(context.getApplicationContext(), name, version);
        db = dbUtil.getWritableDatabase();
        db.enableWriteAheadLogging();
    }

    @Nullable
    public Cursor rawQuery(String query) {
        if (db.isOpen() && !db.isReadOnly()) {
            if (query.startsWith("DELETE") || query.startsWith("INSERT")) {
                db.execSQL(query);
                return null;
            } else {
                return db.rawQuery(query, null);
            }
        } else
            return null;
    }

    public void createTable(Class<?> insertedClass) {
        boolean ok = false;
        List<String> fields = new ArrayList<>();
        for (Field f : insertedClass.getDeclaredFields()) {
            String t = f.getType().getSimpleName();
            if (!t.equals("String") &&
                    !t.equals("int") &&
                    !t.equals("Integer") &&
                    !t.equals("LocalDate") &&
                    !t.equals("LocalDateTime") &&
                    !t.equalsIgnoreCase("double") &&
                    !t.equalsIgnoreCase("float") &&
                    !t.equalsIgnoreCase("boolean") &&
                    !t.equalsIgnoreCase("byte") &&
                    !t.equalsIgnoreCase("byte[]")) {
                /*if (!tables.contains(t))
                    throw new ObjectDatabaseException(new java.sql.SQLException("Can't point to a table that don't exist"));*/
                for (Field ff : f.getType().getDeclaredFields()) {
                    if (ff.isAnnotationPresent(PrimaryKey.class)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok)
                    throw new ObjectDatabaseException(new java.sql.SQLException("Can't point to a table with no primary key"));
            }
            fields.add(f.getName());
        }
        if (!tables.contains(insertedClass.getSimpleName())) {
            try {
                db.execSQL(DatabaseQueryGenerator.getTableQueryByClass(insertedClass));
            } catch (java.sql.SQLException exception) {
                throw new ObjectDatabaseException(exception);
            }
            tables.add(insertedClass.getSimpleName());
            List<String> columns = new ArrayList<>();
            for (Field field : insertedClass.getDeclaredFields()) {
                if (field.getDeclaredAnnotation(Ignored.class) == null) {
                    columns.add(field.getName());
                }
            }
            structure.put(insertedClass.getSimpleName(), columns);
        } else {
            if (dbVersion > oldDbVersion) {
                List<String> oldFields = structure.get(insertedClass.getSimpleName());
                for (String field : fields) {
                    if (!oldFields.contains(field)) {
                        try {
                            String query = DatabaseQueryGenerator.getAddColumnQuery(insertedClass, field);
                            db.execSQL(query);
                            structure.get(insertedClass.getSimpleName()).add(field);
                        } catch (NoSuchFieldException | java.sql.SQLException e) {
                            throw new ObjectDatabaseException(e);
                        }
                    }
                }
            }
        }
    }

    public <T> void multiInsert(List<T> array) {
        multiInsert((T[]) array.toArray());
    }

    public <T> void multiInsert(T[] array) {
        if (array.length == 0)
            return;
        Class<?> insertedClass = array[0].getClass();
        if (!tables.contains(insertedClass.getSimpleName()))
            createTable(insertedClass);
        if (array[0].getClass() == insertedClass) {
            String query = "INSERT INTO " + insertedClass.getSimpleName() + " (";
            for (Field field : insertedClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(AutoIncrement.class) &&
                        !field.isAnnotationPresent(Ignored.class))
                    query += field.getName() + ",";
            }
            query = query.substring(0, query.length() - 1);
            query += ") VALUES (";
            for (int x = 0; x < insertedClass.getDeclaredFields().length; x++) {
                if (!insertedClass.getDeclaredFields()[x].isAnnotationPresent(AutoIncrement.class) &&
                        !insertedClass.getDeclaredFields()[x].isAnnotationPresent(Ignored.class))
                    query += "?,";
            }
            query = query.substring(0, query.length() - 1);
            query += ")";
            db.beginTransactionNonExclusive();
            SQLiteStatement stmt = db.compileStatement(query);
            for (T obj : array) {
                int x = 0;
                for (Field f : insertedClass.getDeclaredFields()) {
                    if (!f.isAnnotationPresent(AutoIncrement.class) &&
                            !f.isAnnotationPresent(Ignored.class)) {
                        x++;
                        f.setAccessible(true);
                        try {
                            if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                                stmt.bindString(x, (Boolean) f.get(obj) ? "1" : "0");
                            else if (f.getType().getSimpleName().equals("byte[]"))
                                stmt.bindBlob(x, (byte[]) f.get(obj));
                            else if (f.getType().getSimpleName().equals("Byte[]")) {
                                byte[] bb = toPrimitives((Byte[]) f.get(obj));
                                if (bb != null)
                                    stmt.bindBlob(x, bb);
                                else
                                    stmt.bindNull(x);
                            } else {
                                if (tables.contains(f.getType().getSimpleName())) {
                                    for (Field ff : f.getType().getDeclaredFields()) {
                                        if (ff.isAnnotationPresent(PrimaryKey.class)) {
                                            boolean isAccessible = ff.isAccessible();
                                            if (!isAccessible)
                                                ff.setAccessible(true);
                                            if (f.get(obj) != null) {
                                                if (!checkExistance(f.get(obj)))
                                                    throw new ObjectDatabaseException(new SQLException("Row don't exist in such table"));
                                                stmt.bindString(x, ff.get(f.get(obj)).toString());
                                            } else {
                                                stmt.bindString(x, "null");
                                            }
                                            if (!isAccessible)
                                                ff.setAccessible(false);
                                            break;
                                        }
                                    }
                                } else {
                                    if (f.get(obj) != null)
                                        stmt.bindString(x, f.get(obj).toString());
                                    else
                                        stmt.bindNull(x);
                                }
                            }
                        } catch (IllegalAccessException e) {
                            throw new ObjectDatabaseException(e);
                        }
                        f.setAccessible(false);
                    }
                }
                long id = stmt.executeInsert();
                try {
                    for (Field f : insertedClass.getDeclaredFields()) {
                        if (f.isAnnotationPresent(PrimaryKey.class) && (f.getType().getSimpleName().equalsIgnoreCase("float") || f.getType().getSimpleName().equals("int") || f.getType().getSimpleName().equals("Integer"))) {
                            boolean isAccessible = f.isAccessible();
                            if (!isAccessible)
                                f.setAccessible(true);
                            f.set(obj, Integer.parseInt(id + ""));
                            f.setAccessible(isAccessible);
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new ObjectDatabaseException(e);
                }
                stmt.clearBindings();
            }
            stmt.close();
            db.setTransactionSuccessful();
            db.endTransaction();
        } else
            throw new ObjectDatabaseException(new IllegalAccessException("Class type error!"));
    }

    public void insertInto(Object obj) {
        Class<?> insertedClass = obj.getClass();
        if (!tables.contains(insertedClass.getSimpleName()))
            createTable(insertedClass);
        if (obj.getClass() == insertedClass) {
            String query = "INSERT INTO " + insertedClass.getSimpleName() + " (";
            for (Field field : insertedClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(AutoIncrement.class) &&
                        !field.isAnnotationPresent(Ignored.class))
                    query += field.getName() + ",";
            }
            query = query.substring(0, query.length() - 1);
            query += ") VALUES (";
            for (int x = 0; x < insertedClass.getDeclaredFields().length; x++) {
                if (!insertedClass.getDeclaredFields()[x].isAnnotationPresent(AutoIncrement.class) &&
                        !insertedClass.getDeclaredFields()[x].isAnnotationPresent(Ignored.class))
                    query += "?,";
            }
            query = query.substring(0, query.length() - 1);
            query += ")";
            db.beginTransactionNonExclusive();
            SQLiteStatement stmt = db.compileStatement(query);
            int x = 0;
            try {
                for (Field f : insertedClass.getDeclaredFields()) {
                    if (!f.isAnnotationPresent(AutoIncrement.class) &&
                            !f.isAnnotationPresent(Ignored.class)) {
                        x++;
                        boolean isAccessible = f.isAccessible();
                        f.setAccessible(true);
                        if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                            stmt.bindString(x, (Boolean) f.get(obj) ? "1" : "0");
                        else if (f.getType().getSimpleName().equals("byte[]"))
                            stmt.bindBlob(x, (byte[]) f.get(obj));
                        else if (f.getType().getSimpleName().equals("Byte[]")) {
                            byte[] bb = toPrimitives((Byte[]) f.get(obj));
                            if (bb != null)
                                stmt.bindBlob(x, bb);
                            else
                                stmt.bindNull(x);
                        } else {
                            if (tables.contains(f.getType().getSimpleName())) {
                                for (Field ff : f.getType().getDeclaredFields()) {
                                    if (ff.isAnnotationPresent(PrimaryKey.class)) {
                                        boolean isAccessible2 = ff.isAccessible();
                                        ff.setAccessible(true);
                                        if (f.get(obj) != null) {
                                            if (!checkExistance(f.get(obj)))
                                                throw new SQLException("Row don't exist in such table");
                                            stmt.bindString(x, ff.get(f.get(obj)).toString());
                                        } else {
                                            stmt.bindString(x, "null");
                                        }
                                        ff.setAccessible(isAccessible2);
                                        break;
                                    }
                                }
                            } else {
                                if (f.get(obj) != null)
                                    stmt.bindString(x, f.get(obj).toString());
                                else
                                    stmt.bindNull(x);
                            }
                        }
                        f.setAccessible(isAccessible);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new ObjectDatabaseException(e);
            }
            long id = stmt.executeInsert();
            try {
                for (Field f : insertedClass.getDeclaredFields()) {
                    if (f.isAnnotationPresent(PrimaryKey.class) && (f.getType().getSimpleName().equalsIgnoreCase("float") || f.getType().getSimpleName().equals("int") || f.getType().getSimpleName().equals("Integer"))) {
                        boolean isAccessible = f.isAccessible();
                        if (!isAccessible)
                            f.setAccessible(true);
                        f.set(obj, Integer.parseInt(id + ""));
                        f.setAccessible(isAccessible);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new ObjectDatabaseException(e);
            }
            stmt.close();
            db.setTransactionSuccessful();
            db.endTransaction();
        } else
            throw new ObjectDatabaseException(new IllegalAccessException("Class type error!"));
    }

    @SuppressLint("Range")
    public <T> T getByPrimaryKey(Class<T> returnClass, Object index) {
        if (!tables.contains(returnClass.getSimpleName())) {
            throw new SQLException("No such table!");
        }
        String primaryKeyName = null;
        boolean isInt = false;
        for (Field f : returnClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(PrimaryKey.class)) {
                if (f.getType().getSimpleName().equals("int") || f.getType().getSimpleName().equals("Integer"))
                    isInt = true;
                primaryKeyName = f.getName();
                break;
            }
        }
        if (primaryKeyName == null)
            throw new SQLiteException("No primary key in table");
        String selection;
        if (isInt)
            selection = primaryKeyName + " = " + index.toString();
        else
            selection = primaryKeyName + " = '" + index.toString() + "'";
        List<String> columns = new ArrayList<>();
        for (Field f : returnClass.getDeclaredFields()) {
            if (!f.isAnnotationPresent(Ignored.class))
                columns.add(f.getName());
        }
        Cursor c = db.query(returnClass.getSimpleName(), columns.toArray(new String[0]), selection, null, null, null, null);
        if (c.moveToFirst()) {
            T _return = null;
            try {
                _return = returnClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new ObjectDatabaseException(e);
            }
            try {
                for (Field f : returnClass.getDeclaredFields()) {
                    if (!f.isAnnotationPresent(Ignored.class)) {
                        boolean acc = f.isAccessible();
                        if (!acc)
                            f.setAccessible(true);
                        if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                            f.set(_return, c.getInt(c.getColumnIndex(f.getName())) > 0);
                        else if (f.getType().getSimpleName().equalsIgnoreCase("byte"))
                            f.set(_return, (byte) c.getInt(c.getColumnIndex(f.getName())));
                        else if (f.getType().getSimpleName().equalsIgnoreCase("double"))
                            f.set(_return, c.getDouble(c.getColumnIndex(f.getName())));
                        else if (f.getType().getSimpleName().equals("LocalDate")) {
                            String data = c.getString(c.getColumnIndex(f.getName()));
                            if (data != null)
                                f.set(_return, LocalDate.parse(data));
                        } else if (f.getType().getSimpleName().equals("LocalDateTime")) {
                            String data = c.getString(c.getColumnIndex(f.getName()));
                            if (data != null)
                                f.set(_return, LocalDateTime.parse(data));
                        } else {
                            if (f.getType().getSimpleName().equals("Byte[]"))
                                f.set(_return, toObjects((byte[]) getObject(c.getColumnIndex(f.getName()), c)));
                            else {
                                if (tables.contains(f.getType().getSimpleName())) {
                                    for (Field ff : f.getType().getDeclaredFields()) {
                                        if (ff.isAnnotationPresent(PrimaryKey.class))
                                            f.set(_return, getByPrimaryKey(f.getType(), getObject(c.getColumnIndex(f.getName()), c)));
                                    }
                                } else
                                    f.set(_return, getObject(c.getColumnIndex(f.getName()), c));
                            }
                        }
                        if (!acc)
                            f.setAccessible(false);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new ObjectDatabaseException(e);
            }
            c.close();
            return _return;
        } else {
            c.close();
            return null;
        }
    }

    @SuppressLint("Range")
    public <T> List<T> getList(Class<T> returnClass, String whereClause, String orderBy) {
        if (!tables.contains(returnClass.getSimpleName())) {
            throw new SQLException("No such table!");
        }
        List<T> _return = new ArrayList<>();
        String query = "SELECT * FROM " + returnClass.getSimpleName();
        if (whereClause != null) {
            whereClause = whereClause.trim();
            if (whereClause.startsWith("WHERE"))
                whereClause = whereClause.replace("WHERE", "");
            query += " WHERE " + whereClause;
        }
        if (orderBy != null) {
            orderBy = orderBy.trim();
            if (orderBy.startsWith("ORDER BY")) {
                orderBy = orderBy.replace("ORDER BY", "");
            }
            query += " ORDER BY " + orderBy;
        }
        Cursor c = rawQuery(query);
        if (c.moveToFirst()) {
            do {
                try {
                    T add = returnClass.newInstance();
                    for (Field f : returnClass.getDeclaredFields()) {
                        if (!f.isAnnotationPresent(Ignored.class)) {
                            boolean acc = f.isAccessible();
                            if (!acc)
                                f.setAccessible(true);
                            if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                                f.set(add, c.getInt(c.getColumnIndex(f.getName())) > 0);
                            else if (f.getType().getSimpleName().equalsIgnoreCase("byte"))
                                f.set(add, (byte) c.getInt(c.getColumnIndex(f.getName())));
                            else if (f.getType().getSimpleName().equalsIgnoreCase("double"))
                                f.set(add, c.getDouble(c.getColumnIndex(f.getName())));
                            else if (f.getType().getSimpleName().equals("LocalDate")) {
                                String data = c.getString(c.getColumnIndex(f.getName()));
                                if (data != null)
                                    f.set(add, LocalDate.parse(data));
                            } else if (f.getType().getSimpleName().equals("LocalDateTime")) {
                                String data = c.getString(c.getColumnIndex(f.getName()));
                                if (data != null)
                                    f.set(add, LocalDateTime.parse(data));
                            } else {
                                if (f.getType().getSimpleName().equals("Byte[]")) {
                                    f.set(add, toObjects((byte[]) getObject(c.getColumnIndex(f.getName()), c)));
                                } else {
                                    if (tables.contains(f.getType().getSimpleName())) {
                                        for (Field ff : f.getType().getDeclaredFields()) {
                                            if (ff.isAnnotationPresent(PrimaryKey.class))
                                                f.set(add, getByPrimaryKey(f.getType(), getObject(c.getColumnIndex(f.getName()), c)));
                                        }
                                    } else
                                        f.set(add, getObject(c.getColumnIndex(f.getName()), c));
                                }
                            }
                            if (!acc)
                                f.setAccessible(false);
                        }
                    }
                    _return.add(add);
                } catch (IllegalAccessException | InstantiationException e) {
                    throw new ObjectDatabaseException(e);
                }
            } while (c.moveToNext());
        }
        c.close();
        return _return;
    }

    public <T> int update(T obj) {
        Class<?> updateClass = obj.getClass();
        Field primaryKey = null;
        if (!tables.contains(updateClass.getSimpleName()))
            throw new SQLiteException("No such table!");
        String query = "UPDATE " + updateClass.getSimpleName() + " SET ";
        for (Field f : updateClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(PrimaryKey.class)) {
                primaryKey = f;
                continue;
            }
            if (f.isAnnotationPresent(AutoIncrement.class) || f.isAnnotationPresent(Ignored.class))
                continue;
            query += f.getName() + " = ?, ";
        }
        query = query.substring(0, query.length() - 2);
        query += " WHERE ";
        if (primaryKey != null) {
            query += primaryKey.getName() + " = ?";
        } else {
            throw new SQLiteException("Missing where statement in update!");
        }
        SQLiteStatement stmt = db.compileStatement(query);
        int x = 0;
        try {
            for (Field f : updateClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(AutoIncrement.class) || f.isAnnotationPresent(Ignored.class) || f.isAnnotationPresent(PrimaryKey.class))
                    continue;
                x++;
                boolean isAccessible = f.isAccessible();
                f.setAccessible(true);
                if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                    stmt.bindString(x, (Boolean) f.get(obj) ? "1" : "0");
                else if (f.getType().getSimpleName().equals("byte[]"))
                    stmt.bindBlob(x, (byte[]) f.get(obj));
                else if (f.getType().getSimpleName().equals("Byte[]")) {
                    byte[] bb = toPrimitives((Byte[]) f.get(obj));
                    if (bb != null)
                        stmt.bindBlob(x, bb);
                    else
                        stmt.bindNull(x);
                } else {
                    if (tables.contains(f.getType().getSimpleName())) {
                        for (Field ff : f.getType().getDeclaredFields()) {
                            if (ff.isAnnotationPresent(PrimaryKey.class)) {
                                boolean isAccessible2 = ff.isAccessible();
                                ff.setAccessible(true);
                                if (f.get(obj) != null) {
                                    if (!checkExistance(f.get(obj)))
                                        throw new SQLException("Row don't exist in such table");
                                    stmt.bindString(x, ff.get(f.get(obj)).toString());
                                } else {
                                    stmt.bindString(x, "null");
                                }
                                ff.setAccessible(isAccessible2);
                                break;
                            }
                        }
                    } else {
                        if (f.get(obj) != null)
                            stmt.bindString(x, f.get(obj).toString());
                        else
                            stmt.bindNull(x);
                    }
                }
                f.setAccessible(isAccessible);
            }
            boolean isAccessible = primaryKey.isAccessible();
            primaryKey.setAccessible(true);
            x++;
            if (primaryKey.get(obj) != null)
                stmt.bindString(x, primaryKey.get(obj).toString());
            else {
                primaryKey.setAccessible(isAccessible);
                throw new SQLiteException("PrimaryKey is null");
            }
            primaryKey.setAccessible(isAccessible);
        } catch (IllegalAccessException e) {
            throw new ObjectDatabaseException(e);
        }
        return stmt.executeUpdateDelete();
    }

    public <T> int delete(T obj) {
        Class<?> deleteClass = obj.getClass();
        if (!tables.contains(deleteClass.getSimpleName()))
            throw new SQLiteException("No such table");
        String query = null;
        Field primaryKey = null;
        for (Field f : deleteClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(PrimaryKey.class)) {
                primaryKey = f;
                query = "DELETE FROM " + deleteClass.getSimpleName() + " WHERE " + f.getName() + " = ?";
                break;
            }
        }
        if (query == null)
            throw new SQLiteException("No PrimaryKey");
        SQLiteStatement stmt = db.compileStatement(query);
        boolean isAccessible = primaryKey.isAccessible();
        primaryKey.setAccessible(true);
        try {
            stmt.bindString(1, primaryKey.get(obj).toString());
        } catch (IllegalAccessException e) {
            throw new ObjectDatabaseException(e);
        }
        primaryKey.setAccessible(isAccessible);
        return stmt.executeUpdateDelete();
    }

    private Object getObject(int index, Cursor c) {
        int type = c.getType(index);
        switch (type) {
            case Cursor.FIELD_TYPE_STRING:
                return c.getString(index);
            case Cursor.FIELD_TYPE_INTEGER:
                return c.getInt(index);
            case Cursor.FIELD_TYPE_FLOAT:
                return c.getFloat(index);
            case Cursor.FIELD_TYPE_BLOB:
                return c.getBlob(index);
            case Cursor.FIELD_TYPE_NULL:
                return null;
            default:
                return null;
        }
    }

    private boolean checkExistance(Object obj) {
        Class<?> checkedClass = obj.getClass();
        if (!tables.contains(checkedClass.getSimpleName()))
            return false;
        try {
            for (Field f : checkedClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(PrimaryKey.class)) {
                    boolean isAccessible = f.isAccessible();
                    boolean isInt = false;
                    if (f.getType().getSimpleName().equals("int") || f.getType().getSimpleName().equals("Integer"))
                        isInt = true;
                    if (!isAccessible)
                        f.setAccessible(true);
                    if (f.get(obj) == null) {
                        if (!isAccessible)
                            f.setAccessible(false);
                        return false;
                    }
                    String selection;
                    if (isInt)
                        selection = f.getName() + " = ?";
                    else
                        selection = f.getName() + " = '?'";
                    Cursor c = db.query(checkedClass.getSimpleName(), new String[]{f.getName()}, selection, new String[]{f.get(obj).toString()}, null, null, null);
                    if (!isAccessible)
                        f.setAccessible(false);
                    if (c.moveToFirst()) {
                        c.close();
                        return true;
                    } else {
                        c.close();
                        return false;
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new ObjectDatabaseException(e);
        }
        return false;
    }

    private byte[] toPrimitives(Byte[] oBytes)
    {
        if (oBytes != null) {
            byte[] bytes = new byte[oBytes.length];
            for (int i = 0; i < oBytes.length; i++) {
                bytes[i] = oBytes[i];
            }
            return bytes;
        }
        return null;
    }

    Byte[] toObjects(byte[] bytesPrim) {
        if (bytesPrim != null) {
            Byte[] bytes = new Byte[bytesPrim.length];
            int i = 0;
            for (byte b : bytesPrim) bytes[i++] = b;
            return bytes;
        }
        return null;
    }

    public void close() {
        if (db.isOpen() && !db.inTransaction()) {
            db.disableWriteAheadLogging();
            db.rawQuery("PRAGMA wal_checkpoint", null).close();
            db.rawQuery("PRAGMA journal_mode = DELETE", null).close();
        }
        dbUtil.close();
        dbHelper = null;
    }

    private static class ObjectDatabaseException extends RuntimeException {
        public ObjectDatabaseException(String message, Throwable cause, StackTraceElement[] stackTrace) {
            super(message, cause);
            setStackTrace(stackTrace);
        }

        public ObjectDatabaseException(Exception exception) {
            super(exception.getMessage(), exception.getCause());
            setStackTrace(exception.getStackTrace());
        }
    }
}
