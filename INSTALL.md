# Install Claude Shim

This guide shows how to build the native `claude` shim and place it first on your `PATH` so your shell runs the compiled shim before the real Claude CLI.

## Prerequisites

- Linux or another POSIX-like environment
- A working Gradle wrapper from this repository
- Network access on the first native build so Gradle can download a GraalVM JDK 25 toolchain automatically
- The real `claude` CLI already installed somewhere else on your `PATH`

## 1. Compile the native image

From the repository root, build the native binary:

```bash
./gradlew clean test nativeCompile
```

The compiled binary is written to:

```text
build/native/nativeCompile/claude
```

## 2. Put the compiled shim first on `PATH`

The shim works by being found before the real Claude CLI. When you run `claude`, the shim starts first, then it searches the rest of `PATH` for the next `claude` executable and forwards the call to it.

That means:

- the compiled shim directory must be **before** the real Claude CLI on `PATH`
- the real Claude CLI must still remain on `PATH` somewhere later

For `zsh`, add the build output directory to the front of `PATH`:

```bash
export PATH="/path/to/claude-shim/build/native/nativeCompile:$PATH"
```

To make that persistent for your user, add the same line to `~/.zshrc`, then reload your shell:

```bash
echo 'export PATH="/path/to/claude-shim/build/native/nativeCompile:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

If you prefer not to reference the repository build directory directly, you can copy the binary into a personal bin directory and prepend that directory instead:

```bash
mkdir -p ~/.local/bin
cp /path/to/claude-shim/build/native/nativeCompile/claude ~/.local/bin/claude
chmod +x ~/.local/bin/claude
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

## 3. Verify the shim is found first

Check which `claude` your shell resolves first:

```bash
which claude
```

It should point to either:

```text
/path/to/claude-shim/build/native/nativeCompile/claude
```

or your copied location such as:

```text
~/.local/bin/claude
```

## 4. Optional configuration

If you want proxy or telemetry settings, create:

```text
~/.config/claude-shim/config.properties
```

Example:

```properties
https_proxy=http://127.0.0.1:8080
disable_telemetry=true
```

## Troubleshooting

### `claude` still resolves to the original CLI

Your shim directory is not first on `PATH`. Check the current order:

```bash
print -l ${(s/:/)PATH}
```

Move the shim directory earlier in `~/.zshrc`, reload the shell, and run `which claude` again.

### The shim cannot find the real Claude CLI

Make sure the original Claude installation is still present later on `PATH`. The shim intentionally skips its own executable and then searches for the next `claude` entry.

### Native build is slow the first time

The first `nativeCompile` run may download GraalVM and build the binary from scratch, so it will be noticeably slower than later runs.
