# User-Defined Functions (Java)

## Currently unsupported

Current unsupported examples in the UDF/Table Function API include:
- `INTERVAL`
- nested/container types for Scalar UDF signatures (`LIST`, `STRUCT`, `MAP`, `ARRAY`, `UNION`, `ENUM`)

## `UdfReader` / `UdfScalarWriter` accessors by type

| DuckDB type | Java object (read) | Java object (write) |
| --- | --- | --- |
| `BOOLEAN` | `Boolean` (`getBoolean`) | `setObject(Boolean)` / `setBoolean` |
| `TINYINT`, `SMALLINT`, `INTEGER`, `UTINYINT`, `USMALLINT` | `Integer` (`getInt`) | `setObject(Integer)` / `setInt` |
| `BIGINT`, `UINTEGER`, `UBIGINT` | `Long` (`getLong`) | `setObject(Long)` / `setLong` |
| `FLOAT` | `Float` (`getFloat`) | `setObject(Float)` / `setFloat` |
| `DOUBLE` | `Double` (`getDouble`) | `setObject(Double)` / `setDouble` |
| `DECIMAL` | `BigDecimal` (`getBigDecimal`) | `setObject(BigDecimal)` / `setBigDecimal` |
| `VARCHAR` | `String` (`getString`) | `setObject(String)` / `setString` |
| `BLOB` | `byte[]` (`getBytes`) | `setObject(byte[])` / `setBytes` |
| `DATE` | `LocalDate` (`getLocalDate`) or `Date` (`getDate`) | `setObject(LocalDate/Date)` / `setLocalDate` / `setDate` |
| `TIME`, `TIME_NS` | `LocalTime` (`getLocalTime`) | `setObject(LocalTime)` / `setLocalTime` |
| `TIME_WITH_TIME_ZONE` | `OffsetTime` (`getOffsetTime`) | `setObject(OffsetTime)` / `setOffsetTime` |
| `TIMESTAMP`, `TIMESTAMP_S`, `TIMESTAMP_MS`, `TIMESTAMP_NS` | `LocalDateTime` (`getLocalDateTime`) | `setObject(LocalDateTime/Date)` / `setLocalDateTime` |
| `TIMESTAMP_WITH_TIME_ZONE` | `OffsetDateTime` (`getOffsetDateTime`) | `setObject(OffsetDateTime/Date)` / `setOffsetDateTime` |
| `UUID` | `UUID` (`getUUID`) | `setObject(UUID)` / `setUUID` |
| `HUGEINT`, `UHUGEINT` | `byte[]` (`getBytes`) | `setObject(byte[])` / `setBytes` |

`UdfScalarWriter` supports both explicit setters and generic `setObject`, using internal conversion for compatible Java object types.

## Table Function bind parameter materialization

Table-function `bind` callback parameters are materialized as Java objects:
- `DECIMAL -> BigDecimal`
- `DATE -> LocalDate`
- `TIME`, `TIME_NS -> LocalTime`
- `TIMESTAMP* -> LocalDateTime`
- `TIME_WITH_TIME_ZONE -> OffsetTime`
- `TIMESTAMP_WITH_TIME_ZONE -> OffsetDateTime`
- `UUID -> java.util.UUID`

## `UdfOutputAppender` object helpers

For table-function output, `UdfOutputAppender` provides Java-object helpers that convert internally to primitive/vector format:
- `setBigDecimal` (exact `DECIMAL` path), `setLocalDate`, `setLocalTime`, `setOffsetTime`
- `setLocalDateTime`, `setDate`, `setOffsetDateTime`, `setUUID`
- matching `append(...)` overloads for the same object types
- `setObject`/`append(Object)` formats for nested output:
  - `LIST`/`ARRAY`: Java arrays or `Collection`
  - `MAP`: `java.util.Map`
  - `STRUCT`: positional `List`/array or named `Map<String, Object>`
  - `UNION`: `java.util.AbstractMap.SimpleEntry<String, Object>`
  - `ENUM`: `String`

## Options and behavior

- `UdfOptions.deterministic=true` (default): allows optimizations for constant inputs.
- `UdfOptions.deterministic=false`: registers the function as `VOLATILE`.
- `UdfOptions.nullSpecialHandling=true`: callback receives rows containing `NULL`.
- `UdfOptions.returnNullOnException=true`: Java exceptions become `NULL`; with `false`, the query fails.
- `TableFunctionOptions.threadSafe=false` (default): forces `max_threads=1`.
- `TableFunctionOptions.threadSafe=true`: applies `maxThreads` (`>=1`) in `init`.
- `TableFunctionDefinition.withProjectionPushdown(true)`: allows emitting only projected columns.

## Limitations and warnings

- `DECIMAL` uses exact `BigDecimal` materialization in UDF runtime (`UdfReader`/`UdfScalarWriter`/`UdfOutputAppender`) and table-function bind parameters.
- If using non-logical registration overloads (`DuckDBColumnType.DECIMAL`), default logical decimal is `DECIMAL(18,3)`.
  Use `UdfLogicalType.decimal(width, scale)` for explicit precision/scale.
- Do not execute SQL on the same `DuckDBConnection` from inside the callback (reentrancy is not supported).
- Enable `threadSafe=true` only when callback state and behavior are truly thread-safe.
- There is currently no dedicated unregister API in this JDBC layer; cleanup follows normal connection/catalog lifecycle.

## Scalar UDF ergonomic registration overloads

All overloads still use the same vector callback contract:

```java
ScalarUdf.apply(UdfContext ctx, UdfReader[] args, UdfScalarWriter out, int rowCount)
```

### Arity overloads (1..4)

```java
conn.registerScalarUdf("plus1", DuckDBColumnType.INTEGER, DuckDBColumnType.INTEGER,
                       (ctx, args, out, rowCount) -> {
                           for (int row = 0; row < rowCount; row++) {
                               out.setInt(row, args[0].getInt(row) + 1);
                           }
                       });

conn.registerScalarUdf("sum3", DuckDBColumnType.INTEGER,
                       DuckDBColumnType.INTEGER, DuckDBColumnType.INTEGER,
                       DuckDBColumnType.INTEGER,
                       (ctx, args, out, rowCount) -> {
                           for (int row = 0; row < rowCount; row++) {
                               out.setInt(row, args[0].getInt(row) + args[1].getInt(row) + args[2].getInt(row));
                           }
                       });
```

### Zero-argument scalar UDFs

```java
conn.registerScalarUdf("answer", DuckDBColumnType.INTEGER, (ctx, args, out, rowCount) -> {
    for (int row = 0; row < rowCount; row++) {
        out.setInt(row, 42);
    }
});
```

### Varargs scalar UDFs

The `baseArgumentType` is required and used for the varargs tail.

```java
conn.registerScalarUdfVarArgs("sum_all", DuckDBColumnType.INTEGER, DuckDBColumnType.INTEGER,
                              (ctx, args, out, rowCount) -> {
                                  for (int row = 0; row < rowCount; row++) {
                                      int sum = 0;
                                      for (UdfReader arg : args) {
                                          if (!arg.isNull(row)) {
                                              sum += arg.getInt(row);
                                          }
                                      }
                                      out.setInt(row, sum);
                                  }
                              },
                              new UdfOptions().nullSpecialHandling(true));
```

### Java `Class<?>` mapping overloads

```java
conn.registerScalarUdf("class_plus1", Integer.class, Integer.class, (ctx, args, out, rowCount) -> {
    for (int row = 0; row < rowCount; row++) {
        out.setInt(row, args[0].getInt(row) + 1);
    }
});
```

`BigDecimal.class` is intentionally rejected in class-based overloads.
For decimals, use explicit logical registration with `UdfLogicalType.decimal(width, scale)`.

## Performance recommendation

Convenience overloads improve registration ergonomics only.
For throughput-sensitive UDFs, keep chunk-oriented processing in `rowCount` loops and avoid per-row SQL calls or unnecessary allocations.

## Examples

### Example 1: Scalar UDF with mixed types

This example combines integer, decimal, timestamp, timestamp with time zone, UUID, and varchar inputs.

```java
package org.duckdb;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.duckdb.udf.UdfLogicalType;
import org.duckdb.udf.UdfOptions;

public final class ScalarMixedTypesExample {
    public static void main(String[] args) throws Exception {
        try (DuckDBConnection conn = DriverManager.getConnection("jdbc:duckdb:").unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {

            conn.registerScalarUdf(
                "event_fingerprint",
                new UdfLogicalType[] {
                    UdfLogicalType.of(DuckDBColumnType.INTEGER),
                    UdfLogicalType.decimal(20, 4),
                    UdfLogicalType.of(DuckDBColumnType.TIMESTAMP),
                    UdfLogicalType.of(DuckDBColumnType.TIMESTAMP_WITH_TIME_ZONE),
                    UdfLogicalType.of(DuckDBColumnType.UUID),
                    UdfLogicalType.of(DuckDBColumnType.VARCHAR)
                },
                UdfLogicalType.of(DuckDBColumnType.VARCHAR),
                (ctx, in, out, rowCount) -> {
                    for (int row = 0; row < rowCount; row++) {
                        if (in[0].isNull(row) || in[1].isNull(row) || in[2].isNull(row) || in[3].isNull(row)
                            || in[4].isNull(row) || in[5].isNull(row)) {
                            out.setNull(row);
                            continue;
                        }

                        int id = in[0].getInt(row);
                        BigDecimal amount = in[1].getBigDecimal(row);
                        LocalDateTime ts = in[2].getLocalDateTime(row);
                        OffsetDateTime tsTz = in[3].getOffsetDateTime(row);
                        UUID requestId = in[4].getUUID(row);
                        String source = in[5].getString(row);

                        String fp = String.format("id=%d|amount=%s|ts=%s|ts_tz=%s|uuid=%s|src=%s",
                                                  id,
                                                  amount.toPlainString(),
                                                  ts,
                                                  tsTz,
                                                  requestId,
                                                  source);
                        out.setString(row, fp);
                    }
                },
                new UdfOptions().deterministic(true).nullSpecialHandling(true)
            );

            try (ResultSet rs = stmt.executeQuery(
                     "SELECT event_fingerprint("
                     + "7, "
                     + "12345.6789::DECIMAL(20,4), "
                     + "TIMESTAMP '2024-01-03 04:05:06.123456', "
                     + "TIMESTAMP WITH TIME ZONE '2024-01-03 04:05:06+00', "
                     + "'550e8400-e29b-41d4-a716-446655440000'::UUID, "
                     + "'api')")) {
                rs.next();
                System.out.println(rs.getString(1));
            }
        }
    }
}
```

### Example 2: Table Function with rich bind parameters (Java objects)

This example shows bind parameter materialization as Java objects and object-friendly output writes.

```java
package org.duckdb;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.duckdb.udf.TableBindResult;
import org.duckdb.udf.TableFunction;
import org.duckdb.udf.TableFunctionDefinition;
import org.duckdb.udf.TableState;
import org.duckdb.udf.UdfLogicalType;

public final class TableBindObjectsExample {
    public static void main(String[] args) throws Exception {
        try (DuckDBConnection conn = DriverManager.getConnection("jdbc:duckdb:").unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {

            conn.registerTableFunction(
                "orders_by_window_java",
                new TableFunction() {
                    @Override
                    public TableBindResult bind(org.duckdb.udf.BindContext ctx, Object[] parameters) {
                        LocalDate baseDate = (LocalDate) parameters[0];
                        LocalTime baseTime = (LocalTime) parameters[1];
                        LocalDateTime baseTs = (LocalDateTime) parameters[2];
                        OffsetDateTime baseTsTz = (OffsetDateTime) parameters[3];
                        BigDecimal baseAmount = (BigDecimal) parameters[4];
                        UUID tenant = (UUID) parameters[5];
                        List<?> tags = (List<?>) parameters[6];

                        Object[] state =
                            new Object[] {baseDate, baseTime, baseTs, baseTsTz, baseAmount, tenant, tags, 0};

                        return new TableBindResult(
                            new String[] {"row_id", "tenant", "amount", "event_day", "event_time", "event_ts", "event_ts_tz", "tag", "attrs", "status"},
                            new UdfLogicalType[] {
                                UdfLogicalType.of(DuckDBColumnType.INTEGER),
                                UdfLogicalType.of(DuckDBColumnType.UUID),
                                UdfLogicalType.decimal(18, 2),
                                UdfLogicalType.of(DuckDBColumnType.DATE),
                                UdfLogicalType.of(DuckDBColumnType.TIME),
                                UdfLogicalType.of(DuckDBColumnType.TIMESTAMP),
                                UdfLogicalType.of(DuckDBColumnType.TIMESTAMP_WITH_TIME_ZONE),
                                UdfLogicalType.of(DuckDBColumnType.VARCHAR),
                                UdfLogicalType.map(UdfLogicalType.of(DuckDBColumnType.VARCHAR),
                                                   UdfLogicalType.of(DuckDBColumnType.INTEGER)),
                                UdfLogicalType.enumeration("new", "retry", "done")
                            },
                            state
                        );
                    }

                    @Override
                    public TableState init(org.duckdb.udf.InitContext ctx, TableBindResult bind) {
                        return new TableState(bind.getBindState());
                    }

                    @Override
                    public int produce(TableState state, UdfOutputAppender out) {
                        Object[] st = (Object[]) state.getState();
                        LocalDate baseDate = (LocalDate) st[0];
                        LocalTime baseTime = (LocalTime) st[1];
                        LocalDateTime baseTs = (LocalDateTime) st[2];
                        OffsetDateTime baseTsTz = (OffsetDateTime) st[3];
                        BigDecimal baseAmount = (BigDecimal) st[4];
                        UUID tenant = (UUID) st[5];
                        List<?> tags = (List<?>) st[6];
                        int offset = (int) st[7];

                        int produced = 0;
                        while (produced < 256 && offset < tags.size()) {
                            String tag = String.valueOf(tags.get(offset));
                            out.beginRow()
                                .append(offset)
                                .append(tenant)
                                .append(baseAmount.add(BigDecimal.valueOf(offset)))
                                .append(baseDate.plusDays(offset))
                                .append(baseTime.plusSeconds(offset))
                                .append(baseTs.plusMinutes(offset))
                                .append(baseTsTz.plusMinutes(offset))
                                .append(tag)
                                .append(Map.of("attempt", offset + 1, "priority", 10 - Math.min(offset, 9)))
                                .append(offset == 0 ? "new" : "done")
                                .endRow();
                            offset++;
                            produced++;
                        }

                        st[7] = offset;
                        return produced;
                    }
                },
                new TableFunctionDefinition().withParameterTypes(new UdfLogicalType[] {
                    UdfLogicalType.of(DuckDBColumnType.DATE),
                    UdfLogicalType.of(DuckDBColumnType.TIME),
                    UdfLogicalType.of(DuckDBColumnType.TIMESTAMP),
                    UdfLogicalType.of(DuckDBColumnType.TIMESTAMP_WITH_TIME_ZONE),
                    UdfLogicalType.decimal(18, 2),
                    UdfLogicalType.of(DuckDBColumnType.UUID),
                    UdfLogicalType.list(UdfLogicalType.of(DuckDBColumnType.VARCHAR))
                })
            );

            try (ResultSet rs = stmt.executeQuery(
                     "SELECT row_id, CAST(amount AS VARCHAR), CAST(event_day AS VARCHAR), CAST(event_time AS VARCHAR), "
                     + "CAST(event_ts AS VARCHAR), CAST(event_ts_tz AS VARCHAR), tag, status "
                     + "FROM orders_by_window_java("
                     + "DATE '2024-01-03', "
                     + "TIME '01:02:03.123456', "
                     + "TIMESTAMP '2024-01-03 04:05:06', "
                     + "TIMESTAMP WITH TIME ZONE '2024-01-03 04:05:06+00', "
                     + "199.50::DECIMAL(18,2), "
                     + "'550e8400-e29b-41d4-a716-446655440000'::UUID, "
                     + "['alpha', 'beta', 'gamma'])")) {
                while (rs.next()) {
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(7));
                }
            }
        }
    }
}
```

### Example 3: Table Function with nested output types

This example writes `LIST`, `MAP`, `STRUCT`, `UNION`, and `ENUM` columns in the output.

```java
package org.duckdb;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.duckdb.udf.TableBindResult;
import org.duckdb.udf.TableFunction;
import org.duckdb.udf.TableFunctionDefinition;
import org.duckdb.udf.TableState;
import org.duckdb.udf.UdfLogicalType;

public final class TableNestedOutputExample {
    public static void main(String[] args) throws Exception {
        UdfLogicalType structType = UdfLogicalType.struct(
            new String[] {"id", "txt"},
            new UdfLogicalType[] {
                UdfLogicalType.of(DuckDBColumnType.INTEGER),
                UdfLogicalType.of(DuckDBColumnType.VARCHAR)
            }
        );

        UdfLogicalType unionType = UdfLogicalType.unionType(
            new String[] {"num", "txt"},
            new UdfLogicalType[] {
                UdfLogicalType.of(DuckDBColumnType.INTEGER),
                UdfLogicalType.of(DuckDBColumnType.VARCHAR)
            }
        );

        try (DuckDBConnection conn = DriverManager.getConnection("jdbc:duckdb:").unwrap(DuckDBConnection.class);
             Statement stmt = conn.createStatement()) {

            conn.registerTableFunction(
                "nested_demo_java",
                new TableFunction() {
                    @Override
                    public TableBindResult bind(org.duckdb.udf.BindContext ctx, Object[] parameters) {
                        int count = ((Number) parameters[0]).intValue();
                        return new TableBindResult(
                            new String[] {"tags", "scores", "meta", "choice", "status"},
                            new UdfLogicalType[] {
                                UdfLogicalType.list(UdfLogicalType.of(DuckDBColumnType.VARCHAR)),
                                UdfLogicalType.map(UdfLogicalType.of(DuckDBColumnType.VARCHAR),
                                                   UdfLogicalType.of(DuckDBColumnType.INTEGER)),
                                structType,
                                unionType,
                                UdfLogicalType.enumeration("small", "medium", "large")
                            },
                            count
                        );
                    }

                    @Override
                    public TableState init(org.duckdb.udf.InitContext ctx, TableBindResult bind) {
                        int count = ((Number) bind.getBindState()).intValue();
                        return new TableState(new int[] {0, count});
                    }

                    @Override
                    public int produce(TableState state, UdfOutputAppender out) {
                        int[] st = (int[]) state.getState();
                        int current = st[0];
                        int end = st[1];
                        int produced = 0;

                        while (produced < 256 && current < end) {
                            out.beginRow()
                                .append(List.of("tag-" + current, "tag-x"))
                                .append(Map.of("a", current, "b", current * 10))
                                .append(Map.of("id", current, "txt", "row-" + current))
                                .append(current % 2 == 0
                                            ? new AbstractMap.SimpleEntry<>("num", current)
                                            : new AbstractMap.SimpleEntry<>("txt", "odd-" + current))
                                .append(current % 3 == 0 ? "small" : (current % 3 == 1 ? "medium" : "large"))
                                .endRow();
                            current++;
                            produced++;
                        }

                        st[0] = current;
                        return produced;
                    }
                },
                new TableFunctionDefinition().withParameterTypes(new DuckDBColumnType[] {DuckDBColumnType.INTEGER})
            );

            try (ResultSet rs = stmt.executeQuery(
                     "SELECT tags[1], scores['a'], meta.id, CAST(choice AS VARCHAR), status "
                     + "FROM nested_demo_java(4) ORDER BY meta.id")) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + " | " + rs.getInt(2) + " | " + rs.getInt(3)
                                       + " | " + rs.getString(4) + " | " + rs.getString(5));
                }
            }
        }
    }
}
```
