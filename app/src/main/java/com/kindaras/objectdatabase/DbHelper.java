package com.kindaras.objectdatabase;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbHelper {
    private SQLiteDatabase db;
    private Database dbUtil;
    private static DbHelper dbHelper;

    private DbHelper(Context context) {
        open(context);
        dbHelper = this;
    }

    public static DbHelper getDb(Context context) {
        if (dbHelper == null) {
            dbHelper = new DbHelper(context);
        }
        return dbHelper;
    }

    private void open(Context context) {
        dbUtil = new Database(context.getApplicationContext());
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

    public void createTable(Class<?> insertedClass) {
        db.execSQL(DatabaseQueryGenerator.getTableQueryByClass(insertedClass));
    }

    public void insertInto(Class<?> insertedClass, Object obj) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        if (obj.getClass() == insertedClass) {
            String query = "INSERT INTO " + insertedClass.getSimpleName() + " (";
            for (Field field : insertedClass.getDeclaredFields()) {
                query += field.getName() + ",";
            }
            query = query.substring(0, query.length() - 1);
            query += ") VALUES (";
            for (int x = 0; x < insertedClass.getDeclaredFields().length; x++) {
                query += "?,";
            }
            query = query.substring(0, query.length() - 1);
            query += ")";
            db.beginTransactionNonExclusive();
            SQLiteStatement stmt = db.compileStatement(query);
            for (int x = 0; x < insertedClass.getDeclaredFields().length; x++) {
                Field f = insertedClass.getDeclaredFields()[x];
                f.setAccessible(true);
                if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                    stmt.bindString(x+1, (Boolean)f.get(obj) ? "1" : "0");
                else if (f.getType().getSimpleName().equals("byte[]"))
                    stmt.bindBlob(x+1, (byte[])f.get(obj));
                else if (f.getType().getSimpleName().equals("Byte[]"))
                    stmt.bindBlob(x+1, toPrimitives((Byte[])f.get(obj)));
                else
                    stmt.bindString(x+1, f.get(obj).toString());
                f.setAccessible(false);
            }
            stmt.execute();
            stmt.close();
            db.setTransactionSuccessful();
            db.endTransaction();
        } else
            throw new IllegalAccessException("Class type error!");
    }

    @SuppressLint("Range")
    public <T> List<T> getList(Class<T> returnClass) throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchFieldException {
        List<T> _return = new ArrayList<>();
        String query = "SELECT * FROM " + returnClass.getSimpleName();
        Cursor c = rawQuery(query);
        if (c.moveToFirst()) {
            do {
                T add = returnClass.newInstance();
                for (int x = 0; x < returnClass.getDeclaredFields().length; x++) {
                    Field f = returnClass.getDeclaredFields()[x];
                    boolean acc = f.isAccessible();
                    if (!acc)
                        f.setAccessible(true);
                    if (f.getType().getSimpleName().equalsIgnoreCase("boolean"))
                        f.set(add, c.getInt(c.getColumnIndex(f.getName())) > 0);
                    else if (f.getType().getSimpleName().equalsIgnoreCase("byte"))
                        f.set(add, (byte)c.getInt(c.getColumnIndex(f.getName())));
                    else {
                        if (f.getType().getSimpleName().equals("Byte[]"))
                            f.set(add, toObjects((byte[])getObject(c.getColumnIndex(f.getName()), c)));
                        else
                            f.set(add, getObject(c.getColumnIndex(f.getName()), c));
                    }
                    if (!acc)
                        f.setAccessible(false);
                }
                _return.add(add);
            } while (c.moveToNext());
        }
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
