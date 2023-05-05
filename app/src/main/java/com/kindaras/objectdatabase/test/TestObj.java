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
    private int integer;
    private boolean aBoolean;
    private Byte bb;
    private Byte[] bArray;
    private Test2 test;
    private LocalDate date;
    private LocalDateTime dateTime;
    private boolean added;

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

    public int getInteger() {
        return integer;
    }

    public void setInteger(int integer) {
        this.integer = integer;
    }

    public boolean isaBoolean() {
        return aBoolean;
    }

    public void setaBoolean(boolean aBoolean) {
        this.aBoolean = aBoolean;
    }

    public Byte getBb() {
        return bb;
    }

    public void setBb(Byte bb) {
        this.bb = bb;
    }

    public Byte[] getbArray() {
        return bArray;
    }

    public void setbArray(Byte[] bArray) {
        this.bArray = bArray;
    }

    public Test2 getTest() {
        return test;
    }

    public void setTest(Test2 test) {
        this.test = test;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public boolean isAdded() {
        return added;
    }

    public void setAdded(boolean added) {
        this.added = added;
    }
}
