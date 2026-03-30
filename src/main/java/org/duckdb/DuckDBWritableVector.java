package org.duckdb;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.duckdb.DuckDBBindings.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.SQLException;

public final class DuckDBWritableVector {
    private final ByteBuffer vectorRef;
    private final int rowCount;
    private final DuckDBVectorTypeInfo typeInfo;
    private final ByteBuffer data;
    private ByteBuffer validity;

    DuckDBWritableVector(ByteBuffer vectorRef, int rowCount) throws SQLException {
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

    public void setNull(int row) throws SQLException {
        checkRowIndex(row);
        ensureValidity();
        duckdb_validity_set_row_validity(validity, row, false);
    }

    public void setBoolean(int row, boolean value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.BOOLEAN);
        data.put(row, value ? (byte) 1 : (byte) 0);
    }

    public void setByte(int row, byte value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.TINYINT);
        data.put(row, value);
    }

    public void setShort(int row, short value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.SMALLINT);
        data.order(LITTLE_ENDIAN).putShort(row * Short.BYTES, value);
    }

    public void setInt(int row, int value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.INTEGER);
        data.order(LITTLE_ENDIAN).putInt(row * Integer.BYTES, value);
    }

    public void setLong(int row, long value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.BIGINT);
        data.order(LITTLE_ENDIAN).putLong(row * Long.BYTES, value);
    }

    public void setFloat(int row, float value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.FLOAT);
        data.order(LITTLE_ENDIAN).putFloat(row * Float.BYTES, value);
    }

    public void setDouble(int row, double value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.DOUBLE);
        data.order(LITTLE_ENDIAN).putDouble(row * Double.BYTES, value);
    }

    public void setBigDecimal(int row, BigDecimal value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.DECIMAL);
        if (value == null) {
            setNull(row);
            return;
        }
        BigDecimal scaled = value.setScale(typeInfo.decimalMeta.scale);
        switch (typeInfo.storageType) {
        case DUCKDB_TYPE_SMALLINT:
            data.order(LITTLE_ENDIAN).putShort(row * Short.BYTES, scaled.unscaledValue().shortValueExact());
            break;
        case DUCKDB_TYPE_INTEGER:
            data.order(LITTLE_ENDIAN).putInt(row * Integer.BYTES, scaled.unscaledValue().intValueExact());
            break;
        case DUCKDB_TYPE_BIGINT:
            data.order(LITTLE_ENDIAN).putLong(row * Long.BYTES, scaled.unscaledValue().longValueExact());
            break;
        case DUCKDB_TYPE_HUGEINT: {
            BigInteger unscaled = scaled.unscaledValue();
            ByteBuffer slice = data.duplicate().order(LITTLE_ENDIAN);
            slice.position(row * typeInfo.widthBytes);
            slice.putLong(unscaled.longValue());
            slice.putLong(unscaled.shiftRight(Long.SIZE).longValue());
            break;
        }
        default:
            throw new SQLException("Unsupported DECIMAL storage type: " + typeInfo.storageType);
        }
    }

    ByteBuffer vectorRef() {
        return vectorRef;
    }

    private void ensureValidity() throws SQLException {
        if (validity != null) {
            return;
        }
        duckdb_vector_ensure_validity_writable(vectorRef);
        validity = duckdb_vector_get_validity(vectorRef, rowCount);
        if (validity == null) {
            throw new SQLException("Cannot initialize vector validity");
        }
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
