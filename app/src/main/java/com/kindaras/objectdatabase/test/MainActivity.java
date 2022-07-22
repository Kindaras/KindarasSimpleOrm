package com.kindaras.objectdatabase.test;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.kindaras.objectdatabase.DbHelper;
import com.kindaras.objectdatabase.R;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TestObj t = new TestObj();
        t.setString("prova");
        t.setInteger(10);
        t.setaBoolean(true);
        t.setBb((byte)0xba);
        t.setbArray(new Byte[]{(byte)0xbb, (byte)0xac});
        DbHelper db = DbHelper.getDb(this);
        db.createTable(t.getClass());
        try {
            db.insertInto(t.getClass(), t);
            List<TestObj> list = db.getList(TestObj.class);
            Log.e("", "");
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
        }
    }
}