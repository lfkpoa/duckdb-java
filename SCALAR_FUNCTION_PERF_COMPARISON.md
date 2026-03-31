# Scalar Function Performance Comparison Example

This repository now includes a runnable benchmark for the current branch:

```bash
java -cp build/release/duckdb_jdbc.jar:build/release/duckdb_jdbc_tests.jar org.duckdb.ScalarFunctionPerfExample
java -cp build/release/duckdb_jdbc.jar:build/release/duckdb_jdbc_tests.jar org.duckdb.ScalarFunctionPerfExample 20000000 2 5
```

The benchmark is intentionally low-compute (`i + 1`) so callback bridge + chunk conversion overhead is visible.
It also prints:

- `callback_chunks`
- `callback_rows`
- `avg_rows_per_chunk`

This makes the chunk-oriented processing explicit in the measured output.

## Current Branch Callback Shape

```java
conn.registerScalarFunction("bench_add_one_int", new String[] {"INTEGER"}, "INTEGER",
    (input, rowCount, out) -> {
        DuckDBReadableVector in = input.vector(0);
        for (int i = 0; i < rowCount; i++) {
            if (in.isNull(i)) {
                out.setNull(i);
            } else {
                out.setInt(i, in.getInt(i) + 1);
            }
        }
    });
```

## Equivalent Setup on `feature/java-udf-single`

```java
conn.registerScalarUdf("bench_add_one_int",
    new DuckDBColumnType[] {DuckDBColumnType.INTEGER},
    DuckDBColumnType.INTEGER,
    (ctx, args, out, rowCount) -> {
        UdfReader in = args[0];
        for (int i = 0; i < rowCount; i++) {
            if (in.isNull(i)) {
                out.setNull(i);
            } else {
                out.setInt(i, in.getInt(i) + 1);
            }
        }
    });
```

Use the same SQL for both branches:

```sql
SELECT sum(bench_add_one_int(i::INTEGER)) FROM range(20000000) t(i);
```

For stable comparison, run with:

```sql
PRAGMA threads=1;
```
