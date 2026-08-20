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
import java.util.Locale;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Test2 t2 = new Test2();
        t2.setTest("test");
        t2.setIgnored(Locale.CANADA);
        DbHelper db = DbHelper.getDb(this, "test.db", 1);
        db.createTable(Test2.class);
        db.insertInto(t2);
        List<Test2> l = db.getList(Test2.class, null, null);
        Log.e("a", "a");
    }
}