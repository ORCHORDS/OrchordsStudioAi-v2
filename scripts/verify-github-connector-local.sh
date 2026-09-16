#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

bash scripts/preflight-local.sh

./gradlew :app:compileDebugKotlin

./gradlew :app:testDebugUnitTest \
  --tests 'com.orchords.orchordsai.security.McpSecretCodecTest' \
  --tests 'com.orchords.orchordsai.security.PreferenceStoreV7MigrationTest' \
  --tests 'com.orchords.orchordsai.data.ai.mcp.GitHubMcpPresetTest' \
  --tests 'com.orchords.orchordsai.data.ai.mcp.GitHubMcpSafetyTest' \
  --tests 'com.orchords.orchordsai.data.ai.mcp.GitHubConnectorStateTest' \
  --tests 'com.orchords.orchordsai.data.ai.mcp.McpAuthFailureClassifierTest' \
  --tests 'com.orchords.orchordsai.data.ai.mcp.McpDiagnosticSanitizerTest' \
  --tests 'com.orchords.orchordsai.data.sync.SettingsBackupProjectionTest'

./gradlew :app:lintDebug

echo 'GitHub connector local verification passed.'
