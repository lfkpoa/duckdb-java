package org.duckdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.duckdb.DuckDBBindings.*;
import static org.duckdb.DuckDBBindings.CAPIType.DUCKDB_TYPE_INTEGER;
import static org.duckdb.TestDuckDBJDBC.JDBC_URL;
import static org.duckdb.test.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TestScalarFunctions {
    private interface ResultSetVerifier {
        void verify(ResultSet rs) throws Exception;
    }

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
        test_register_scalar_function_integer();
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

    public static void test_register_scalar_function_boolean() throws Exception {
        assertUnaryScalarFunction("java_not_bool", "BOOLEAN", "BOOLEAN", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setBoolean(i, !in.getBoolean(i));
                }
            }
        }, "SELECT java_not_bool(v) FROM (VALUES (TRUE), (NULL), (FALSE)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Boolean.class), false);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Boolean.class), true);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_tinyint() throws Exception {
        assertUnaryScalarFunction("java_add_tinyint", "TINYINT", "TINYINT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setByte(i, (byte) (in.getByte(i) + 1));
                }
            }
        }, "SELECT java_add_tinyint(v) FROM (VALUES (41::TINYINT), (NULL), (-2::TINYINT)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Byte.class), (byte) 42);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Byte.class), (byte) -1);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_smallint() throws Exception {
        assertUnaryScalarFunction("java_add_smallint", "SMALLINT", "SMALLINT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setShort(i, (short) (in.getShort(i) + 2));
                }
            }
        }, "SELECT java_add_smallint(v) FROM (VALUES (40::SMALLINT), (NULL), (-4::SMALLINT)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Short.class), (short) 42);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Short.class), (short) -2);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_integer() throws Exception {
        assertUnaryScalarFunction("java_add_int", "INTEGER", "INTEGER", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setInt(i, in.getInt(i) + 1);
                }
            }
        }, "SELECT java_add_int(v) FROM (VALUES (1), (NULL), (41)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Integer.class), 2);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Integer.class), 42);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_bigint() throws Exception {
        assertUnaryScalarFunction("java_add_bigint", "BIGINT", "BIGINT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setLong(i, in.getLong(i) + 3);
                }
            }
        }, "SELECT java_add_bigint(v) FROM (VALUES (39::BIGINT), (NULL), (-5::BIGINT)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Long.class), 42L);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Long.class), -2L);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_utinyint() throws Exception {
        assertUnaryScalarFunction("java_add_utinyint", "UTINYINT", "UTINYINT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setUint8(i, in.getUint8(i) + 1);
                }
            }
        }, "SELECT java_add_utinyint(v) FROM (VALUES (41::UTINYINT), (NULL), (254::UTINYINT)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Short.class), (short) 42);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Short.class), (short) 255);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_usmallint() throws Exception {
        assertUnaryScalarFunction("java_add_usmallint", "USMALLINT", "USMALLINT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setUint16(i, in.getUint16(i) + 2);
                }
            }
        }, "SELECT java_add_usmallint(v) FROM (VALUES (40::USMALLINT), (NULL), (65533::USMALLINT)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Integer.class), 42);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Integer.class), 65535);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_uinteger() throws Exception {
        assertUnaryScalarFunction("java_add_uinteger", "UINTEGER", "UINTEGER", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setUint32(i, in.getUint32(i) + 3);
                }
            }
        }, "SELECT java_add_uinteger(v) FROM (VALUES (39::UINTEGER), (NULL), (4294967292::UINTEGER)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Long.class), 42L);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Long.class), 4294967295L);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_ubigint() throws Exception {
        assertUnaryScalarFunction("java_add_ubigint", "UBIGINT", "UBIGINT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            BigInteger increment = BigInteger.ONE;
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setUint64(i, in.getUint64(i).add(increment));
                }
            }
        },
                                  "SELECT java_add_ubigint(v) FROM (VALUES (41::UBIGINT), (NULL), "
                                      + "(18446744073709551614::UBIGINT)) t(v)",
                                  rs -> {
                                      assertTrue(rs.next());
                                      assertEquals(rs.getObject(1, BigInteger.class), new BigInteger("42"));
                                      assertTrue(rs.next());
                                      assertNullRow(rs);
                                      assertTrue(rs.next());
                                      assertEquals(rs.getObject(1, BigInteger.class),
                                                   new BigInteger("18446744073709551615"));
                                      assertFalse(rs.next());
                                  });
    }

    public static void test_register_scalar_function_float() throws Exception {
        assertUnaryScalarFunction("java_add_float", "FLOAT", "FLOAT", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setFloat(i, in.getFloat(i) + 1.25f);
                }
            }
        }, "SELECT java_add_float(v) FROM (VALUES (40.75::FLOAT), (NULL), (-2.5::FLOAT)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Float.class), 42.0f);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Float.class), -1.25f);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_double() throws Exception {
        assertUnaryScalarFunction("java_add_double", "DOUBLE", "DOUBLE", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setDouble(i, in.getDouble(i) + 1.5d);
                }
            }
        }, "SELECT java_add_double(v) FROM (VALUES (40.5::DOUBLE), (NULL), (-3.0::DOUBLE)) t(v)", rs -> {
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Double.class), 42.0d);
            assertTrue(rs.next());
            assertNullRow(rs);
            assertTrue(rs.next());
            assertEquals(rs.getObject(1, Double.class), -1.5d);
            assertFalse(rs.next());
        });
    }

    public static void test_register_scalar_function_decimal() throws Exception {
        assertUnaryScalarFunction("java_add_decimal", "DECIMAL(38,10)", "DECIMAL(38,10)", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            BigDecimal increment = new BigDecimal("0.0000000001");
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setBigDecimal(i, in.getBigDecimal(i).add(increment));
                }
            }
        },
                                  "SELECT java_add_decimal(v) FROM (VALUES "
                                      + "(CAST('12345678901234567890.1234567890' AS DECIMAL(38,10))), "
                                      + "(NULL), "
                                      + "(CAST('-0.0000000001' AS DECIMAL(38,10)))) t(v)",
                                  rs -> {
                                      assertTrue(rs.next());
                                      assertEquals(rs.getObject(1, BigDecimal.class),
                                                   new BigDecimal("12345678901234567890.1234567891"));
                                      assertTrue(rs.next());
                                      assertNullRow(rs);
                                      assertTrue(rs.next());
                                      assertEquals(rs.getObject(1, BigDecimal.class), BigDecimal.ZERO.setScale(10));
                                      assertFalse(rs.next());
                                  });
    }

    public static void test_register_scalar_function_varchar() throws Exception {
        assertUnaryScalarFunction("java_suffix_varchar", "VARCHAR", "VARCHAR", (input, rowCount, out) -> {
            DuckDBReadableVector in = input.vector(0);
            for (int i = 0; i < rowCount; i++) {
                if (in.isNull(i)) {
                    out.setNull(i);
                } else {
                    out.setString(i, in.getString(i) + "_java");
                }
            }
        },
                                  "SELECT java_suffix_varchar(v) FROM (VALUES ('duck'), (NULL), "
                                      + "('abcdefghijklmnop')) t(v)",
                                  rs -> {
                                      assertTrue(rs.next());
                                      assertEquals(rs.getObject(1, String.class), "duck_java");
                                      assertTrue(rs.next());
                                      assertNullRow(rs);
                                      assertTrue(rs.next());
                                      assertEquals(rs.getObject(1, String.class), "abcdefghijklmnop_java");
                                      assertFalse(rs.next());
                                  });
    }

    private static void assertUnaryScalarFunction(String functionName, String parameterType, String returnType,
                                                  DuckDBVectorizedScalarFunction function, String query,
                                                  ResultSetVerifier verifier) throws Exception {
        try (DuckDBConnection conn = DriverManager.getConnection(JDBC_URL).unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {
            conn.registerScalarFunction(functionName, new String[] {parameterType}, returnType, function);
            try (ResultSet rs = stmt.executeQuery(query)) {
                verifier.verify(rs);
            }
        }
    }

    private static void assertNullRow(ResultSet rs) throws Exception {
        assertEquals(rs.getObject(1), null);
        assertTrue(rs.wasNull());
    }
}
