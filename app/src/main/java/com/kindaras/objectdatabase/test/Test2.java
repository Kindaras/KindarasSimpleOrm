package com.kindaras.objectdatabase.test;

import com.kindaras.objectdatabase.AutoIncrement;
import com.kindaras.objectdatabase.Ignored;
import com.kindaras.objectdatabase.PrimaryKey;

import java.util.Locale;

public class Test2 {
    @PrimaryKey
    @AutoIncrement
    private int id;
    private String test;
    @Ignored
    private Locale ignored;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public Locale getIgnored() {
        return ignored;
    }

    public void setIgnored(Locale ignored) {
        this.ignored = ignored;
    }
}
