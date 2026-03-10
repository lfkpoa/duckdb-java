# Plano de Adequação — JNI UDF Bridge (duckdb-java)

## Pré-requisito de build (obrigatório antes de qualquer tarefa)
Conforme o `README.md`, o fluxo padrão do projeto é:
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release` para gerar os artefatos em `build/release/`
- `make test` para executar os testes

Isso evita o erro recorrente `Error: /workspace/duckdb-java/build/release is not a directory`, pois o próprio `make release` cria/configura `build/release` quando necessário.

```bash
CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release
make test
```

> Dica de performance: neste ambiente, prefira manter `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc)` para acelerar a compilação C++/JNI do DuckDB.

> Regra para este plano: **ao terminar cada tarefa, sempre executar `make release` e `make test`**.
> Importante: **aguarde o término completo do `make release` (100%) antes de iniciar `make test`**.

## Objetivo Geral
Migrar incrementalmente o bridge de callbacks UDF/Table Function para um modelo mais consistente e com menos boilerplate JNI, preservando comportamento e preparando o caminho para reduzir dependência de `DuckDBNative` em favor de composição via bindings.

---

## Tarefa 1 — Finalizar RAII no bind callback
**Objetivo**
- Aplicar `CallbackEnvGuard` no `java_table_function_bind_callback` para remover `did_attach/get_callback_env/cleanup_callback_env` manual.
- Garantir que caminhos de erro mantenham o mesmo `duckdb_bind_set_error(...)` e limpeza de refs.

**Arquivos a alterar**
- `src/jni/duckdb_java.cpp`

**Testes a executar**
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release`
- `make test`
- (opcional) executar testes seletivos relevantes para a tarefa
---

## Tarefa 2 — Consolidar helper de cleanup nos caminhos restantes de UDF JNI
**Objetivo**
- Substituir `env->DeleteLocalRef(...)` residual em caminhos UDF/table UDF por `delete_local_ref(...)` quando apropriado.
- Evitar mudanças de semântica (somente refactor mecânico).

**Arquivos a alterar**
- `src/jni/duckdb_java.cpp`
- (se necessário) `src/jni/util.hpp`
- (se necessário) `src/jni/util.cpp`

**Testes a executar**
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release`
- `make test`
- (opcional) executar testes seletivos relevantes para a tarefa
---

## Tarefa 3 — Reforçar regressão de erro em callbacks Table Function (ajuste de testes existentes)
**Objetivo**
- Cobrir explicitamente propagação de mensagem de erro em:
  - exceção em `init(...)`
  - exceção em `produce(...)`
- Validar que refactor de lifecycle JNI não alterou mensagens/fluxo de exceção.

**Arquivos a alterar**
- `src/test/java/org/duckdb/TestDuckDBJDBC.java`

**Testes a executar**
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release`
- `make test`
- (opcional) executar testes seletivos relevantes para a tarefa
---

## Tarefa 4 — Preparar API bindings para callback bridge real (sem remover caminho antigo)
**Objetivo**
- Introduzir primitives em bindings para registrar callback Java real, substituindo callbacks smoke/range hardcoded.
- Manter compatibilidade transitória com caminho atual.

**Arquivos a alterar**
- `src/main/java/org/duckdb/DuckDBBindings.java`
- `src/jni/bindings_scalar_function.cpp`
- `src/jni/bindings_table_function.cpp`
- (eventualmente) `src/jni/util.*`

**Testes a executar**
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release`
- `make test`
- (opcional) executar testes seletivos relevantes para a tarefa
---

## Tarefa 5 — Migrar orquestração de registro UDF do caminho DuckDBNative para bindings-first
**Objetivo**
- Reduzir o uso de `duckdb_jdbc_register_scalar_udf`/`duckdb_jdbc_register_table_function` em `DuckDBNative`.
- Mover montagem do registro para Java com chamadas de bindings C API.

**Arquivos a alterar**
- `src/main/java/org/duckdb/DuckDBConnection.java`
- `src/main/java/org/duckdb/DuckDBNative.java`
- `src/main/java/org/duckdb/DuckDBBindings.java`
- `src/jni/duckdb_java.cpp` (somente se necessário para compatibilidade)

**Testes a executar**
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release`
- `make test`
- (opcional) executar testes seletivos relevantes para a tarefa
---

## Tarefa 6 — Limpeza final e documentação
**Objetivo**
- Remover caminhos redundantes após migração concluída.
- Atualizar docs para refletir arquitetura final e decisões.

**Arquivos a alterar**
- `UDF.MD`
- `README.md` (se necessário)
- fontes JNI/Java redundantes identificadas nas tarefas anteriores

**Testes a executar**
- `CMAKE_BUILD_PARALLEL_LEVEL=$(nproc) make release`
- `make test`
- (opcional) executar testes seletivos relevantes para a tarefa
---

## Observações de Qualidade
- Em cada PR incremental: sem mudança comportamental não intencional.
- Não alterar contrato de erro SQL sem teste explícito.
- Preferir PRs pequenos e reversíveis.
- Ao finalizar cada tarefa: rodar obrigatoriamente `make release` e `make test`.
- Sempre aguardar a conclusão completa do `make release` antes de rodar `make test`.
