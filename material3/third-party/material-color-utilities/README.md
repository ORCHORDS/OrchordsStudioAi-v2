# material-color-utilities (vendored)

Vendored copy of Material Color Utilities for use inside the `:material3` Gradle
module. This avoids the historical Git submodule dependency, which made a plain
`git clone` of `ORCHORDS/OrchordsStudioAi` produce an unbuildable tree.

## Upstream

- **Repository:** <https://github.com/material-foundation/material-color-utilities>
- **Pinned commit:** `5b3618b16fdc3825e21d5679bafd144662088ea1`
- **License:** Apache License 2.0 (see `LICENSE` next to this file)
- **Kotlin sources only.** The Java / Dart / TypeScript / C++ / Swift variants shipped upstream
  are not vendored; only the Kotlin tree is included because the `:material3` module
  compiles against the `dynamiccolor`, `hct`, `palettes`, `quantize`, `scheme`,
  `score`, `temperature`, `utils`, `blend`, `contrast`, `dislike` packages.

## Where the vendored sources live

The Kotlin sources are checked in under
`material3/src/main/java/<package>/<File>.kt` so they compile as part of the
`:material3` Android library module without any extra Gradle wiring.

## Packages vendored

- `dynamiccolor` — `ColorSpec.kt`, `ColorSpec2021.kt`, `ColorSpec2025.kt`,
  `ColorSpec2026.kt`, `ColorSpecs.kt`, `ContrastCurve.kt`, `DynamicColor.kt`,
  `DynamicScheme.kt`, `MaterialDynamicColors.kt`, `ToneDeltaPair.kt`, `Variant.kt`
- `hct` — `Cam16.kt`, `Hct.kt`, `HctSolver.kt`, `ViewingConditions.kt`
- `palettes` — `CorePalettes.kt`, `TonalPalette.kt`
- `quantize` — `PointProvider.kt`, `PointProviderLab.kt`, `Quantizer.kt`,
  `QuantizerCelebi.kt`, `QuantizerMap.kt`, `QuantizerResult.kt`,
  `QuantizerWsmeans.kt`, `QuantizerWu.kt`
- `scheme` — `SchemeCmf.kt`, `SchemeContent.kt`, `SchemeExpressive.kt`,
  `SchemeFidelity.kt`, `SchemeFruitSalad.kt`, `SchemeMonochrome.kt`,
  `SchemeNeutral.kt`, `SchemeRainbow.kt`, `SchemeTonalSpot.kt`, `SchemeVibrant.kt`
- `score` — `Score.kt`
- `temperature` — `TemperatureCache.kt`
- `utils` — `ColorUtils.kt`, `MathUtils.kt`, `StringUtils.kt`
- `blend` — `Blend.kt`
- `contrast` — `Contrast.kt`
- `dislike` — `DislikeAnalyzer.kt`

## Modifications

None. The vendored Kotlin sources are byte-identical to upstream commit
`5b3618b16fdc3825e21d5679bafd144662088ea1`, modulo the path on disk. Each `.kt`
file still declares the upstream `package …;` directive and the upstream
import graph is preserved. The `:material3` Android library module's
namespace (`com.orchords.material3`) controls R-class generation only and does
not affect these top-level Kotlin packages.

To update to a newer upstream release, re-vendor from a fresh clone of upstream
at the desired SHA, replace the package directories under
`material3/src/main/java/`, and update the "Pinned commit" line in this file.

## Apache-2.0 attribution

Apache License 2.0 requires the LICENSE text to accompany the work and that
upstream copyright / NOTICE files (if any) be preserved. The full LICENSE is
at `LICENSE` next to this file; upstream does not ship a separate NOTICE file.
