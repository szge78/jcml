# jcml

Reads the `Config_Message_Log` audit table from a Cisco UCCE/PCCE MS SQL database, deserializes the binary blobs stored in `ConfigMessage` according to JSON schemas, converts the results to JSON, and runs them through a configurable pipeline of actions that filter, enrich, and reshape the data. The final result is served over REST (JSON/XML) and SOAP (XML).

The binary blobs stored in `Config_Message_Log.ConfigMessage` are most likely serialized C++ structs. Schemas are reverse-engineered from observed wire data, the Cisco *Database Schema Handbook*, and the output of the Cisco-provided `dumpcfg` diagnostic tool.

The project is in no way affiliated with Cisco.

The name "jcml" is a play on "JSON Cisco Message Log".

## Goals

- **Schema coverage** — identify every distinct `messageType` (every combination of `logOperation` × `tableName`) that can appear in `Config_Message_Log`.
- **Version coverage** — produce and maintain schemas for all relevant Cisco UCCE/PCCE versions (and unsupported versions if needed), since the binary layout may differ between releases.


## Build & run

```bash
./gradlew build        # produces build/libs/jcml-<version>-all.jar
./gradlew run          # runs with -Dmicronaut.environments=prod
```

External config override: set `-Dmicronaut.config.files=~/configs/jcml.yml`.

## Endpoints

| Protocol | Address                                                | Notes                                                                                                          |
|----------|--------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| REST | `/report?dateFrom=...&dateTo=...`                      | ISO-8601 date strings                                                                                          |
| REST | `/report/with-ignored-steps?...&ignoredSteps=StepName` | Skip named steps at call time                                                                                  |
| SOAP | `/ws/report`                                           | WSDL at `?wsdl`; GZIP compressed. Notice the SOAP interface uses a different port (defined in application.yml) |
| Metrics | `/prometheus`                                          | Micrometer / Prometheus                                                                                        |

## Configuration

`src/main/resources/application.yml` — server ports, DB connection, CML timezone, deserialization chunk size, schema and pipeline paths.
`src/main/resources/pipeline.json` — ordered list of named, enable/disable-able steps. Each step names an Action class and carries its own `config` block.

## Binary deserialization

Each row in `Config_Message_Log` carries a binary blob. The deserializer reads a fixed 24-byte header (6 × 4-byte little-endian integers) first, then dispatches to a JSON schema matched by message type for the remainder of the buffer.

### Schema files

Schema files live in the `schemas/` directory (e.g. `ADD__AGENT.json`). A `messageType` is the concatenation of `logOperation` and `tableName` from `Config_Message_Log`, joined by a double underscore and uppercased: `{logOperation}__{tableName}`. For example, `logOperation = UPDATE` and `tableName = SKILL_GROUP` yield the message type `UPDATE__SKILL_GROUP`, stored in `schemas/UPDATE__SKILL_GROUP.json`.

Top-level fields:

| Field | Required | Description |
|-------|----------|-------------|
| `messageType` | yes | Matches the value in `Config_Message_Log` |
| `version` | yes | Schema version string |
| `description` | no | Human-readable note |
| `fields` | yes | Ordered array of field descriptors (see below) |

Each field descriptor:

| Field | Required | Description                                                        |
|-------|----------|--------------------------------------------------------------------|
| `name` | yes | Output key name                                                    |
| `type` | yes | One of the nine types listed below                                 |
| `isArray` | no | `true` → field is a variable-length array (must be last in schema) |
| `nestedSchema` | no | Schema name to use for `OBJECT` fields                             |
| `stringPadding` | no | Padding mode for `STRING` fields, default: ALIGN_4.                |
| `description` | no | Human-readable note                                                |

### Field types

| Type | Size | Notes |
|------|------|-------|
| `BYTE` | 1 byte | Unsigned |
| `CHAR` | 1 byte | Unsigned |
| `SHORT` | 2 bytes | Unsigned, little-endian |
| `INTEGER` | 4 bytes | Unsigned, little-endian |
| `LONG` | 8 bytes | Signed, little-endian |
| `FLOAT` | 4 bytes | IEEE 754 |
| `DOUBLE` | 8 bytes | IEEE 754 |
| `STRING` | variable | 2-byte LE length prefix + data + null terminator + optional padding |
| `OBJECT` | variable | Nested struct; references another schema via `nestedSchema` |

### Arrays

A field with `"isArray": true` must be the last field in its schema. The deserializer reads elements until the buffer is exhausted. For `OBJECT` arrays, each element is deserialized using the referenced nested schema.

### String padding

After reading `length + 1` bytes (null terminator), optional alignment padding is consumed. Modes: `NONE`, `ALIGN_2`, `ALIGN_4`, `FIXED_1`, `FIXED_2`, `FIXED_3`.

### Schema path configuration

`schema.path` in `application.yml` accepts either `classpath:schemas` (schemas bundled in the JAR) or an absolute filesystem path. Because `application.yml` itself can be externalized via `-Dmicronaut.config.files=`, deployments can point to a site-specific schema directory without rebuilding.

`schema.auto-refresh: true` combined with `schema.auto-refresh-interval` allows live schema updates without a service restart.

### Version compatibility

The binary layout of `Config_Message_Log` blobs differs across UCCE/PCCE versions. Schemas must match the exact version deployed. When upgrading UCCE/PCCE, schema files may need to be revised.

Tested against UCCE 12.6.

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

|                            | |
|----------------------------|-|
| Java 25 / Micronaut 4.10.7 | Runtime and DI framework |
| Apache CXF 4.0.4           | SOAP/JAX-WS |
| Micronaut Data JDBC        | DB access (MS SQL Server) |
| Jackson                    | JSON serialization |
| Josson 1.5.1               | Expression language used in templates and enrichment |
| Micrometer + Prometheus    | Metrics |
| Java Virtual Threads       | Parallel deserialization and pipeline steps |

## Contributing

### Adding new schemas

Each unrecognized `messageType` in `Config_Message_Log` is a gap in coverage. To add one:

1. Capture raw blob data for the target `messageType` (e.g. by querying `Config_Message_Log` directly or using `dumpcfg`).
2. Cross-reference field positions with the Cisco *Database Schema Handbook* for the matching UCCE/PCCE version.
3. Write a `schemas/{logOperation}__{tableName}.json` file following the schema format described above (field types, ordering, padding modes).
4. Iterate: run jcml against live or captured data, compare deserialized output against expected values, adjust field types and padding until the output is correct.
5. Open a pull request with the new schema file and a brief description of the message type and version tested against.

### Adding new pipeline Actions

Actions live in `src/main/java/…/pipeline/actions/`. To add one:

1. Create a class that implements the `PipelineAction` interface (look at existing actions for the pattern).
2. Declare it as a Micronaut bean (`@Singleton` or `@Prototype`).
3. Read any action-specific settings from the `config` block passed to `execute()`.
4. Register the new class name in `pipeline.json` as a step.
5. Open a pull request with the implementation and, if applicable, a sample `pipeline.json` snippet showing its config keys.

### No programming experience? Provide data access

Schema reverse-engineering requires access to a live or archived Cisco UCCE/PCCE system. If you have access to such a system but not the time or skills to write schemas yourself, you can still contribute by:

- Sharing sanitized or anonymized `Config_Message_Log` exports (binary blobs + `logOperation`/`tableName` columns) for message types not yet covered.
- Running `dumpcfg` and sharing its output for specific object types.
- Providing version information (UCCE/PCCE release, patch level) alongside the captured data.

Open an issue to discuss how to share data safely.

## License

MIT
