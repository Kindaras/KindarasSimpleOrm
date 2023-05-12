package com.kindaras.objectdatabase.test;

import com.kindaras.objectdatabase.AutoIncrement;
import com.kindaras.objectdatabase.Ignored;
import com.kindaras.objectdatabase.PrimaryKey;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

public class TestObj {
    @PrimaryKey
    @AutoIncrement
    private int id;
    private String string;

    private Test2 test;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public Test2 getTest() {
        return test;
    }

    public void setTest(Test2 test) {
        this.test = test;
    }
}
