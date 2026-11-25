# Rule 1 - OpenAPI 必須拆成根檔、api group 與 component

- Level: `MUST`
- `specs/truth/contracts/` 不得把全部 path 與全部 schema 寫進同一個 `openapi.yaml`。
- 根檔只放 `info`、`tags` 與 `$ref`。path 依 api group 分檔。parameter 與 schema 寫在 `components/`。
- api group 用該組操作的 tag／業務邊界命名。跨 group 共用的 schema 放 `components/schemas/shared.yaml`；只屬於某一 group 的 schema 放同名檔。
- 根檔的 path 與 component 都以 `$ref` 指向分檔，不在根檔重寫操作本體。
- groups 預設一層：`groups/<api-group>.yaml`。

## Good Example

- 這個例子是好的，因為根檔只掛索引，操作與 schema 各有自己的檔，且 groups 仍是一層。

```text
specs/truth/contracts/
├── openapi.yaml
├── groups/
│   ├── 工位終端.yaml
│   └── 品檢.yaml
└── components/
    ├── parameters.yaml
    └── schemas/
        ├── shared.yaml
        ├── 工位終端.yaml
        └── 品檢.yaml
```

```yaml
# openapi.yaml
paths:
  /operations/start:
    $ref: ./groups/工位終端.yaml#/startOperation
components:
  schemas:
    ErrorResponse:
      $ref: ./components/schemas/shared.yaml#/ErrorResponse
```

## Bad Example

- 這個例子是壞的，因為全部 path 與 schema 擠在同一個檔。

```text
specs/truth/contracts/openapi.yaml
```

```yaml
# openapi.yaml
paths:
  /operations/start:
    post:
      operationId: startOperation
components:
  schemas:
    StartOperationRequest:
      type: object
    JudgeInspectionRequest:
      type: object
```

# Rule 2 - groups 再多一層是 incremental，超過 10 個 yaml 才開該層

- Level: `MUST`
- groups 底下還可以再多一個階層，用來區隔不同 groups。
- 這是 incremental：可以超過 10 個 yaml 時再開該層。不到門檻必須維持 `groups/<api-group>.yaml`。
- 不可因為「以後可能會變多」或「看起來比較整齊」就提前開層。
- 開層之後，根檔 `$ref` 跟著改成 `./groups/<區隔>/<api-group>.yaml#/<operation>`。component 不套用這條；沒被要求時不要連 `components/` 一起加層。

## Good Example

- 這個例子是好的，因為 7 個 yaml 仍扁平；超過 10 個 yaml 才用下一層區隔不同 groups。

```text
# 7 個 yaml：不開該層
groups/
├── 工位終端.yaml
├── 品檢.yaml
├── 可用量.yaml
├── 報廢審核.yaml
├── 狀態板.yaml
├── 追溯.yaml
└── 欄位設定.yaml

# 超過 10 個 yaml：再開該層
groups/
├── 現場/
│   ├── 工位終端.yaml
│   └── 品檢.yaml
└── 設定/
    └── 欄位設定.yaml
```

## Bad Example

- 這個例子是壞的，因為只有少數 yaml 就先開下一層。

```text
# 只有 7 個 yaml 就開層
groups/
├── 現場/
│   └── 工位終端.yaml
└── 設定/
    └── 欄位設定.yaml
```
