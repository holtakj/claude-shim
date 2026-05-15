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

## Environments

Environments let you switch between different configurations (e.g. separate API keys for different customers) at startup.

### Setup

Create one `.properties` file per environment in the `envs/` subdirectory of your config location:

- Linux: `~/.config/claude-shim/envs/`
- macOS: `~/Library/Application Support/claude-shim/envs/`
- Windows: `%APPDATA%\claude-shim\envs\`

Example `~/.config/claude-shim/envs/customer-a.properties`:

```properties
env.ANTHROPIC_API_KEY=sk-ant-api-key-for-customer-a
https_proxy=http://customer-a-proxy:8080
disable_telemetry=true
```

Example `~/.config/claude-shim/envs/customer-b.properties`:

```properties
env.ANTHROPIC_API_KEY=sk-ant-api-key-for-customer-b
```

Use the `env.` prefix to set arbitrary environment variables. The standard config keys (`https_proxy`, `http_proxy`, `no_proxy`, `disable_telemetry`) work without a prefix and override the global config.

### Usage

Select an environment directly:

```bash
claude --env customer-a
```

If multiple environments exist and no `--env` flag is given, the shim prompts interactively:

```
Select environment:
  [1] customer-a
  [2] customer-b
Choice:
```

If only one environment exists, it is selected automatically. If no environments exist (no `envs/` directory or no files in it), the shim proceeds without environment selection.

### Per-directory environments

You can bind directories to environments in the global `config.properties` so that running `claude` inside a configured path (or any of its subdirectories) automatically selects the matching environment — no `--env` flag and no interactive prompt.

Add one `paths.<env-name>=...` entry per environment. The value is a comma-separated list of absolute paths; `~` is expanded to the user's home directory.

```properties
# ~/.config/claude-shim/config.properties
paths.customer-a=~/work/customer-a,/srv/customer-a
paths.customer-b=~/work/customer-b
```

Resolution order when no `--env` flag is given:

1. **Path match** — the current working directory is checked against all configured paths. If a match is found, that environment is auto-selected. If multiple paths match (e.g. nested mappings), the **longest path wins**, so more specific directories override more general ones.
2. **Single environment** — if exactly one environment file exists, it is selected automatically.
3. **Interactive prompt** — the user is asked to choose.

The `--env` flag always overrides path matching. If a path mapping points to an environment name that does not have a corresponding `.properties` file, a warning is logged and the shim falls back to the normal selection.

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
- `paths.<env-name>` — comma-separated list of directories that should auto-select `<env-name>` (see [Per-directory environments](#per-directory-environments))

### Environment properties files

Environment files support the same keys as above, plus arbitrary environment variables using the `env.` prefix:

```properties
https_proxy=http://customer-proxy:8080
disable_telemetry=true
env.ANTHROPIC_API_KEY=sk-ant-api-key-for-this-customer
env.SOME_OTHER_VAR=value
```

Keys without the `env.` prefix override the global config for that session. Keys with `env.` are set as environment variables on the forwarded `claude` process.

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

## License

This project is licensed under the Apache License 2.0. See [`LICENSE`](LICENSE).
