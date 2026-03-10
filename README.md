# Claude Shim

A native-friendly Java wrapper for the `claude` CLI.

Features:

- PATH shadowing (no renaming required)
- YAML configuration
- HTTP / HTTPS / SOCKS proxy support
- Optional telemetry disabling
- Structured JSON logging
- Plugin hooks
- GraalVM native binary support
- Automatic GraalVM JDK 25 provisioning for native builds
- Gradle configuration cache enabled by default
- Debug mode (`--shim-debug`)

## Build

```bash
./gradlew build
```

Configuration cache is enabled by default, so repeated Gradle runs should be faster after the first invocation.

## Run tests

```bash
./gradlew test
```

## Build native binary

On the first `nativeCompile`, Gradle automatically provisions a GraalVM JDK 25 toolchain with `native-image` support when network access is available.

```bash
./gradlew nativeCompile
```

If automatic provisioning is unavailable in your environment, you can still point Gradle at an existing GraalVM installation with `GRAALVM_HOME`.

Binary output:

```
build/native/nativeCompile/claude
```

## Config file

Create:

```
~/.config/claude-shim/config.yaml
```

Example:

```yaml
https_proxy: http://127.0.0.1:8080
disable_telemetry: true
log_file: ~/.claude-shim.log
```

## Debug mode

```
claude --shim-debug
```

Shows detected binary and environment configuration.

## Troubleshooting

### Native toolchain download fails

If `./gradlew nativeCompile` cannot download GraalVM automatically, check that the machine has network access to the toolchain repositories Gradle uses. As a fallback, point Gradle at an existing GraalVM installation:

```bash
export GRAALVM_HOME=/path/to/graalvm
./gradlew nativeCompile
```

### Native build works poorly behind restrictive filesystem or security policies

This project repairs broken `native-image` launchers in auto-provisioned GraalVM toolchains before `nativeCompile` runs. If your environment wipes or blocks files under `~/.gradle/jdks`, remove the affected downloaded JDK and run the build again so Gradle can provision it cleanly.

### First native build is slow

The first `nativeCompile` run downloads GraalVM and builds the binary from scratch, so it is expected to take noticeably longer than later runs.

### Configuration cache is not reused

Configuration cache entries are invalidated when files like `build.gradle`, `settings.gradle`, or `gradle.properties` change. That is normal. The next run should reuse the cache again if the build configuration stays unchanged.
