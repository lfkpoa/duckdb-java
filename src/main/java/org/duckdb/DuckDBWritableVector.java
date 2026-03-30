package org.duckdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.duckdb.DuckDBBindings.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.SQLException;

public final class DuckDBWritableVector {
    private static final BigInteger UINT64_MAX = new BigInteger("18446744073709551615");

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

    public void setUint8(int row, int value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.UTINYINT);
        checkUnsignedRange("UTINYINT", value, 0xFFL);
        data.put(row, (byte) value);
    }

    public void setUint16(int row, int value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.USMALLINT);
        checkUnsignedRange("USMALLINT", value, 0xFFFFL);
        data.order(LITTLE_ENDIAN).putShort(row * Short.BYTES, (short) value);
    }

    public void setInt(int row, int value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.INTEGER);
        data.order(LITTLE_ENDIAN).putInt(row * Integer.BYTES, value);
    }

    public void setUint32(int row, long value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.UINTEGER);
        checkUnsignedRange("UINTEGER", value, 0xFFFFFFFFL);
        data.order(LITTLE_ENDIAN).putInt(row * Integer.BYTES, (int) value);
    }

    public void setLong(int row, long value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.BIGINT);
        data.order(LITTLE_ENDIAN).putLong(row * Long.BYTES, value);
    }

    public void setUint64(int row, BigInteger value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.UBIGINT);
        if (value == null) {
            setNull(row);
            return;
        }
        if (value.signum() < 0 || value.compareTo(UINT64_MAX) > 0) {
            throw new SQLException("Value out of range for UBIGINT: " + value);
        }
        byte[] bytes = new byte[Long.BYTES];
        byte[] source = value.toByteArray();
        int copyLength = Math.min(source.length, Long.BYTES);
        for (int i = 0; i < copyLength; i++) {
            bytes[Long.BYTES - copyLength + i] = source[source.length - copyLength + i];
        }
        reverseInPlace(bytes);
        ByteBuffer slice = data.duplicate();
        slice.position(row * Long.BYTES);
        slice.put(bytes);
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

    public void setString(int row, String value) throws SQLException {
        checkRowIndex(row);
        requireType(DuckDBColumnType.VARCHAR);
        if (value == null) {
            setNull(row);
            return;
        }
        duckdb_vector_assign_string_element_len(vectorRef, row, value.getBytes(UTF_8));
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

    private static void checkUnsignedRange(String typeName, long value, long maxValue) throws SQLException {
        if (value < 0 || value > maxValue) {
            throw new SQLException("Value out of range for " + typeName + ": " + value);
        }
    }

    private static void reverseInPlace(byte[] bytes) {
        for (int i = 0; i < bytes.length / 2; i++) {
            byte tmp = bytes[i];
            bytes[i] = bytes[bytes.length - 1 - i];
            bytes[bytes.length - 1 - i] = tmp;
        }
    }
}
