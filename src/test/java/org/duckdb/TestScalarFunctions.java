package org.duckdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.duckdb.DuckDBBindings.*;
import static org.duckdb.DuckDBBindings.CAPIType.DUCKDB_TYPE_INTEGER;
import static org.duckdb.TestDuckDBJDBC.JDBC_URL;
import static org.duckdb.test.Assertions.*;

import java.nio.ByteBuffer;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TestScalarFunctions {
    public static void test_bindings_scalar_function() throws Exception {
        ByteBuffer intType = duckdb_create_logical_type(DUCKDB_TYPE_INTEGER.typeId);
        ByteBuffer scalarFunction = duckdb_create_scalar_function();
        assertNotNull(scalarFunction);

        duckdb_scalar_function_set_name(scalarFunction, "binding_scalar_fn".getBytes(UTF_8));
        duckdb_scalar_function_add_parameter(scalarFunction, intType);
        duckdb_scalar_function_set_return_type(scalarFunction, intType);
        duckdb_scalar_function_set_varargs(scalarFunction, intType);
        duckdb_scalar_function_set_special_handling(scalarFunction);
        duckdb_scalar_function_set_volatile(scalarFunction);

        try (DuckDBConnection conn = DriverManager.getConnection(JDBC_URL).unwrap(DuckDBConnection.class)) {
            assertEquals(duckdb_register_scalar_function(conn.connRef, scalarFunction), 1);

            ByteBuffer scalarFunctionSet = duckdb_create_scalar_function_set("binding_scalar_fn_set".getBytes(UTF_8));
            assertNotNull(scalarFunctionSet);
            assertEquals(duckdb_add_scalar_function_to_set(scalarFunctionSet, scalarFunction), 0);
            assertEquals(duckdb_register_scalar_function_set(conn.connRef, scalarFunctionSet), 1);
            duckdb_destroy_scalar_function_set(scalarFunctionSet);

            assertThrows(() -> { duckdb_register_scalar_function(null, scalarFunction); }, SQLException.class);
            assertThrows(() -> { duckdb_register_scalar_function(conn.connRef, null); }, SQLException.class);
            assertThrows(() -> { duckdb_create_scalar_function_set(null); }, SQLException.class);
        }

        duckdb_destroy_scalar_function(scalarFunction);
        duckdb_destroy_logical_type(intType);

        assertThrows(() -> { duckdb_destroy_scalar_function(null); }, SQLException.class);
        assertThrows(() -> { duckdb_scalar_function_set_name(null, "x".getBytes(UTF_8)); }, SQLException.class);
    }

    public static void test_register_scalar_function() throws Exception {
        try (DuckDBConnection conn = DriverManager.getConnection(JDBC_URL).unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {
            conn.registerScalarFunction("java_add_one", new String[] {"INTEGER"}, "INTEGER", (input, rowCount, out) -> {
                DuckDBReadableVector in = input.vector(0);
                for (int i = 0; i < rowCount; i++) {
                    if (in.isNull(i)) {
                        out.setNull(i);
                    } else {
                        out.setInt(i, in.getInt(i) + 1);
                    }
                }
            });

            try (ResultSet rs = stmt.executeQuery("SELECT java_add_one(i) FROM (VALUES (1), (NULL), (41)) t(i)")) {
                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 2);
                assertFalse(rs.wasNull());

                assertTrue(rs.next());
                assertEquals(rs.getObject(1), null);
                assertTrue(rs.wasNull());

                assertTrue(rs.next());
                assertEquals(rs.getInt(1), 42);
                assertFalse(rs.wasNull());

                assertFalse(rs.next());
            }
        }
    }

    public static void test_register_scalar_function_parallel() throws Exception {
        try (DuckDBConnection conn = DriverManager.getConnection(JDBC_URL).unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA threads=4");
            conn.registerScalarFunction(
                "java_add_one_bigint", new String[] {"BIGINT"}, "BIGINT",
                (input, rowCount, out) -> {
                    DuckDBReadableVector in = input.vector(0);
                    for (int i = 0; i < rowCount; i++) {
                        out.setLong(i, in.getLong(i) + 1);
                    }
                });

            try (ResultSet rs =
                     stmt.executeQuery("SELECT sum(java_add_one_bigint(i)) FROM range(1000000) t(i)")) {
                assertTrue(rs.next());
                assertEquals(rs.getLong(1), 500000500000L);
                assertFalse(rs.wasNull());
                assertFalse(rs.next());
            }
        }
    }
}
