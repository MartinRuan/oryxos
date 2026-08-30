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
# 关键修复：强制 Win32 控制台回 GBK (Code Page 936)
# ============================================================
# PowerShell 7 默认将控制台代码页改为 65001 (UTF-8)，
# 但 Win32 ReadFile() 在 cp65001 下对多字节 UTF-8 有已知 bug：
#   - 可能返回 0 字节 (EOF)，导致 JVM 的 System.in.read() 立刻返回 -1
#   - 中文输入法（IME）上屏的字符丢失或截断
#
# 解决方案：切回系统原生 GBK (cp936)。
# GBK 下 IME 输入稳定可靠，JVM 用 GBK 解码后内部是 Unicode，
# HTTP 传输层（RestClient）自动用 UTF-8 发给 LLM API。
# ============================================================
chcp 936 | Out-Null

$JvmArgs = @(
    "-Dloader.main=com.oryxos.cli.OryxOsCli",
    "-Doryxos.console.charset=GBK"
)

if (Test-Path $BootJar) {
    & $JavaCmd @JvmArgs -cp $BootJar org.springframework.boot.loader.launch.PropertiesLauncher @CliArgs
} else {
    $argString = $CliArgs -join ' '
    & mvn spring-boot:run -pl oryxos-boot "-Dspring-boot.run.main-class=com.oryxos.cli.OryxOsCli" "-Dspring-boot.run.arguments=$argString"
}
