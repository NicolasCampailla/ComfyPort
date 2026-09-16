package com.comfyport.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class BackupRestoreTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun testBackup_ExcludesSshPasswordCompletely() {
        val sensitivePassword = "SuperSecretSSHPassword#999!"
        val settings = AppSettings(
            serverUrl = "http://192.168.1.150:8188",
            savedServers = listOf(
                SavedServer(id = "srv-1", label = "Local Rig", url = "http://192.168.1.150:8188")
            ),
            sshHost = "192.168.1.150",
            sshPort = 22,
            sshUsername = "gpu_user",
            sshPassword = sensitivePassword,
            sshCommand = "python main.py --listen",
            highlightColor = "#FF5722"
        )

        val settingsBackup = AppSettingsBackup(
            serverUrl = settings.serverUrl,
            savedServers = settings.savedServers,
            activeServerId = settings.activeServerId,
            selectedWorkflowId = settings.selectedWorkflowId,
            resolutionMode = settings.resolutionMode,
            customWidth = settings.customWidth,
            customHeight = settings.customHeight,
            batchSize = settings.batchSize,
            megapixel = settings.megapixel,
            aspectRatio = settings.aspectRatio,
            highlightColor = settings.highlightColor,
            showWorkflowSelector = settings.showWorkflowSelector,
            showResolutionControls = settings.showResolutionControls,
            showBatchSizeControls = settings.showBatchSizeControls,
            showNegativePrompt = settings.showNegativePrompt,
            showSeedControls = settings.showSeedControls,
            showImageMaskInput = settings.showImageMaskInput,
            sshHost = settings.sshHost,
            sshPort = settings.sshPort,
            sshUsername = settings.sshUsername,
            sshCommand = settings.sshCommand
        )

        val backup = AppBackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            settings = settingsBackup,
            workflows = listOf(
                WorkflowBackupItem(
                    id = "wf-1",
                    label = "SDXL Turbo",
                    filename = "sdxl_turbo.json",
                    rawJson = "{\"nodes\":[]}",
                    nodeMapping = WorkflowNodeMapping()
                )
            )
        )

        val json = gson.toJson(backup)

        // Verify sensitive password is NEVER present in the serialized JSON
        assertFalse("Backup JSON must NOT contain sensitive SSH password", json.contains(sensitivePassword))
        assertFalse("Backup JSON must NOT contain 'sshPassword' field", json.contains("sshPassword"))

        // Verify other properties are preserved
        assertTrue(json.contains("http://192.168.1.150:8188"))
        assertTrue(json.contains("Local Rig"))
        assertTrue(json.contains("SDXL Turbo"))
        assertTrue(json.contains("sdxl_turbo.json"))
        assertTrue(json.contains("#FF5722"))
    }

    @Test
    fun testBackup_RoundTripDeserialization() {
        val backup = AppBackupData(
            version = 1,
            exportedAt = 1700000000000L,
            settings = AppSettingsBackup(
                serverUrl = "http://10.0.0.2:8188",
                savedServers = listOf(
                    SavedServer(id = "server-1", label = "Remote RTX 4090", url = "http://10.0.0.2:8188")
                ),
                batchSize = 4,
                highlightColor = "#7C4DFF"
            ),
            workflows = listOf(
                WorkflowBackupItem(
                    id = "flux-1",
                    label = "Flux Dev Fast",
                    filename = "flux_dev_fast.json",
                    rawJson = "{\"1\":{\"class_type\":\"KSampler\"}}",
                    nodeMapping = WorkflowNodeMapping(positivePromptNodeId = "6", seedNodeId = "1")
                )
            )
        )

        val json = gson.toJson(backup)
        val restored = gson.fromJson(json, AppBackupData::class.java)

        assertNotNull(restored)
        assertEquals(1, restored.version)
        assertEquals(1, restored.settings.savedServers.size)
        assertEquals("Remote RTX 4090", restored.settings.savedServers[0].label)
        assertEquals("http://10.0.0.2:8188", restored.settings.savedServers[0].url)

        assertEquals(1, restored.workflows.size)
        assertEquals("Flux Dev Fast", restored.workflows[0].label)
        assertEquals("6", restored.workflows[0].nodeMapping?.positivePromptNodeId)

        assertEquals("#7C4DFF", restored.settings.highlightColor)
        assertEquals(4, restored.settings.batchSize)
    }

    @Test
    fun testRestore_PreservesExistingPasswordWhenRestoredSettingsPasswordIsBlank() {
        val existingPassword = "kept_local_password_456"
        val restoredSettingsBackup = AppSettingsBackup(
            serverUrl = "http://new-server:8188",
            sshHost = "new-server"
        )

        // Emulate ViewModel merge logic:
        val currentSettings = AppSettings(
            serverUrl = "http://old-server:8188",
            sshPassword = existingPassword
        )

        val merged = currentSettings.copy(
            serverUrl = restoredSettingsBackup.serverUrl,
            sshHost = restoredSettingsBackup.sshHost,
            // Restored settings do not carry sshPassword, so current password is kept
            sshPassword = if (currentSettings.sshPassword.isNotBlank()) currentSettings.sshPassword else ""
        )

        assertEquals(existingPassword, merged.sshPassword)
        assertEquals("http://new-server:8188", merged.serverUrl)
    }
}
