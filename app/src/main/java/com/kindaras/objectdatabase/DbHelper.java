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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbHelper {
    private SQLiteDatabase db;
    private Database dbUtil;
    private static DbHelper dbHelper;

    private List<String> tables = new ArrayList<>();

    @SuppressLint("Range")
    private DbHelper(Context context, String name, int version) {
        open(context, name, version);
        dbHelper = this;
        Cursor c = rawQuery("select name from sqlite_master where type = 'table' and name not like 'sqlite_%' and name != 'android_metadata'");
        if (c.moveToFirst()) {
            do {
                tables.add(c.getString(c.getColumnIndex("name")));
            } while (c.moveToNext());
        }
    }

    public static DbHelper getDb(Context context, String name, int version) {
        if (dbHelper == null) {
            dbHelper = new DbHelper(context, name, version);
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
        if (db.isOpen() && !db.isReadOnly())
            return db.rawQuery(query, null);
        else
            return null;
    }

    public void createTable(Class<?> insertedClass) throws java.sql.SQLException {
        boolean ok = false;
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
                if (!tables.contains(t))
                    throw new java.sql.SQLException("Can't point to a table that don't exist");
                for (Field ff : f.getType().getDeclaredFields()) {
                    if (ff.isAnnotationPresent(PrimaryKey.class)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok)
                    throw new java.sql.SQLException("Can't point to a table with no primary key");
            }
        }
        db.execSQL(DatabaseQueryGenerator.getTableQueryByClass(insertedClass));
        tables.add(insertedClass.getSimpleName());
    }

    public void insertInto(Class<?> insertedClass, Object obj) throws IllegalAccessException, java.sql.SQLException {
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
            for (Field f : insertedClass.getDeclaredFields()) {
                if (!f.isAnnotationPresent(AutoIncrement.class) &&
                        !f.isAnnotationPresent(Ignored.class)) {
                    x++;
                    f.setAccessible(true);
                    if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                        stmt.bindString(x, (Boolean) f.get(obj) ? "1" : "0");
                    else if (f.getType().getSimpleName().equals("byte[]"))
                        stmt.bindBlob(x, (byte[]) f.get(obj));
                    else if (f.getType().getSimpleName().equals("Byte[]"))
                        stmt.bindBlob(x, toPrimitives((Byte[]) f.get(obj)));
                    else {
                        if (tables.contains(f.getType().getSimpleName())) {
                            for (Field ff : f.getType().getDeclaredFields()) {
                                if (ff.isAnnotationPresent(PrimaryKey.class)) {
                                    boolean isAccessible = ff.isAccessible();
                                    if (!isAccessible)
                                        ff.setAccessible(true);
                                    if (f.get(obj) != null) {
                                        if (!checkExistance(f.getType(), f.get(obj)))
                                            throw new SQLException("Row don't exist in such table");
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
                    f.setAccessible(false);
                }
            }
            stmt.execute();
            stmt.close();
            db.setTransactionSuccessful();
            db.endTransaction();
        } else
            throw new IllegalAccessException("Class type error!");
    }

    @SuppressLint("Range")
    public <T> T getByPrimaryKey(Class<T> returnClass, Object index) throws InstantiationException, IllegalAccessException {
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
            T _return = returnClass.newInstance();
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
                    else if (f.getType().getSimpleName().equals("LocalDate"))
                        f.set(_return, LocalDate.parse(c.getString(c.getColumnIndex(f.getName()))));
                    else if (f.getType().getSimpleName().equals("LocalDateTime"))
                        f.set(_return, LocalDateTime.parse(c.getString(c.getColumnIndex(f.getName()))));
                    else {
                        if (f.getType().getSimpleName().equals("Byte[]"))
                            f.set(_return, toObjects((byte[]) getObject(c.getColumnIndex(f.getName()), c)));
                        else {
                            if (tables.contains(f.getType().getSimpleName())) {
                                for (Field ff : f.getType().getDeclaredFields()) {
                                    if (ff.isAnnotationPresent(PrimaryKey.class))
                                        f.set(_return, getByPrimaryKey(f.getType(), getObject(c.getColumnIndex(ff.getName()), c)));
                                }
                            } else
                                f.set(_return, getObject(c.getColumnIndex(f.getName()), c));
                        }
                    }
                    if (!acc)
                        f.setAccessible(false);
                }
            }
            c.close();
            return _return;
        } else {
            c.close();
            return null;
        }
    }

    @SuppressLint("Range")
    public <T> List<T> getList(Class<T> returnClass) throws IllegalAccessException, InstantiationException {
        if (!tables.contains(returnClass.getSimpleName())) {
            throw new SQLException("No such table!");
        }
        List<T> _return = new ArrayList<>();
        String query = "SELECT * FROM " + returnClass.getSimpleName();
        Cursor c = rawQuery(query);
        if (c.moveToFirst()) {
            do {
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
                        else if (f.getType().getSimpleName().equals("LocalDate"))
                            f.set(add, LocalDate.parse(c.getString(c.getColumnIndex(f.getName()))));
                        else if (f.getType().getSimpleName().equals("LocalDateTime"))
                            f.set(add, LocalDateTime.parse(c.getString(c.getColumnIndex(f.getName()))));
                        else {
                            if (f.getType().getSimpleName().equals("Byte[]"))
                                f.set(add, toObjects((byte[]) getObject(c.getColumnIndex(f.getName()), c)));
                            else {
                                if (tables.contains(f.getType().getSimpleName())) {
                                    for (Field ff : f.getType().getDeclaredFields()) {
                                        if (ff.isAnnotationPresent(PrimaryKey.class))
                                            f.set(add, getByPrimaryKey(f.getType(), getObject(c.getColumnIndex(ff.getName()), c)));
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
            } while (c.moveToNext());
        }
        c.close();
        return _return;
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

    private boolean checkExistance(Class<?> checkedClass, Object obj) throws IllegalAccessException {
        if (!tables.contains(checkedClass.getSimpleName()))
            return false;
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
                Cursor c = db.query(checkedClass.getSimpleName(), new String[] {f.getName()}, selection, new String[]{f.get(obj).toString()}, null, null, null);
                if (!isAccessible)
                    f.setAccessible(false);
                if (c.moveToFirst()) {
                    c.close();
                    return true;
                } else {
                    c.close();
                    return false;
                }
            } else
                return false;
        }
        return false;
    }

    private byte[] toPrimitives(Byte[] oBytes)
    {
        byte[] bytes = new byte[oBytes.length];
        for(int i = 0; i < oBytes.length; i++){
            bytes[i] = oBytes[i];
        }
        return bytes;
    }

    Byte[] toObjects(byte[] bytesPrim) {
        Byte[] bytes = new Byte[bytesPrim.length];
        int i = 0;
        for (byte b : bytesPrim) bytes[i++] = b;
        return bytes;
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
}
