package org.duckdb;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.duckdb.DuckDBBindings.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.SQLException;

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

    public int getInt(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.INTEGER);
        return data.order(LITTLE_ENDIAN).getInt(row * Integer.BYTES);
    }

    public long getLong(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.BIGINT);
        return data.order(LITTLE_ENDIAN).getLong(row * Long.BYTES);
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

    public BigDecimal getBigDecimal(int row) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.DECIMAL);
        switch (typeInfo.storageType) {
        case DUCKDB_TYPE_SMALLINT:
            return BigDecimal.valueOf(data.order(LITTLE_ENDIAN).getShort(row * Short.BYTES), typeInfo.decimalMeta.scale);
        case DUCKDB_TYPE_INTEGER:
            return BigDecimal.valueOf(data.order(LITTLE_ENDIAN).getInt(row * Integer.BYTES), typeInfo.decimalMeta.scale);
        case DUCKDB_TYPE_BIGINT:
            return BigDecimal.valueOf(data.order(LITTLE_ENDIAN).getLong(row * Long.BYTES), typeInfo.decimalMeta.scale);
        case DUCKDB_TYPE_HUGEINT: {
            ByteBuffer slice = data.duplicate().order(LITTLE_ENDIAN);
            slice.position(row * typeInfo.widthBytes);
            long lower = slice.getLong();
            long upper = slice.getLong();
            return new BigDecimal(upper).multiply(ULONG_MULTIPLIER)
                .add(new BigDecimal(Long.toUnsignedString(lower)))
                .scaleByPowerOfTen(typeInfo.decimalMeta.scale * -1);
        }
        default:
            throw new SQLException("Unsupported DECIMAL storage type: " + typeInfo.storageType);
        }
    }

    ByteBuffer vectorRef() {
        return vectorRef;
    }

    private void requireType(DuckDBColumnType expected) throws SQLException {
        if (typeInfo.columnType != expected) {
            throw new SQLException("Expected vector type " + expected + ", found " + typeInfo.columnType);
        }
    }

    private void checkRowIndex(int row) {
        if (row < 0 || row >= rowCount) {
            throw new IndexOutOfBoundsException("Row index out of bounds: " + row);
        }
    }
}
