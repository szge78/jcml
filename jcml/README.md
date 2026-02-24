# jcml

Reads the `Config_Message_Log` audit table from a Cisco UCCE/PCCE MS SQL database, deserializes the binary blobs stored in each row according to JSON schemas, converts the results to JSON, and runs them through a configurable pipeline of actions that filter, enrich, and reshape the data. The final result is served over REST (JSON/XML) and SOAP (XML).

## Build & run

```bash
./gradlew build        # produces build/libs/jcml-<version>-all.jar
./gradlew run          # runs with -Dmicronaut.environments=prod
```

External config override: set `-Dmicronaut.config.files=~/configs/jcml.yml`.

## Endpoints

| Protocol | Address | Notes |
|----------|---------|-------|
| REST | `http://<host>:8080/report?dateFrom=...&dateTo=...` | ISO-8601 date strings |
| REST | `http://<host>:8080/report/with-ignored-steps?...&ignoredSteps=StepName` | Skip named steps at call time |
| SOAP | `http://<host>:8081/ws/report` | WSDL at `?wsdl`; GZIP compressed |
| Metrics | `http://<host>:8080/prometheus` | Micrometer / Prometheus |

## Configuration

`src/main/resources/application.yml` — server ports, DB connection, CML timezone, deserialization chunk size, schema and pipeline paths.
`src/main/resources/pipeline.json` — ordered list of named, enable/disable-able steps. Each step names an Action class and carries its own `config` block.

## Pipeline actions

Actions are chained sequentially; each receives the full list of items produced by the previous step.

### EntityPreloadAction
Calls `findAll()` (or a configurable method) on a Micronaut Data repository and stores the result in the session context as a map (by entity ID) and a list.

Config keys: `entityClassName`, `repositoryClassName`, `sessionContextKey`, `finderMethod` (optional, defaults to `findAll`).

### NativeSqlPreloadAction
Runs an arbitrary native SQL query and stores results in the session context by a designated ID column. Useful when a repository join is needed.

Config keys: `nativeSql`, `idColumn`, `sessionContextKey`.

### CmlHeaderExtractorAction
Scans items matching a field/value filter (e.g. only `ADD` messages), reads transaction metadata (CML ID, machine name, PID, process name, user) from the binary `_header` array, and indexes them by CML ID in the session context. Handles CML ID recycling via a recovery-key tree.

Config keys: `filterField`, `filterValue`, `headerKey`, `keyPrefix`, `cmlId` (`lookupIndex`, `targetKey`), `fields` array (each: `lookupKey`, `targetKey`, `type`).

### SessionEnrichAction
For each item, evaluates a Josson expression to extract an ID, looks up the session context entry, and copies configured fields onto the item (with optional renaming). Also stamps `_cmlTransactionId` (`{id}_{recoveryKey}`) for transaction-level grouping. Handles recycled CML IDs via floor lookup.

Config keys: `prefix`, `idExpression`, `idType`, `includeFields`, `fieldMappings`.

### CmlTransactionFilterAction
Drops every item belonging to a transaction (identified by `_cmlTransactionId`) if any item in that transaction matches a condition. Runs both passes in parallel.

Config keys: `conditions` (array of `"fieldName==value"` strings) or `condition` (single string).

### ArrayUnwrapAction
Expands items whose message type has nested arrays: produces one output item per array element, merging the element's fields into a copy of the parent. Adds `_arrayIdx` and optionally `_sourceArrayKeyFieldName` to each output.

Config keys: `arraysToUnwrap` (map of `messageType → [arrayFieldName, ...]`), `messageTypeField`, `fieldsToCopy`, `indexFieldName`, `arrayKeyFieldName`.

### TimestampConverterAction
Finds a Windows FILETIME field by name, converts it to Unix epoch milliseconds and an ISO-8601 string in a configurable timezone.

Config keys: `inputFieldNames`, `zoneIdForHumanReadableTimestamp`, `unixTimestampFieldName`, `humanReadableTimestampFieldName`.

### BatchTemplateAction
Applies Josson expression templates to produce derived fields (e.g. `_full_description`). Rules are evaluated in order; the first matching condition wins. Template placeholders use `{{expression}}` syntax. Pre-compiles expressions and caches condition→rule mappings per message type for throughput.

Config keys: `rules` array — each rule has a `condition` (Josson boolean expression) and a `templates` map (`fieldName → "literal {{expression}} text"`).

### SortAction
Sorts the item list by one or more fields, each with configurable direction and null placement. Field paths support dot notation (e.g. `_dbMetadata.recoveryKey`).

Config keys: `sortKeys` array — each entry: `field`, `direction` (`asc`/`desc`), `nullsFirst` (boolean).

### ContextDumpAction
Logs the session and/or global context once per session (keyed by label). Useful for debugging pipeline state after preload steps. `keysOnly` mode logs key names and sizes without serializing full data.

Config keys: `label`, `dumpGlobal`, `dumpSession`, `keysOnly`.

## Tech stack

| | |
|-|-|
| Java 25 / Micronaut 4.6.1 | Runtime and DI framework |
| Apache CXF 4.0.4 | SOAP/JAX-WS |
| Micronaut Data JDBC | DB access (MS SQL Server) |
| Jackson | JSON serialization |
| Josson 1.5.1 | Expression language used in templates and enrichment |
| Micrometer + Prometheus | Metrics |
| Java Virtual Threads | Parallel deserialization and pipeline steps |
