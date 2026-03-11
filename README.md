It's required to have a JDK installed to build.
Make sure the `JAVA_HOME` environment variable is set.


### Development

To build the driver, run `make release`. 


This will produce two jars in the build folder:
`build/release/duckdb_jdbc.jar`
`build/release/duckdb_jdbc_tests.jar`

The tests can be ran using using `make test` or this command
```
java -cp "build/release/duckdb_jdbc_tests.jar:build/release/duckdb_jdbc.jar" org/duckdb/TestDuckDBJDBC
```

This optionally takes an argument to only run a single test, for example:
```
java -cp "build/release/duckdb_jdbc_tests.jar:build/release/duckdb_jdbc.jar"  org/duckdb/TestDuckDBJDBC test_valid_but_local_config_throws_exception
```

### User-Defined Functions (Java)

All Java UDF documentation and examples are available in [UDF.MD](UDF.MD).

Scalar UDF registration includes ergonomic overloads for:
- arity 1..4,
- zero-argument functions,
- varargs registration,
- Java `Class<?>` type-mapped registration.

These APIs keep the same vector callback runtime. For best performance, use chunk-oriented loops (`rowCount`) and prefer explicit logical types for precision-sensitive signatures.

UDF registration is routed through `DuckDBBindings` JNI entry points (bindings-first surface).
