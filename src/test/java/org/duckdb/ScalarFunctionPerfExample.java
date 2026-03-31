package org.duckdb;

import static org.duckdb.TestDuckDBJDBC.JDBC_URL;
import static org.duckdb.test.Assertions.assertEquals;
import static org.duckdb.test.Assertions.assertFalse;
import static org.duckdb.test.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runnable benchmark to expose the scalar-function bridge behavior in this branch.
 *
 * <p>It measures a low-compute INTEGER UDF so callback/conversion overhead dominates. The benchmark also prints
 * callback chunk count to make the chunk-oriented execution visible.
 *
 * <p>Usage:
 *
 * <pre>
 * java -cp build/release/duckdb_jdbc.jar:build/release/duckdb_jdbc_tests.jar org.duckdb.ScalarFunctionPerfExample
 * java -cp build/release/duckdb_jdbc.jar:build/release/duckdb_jdbc_tests.jar org.duckdb.ScalarFunctionPerfExample
 * 20000000 2 5
 * </pre>
 */
public final class ScalarFunctionPerfExample {
    private static final long DEFAULT_ROWS = 20_000_000L;
    private static final int DEFAULT_WARMUP_RUNS = 2;
    private static final int DEFAULT_MEASURED_RUNS = 5;

    private ScalarFunctionPerfExample() {
    }

    public static void main(String[] args) throws Exception {
        long rows = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_ROWS;
        int warmupRuns = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_WARMUP_RUNS;
        int measuredRuns = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_MEASURED_RUNS;

        try (DuckDBConnection conn = DriverManager.getConnection(JDBC_URL).unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA threads=1");

            AtomicLong callbackCount = new AtomicLong();
            AtomicLong callbackRows = new AtomicLong();

            conn.registerScalarFunction("bench_add_one_int", new String[] {"INTEGER"}, "INTEGER",
                                        (input, rowCount, out) -> {
                                            callbackCount.incrementAndGet();
                                            callbackRows.addAndGet(rowCount);
                                            DuckDBReadableVector in = input.vector(0);
                                            for (int i = 0; i < rowCount; i++) {
                                                if (in.isNull(i)) {
                                                    out.setNull(i);
                                                } else {
                                                    out.setInt(i, in.getInt(i) + 1);
                                                }
                                            }
                                        });

            String query = "SELECT sum(bench_add_one_int(i::INTEGER)) FROM range(" + rows + ") t(i)";
            long expected = rows * (rows + 1L) / 2L;

            for (int i = 0; i < warmupRuns; i++) {
                runAndCheck(stmt, query, expected);
            }

            double totalMs = 0.0;
            for (int i = 0; i < measuredRuns; i++) {
                long startNs = System.nanoTime();
                runAndCheck(stmt, query, expected);
                double runMs = (System.nanoTime() - startNs) / 1_000_000.0;
                totalMs += runMs;
                double rowsPerSecond = (rows * 1000.0) / runMs;
                System.out.printf("run=%d elapsed_ms=%.3f rows_per_second=%.2f%n", i + 1, runMs, rowsPerSecond);
            }

            double avgMs = totalMs / measuredRuns;
            double avgRowsPerSecond = (rows * 1000.0) / avgMs;
            long chunks = callbackCount.get();
            long seenRows = callbackRows.get();
            double avgRowsPerChunk = chunks == 0 ? 0.0 : (double) seenRows / chunks;

            System.out.printf("summary rows=%d warmup_runs=%d measured_runs=%d avg_ms=%.3f avg_rows_per_second=%.2f%n",
                              rows, warmupRuns, measuredRuns, avgMs, avgRowsPerSecond);
            System.out.printf("callback_chunks=%d callback_rows=%d avg_rows_per_chunk=%.2f%n", chunks, seenRows,
                              avgRowsPerChunk);
        }
    }

    private static void runAndCheck(Statement stmt, String query, long expectedSum) throws Exception {
        try (ResultSet rs = stmt.executeQuery(query)) {
            assertTrue(rs.next());
            assertEquals(rs.getLong(1), expectedSum);
            assertFalse(rs.next());
        }
    }
}
