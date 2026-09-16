package com.comfyport.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder

object BackupRestoreManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportBackupData(context: Context, current: AppSettings): String {
        // Security constraint: Explicitly do not memorize or export SSH password in backups!
        val settingsBackup = AppSettingsBackup(
            serverUrl = current.serverUrl,
            savedServers = current.savedServers,
            activeServerId = current.activeServerId,
            selectedWorkflowId = current.selectedWorkflowId,
            resolutionMode = current.resolutionMode,
            customWidth = current.customWidth,
            customHeight = current.customHeight,
            batchSize = current.batchSize,
            megapixel = current.megapixel,
            aspectRatio = current.aspectRatio,
            highlightColor = current.highlightColor,
            showWorkflowSelector = current.showWorkflowSelector,
            showResolutionControls = current.showResolutionControls,
            showBatchSizeControls = current.showBatchSizeControls,
            showNegativePrompt = current.showNegativePrompt,
            showSeedControls = current.showSeedControls,
            showImageMaskInput = current.showImageMaskInput,
            sshHost = current.sshHost,
            sshPort = current.sshPort,
            sshUsername = current.sshUsername,
            sshCommand = current.sshCommand
            // sshPassword is intentionally omitted and blank for security!
        )

        val workflowBackups = current.savedWorkflows.mapNotNull { wf ->
            try {
                val jsonContent = context.openFileInput(wf.filename).bufferedReader().use { it.readText() }
                WorkflowBackupItem(
                    id = wf.id,
                    label = wf.label,
                    filename = wf.filename,
                    rawJson = jsonContent,
                    dateAdded = wf.dateAdded,
                    nodeMapping = wf.nodeMapping
                )
            } catch (e: Exception) {
                null
            }
        }

        val backup = AppBackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            settings = settingsBackup,
            workflows = workflowBackups
        )

        return gson.toJson(backup)
    }

    fun restoreBackupData(
        context: Context,
        current: AppSettings,
        backupJson: String
    ): Result<Pair<AppSettings, List<SavedWorkflow>>> {
        return try {
            val backup = Gson().fromJson(backupJson, AppBackupData::class.java)
                ?: return Result.failure(Exception("Invalid or corrupted backup JSON."))

            val restoredWorkflows = mutableListOf<SavedWorkflow>()
            for (wfItem in backup.workflows) {
                try {
                    val filename = if (wfItem.filename.isNotBlank()) wfItem.filename else "wf_${wfItem.id}.json"
                    context.openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                        output.write(wfItem.rawJson.toByteArray())
                    }
                    restoredWorkflows.add(
                        SavedWorkflow(
                            id = wfItem.id,
                            label = wfItem.label,
                            filename = filename,
                            dateAdded = wfItem.dateAdded,
                            nodeMapping = wfItem.nodeMapping
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val existingWorkflows = current.savedWorkflows.filterNot { existing ->
                restoredWorkflows.any { it.id == existing.id }
            }
            val allWorkflows = existingWorkflows + restoredWorkflows

            val s = backup.settings
            val mergedServers = (current.savedServers.filterNot { cur -> s.savedServers.any { it.id == cur.id } } + s.savedServers)

            val updated = current.copy(
                serverUrl = if (s.serverUrl.isNotBlank()) s.serverUrl else current.serverUrl,
                savedServers = mergedServers,
                activeServerId = if (s.activeServerId.isNotBlank()) s.activeServerId else current.activeServerId,
                selectedWorkflowId = if (s.selectedWorkflowId.isNotBlank()) s.selectedWorkflowId else current.selectedWorkflowId,
                savedWorkflows = allWorkflows,
                resolutionMode = s.resolutionMode,
                customWidth = s.customWidth,
                customHeight = s.customHeight,
                batchSize = s.batchSize,
                megapixel = s.megapixel,
                aspectRatio = s.aspectRatio,
                highlightColor = s.highlightColor,
                showWorkflowSelector = s.showWorkflowSelector,
                showResolutionControls = s.showResolutionControls,
                showBatchSizeControls = s.showBatchSizeControls,
                showNegativePrompt = s.showNegativePrompt,
                showSeedControls = s.showSeedControls,
                showImageMaskInput = s.showImageMaskInput,
                sshHost = if (s.sshHost.isNotBlank()) s.sshHost else current.sshHost,
                sshPort = if (s.sshPort > 0) s.sshPort else current.sshPort,
                sshUsername = if (s.sshUsername.isNotBlank()) s.sshUsername else current.sshUsername,
                sshCommand = if (s.sshCommand.isNotBlank()) s.sshCommand else current.sshCommand
            )

            Result.success(Pair(updated, restoredWorkflows))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
