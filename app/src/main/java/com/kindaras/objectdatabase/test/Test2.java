package com.kindaras.objectdatabase.test;

import com.kindaras.objectdatabase.AutoIncrement;
import com.kindaras.objectdatabase.PrimaryKey;

public class Test2 {
    @PrimaryKey
    private int id;
    private String test;
    private Integer x;

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

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }
}
