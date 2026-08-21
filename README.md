# KindarasSimpleOrm

A **very simple** ORM for **Android SQLite**. It covers the basic operations needed for **small databases**: map a Java class to a table, persist objects, run CRUD and manage simple foreign keys. There are no query builders, and no advanced ORM features.

The class becomes the table (class name = table name) with the class fields as the columns.

Has a simple version management system to apply changes in the structure with database version change.

Current version: **1.2.1** · minSdk **29** · Java **11**

## Quick start

1. Get a `DbHelper` instance (singleton).
2. Define a class with an empty constructor and annotate the primary key.
3. Insert, read, update, or delete objects.

```java
DbHelper db = DbHelper.getDb(context, "app.db", 1);

Person person = new Person();
person.name = "Ada";
db.insertInto(person); // assigns person.id automatically

Person loaded = db.getByPrimaryKey(Person.class, person.id);
List<Person> people = db.getList(Person.class, "name = 'Ada'", "id ASC");

person.name = "Augusta";
db.update(person);
db.delete(person);

db.close();
```

```java
public class Person {
    @PrimaryKey
    @AutoIncrement
    public int id;
    public String name;
}
```

If the table does not exist yet, `insertInto` / `multiInsert` create it. You can also call `createTable` yourself.

## Annotations

| Annotation | Purpose |
|---|---|
| `@PrimaryKey` | Primary key field. Required for `update`, `delete`, and `getByPrimaryKey`. |
| `@AutoIncrement` | Only on an `int` / `Integer` primary key. After insert, the generated id is written back onto the object. |
| `@Ignored` | The field is not persisted (skipped in `CREATE TABLE` and in CRUD). |

A field whose type is another class with `@PrimaryKey` is treated as a **foreign key**: on insert, the related row is inserted if it is missing; on read, it is loaded by key.

## `DbHelper` methods

This is the library entry point. All operations go through it.

### `getDb(Context context, String name, int version)`

Opens (or reuses) the database. Returns the same instance until you call `close()`. `name` is the SQLite file, `version` is the schema version: if it increases, new columns on already mapped classes are added with `ALTER TABLE`.

### `createTable(Class<?> insertedClass)`

Creates the table from the class name and fields. `@Ignored` fields are skipped. If the table already exists and the DB version went up, only missing columns are added.

### `insertInto(Object obj)`

Inserts one object. Creates the table if needed. `@AutoIncrement` fields are left out of the `INSERT`; the generated id is assigned on the object. Nested objects with `@PrimaryKey` are inserted if they do not already exist.

### `multiInsert(List<T> array)` / `multiInsert(T[] array)`

Batch insert in a single transaction. Empty arrays/lists are ignored. Unlike `insertInto`, if a nested object is not already in the table, the operation fails.

### `getByPrimaryKey(Class<T> returnClass, Object index)`

Returns the object with that primary key, or `null`. Nested objects are loaded as well. The table must already exist.

### `getList(Class<T> returnClass, String whereClause, String orderBy)`

Returns a list of objects. `whereClause` and `orderBy` are optional (`null` = all rows, no ordering). Leading `WHERE` and `ORDER BY` prefixes are stripped if present.
`whereClause` and `orderBy` are written in Sqlite syntax.

```java
db.getList(Person.class, null, null);
db.getList(Person.class, "name = 'Ada'", "id DESC");
db.getList(Person.class, "WHERE name = 'Ada'", "ORDER BY id DESC");
```

### `update(T obj)`

Updates the row identified by `@PrimaryKey`. Returns the number of rows changed. The key must not be `null`.

### `delete(T obj)`

Deletes the row identified by `@PrimaryKey`. Returns the number of rows deleted.

### `rawQuery(String query)`

Runs raw SQL. `SELECT` (and similar queries) return a `Cursor`; `INSERT` and `DELETE` execute the statement and return `null`. Close the `Cursor` when you are done.

### `close()`

Closes the database and clears the singleton. The next `getDb` call creates a new instance.

## Supported types

`String`, `int` / `Integer`, `double` / `float`, `boolean`, `byte`, `byte[]` / `Byte[]`, `LocalDate`, `LocalDateTime`, enums (stored as `name()`), objects with `@PrimaryKey` (foreign key).

Entity classes must have a **public no-arg constructor**.
