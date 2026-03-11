# Install Claude Shim

This guide shows how to build the native `claude` shim and place it first on your `PATH` so your shell runs the compiled shim before the real Claude CLI.

## Prerequisites

- A working Gradle wrapper from this repository
- Network access on the first native build so Gradle can download a GraalVM JDK 25 toolchain automatically
- The real `claude` CLI already installed somewhere else on your `PATH`

## 1. Compile the native image

From the repository root, build the native binary.

Linux/macOS:

```bash
./gradlew clean test nativeCompile
```

Windows PowerShell:

```powershell
.\gradlew.bat clean test nativeCompile
```

The compiled binary is written to:

- Linux/macOS: `build/native/nativeCompile/claude`
- Windows: `build/native/nativeCompile/claude.exe`

## 2. Put the compiled shim first on `PATH`

The shim works by being found before the real Claude CLI. When you run `claude`, the shim starts first, then it searches the rest of `PATH` for the next `claude` executable and forwards the call to it.

That means:

- the compiled shim directory must be **before** the real Claude CLI on `PATH`
- the real Claude CLI must still remain on `PATH` somewhere later

### Linux/macOS (`zsh`)

Add the build output directory to the front of `PATH`:

```bash
export PATH="/path/to/claude-shim/build/native/nativeCompile:$PATH"
```

Persist it in `~/.zshrc`:

```bash
echo 'export PATH="/path/to/claude-shim/build/native/nativeCompile:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Optional copy-based setup:

```bash
mkdir -p ~/.local/bin
cp /path/to/claude-shim/build/native/nativeCompile/claude ~/.local/bin/claude
chmod +x ~/.local/bin/claude
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Windows (PowerShell)

Add the build output directory for the current session:

```powershell
$env:PATH = "C:\path\to\claude-shim\build\native\nativeCompile;$env:PATH"
```

Persist it for the current user:

```powershell
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
[Environment]::SetEnvironmentVariable("Path", "C:\path\to\claude-shim\build\native\nativeCompile;" + $userPath, "User")
```

After updating user-level `Path`, open a new terminal.

## 3. Verify the shim is found first

Linux/macOS:

```bash
which claude
```

Windows PowerShell:

```powershell
Get-Command claude
```

It should point to your shim location first.

## 4. Optional configuration

If you want proxy or telemetry settings, create `config.properties` in the default OS-specific config location:

- Linux: `~/.config/claude-shim/config.properties` (or `$XDG_CONFIG_HOME/claude-shim/config.properties`)
- macOS: `~/Library/Application Support/claude-shim/config.properties`
- Windows: `%APPDATA%\claude-shim\config.properties`

Example:

```properties
https_proxy=http://127.0.0.1:8080
disable_telemetry=true
```

## Troubleshooting

### `claude` still resolves to the original CLI

Your shim directory is not first on `PATH`. Move the shim directory earlier, open a new shell/session, and verify command resolution again.

### The shim cannot find the real Claude CLI

Make sure the original Claude installation is still present later on `PATH`. The shim intentionally skips its own executable and then searches for the next `claude` entry.

### Native build is slow the first time

The first `nativeCompile` run may download GraalVM and build the binary from scratch, so it will be noticeably slower than later runs.
