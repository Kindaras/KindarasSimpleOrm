package com.kindaras.objectdatabase;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.Nullable;

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
            Map<String, String> getters = new HashMap<>();
            for (Method m : insertedClass.getMethods()) {
                if (m.getName().startsWith("get") || m.getName().startsWith("is"))
                    getters.put(m.getName().replace("get", "").replace("is", "").toLowerCase(), m.getName());
            }
            for (int x = 0; x < insertedClass.getDeclaredFields().length; x++) {
                if (insertedClass.getDeclaredFields()[x].getType().getSimpleName().equalsIgnoreCase("boolean")) {
                    stmt.bindString(x + 1, (Boolean)insertedClass.getMethod(getters.get(insertedClass.getDeclaredFields()[x].getName().toLowerCase())).invoke(obj) ? "1" : "0");
                } else {
                    stmt.bindString(x + 1, insertedClass.getMethod(getters.get(insertedClass.getDeclaredFields()[x].getName().toLowerCase())).invoke(obj).toString());
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
    public <T> List<T> getList(Class<T> returnClass) throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchFieldException {
        List<T> _return = new ArrayList<>();
        String query = "SELECT * FROM " + returnClass.getSimpleName();
        Cursor c = rawQuery(query);
        Constructor<?>[] constructors = returnClass.getConstructors();
        Constructor<T> constructor = null;
        for (int x = 0; x < constructors.length; x++) {
            if (constructors[x].getDeclaredAnnotation(com.kindaras.objectdatabase.Constructor.class) != null)
                constructor = (Constructor<T>) constructors[x];
        }
        if (c.moveToFirst() && constructor != null) {
            do {
                Object[] array = new Object[constructor.getParameterCount()];
                for (int x = 0; x < array.length; x++) {
                    if (returnClass.getDeclaredField(constructor.getAnnotation(com.kindaras.objectdatabase.Constructor.class).parameters()[x]).getType().getSimpleName().equalsIgnoreCase("boolean")) {
                            array[x] = c.getInt(c.getColumnIndex(constructor.getAnnotation(com.kindaras.objectdatabase.Constructor.class).parameters()[x])) > 0 ? true : false;
                    } else {
                        array[x] = getObject(c.getColumnIndex(constructor.getAnnotation(com.kindaras.objectdatabase.Constructor.class).parameters()[x]), c);
                    }
                }
                T add = constructor.newInstance(array);
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
