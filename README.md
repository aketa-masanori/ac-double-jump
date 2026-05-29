# AcDoubleJump

Paper 1.21.11 向けの軽量ダブルジャンププラグインです。  
ワールド名ベースの `config.yml` 設定と permission 判定で制御します。

## Build

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

## Permission behavior

- `acdoublejump.use`: グローバル利用可否（default true）
- `acdoublejump.use.<worldname>`: ワールド個別上書き（明示設定時のみ有効）
- `acdoublejump.reload`: リロード実行権限（default op）

## Future Paper upgrade (26.1 など)

1. `gradle.properties` の `paperApiVersion` を更新
2. 必要なら `src/main/resources/plugin.yml` の `api-version` を更新
3. `./gradlew build` を実行
4. サーバーで `join / world-change / cooldown / fall-damage` を確認
