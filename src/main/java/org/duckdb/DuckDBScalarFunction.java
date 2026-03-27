package org.duckdb;

@FunctionalInterface
public interface DuckDBScalarFunction {
    Object[] apply(DuckDBVector[] args, int rowCount) throws Exception;
}
