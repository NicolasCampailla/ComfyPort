package com.comfyport.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshClientTest {

    @Test
    fun testPrepareLaunchCommand_WindowsFullPathBatch() {
        val raw = "C:\\Users\\nicol\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat"
        val cmd = SshClient.prepareLaunchCommand(raw, "nicol")

        assertTrue(cmd.startsWith("powershell -NoProfile -ExecutionPolicy Bypass -Command"))
        assertTrue(cmd.contains("Invoke-CimMethod -ClassName Win32_Process -MethodName Create"))
        assertTrue(cmd.contains("C:\\Users\\nicol\\Desktop\\ComfyUI_windows_portable"))
        assertTrue(cmd.contains("run_nvidia_gpu.bat"))
    }

    @Test
    fun testPrepareLaunchCommand_StripsLeadingStartAndQuotes() {
        val raw = "start \"\" \"C:\\ComfyUI_windows_portable\\run_nvidia_gpu.bat\""
        val cmd = SshClient.prepareLaunchCommand(raw)

        assertTrue(cmd.startsWith("powershell -NoProfile -ExecutionPolicy Bypass -Command"))
        assertTrue(cmd.contains("C:\\ComfyUI_windows_portable"))
        assertTrue(cmd.contains("run_nvidia_gpu.bat"))
    }

    @Test
    fun testPrepareLaunchCommand_ReplacesUsernameToken() {
        val raw = "C:\\Users\\%USERNAME%\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat"
        val cmd = SshClient.prepareLaunchCommand(raw, "alice")

        assertTrue(cmd.contains("C:\\Users\\alice\\Desktop\\ComfyUI_windows_portable"))
    }

    @Test
    fun testPrepareLaunchCommand_RelativeBatchResolvesCandidates() {
        val raw = "run_nvidia_gpu.bat"
        val cmd = SshClient.prepareLaunchCommand(raw, "nicol")

        assertTrue(cmd.startsWith("powershell -NoProfile -ExecutionPolicy Bypass -Command"))
        assertTrue(cmd.contains("\$candidates"))
        assertTrue(cmd.contains("C:\\Users\\nicol\\Desktop\\ComfyUI_windows_portable"))
        assertTrue(cmd.contains("Invoke-CimMethod"))
    }

    @Test
    fun testPrepareLaunchCommand_CustomCommandsPreserved() {
        val nohup = "nohup python3 main.py --listen 0.0.0.0 > /dev/null 2>&1 &"
        assertEquals(nohup, SshClient.prepareLaunchCommand(nohup))

        val ps = "powershell -Command \"Get-Process\""
        assertEquals(ps, SshClient.prepareLaunchCommand(ps))
    }

    @Test
    fun testBuildLogTailCommand_WindowsPortableResolvesLog() {
        val raw = "C:\\Users\\nicol\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat"
        val cmd = SshClient.buildLogTailCommand(raw, "nicol")

        assertTrue(cmd.startsWith("powershell -NoProfile -ExecutionPolicy Bypass -Command"))
        assertTrue(cmd.contains("comfyui.log"))
        assertTrue(cmd.contains("Get-Content -Path \$logFile -Tail 40 -Wait"))
    }

    @Test
    fun testBuildLogTailCommand_LinuxCustomFallsBackToTail() {
        val raw = "python3 main.py --listen 0.0.0.0"
        val cmd = SshClient.buildLogTailCommand(raw, "ubuntu")

        assertTrue(cmd.startsWith("tail -f -n 40"))
        assertTrue(cmd.contains("comfyui.log"))
    }
}
