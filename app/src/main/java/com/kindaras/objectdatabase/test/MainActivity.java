package com.kindaras.objectdatabase.test;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.kindaras.objectdatabase.DbHelper;
import com.kindaras.objectdatabase.R;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Test2 t2 = new Test2();
        t2.setTest("test");
        TestObj t = new TestObj();
        t.setString("t");
        t.setTest(t2);
        DbHelper db = DbHelper.getDb(this, "test.db", 9);
        db.createTable(TestObj.class);
        db.createTable(Test2.class);
        db.insertInto(t);
        List<TestObj> l = db.getList(TestObj.class, null, null);
        Log.e("a", "a");
    }
}