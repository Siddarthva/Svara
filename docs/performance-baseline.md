# Performance baseline

Recorded before any performance change.

## Build and device

| Item | Value |
|---|---|
| Git commit | `06869b0` (docs: record additions and interpretations made while building the presets) |
| Device | Android Emulator, `sdk_gphone64_x86_64` (Google), AVD "Pixel_10", x86_64, 4 vCPU, 3.8 GB RAM, `ro.kernel.qemu=1` |
| Android version | 17 (API 37) |
| Free storage | 7.3 GB of 10 GB on /data (24 % used) |
| Battery | 100 %, AC powered (emulated battery; it does not drain) |
| Test library | 10,117 audio files, 342 MB, in `/sdcard/Music` (10,000-file synthetic bulk set, a tagged test set with `.lrc` lyrics, and a 24-track set with cover art and timed lyrics) |
| Build for measurements | `release` (R8 optimisation on, resource shrinking on, Baseline Profile bundled, debug-signed so it installs). Macrobenchmarks run the `benchmarkRelease` variant the Baseline Profile plugin creates from it. |

**Validity.** No physical device was available; every number here comes from an emulator. Emulator timings depend on the host machine and its GPU emulation (`swiftshader_indirect`), so treat them as indicative and use them to compare before and after on the same host, not as absolute targets. Battery and thermal numbers cannot be measured on an emulator (its battery is simulated and never drains, and it reports no thermal status); those rows are marked "Not measured".

## Measurements before changes

Filled in by Phase 2 and 3 below, before any app code changes.
