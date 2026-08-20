package com.kindaras.objectdatabase;

import android.app.Application;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;

import com.kindaras.objectdatabase.test.Test2;
import com.kindaras.objectdatabase.test.TestObj;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class DbHelperTest {

    private Application app;
    private String dbName;
    private DbHelper db;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        dbName = "dbhelper-" + System.nanoTime() + ".db";
        db = DbHelper.getDb(app, dbName, 1);
    }

    @After
    public void tearDown() {
        if (db != null) {
            try {
                db.close();
            } catch (RuntimeException ignored) {
            }
            db = null;
        }
        if (app != null && dbName != null) {
            app.deleteDatabase(dbName);
        }
    }

    @Test
    public void getDb_returnsSameInstanceUntilClosed() {
        DbHelper second = DbHelper.getDb(app, "other.db", 2);
        assertSame(db, second);
    }

    @Test
    public void getDb_afterClose_createsNewInstance() {
        db.close();
        db = null;
        DbHelper next = DbHelper.getDb(app, dbName, 1);
        assertNotNull(next);
        db = next;
    }

    @Test
    public void insertInto_setsAutoincrementIdAndRoundtrips() {
        Person person = new Person();
        person.name = "Ada";

        db.insertInto(person);

        assertTrue(person.id > 0);
        Person loaded = db.getByPrimaryKey(Person.class, person.id);
        assertNotNull(loaded);
        assertEquals(person.id, loaded.id);
        assertEquals("Ada", loaded.name);
    }

    @Test
    public void getList_returnsInsertedRows() {
        db.insertInto(person("Ada"));
        db.insertInto(person("Grace"));

        List<Person> people = db.getList(Person.class, null, null);

        assertEquals(2, people.size());
    }

    @Test
    public void getList_filtersWithWhereClause() {
        db.insertInto(person("Ada"));
        db.insertInto(person("Grace"));

        List<Person> people = db.getList(Person.class, "name = 'Ada'", null);

        assertEquals(1, people.size());
        assertEquals("Ada", people.get(0).name);
    }

    @Test
    public void getList_stripsWherePrefix() {
        db.insertInto(person("Ada"));

        List<Person> people = db.getList(Person.class, "WHERE name = 'Ada'", null);

        assertEquals(1, people.size());
        assertEquals("Ada", people.get(0).name);
    }

    @Test
    public void getList_ordersResults() {
        db.insertInto(person("Grace"));
        db.insertInto(person("Ada"));

        List<Person> people = db.getList(Person.class, null, "name ASC");

        assertEquals("Ada", people.get(0).name);
        assertEquals("Grace", people.get(1).name);
    }

    @Test
    public void getList_stripsOrderByPrefix() {
        db.insertInto(person("Grace"));
        db.insertInto(person("Ada"));

        List<Person> people = db.getList(Person.class, null, "ORDER BY name ASC");

        assertEquals("Ada", people.get(0).name);
        assertEquals("Grace", people.get(1).name);
    }

    @Test
    public void update_changesPersistedFields() {
        Person person = person("Ada");
        db.insertInto(person);
        person.name = "Augusta";

        int updated = db.update(person);

        assertEquals(1, updated);
        assertEquals("Augusta", db.getByPrimaryKey(Person.class, person.id).name);
    }

    @Test
    public void delete_removesRow() {
        Person person = person("Ada");
        db.insertInto(person);

        int deleted = db.delete(person);

        assertEquals(1, deleted);
        assertNull(db.getByPrimaryKey(Person.class, person.id));
    }

    @Test
    public void getByPrimaryKey_missingRow_returnsNull() {
        db.createTable(Person.class);

        assertNull(db.getByPrimaryKey(Person.class, 99));
    }

    @Test
    public void ignoredField_isNotPersisted() {
        WithIgnored row = new WithIgnored();
        row.name = "visible";
        row.secret = "hidden";

        db.insertInto(row);
        WithIgnored loaded = db.getByPrimaryKey(WithIgnored.class, row.id);

        assertEquals("visible", loaded.name);
        assertNull(loaded.secret);
    }

    @Test
    public void multiInsert_array_insertsAllRows() {
        Person[] people = {person("Ada"), person("Grace")};

        db.multiInsert(people);

        List<Person> loaded = db.getList(Person.class, null, "name ASC");
        assertEquals(2, loaded.size());
        assertEquals("Ada", loaded.get(0).name);
        assertEquals("Grace", loaded.get(1).name);
        assertTrue(people[0].id > 0);
        assertTrue(people[1].id > 0);
    }

    @Test
    public void multiInsert_list_insertsAllRows() {
        List<Person> people = Arrays.asList(person("Ada"), person("Grace"));

        db.multiInsert(people);

        assertEquals(2, db.getList(Person.class, null, null).size());
    }

    @Test
    public void multiInsert_emptyArray_doesNothing() {
        db.createTable(Person.class);

        db.multiInsert(new Person[0]);

        assertTrue(db.getList(Person.class, null, null).isEmpty());
    }

    @Test
    public void insertInto_nullString_roundtripsAsNull() {
        Person person = person(null);

        db.insertInto(person);

        assertNull(db.getByPrimaryKey(Person.class, person.id).name);
    }

    @Test
    public void insertInto_persistsSupportedTypes() {
        TypedRow row = new TypedRow();
        row.flag = true;
        row.tiny = (byte) 7;
        row.blob = new byte[]{1, 2, 3};
        row.amount = 12.5;
        row.date = LocalDate.of(2026, 8, 21);
        row.dateTime = LocalDateTime.of(2026, 8, 21, 10, 15, 30);

        db.insertInto(row);
        TypedRow loaded = db.getByPrimaryKey(TypedRow.class, row.id);

        assertTrue(loaded.flag);
        assertEquals((byte) 7, loaded.tiny);
        assertArrayEquals(new byte[]{1, 2, 3}, loaded.blob);
        assertEquals(12.5, loaded.amount, 0.0001);
        assertEquals(LocalDate.of(2026, 8, 21), loaded.date);
        assertEquals(LocalDateTime.of(2026, 8, 21, 10, 15, 30), loaded.dateTime);
    }

    @Test
    public void stringPrimaryKey_roundtrips() {
        StringKey row = new StringKey();
        row.code = "IT";
        row.value = "Italy";

        db.insertInto(row);
        StringKey loaded = db.getByPrimaryKey(StringKey.class, "IT");

        assertNotNull(loaded);
        assertEquals("IT", loaded.code);
        assertEquals("Italy", loaded.value);

        row.value = "Italia";
        assertEquals(1, db.update(row));
        assertEquals("Italia", db.getByPrimaryKey(StringKey.class, "IT").value);
        assertEquals(1, db.delete(row));
        assertNull(db.getByPrimaryKey(StringKey.class, "IT"));
    }

    @Test
    public void nestedObject_isInsertedAndLoaded() {
        db.createTable(Child.class);
        db.createTable(Parent.class);

        Parent parent = new Parent();
        parent.child = new Child();
        parent.child.label = "nested";

        db.insertInto(parent);

        assertTrue(parent.id > 0);
        assertTrue(parent.child.id > 0);
        Parent loaded = db.getByPrimaryKey(Parent.class, parent.id);
        assertNotNull(loaded.child);
        assertEquals(parent.child.id, loaded.child.id);
        assertEquals("nested", loaded.child.label);
    }

    @Test
    public void nestedObject_existingChild_isReused() {
        db.createTable(Child.class);
        db.createTable(Parent.class);
        Child child = new Child();
        child.label = "shared";
        db.insertInto(child);

        Parent parent = new Parent();
        parent.child = child;
        db.insertInto(parent);

        Parent loaded = db.getByPrimaryKey(Parent.class, parent.id);
        assertEquals(child.id, loaded.child.id);
        assertEquals(1, db.getList(Child.class, null, null).size());
    }

    @Test
    public void multiInsert_missingNestedRow_throws() {
        db.createTable(Child.class);
        db.createTable(Parent.class);
        Parent parent = new Parent();
        parent.child = new Child();
        parent.child.label = "missing";

        try {
            db.multiInsert(new Parent[]{parent});
            fail("expected exception when nested row does not exist");
        } catch (RuntimeException e) {
            assertTrue(String.valueOf(e.getMessage()),
                    String.valueOf(e.getMessage()).contains("exist"));
        }
    }

    @Test
    public void createTable_referenceWithoutPrimaryKey_throws() {
        try {
            db.createTable(InvalidRef.class);
            fail("expected exception");
        } catch (RuntimeException e) {
            assertTrue(String.valueOf(e.getMessage()),
                    String.valueOf(e.getMessage()).contains("primary key"));
        }
    }

    @Test
    public void createTable_isIdempotentForSameVersion() {
        db.createTable(Person.class);
        db.createTable(Person.class);
        db.insertInto(person("Ada"));

        assertEquals(1, db.getList(Person.class, null, null).size());
    }

    @Test
    public void getByPrimaryKey_missingTable_throws() {
        try {
            db.getByPrimaryKey(Person.class, 1);
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("No such table!", e.getMessage());
        }
    }

    @Test
    public void getList_missingTable_throws() {
        try {
            db.getList(Person.class, null, null);
            fail("expected SQLException");
        } catch (SQLException e) {
            assertEquals("No such table!", e.getMessage());
        }
    }

    @Test
    public void update_missingTable_throws() {
        try {
            db.update(person("Ada"));
            fail("expected SQLiteException");
        } catch (SQLiteException e) {
            assertEquals("No such table!", e.getMessage());
        }
    }

    @Test
    public void delete_missingTable_throws() {
        try {
            db.delete(person("Ada"));
            fail("expected SQLiteException");
        } catch (SQLiteException e) {
            assertEquals("No such table", e.getMessage());
        }
    }

    @Test
    public void update_entityWithoutPrimaryKey_throws() {
        db.createTable(NoPk.class);
        NoPk row = new NoPk();
        row.name = "x";
        db.insertInto(row);

        try {
            db.update(row);
            fail("expected SQLiteException");
        } catch (SQLiteException e) {
            assertEquals("Missing where statement in update!", e.getMessage());
        }
    }

    @Test
    public void delete_entityWithoutPrimaryKey_throws() {
        db.createTable(NoPk.class);
        NoPk row = new NoPk();
        row.name = "x";
        db.insertInto(row);

        try {
            db.delete(row);
            fail("expected SQLiteException");
        } catch (SQLiteException e) {
            assertEquals("No PrimaryKey", e.getMessage());
        }
    }

    @Test
    public void rawQuery_select_returnsCursor() {
        Person person = person("Ada");
        db.insertInto(person);

        Cursor cursor = db.rawQuery("SELECT name FROM Person WHERE id = " + person.id);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        assertEquals("Ada", cursor.getString(0));
        cursor.close();
    }

    @Test
    public void rawQuery_delete_executesAndReturnsNull() {
        Person person = person("Ada");
        db.insertInto(person);

        Cursor cursor = db.rawQuery("DELETE FROM Person WHERE id = " + person.id);

        assertNull(cursor);
        assertNull(db.getByPrimaryKey(Person.class, person.id));
    }

    @Test
    public void testModels_nestedInsertAndIgnoredField() {
        Test2 nested = new Test2();
        nested.setTest("child");
        nested.setIgnored(Locale.CANADA);
        TestObj parent = new TestObj();
        parent.setString("parent");
        parent.setTest(nested);

        db.createTable(Test2.class);
        db.createTable(TestObj.class);
        db.insertInto(parent);

        List<TestObj> loaded = db.getList(TestObj.class, null, null);
        assertEquals(1, loaded.size());
        assertEquals("parent", loaded.get(0).getString());
        assertNotNull(loaded.get(0).getTest());
        assertEquals("child", loaded.get(0).getTest().getTest());
        assertNull(loaded.get(0).getTest().getIgnored());
        assertNotEquals(0, loaded.get(0).getId());
    }

    private Person person(String name) {
        Person person = new Person();
        person.name = name;
        return person;
    }

    public static class Person {
        @PrimaryKey
        @AutoIncrement
        public int id;
        public String name;
    }

    public static class WithIgnored {
        @PrimaryKey
        @AutoIncrement
        public int id;
        public String name;
        @Ignored
        public String secret;
    }

    public static class TypedRow {
        @PrimaryKey
        @AutoIncrement
        public int id;
        public boolean flag;
        public byte tiny;
        public byte[] blob;
        public double amount;
        public LocalDate date;
        public LocalDateTime dateTime;
    }

    public static class StringKey {
        @PrimaryKey
        public String code;
        public String value;
    }

    public static class Child {
        @PrimaryKey
        @AutoIncrement
        public int id;
        public String label;
    }

    public static class Parent {
        @PrimaryKey
        @AutoIncrement
        public int id;
        public Child child;
    }

    public static class NoPkType {
        public String label;
    }

    public static class InvalidRef {
        @PrimaryKey
        @AutoIncrement
        public int id;
        public NoPkType ref;
    }

    public static class NoPk {
        public String name;
    }
}
