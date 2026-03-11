#pragma once

#include <jni.h>

void duckdb_jdbc_register_scalar_udf_impl(JNIEnv *env, jclass clazz, jobject conn_ref_buf, jbyteArray name_j,
                                          jobject callback, jobjectArray argument_logical_types_j,
                                          jobject return_logical_type_j, jboolean special_handling,
                                          jboolean return_null_on_exception, jboolean deterministic, jboolean var_args);

void duckdb_jdbc_register_table_function_impl(JNIEnv *env, jclass clazz, jobject conn_ref_buf, jbyteArray name_j,
                                              jobject callback, jobjectArray parameter_types_j,
                                              jboolean supports_projection_pushdown, jint max_threads,
                                              jboolean thread_safe);
