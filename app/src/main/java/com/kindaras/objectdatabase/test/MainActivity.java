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
        TestObj t = new TestObj();
        t.setString("prova");
        t.setInteger(10);
        t.setaBoolean(true);
        t.setBb((byte)0xba);
        t.setbArray(new Byte[]{(byte)0xbb, (byte)0xac});
        t.setDate(LocalDate.now());
        t.setDateTime(LocalDateTime.now());
        Test2 tt = new Test2();
        tt.setId(1);
        tt.setTest("yay");
        tt.setX(1.9878f);
        t.setTest(tt);
        TestObj t2;
        DbHelper db = DbHelper.getDb(this, "test.db", 1);
        try {
            //db.insertInto(tt);
            //db.multiInsert(new TestObj[] {t,t2});
            List<TestObj> list = db.getList(TestObj.class, null, null);
            t = db.getByPrimaryKey(TestObj.class, 1);
            t2 = db.getByPrimaryKey(TestObj.class, 2);
            t.setaBoolean(true);
            t.setInteger(10);
            t.setbArray(new Byte[]{(byte)0xbb});
            int update = db.update(t);
            int delete = db.delete(t2);
            List<TestObj> list1 = db.getList(TestObj.class, null, null);
            db.close();
            Log.e("", "");
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
        }
    }
}