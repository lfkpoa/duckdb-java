package org.duckdb;

@FunctionalInterface
public interface DuckDBScalarFunction {
    /**
     * Processes a full input chunk and returns one output value per row.
     *
     * <p>The callback receives argument vectors aligned by row index. The returned array must have exactly
     * {@code rowCount} elements, where each element corresponds to one output row. Returning {@code null} for an
     * element writes SQL {@code NULL} for that row.
     *
     * <p>Supported scalar SQL types in the MVP for inputs/outputs: {@code BOOLEAN}, {@code TINYINT},
     * {@code SMALLINT}, {@code INTEGER}, {@code BIGINT}, {@code FLOAT}, {@code DOUBLE}, {@code VARCHAR},
     * {@code DATE}, {@code TIME}, {@code TIMESTAMP}, {@code TIMESTAMP WITH TIME ZONE}, and {@code DECIMAL}.
     *
     * @param args input vectors for the current chunk
     * @param rowCount number of rows in the current chunk
     * @return output values for all rows in the chunk
     * @throws Exception when function execution fails
     */
    Object[] apply(DuckDBVector[] args, int rowCount) throws Exception;
}
