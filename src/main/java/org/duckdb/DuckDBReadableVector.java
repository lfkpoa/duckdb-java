package org.duckdb;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.duckdb.DuckDBBindings.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class DuckDBReadableVector {
    private static final BigDecimal ULONG_MULTIPLIER = new BigDecimal("18446744073709551616");

    private final ByteBuffer vectorRef;
    private final int rowCount;
    private final DuckDBVectorTypeInfo typeInfo;
    private final ByteBuffer data;
    private final ByteBuffer validity;

    DuckDBReadableVector(ByteBuffer vectorRef, int rowCount) throws SQLException {
        if (vectorRef == null) {
            throw new SQLException("Invalid vector reference");
        }
        this.vectorRef = vectorRef;
        this.rowCount = rowCount;
        this.typeInfo = DuckDBVectorTypeInfo.fromVector(vectorRef);
        this.data = duckdb_vector_get_data(vectorRef, (long) rowCount * typeInfo.widthBytes);
        this.validity = duckdb_vector_get_validity(vectorRef, rowCount);
    }

    public DuckDBColumnType getType() {
        return typeInfo.columnType;
    }

    public int rowCount() {
        return rowCount;
    }

    public boolean isNull(int row) {
        checkRowIndex(row);
        if (validity == null) {
            return false;
        }
        int entryPos = (row / 64) * Long.BYTES;
        long mask = validity.order(LITTLE_ENDIAN).getLong(entryPos);
        return (mask & (1L << (row % 64))) == 0;
    }

    public boolean getBoolean(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.BOOLEAN);
        return data.get(row) != 0;
    }

    public byte getByte(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.TINYINT);
        return data.get(row);
    }

    public short getShort(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.SMALLINT);
        return data.order(LITTLE_ENDIAN).getShort(row * Short.BYTES);
    }

    public short getUint8(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.UTINYINT);
        return (short) Byte.toUnsignedInt(data.get(row));
    }

    public int getUint16(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.USMALLINT);
        return Short.toUnsignedInt(data.order(LITTLE_ENDIAN).getShort(row * Short.BYTES));
    }

    public int getInt(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.INTEGER);
        return data.order(LITTLE_ENDIAN).getInt(row * Integer.BYTES);
    }

    public long getUint32(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.UINTEGER);
        return Integer.toUnsignedLong(data.order(LITTLE_ENDIAN).getInt(row * Integer.BYTES));
    }

    public long getLong(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.BIGINT);
        return data.order(LITTLE_ENDIAN).getLong(row * Long.BYTES);
    }

    public BigInteger getUint64(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.UBIGINT);
        byte[] bytes = new byte[Long.BYTES];
        ByteBuffer slice = data.duplicate();
        slice.position(row * Long.BYTES);
        slice.get(bytes);
        reverseInPlace(bytes);
        return new BigInteger(1, bytes);
    }

    public float getFloat(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.FLOAT);
        return data.order(LITTLE_ENDIAN).getFloat(row * Float.BYTES);
    }

    public double getDouble(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.DOUBLE);
        return data.order(LITTLE_ENDIAN).getDouble(row * Double.BYTES);
    }

    public LocalDate getLocalDate(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.DATE);
        return LocalDate.ofEpochDay(data.order(LITTLE_ENDIAN).getInt(row * Integer.BYTES));
    }

    public Date getDate(int row) throws SQLException {
        return Date.valueOf(getLocalDate(row));
    }

    public LocalDateTime getLocalDateTime(int row) throws SQLException {
        checkRowIndex(row);
        requireTimestampType();
        long epochValue = data.order(LITTLE_ENDIAN).getLong(row * Long.BYTES);
        switch (typeInfo.capiType) {
        case DUCKDB_TYPE_TIMESTAMP_S:
            return DuckDBTimestamp.localDateTimeFromTimestamp(epochValue, ChronoUnit.SECONDS, null);
        case DUCKDB_TYPE_TIMESTAMP_MS:
            return DuckDBTimestamp.localDateTimeFromTimestamp(epochValue, ChronoUnit.MILLIS, null);
        case DUCKDB_TYPE_TIMESTAMP:
            return DuckDBTimestamp.localDateTimeFromTimestamp(epochValue, ChronoUnit.MICROS, null);
        case DUCKDB_TYPE_TIMESTAMP_NS:
            return DuckDBTimestamp.localDateTimeFromTimestamp(epochValue, ChronoUnit.NANOS, null);
        case DUCKDB_TYPE_TIMESTAMP_TZ:
            return DuckDBTimestamp.localDateTimeFromTimestampWithTimezone(epochValue, ChronoUnit.MICROS, null);
        default:
            throw new SQLException("Expected vector type TIMESTAMP*, found " + typeInfo.columnType);
        }
    }

    public Timestamp getTimestamp(int row) throws SQLException {
        return Timestamp.valueOf(getLocalDateTime(row));
    }

    public OffsetDateTime getOffsetDateTime(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.TIMESTAMP_WITH_TIME_ZONE);
        long micros = data.order(LITTLE_ENDIAN).getLong(row * Long.BYTES);
        Instant instant = instantFromEpoch(micros, ChronoUnit.MICROS);
        return instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    public BigDecimal getBigDecimal(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.DECIMAL);
        switch (typeInfo.storageType) {
        case DUCKDB_TYPE_SMALLINT:
            return BigDecimal.valueOf(data.order(LITTLE_ENDIAN).getShort(row * Short.BYTES),
                                      typeInfo.decimalMeta.scale);
        case DUCKDB_TYPE_INTEGER:
            return BigDecimal.valueOf(data.order(LITTLE_ENDIAN).getInt(row * Integer.BYTES),
                                      typeInfo.decimalMeta.scale);
        case DUCKDB_TYPE_BIGINT:
            return BigDecimal.valueOf(data.order(LITTLE_ENDIAN).getLong(row * Long.BYTES), typeInfo.decimalMeta.scale);
        case DUCKDB_TYPE_HUGEINT: {
            ByteBuffer slice = data.duplicate().order(LITTLE_ENDIAN);
            slice.position(row * typeInfo.widthBytes);
            long lower = slice.getLong();
            long upper = slice.getLong();
            return new BigDecimal(upper)
                .multiply(ULONG_MULTIPLIER)
                .add(new BigDecimal(Long.toUnsignedString(lower)))
                .scaleByPowerOfTen(typeInfo.decimalMeta.scale * -1);
        }
        default:
            throw new SQLException("Unsupported DECIMAL storage type: " + typeInfo.storageType);
        }
    }

    public String getString(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.VARCHAR);
        return new String(duckdb_jdbc_varchar_string_bytes(data, row), UTF_8);
    }

    ByteBuffer vectorRef() {
        return vectorRef;
    }

    private void requireType(DuckDBColumnType expected) throws SQLException {
        if (typeInfo.columnType != expected) {
            throw new SQLException("Expected vector type " + expected + ", found " + typeInfo.columnType);
        }
    }

    private void requireTimestampType() throws SQLException {
        switch (typeInfo.columnType) {
        case TIMESTAMP:
        case TIMESTAMP_S:
        case TIMESTAMP_MS:
        case TIMESTAMP_NS:
        case TIMESTAMP_WITH_TIME_ZONE:
            return;
        default:
            throw new SQLException("Expected vector type TIMESTAMP*, found " + typeInfo.columnType);
        }
    }

    private void checkRowIndex(int row) {
        if (row < 0 || row >= rowCount) {
            throw new IndexOutOfBoundsException("Row index out of bounds: " + row);
        }
    }

    private static void reverseInPlace(byte[] bytes) {
        for (int i = 0; i < bytes.length / 2; i++) {
            byte tmp = bytes[i];
            bytes[i] = bytes[bytes.length - 1 - i];
            bytes[bytes.length - 1 - i] = tmp;
        }
    }

    private static Instant instantFromEpoch(long value, ChronoUnit unit) throws SQLException {
        switch (unit) {
        case SECONDS:
            return Instant.ofEpochSecond(value);
        case MILLIS:
            return Instant.ofEpochMilli(value);
        case MICROS: {
            long epochSecond = Math.floorDiv(value, 1_000_000L);
            long nanoAdjustment = Math.floorMod(value, 1_000_000L) * 1000L;
            return Instant.ofEpochSecond(epochSecond, nanoAdjustment);
        }
        case NANOS: {
            long epochSecond = Math.floorDiv(value, 1_000_000_000L);
            long nanoAdjustment = Math.floorMod(value, 1_000_000_000L);
            return Instant.ofEpochSecond(epochSecond, nanoAdjustment);
        }
        default:
            throw new SQLException("Unsupported unit type: " + unit);
        }
    }
}
