# Claude Shim

A native-friendly Java wrapper for the `claude` CLI on Linux, macOS, and Windows.

> Quickstart: see [`INSTALL.md`](INSTALL.md) for the fastest path to building the native shim and putting it first on `PATH`.

## Description

Features:

- PATH shadowing (no renaming required)
- HTTP / HTTPS proxy support
- Optional telemetry disabling
- GraalVM native binary support
- Automatic GraalVM JDK 25 provisioning for native builds

## Build

Linux/macOS:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

Configuration cache is enabled by default, so repeated Gradle runs should be faster after the first invocation.

## Run tests

Linux/macOS:

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

## Build native binary

On the first `nativeCompile`, Gradle automatically provisions a GraalVM JDK 25 toolchain with `native-image` support when network access is available.

Linux/macOS:

```bash
./gradlew nativeCompile
```

Windows:

```powershell
.\gradlew.bat nativeCompile
```

If automatic provisioning is unavailable in your environment, you can still point Gradle at an existing GraalVM installation with `GRAALVM_HOME`.

Binary output:

- Linux/macOS: `build/native/nativeCompile/claude`
- Windows: `build/native/nativeCompile/claude.exe`

## Config file

Default config location:

- Linux: `~/.config/claude-shim/config.properties` (or `$XDG_CONFIG_HOME/claude-shim/config.properties`)
- macOS: `~/Library/Application Support/claude-shim/config.properties`
- Windows: `%APPDATA%\claude-shim\config.properties` (Roaming AppData)

Example:

```properties
https_proxy=http://127.0.0.1:8080
disable_telemetry=true
```

Supported keys:

- `https_proxy`
- `http_proxy`
- `no_proxy`
- `disable_telemetry`

## Troubleshooting

### Native toolchain download fails

If `nativeCompile` cannot download GraalVM automatically, check that the machine has network access to the toolchain repositories Gradle uses. As a fallback, point Gradle at an existing GraalVM installation.

Linux/macOS:

```bash
export GRAALVM_HOME=/path/to/graalvm
./gradlew nativeCompile
```

Windows PowerShell:

```powershell
$env:GRAALVM_HOME = "C:\path\to\graalvm"
.\gradlew.bat nativeCompile
```

### Native build works poorly behind restrictive filesystem or security policies

This project repairs broken `native-image` launchers in auto-provisioned GraalVM toolchains before `nativeCompile` runs. If your environment wipes or blocks files under the Gradle JDK cache (`~/.gradle/jdks` on Unix-like systems), remove the affected downloaded JDK and run the build again so Gradle can provision it cleanly.

### First native build is slow

The first `nativeCompile` run downloads GraalVM and builds the binary from scratch, so it is expected to take noticeably longer than later runs.

### Configuration cache is not reused

Configuration cache entries are invalidated when files like `build.gradle`, `settings.gradle`, or `gradle.properties` change. That is normal. The next run should reuse the cache again if the build configuration stays unchanged.
