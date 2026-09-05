param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliArgs
)

$RootDir = $PSScriptRoot
if (-not $RootDir) {
    $RootDir = (Get-Location).Path
}
$BootJar = Join-Path $RootDir "oryxos-boot\target\oryxos-boot-0.1.0-SNAPSHOT.jar"

$JavaCmd = "java"
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $JavaCmd = "$env:JAVA_HOME\bin\java.exe"
}

# ============================================================
# 加载本地环境配置 (.env / .oryxos/.env)
# 仅供本地开发与测试使用，已被 .gitignore 忽略，不会随代码提交
# ============================================================
$EnvCandidates = @(
    Join-Path $RootDir ".env",
    Join-Path $RootDir ".oryxos\.env"
)
$LoadedEnvVars = @{}
foreach ($envPath in $EnvCandidates) {
    if (Test-Path $envPath) {
        Get-Content $envPath -Encoding UTF8 | ForEach-Object {
            $s = $_.Trim()
            if ($s -and -not $s.StartsWith("#") -and $s.Contains("=")) {
                $idx = $s.IndexOf("=")
                $varName = $s.Substring(0, $idx).Trim()
                $varVal = $s.Substring($idx + 1).Trim().Trim('"').Trim("'")
                $currentVal = [System.Environment]::GetEnvironmentVariable($varName, [System.EnvironmentVariableTarget]::Process)
                if ([string]::IsNullOrWhiteSpace($currentVal)) {
                    [System.Environment]::SetEnvironmentVariable($varName, $varVal, [System.EnvironmentVariableTarget]::Process)
                }
                $LoadedEnvVars[$varName] = $varVal
            }
        }
    }
}

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
foreach ($kv in $LoadedEnvVars.GetEnumerator()) {
    $JvmArgs += "-D$($kv.Key)=$($kv.Value)"
}

if (Test-Path $BootJar) {
    & $JavaCmd @JvmArgs -cp $BootJar org.springframework.boot.loader.launch.PropertiesLauncher @CliArgs
} else {
    $argString = $CliArgs -join ' '
    $jvmArgList = @("-Doryxos.console.charset=$ConsoleCharset")
    foreach ($kv in $LoadedEnvVars.GetEnumerator()) {
        $jvmArgList += "-D$($kv.Key)=$($kv.Value)"
    }
    $jvmArgString = $jvmArgList -join ' '
    & mvn spring-boot:run -pl oryxos-boot "-Dspring-boot.run.main-class=com.oryxos.cli.OryxOsCli" "-Dspring-boot.run.jvmArguments=$jvmArgString" "-Dspring-boot.run.arguments=$argString"
}
