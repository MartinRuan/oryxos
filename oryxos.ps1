param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliArgs
)

$RootDir = $PSScriptRoot
$BootJar = Join-Path $RootDir "oryxos-boot\target\oryxos-boot-0.1.0-SNAPSHOT.jar"

$JavaCmd = "java"
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $JavaCmd = "$env:JAVA_HOME\bin\java.exe"
}

# ============================================================
# ============================================================
# 关键修复：Windows 控制台编码配置 (GBK / 代码页 936)
# ============================================================
# Windows PowerShell / CMD 下，中文输入法（IME）上屏默认依赖 GBK (cp936)。
# 若强制切换为 cp65001 (UTF-8)，Win32 控制台输入缓冲区会导致中文丢失或产生乱码。
#
# 解决方案：
# 1. 默认设置控制台代码页为 936 (GBK)，让中文输入法稳定上屏
# 2. 通过 -Doryxos.console.charset=GBK 让 CliChannel 以 GBK 解码控制台输入
# 3. Java 内部统一为 Unicode，HTTP 传输层自动使用 UTF-8 与 LLM API 交互
# ============================================================
if ($env:ORYXOS_CONSOLE_CHARSET) {
    $ConsoleCharset = $env:ORYXOS_CONSOLE_CHARSET
} else {
    try {
        chcp 936 | Out-Null
    } catch {}
    $ConsoleCharset = "GBK"
}

$JvmArgs = @(
    "-Dloader.main=com.oryxos.cli.OryxOsCli",
    "-Doryxos.console.charset=$ConsoleCharset"
)

if (Test-Path $BootJar) {
    & $JavaCmd @JvmArgs -cp $BootJar org.springframework.boot.loader.launch.PropertiesLauncher @CliArgs
} else {
    $argString = $CliArgs -join ' '
    $jvmArgString = "-Doryxos.console.charset=$ConsoleCharset"
    & mvn spring-boot:run -pl oryxos-boot "-Dspring-boot.run.main-class=com.oryxos.cli.OryxOsCli" "-Dspring-boot.run.jvmArguments=$jvmArgString" "-Dspring-boot.run.arguments=$argString"
}
