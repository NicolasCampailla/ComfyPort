package com.comfyport.network

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SshExecutionResult(
    val success: Boolean,
    val output: String,
    val errorMessage: String? = null
)

object SshClient {

    /**
     * Connects to a remote host via OpenSSH and executes the specified command.
     * Designed for starting remote processes (such as ComfyUI).
     */
    suspend fun executeCommand(
        host: String,
        port: Int = 22,
        user: String,
        password: String,
        command: String,
        connectTimeoutMs: Int = 10000,
        waitOutputMs: Int = 6000
    ): SshExecutionResult = withContext(Dispatchers.IO) {
        val trimmedHost = host.trim()
        val trimmedUser = user.trim()
        val trimmedCommand = command.trim()

        if (trimmedHost.isBlank()) {
            return@withContext SshExecutionResult(
                success = false,
                output = "",
                errorMessage = "SSH Host/IP address cannot be empty."
            )
        }
        if (trimmedUser.isBlank()) {
            return@withContext SshExecutionResult(
                success = false,
                output = "",
                errorMessage = "SSH Username cannot be empty."
            )
        }
        if (trimmedCommand.isBlank()) {
            return@withContext SshExecutionResult(
                success = false,
                output = "",
                errorMessage = "SSH Start command cannot be empty."
            )
        }

        var session: Session? = null
        var channel: ChannelExec? = null

        try {
            val jsch = JSch()
            val effectivePort = if (port in 1..65535) port else 22

            session = jsch.getSession(trimmedUser, trimmedHost, effectivePort).apply {
                setPassword(password)
                val config = Properties().apply {
                    put("StrictHostKeyChecking", "no")
                    put("PreferredAuthentications", "password,keyboard-interactive")
                }
                setConfig(config)
                timeout = connectTimeoutMs
                connect(connectTimeoutMs)
            }

            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(trimmedCommand)

            val stdOutStream = ByteArrayOutputStream()
            val stdErrStream = ByteArrayOutputStream()
            channel.outputStream = stdOutStream
            channel.setErrStream(stdErrStream)

            channel.connect(connectTimeoutMs)

            // Collect output up to waitOutputMs or until the channel closes
            val startWait = System.currentTimeMillis()
            while (!channel.isClosed && System.currentTimeMillis() - startWait < waitOutputMs) {
                delay(100)
            }

            val exitStatus = channel.exitStatus
            val stdOut = stdOutStream.toString("UTF-8").trim()
            val stdErr = stdErrStream.toString("UTF-8").trim()

            val combined = when {
                stdOut.isNotBlank() && stdErr.isNotBlank() -> "$stdOut\n$stdErr"
                stdOut.isNotBlank() -> stdOut
                else -> stdErr
            }

            // If exit status is 0 or -1 (detached/running background task), it succeeded
            val success = exitStatus == 0 || exitStatus == -1

            val errorMsg = if (!success) {
                when {
                    stdErr.isNotBlank() -> stdErr
                    stdOut.isNotBlank() -> stdOut
                    else -> "Command failed with exit code $exitStatus"
                }
            } else null

            SshExecutionResult(
                success = success,
                output = if (combined.isNotBlank()) combined else "Command dispatched successfully via SSH.",
                errorMessage = errorMsg
            )
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "SSH connection error"
            SshExecutionResult(
                success = false,
                output = "",
                errorMessage = msg
            )
        } finally {
            try {
                channel?.disconnect()
            } catch (_: Exception) {}
            try {
                session?.disconnect()
            } catch (_: Exception) {}
        }
    }

    /**
     * Prepares a start command for execution over SSH.
     * If the command points to a Windows batch file (.bat/.cmd) with a directory path,
     * it wraps it in PowerShell Invoke-CimMethod Win32_Process Create with the correct
     * working directory. This completely detaches the process from OpenSSH's job object
     * so that ComfyUI continues running after the SSH session disconnects.
     */
    fun prepareLaunchCommand(rawCommand: String, username: String = ""): String {
        val trimmed = rawCommand.trim()
        if (trimmed.isBlank()) return trimmed

        // If it's already a custom command (PowerShell, nohup, etc.), keep as-is
        if (trimmed.startsWith("powershell", ignoreCase = true) ||
            trimmed.startsWith("nohup", ignoreCase = true) ||
            trimmed.startsWith("Invoke-", ignoreCase = true)
        ) {
            return trimmed
        }

        // Clean out legacy "start \"\"", "cmd /c start /b \"\"", quotes
        var cleaned = trimmed
        if (cleaned.startsWith("cmd /c start /b \"\"", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("cmd /c start /b \"\"").trim()
        } else if (cleaned.startsWith("cmd /c start /b", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("cmd /c start /b").trim()
        } else if (cleaned.startsWith("cmd /c", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("cmd /c").trim()
        } else if (cleaned.startsWith("start \"\"", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("start \"\"").trim()
        } else if (cleaned.startsWith("start /b \"\"", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("start /b \"\"").trim()
        } else if (cleaned.startsWith("start /b", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("start /b").trim()
        } else if (cleaned.startsWith("start", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("start").trim()
        }

        cleaned = cleaned.trim().removeSurrounding("\"").trim()

        // Replace %USERNAME% token if provided
        if (username.isNotBlank() && cleaned.contains("%USERNAME%", ignoreCase = true)) {
            cleaned = cleaned.replace("%USERNAME%", username, ignoreCase = true)
        }

        val normalized = cleaned.replace("/", "\\")
        val isBatch = normalized.endsWith(".bat", ignoreCase = true) || normalized.endsWith(".cmd", ignoreCase = true)
        val hasPath = normalized.contains("\\")

        if (isBatch || hasPath) {
            val parentDir = normalized.substringBeforeLast('\\', "")
            val fileName = normalized.substringAfterLast('\\')

            if (parentDir.isNotBlank()) {
                // User provided a full or relative path to the batch script
                // We use Invoke-CimMethod to launch via WMI with CurrentDirectory set,
                // which decouples the process from the SSH session so it survives disconnect.
                return "powershell -NoProfile -ExecutionPolicy Bypass -Command \"" +
                        "\$p = '$normalized'; " +
                        "if (-not (Test-Path \$p)) { Write-Error ('Batch file not found: ' + \$p); exit 1 }; " +
                        "\$dir = Split-Path -Parent \$p; " +
                        "\$file = Split-Path -Leaf \$p; " +
                        "\$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{" +
                        "CommandLine=('cmd.exe /c ' + \$file); " +
                        "CurrentDirectory=\$dir" +
                        "}; " +
                        "if (\$r.ReturnValue -eq 0) { " +
                        "Write-Output ('ComfyUI background process started successfully (PID: ' + \$r.ProcessId + ') in ' + \$dir); " +
                        "} else { " +
                        "Write-Error ('WMI process launch failed with error code: ' + \$r.ReturnValue); exit \$r.ReturnValue" +
                        "}\""
            }
        }

        // If the user specified "run_nvidia_gpu.bat" without a path (or legacy default)
        if (cleaned.equals("run_nvidia_gpu.bat", ignoreCase = true) ||
            cleaned.equals("run_cpu.bat", ignoreCase = true) ||
            cleaned.isBlank()
        ) {
            val targetName = if (cleaned.isNotBlank()) cleaned else "run_nvidia_gpu.bat"
            val userPrefix = if (username.isNotBlank()) username else "\$env:USERNAME"
            return "powershell -NoProfile -ExecutionPolicy Bypass -Command \"" +
                    "\$candidates = @(" +
                    "'C:\\Users\\$userPrefix\\Desktop\\ComfyUI_windows_portable\\$targetName', " +
                    "'C:\\ComfyUI_windows_portable\\$targetName', " +
                    "'C:\\Users\\$userPrefix\\ComfyUI\\$targetName', " +
                    "'C:\\Users\\$userPrefix\\Desktop\\ComfyUI\\$targetName'" +
                    "); " +
                    "\$found = \$candidates | Where-Object { Test-Path \$_ } | Select-Object -First 1; " +
                    "if (\$null -eq \$found) { " +
                    "Write-Error 'Could not find $targetName in standard paths. Please enter the full path in Settings (e.g. C:\\path\\to\\$targetName)'; exit 1 " +
                    "}; " +
                    "\$dir = Split-Path -Parent \$found; " +
                    "\$file = Split-Path -Leaf \$found; " +
                    "\$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{" +
                    "CommandLine=('cmd.exe /c ' + \$file); " +
                    "CurrentDirectory=\$dir" +
                    "}; " +
                    "if (\$r.ReturnValue -eq 0) { " +
                    "Write-Output ('ComfyUI background process started successfully (PID: ' + \$r.ProcessId + ') in ' + \$dir); " +
                    "} else { " +
                    "Write-Error ('WMI process launch failed with error code: ' + \$r.ReturnValue); exit \$r.ReturnValue" +
                    "}\""
        }

        return trimmed
    }

    /**
     * Builds a command to tail ComfyUI's log file.
     * On Windows, it locates comfyui.log using candidates and runs Get-Content -Wait -Tail 40.
     * On Linux/Mac, it runs tail -f -n 40.
     */
    fun buildLogTailCommand(startCommand: String, username: String = ""): String {
        var trimmed = startCommand.trim().removeSurrounding("\"").removeSurrounding("'")
        if (trimmed.startsWith("start ", ignoreCase = true)) {
            trimmed = trimmed.substring(6).trim()
            if (trimmed.startsWith("\"\"")) {
                trimmed = trimmed.substring(2).trim()
            }
            trimmed = trimmed.removeSurrounding("\"").removeSurrounding("'")
        }

        val userPrefix = if (username.isNotBlank()) username else "\$env:USERNAME"
        trimmed = trimmed.replace("%USERNAME%", userPrefix, ignoreCase = true)

        // Linux/Mac fallback
        if (trimmed.startsWith("nohup", ignoreCase = true) || trimmed.contains("python3", ignoreCase = true)) {
            return "tail -f -n 40 comfyui.log 2>/dev/null || tail -f -n 40 ComfyUI/user/comfyui.log"
        }

        // On Windows: search for comfyui.log based on start command path or standard portable directories
        val baseDir = if (trimmed.contains("\\")) {
            trimmed.replace("/", "\\").substringBeforeLast('\\').trim().removeSurrounding("\"")
        } else {
            "C:\\Users\\$userPrefix\\Desktop\\ComfyUI_windows_portable"
        }

        return "powershell -NoProfile -ExecutionPolicy Bypass -Command \"" +
                "\$candidates = @(" +
                "'$baseDir\\ComfyUI\\user\\comfyui.log', " +
                "'$baseDir\\user\\comfyui.log', " +
                "'$baseDir\\comfyui.log', " +
                "'C:\\Users\\$userPrefix\\Desktop\\ComfyUI_windows_portable\\ComfyUI\\user\\comfyui.log', " +
                "'C:\\ComfyUI_windows_portable\\ComfyUI\\user\\comfyui.log'" +
                "); " +
                "\$logFile = \$candidates | Where-Object { Test-Path \$_ } | Select-Object -First 1; " +
                "if (\$null -ne \$logFile) { " +
                "Write-Output ('[Attached to ' + \$logFile + ']'); " +
                "Get-Content -Path \$logFile -Tail 40 -Wait " +
                "} else { " +
                "Write-Output 'Waiting for ComfyUI log file to be created...' " +
                "}\""
    }

    /**
     * Connects via SSH and streams command output (stdout and stderr) line-by-line in real time.
     * Keeps running until the remote channel closes or the caller cancels the coroutine.
     */
    suspend fun streamCommandOutput(
        host: String,
        port: Int = 22,
        user: String,
        password: String,
        command: String,
        connectTimeoutMs: Int = 10000,
        onLine: (String, Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        val trimmedHost = host.trim()
        val trimmedUser = user.trim()
        val trimmedCommand = command.trim()

        if (trimmedHost.isBlank() || trimmedUser.isBlank() || trimmedCommand.isBlank()) {
            return@withContext
        }

        var session: Session? = null
        var channel: ChannelExec? = null

        try {
            val jsch = JSch()
            val effectivePort = if (port in 1..65535) port else 22

            session = jsch.getSession(trimmedUser, trimmedHost, effectivePort).apply {
                setPassword(password)
                val config = Properties().apply {
                    put("StrictHostKeyChecking", "no")
                    put("PreferredAuthentications", "password,keyboard-interactive")
                }
                setConfig(config)
                timeout = connectTimeoutMs
                connect(connectTimeoutMs)
            }

            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(trimmedCommand)

            val stdOut = channel.inputStream
            val stdErr = channel.errStream

            channel.connect(connectTimeoutMs)

            val outReader = BufferedReader(InputStreamReader(stdOut, StandardCharsets.UTF_8))
            val errReader = BufferedReader(InputStreamReader(stdErr, StandardCharsets.UTF_8))

            val jobOut = launch {
                try {
                    while (isActive) {
                        val line = outReader.readLine() ?: break
                        onLine(line, false)
                    }
                } catch (_: Exception) {}
            }

            val jobErr = launch {
                try {
                    while (isActive) {
                        val line = errReader.readLine() ?: break
                        onLine(line, true)
                    }
                } catch (_: Exception) {}
            }

            while (isActive && !channel.isClosed) {
                delay(150)
            }

            jobOut.cancel()
            jobErr.cancel()
        } catch (e: Exception) {
            onLine(e.localizedMessage ?: e.message ?: "SSH streaming error", true)
        } finally {
            try { channel?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
        }
    }
}
