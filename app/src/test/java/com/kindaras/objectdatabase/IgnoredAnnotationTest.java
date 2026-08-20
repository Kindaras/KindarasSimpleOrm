package com.kindaras.objectdatabase;

import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IgnoredAnnotationTest {

    static class Sample {
        @PrimaryKey
        @AutoIncrement
        private int id;
        private String name;
        @Ignored
        private String skipped;
    }

    @Test
    public void createTableQuery_skipsIgnoredFields() throws SQLException {
        String query = DatabaseQueryGenerator.getTableQueryByClass(Sample.class);

        assertEquals(
                "CREATE TABLE IF NOT EXISTS Sample (id integer PRIMARY KEY NOT NULL,name text);",
                query
        );
        assertFalse(query.contains("skipped"));
    }
}
