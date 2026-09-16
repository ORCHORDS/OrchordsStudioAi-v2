#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

bash scripts/preflight-local.sh

./gradlew :app:compileDebugKotlin

./gradlew :app:testDebugUnitTest \
  --tests 'com.orchords.orchordsai.data.safety.AiContentReportProjectionTest' \
  --tests 'com.orchords.orchordsai.data.safety.AiContentReportUiPolicyTest'

./gradlew :app:lintDebug

echo 'AI content report local verification passed.'
