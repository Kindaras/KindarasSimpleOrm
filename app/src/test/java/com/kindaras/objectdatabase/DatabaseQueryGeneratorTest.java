package com.kindaras.objectdatabase;

import org.junit.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DatabaseQueryGeneratorTest {

    @Test
    public void getTableQueryByClass_createsIntegerPkAndTextColumn() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(Person.class);

        assertEquals(
                "CREATE TABLE IF NOT EXISTS Person (id integer PRIMARY KEY NOT NULL,name text);",
                query
        );
    }

    @Test
    public void getTableQueryByClass_mapsSupportedJavaTypes() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(AllTypes.class);

        assertEquals(
                "CREATE TABLE IF NOT EXISTS AllTypes (id integer PRIMARY KEY NOT NULL,boxedInt integer,name text,flag integer,boxedFlag integer,tiny integer,blob blob,amount real,floating real,date date,dateTime datetime);",
                query
        );
    }

    @Test
    public void getTableQueryByClass_usesReferencedPrimaryKeyType() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(User.class);

        assertEquals(
                "CREATE TABLE IF NOT EXISTS User (id integer PRIMARY KEY NOT NULL,address integer);",
                query
        );
    }

    @Test
    public void getTableQueryByClass_usesTextForStringPrimaryKeyReference() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(Tagged.class);

        assertEquals(
                "CREATE TABLE IF NOT EXISTS Tagged (id integer PRIMARY KEY NOT NULL,tag text);",
                query
        );
    }

    @Test
    public void getTableQueryByClass_usesTextWhenReferencedTypeHasNoPrimaryKey() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(Holder.class);

        assertEquals(
                "CREATE TABLE IF NOT EXISTS Holder (id integer PRIMARY KEY NOT NULL,note text);",
                query
        );
    }

    @Test
    public void getTableQueryByClass_mapsEnumToText() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(WithEnum.class);

        assertTrue(query, query.contains("priority text"));
        assertFalse(query, query.contains("priority null"));
    }

    @Test
    public void getTableQueryByClass_autoIncrementOnNonInteger_throws() {
        try {
            DatabaseQueryGenerator.getTableQueryByClass(StringAutoIncrement.class);
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("Column not INTEGER can't be AUTOINCREMENT", e.getMessage());
        }
    }

    @Test
    public void getTableQueryByClass_autoIncrementWithoutPrimaryKey_throws() {
        try {
            DatabaseQueryGenerator.getTableQueryByClass(AutoIncrementWithoutPk.class);
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("Column not PRIMARY KEY can't be AUTOINCREMENT", e.getMessage());
        }
    }

    @Test
    public void getAddColumnQuery_appendsTypedColumn() throws Exception {
        String query = DatabaseQueryGenerator.getAddColumnQuery(Person.class, "name");

        assertEquals("ALTER TABLE Person ADD name text", query);
    }

    @Test
    public void getAddColumnQuery_includesPrimaryKeyClause() throws Exception {
        String query = DatabaseQueryGenerator.getAddColumnQuery(Person.class, "id");

        assertEquals("ALTER TABLE Person ADD id integer PRIMARY KEY NOT NULL", query);
    }

    @Test
    public void getAddColumnQuery_unknownField_throws() throws SQLException {
        try {
            DatabaseQueryGenerator.getAddColumnQuery(Person.class, "missing");
            fail("expected NoSuchFieldException");
        } catch (NoSuchFieldException expected) {
            assertEquals("missing", expected.getMessage());
        }
    }

    @Test
    public void getAddColumnQuery_autoIncrementOnNonInteger_throws() {
        try {
            DatabaseQueryGenerator.getAddColumnQuery(StringAutoIncrement.class, "id");
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("Column not INTEGER can't be AUTOINCREMENT", e.getMessage());
        } catch (NoSuchFieldException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void getAddColumnQuery_autoIncrementWithoutPrimaryKey_throws() {
        try {
            DatabaseQueryGenerator.getAddColumnQuery(AutoIncrementWithoutPk.class, "counter");
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("Column not PRIMARY KEY can't be AUTOINCREMENT", e.getMessage());
        } catch (NoSuchFieldException e) {
            fail(e.getMessage());
        }
    }

    static class Person {
        @PrimaryKey
        @AutoIncrement
        private int id;
        private String name;
    }

    static class AllTypes {
        @PrimaryKey
        private int id;
        private Integer boxedInt;
        private String name;
        private boolean flag;
        private Boolean boxedFlag;
        private byte tiny;
        private byte[] blob;
        private double amount;
        private float floating;
        private LocalDate date;
        private LocalDateTime dateTime;
    }

    static class Address {
        @PrimaryKey
        private int id;
        private String city;
    }

    static class User {
        @PrimaryKey
        @AutoIncrement
        private int id;
        private Address address;
    }

    static class Tag {
        @PrimaryKey
        private String code;
    }

    static class Tagged {
        @PrimaryKey
        private int id;
        private Tag tag;
    }

    static class Note {
        private String text;
    }

    static class Holder {
        @PrimaryKey
        private int id;
        private Note note;
    }

    enum Priority {
        LOW,
        HIGH
    }

    static class WithEnum {
        @PrimaryKey
        private int id;
        private Priority priority;
    }

    static class StringAutoIncrement {
        @PrimaryKey
        @AutoIncrement
        private String id;
    }

    static class AutoIncrementWithoutPk {
        @AutoIncrement
        private int counter;
        @PrimaryKey
        private int id;
    }
}
