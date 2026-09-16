package com.comfyport.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.comfyport.network.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("comfy_prefs", Context.MODE_PRIVATE)
    
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "comfy_secret_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val gson = Gson()

    init {
        migrateLegacyPrefs()
    }

    private fun migrateLegacyPrefs() {
        val keysToMigrate = listOf(
            "ssh_password"
        )
        
        var migrated = false
        val prefsEditor = prefs.edit()
        val encryptedPrefsEditor = encryptedPrefs.edit()
        
        for (key in keysToMigrate) {
            if (prefs.contains(key)) {
                val value = prefs.getString(key, null)
                if (value != null) {
                    encryptedPrefsEditor.putString(key, value)
                    migrated = true
                }
                prefsEditor.remove(key)
            }
        }
        
        if (migrated) {
            encryptedPrefsEditor.apply()
            prefsEditor.apply()
            AppLogger.i("SettingsManager", "Legacy API keys successfully migrated to secure EncryptedSharedPreferences.")
        }
    }

    private fun getSensitiveKey(key: String, default: String = ""): String {
        return encryptedPrefs.getString(key, null) ?: default
    }

    fun getSettings(): AppSettings {
        val aspectRatios = listOf(
            "1:1 (Square)", "4:3 (Landscape)", "3:4 (Portrait)",
            "3:2 (Landscape)", "2:3 (Portrait)", "16:9 (Widescreen)",
            "9:16 (Vertical)", "21:9 (Ultrawide)", "9:21 (Tall Vertical)",
            "1:1 (Perfect Square)", "2:3 (Classic Portrait)", "3:4 (Golden Ratio)",
            "16:9 (Panorama)"
        )
        val megapixelOptions = listOf(
            "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0",
            "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0",
            "2.1", "2.2", "2.3", "2.4", "2.5"
        )

        var mp = prefs.getString("megapixel", "1.0") ?: "1.0"
        if (!megapixelOptions.contains(mp)) {
            mp = "1.0"
        }

        var ar = prefs.getString("aspect_ratio", "1:1 (Square)") ?: "1:1 (Square)"
        if (!aspectRatios.contains(ar)) {
            ar = "1:1 (Square)"
        }


        val savedServersJson = prefs.getString("saved_servers", null)
        val savedServers: List<SavedServer> = if (!savedServersJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<SavedServer>>() {}.type
                gson.fromJson<List<SavedServer>>(savedServersJson, type)
                    ?.filter { it.url != "http://10.0.2.2:8188" && it.id != "default_local" }
                    ?: emptyList()
            } catch (e: Exception) { emptyList() }
        } else {
            emptyList()
        }

        val savedWorkflowsJson = prefs.getString("saved_workflows", null)
        val savedWorkflows: List<SavedWorkflow> = if (!savedWorkflowsJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<SavedWorkflow>>() {}.type
                gson.fromJson<List<SavedWorkflow>>(savedWorkflowsJson, type)
                    ?.filter { it.id != "default" }
                    ?: emptyList()
            } catch (e: Exception) { emptyList() }
        } else {
            emptyList()
        }

        var serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl == "http://10.0.2.2:8188") serverUrl = ""

        var activeServerId = prefs.getString("active_server_id", "") ?: ""
        if (activeServerId == "default_local" || savedServers.none { it.id == activeServerId }) {
            activeServerId = savedServers.firstOrNull()?.id ?: ""
        }

        var selectedWorkflowId = prefs.getString("selected_workflow_id", "") ?: ""
        if (selectedWorkflowId == "default" || savedWorkflows.none { it.id == selectedWorkflowId }) {
            selectedWorkflowId = savedWorkflows.firstOrNull()?.id ?: ""
        }

        val activeWf = savedWorkflows.firstOrNull { it.id == selectedWorkflowId }
        val workflowToUse = activeWf?.filename ?: prefs.getString("workflow_to_use", "") ?: ""

        val seedModeStr = prefs.getString("seed_mode", SeedMode.Random.name) ?: SeedMode.Random.name
        val seedMode = try {
            SeedMode.valueOf(seedModeStr)
        } catch (e: Exception) {
            SeedMode.Random
        }

        return AppSettings(
            serverUrl = if (serverUrl.isBlank() && savedServers.isNotEmpty()) savedServers.first().url else serverUrl,
            savedServers = savedServers,
            activeServerId = activeServerId,
            savedWorkflows = savedWorkflows,
            selectedWorkflowId = selectedWorkflowId,
            resolutionMode = prefs.getString("resolution_mode", "SDXL") ?: "SDXL",
            customWidth = prefs.getInt("custom_width", 1024),
            customHeight = prefs.getInt("custom_height", 1024),
            batchSize = prefs.getInt("batch_size", 1).coerceAtLeast(1),
            megapixel = mp,
            aspectRatio = ar,
            workflowToUse = workflowToUse,
            outputFormat = prefs.getString("output_format", "PNG") ?: "PNG",
            showWorkflowSelector = prefs.getBoolean("show_workflow_selector", true),
            showResolutionControls = prefs.getBoolean("show_resolution_controls", true),
            showBatchSizeControls = prefs.getBoolean("show_batch_size_controls", true),
            showNegativePrompt = prefs.getBoolean("show_negative_prompt", true),
            showSeedControls = prefs.getBoolean("show_seed_controls", true),
            showImageMaskInput = prefs.getBoolean("show_image_mask_input", true),
            seedMode = seedMode,
            fixedSeedValue = prefs.getLong("fixed_seed_value", 42L),
            customSeedValue = prefs.getLong("custom_seed_value", 42L),
            lastUsedSeedValue = prefs.getLong("last_used_seed_value", 42L),
            sshHost = prefs.getString("ssh_host", "") ?: "",
            sshPort = prefs.getInt("ssh_port", 22),
            sshUsername = prefs.getString("ssh_username", "") ?: "",
            sshPassword = getSensitiveKey("ssh_password", ""),
            sshCommand = prefs.getString("ssh_command", null)?.let {
                if (it.isBlank() || it == "start \"\" run_nvidia_gpu.bat") {
                    "C:\\Users\\%USERNAME%\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat"
                } else it
            } ?: "C:\\Users\\%USERNAME%\\Desktop\\ComfyUI_windows_portable\\run_nvidia_gpu.bat",
            highlightColor = prefs.getString("highlight_color", "cyan") ?: "cyan"
        )
    }

    fun saveSettings(settings: AppSettings) {
        // Save sensitive keys in EncryptedSharedPreferences
        encryptedPrefs.edit().apply {
            putString("ssh_password", settings.sshPassword)
            apply()
        }

        // Save non-sensitive keys in standard SharedPreferences
        prefs.edit().apply {
            putString("server_url", settings.serverUrl)
            putString("saved_servers", gson.toJson(settings.savedServers))
            putString("active_server_id", settings.activeServerId)
            putString("saved_workflows", gson.toJson(settings.savedWorkflows))
            putString("selected_workflow_id", settings.selectedWorkflowId)
            putString("resolution_mode", settings.resolutionMode)
            putInt("custom_width", settings.customWidth)
            putInt("custom_height", settings.customHeight)
            putInt("batch_size", settings.batchSize)
            putString("megapixel", settings.megapixel)
            putString("aspect_ratio", settings.aspectRatio)
            putString("workflow_to_use", settings.workflowToUse)
            putString("output_format", settings.outputFormat)

            // Modular feature visibility
            putBoolean("show_workflow_selector", settings.showWorkflowSelector)
            putBoolean("show_resolution_controls", settings.showResolutionControls)
            putBoolean("show_batch_size_controls", settings.showBatchSizeControls)
            putBoolean("show_negative_prompt", settings.showNegativePrompt)
            putBoolean("show_seed_controls", settings.showSeedControls)
            putBoolean("show_image_mask_input", settings.showImageMaskInput)

            // Theme Customization
            putString("highlight_color", settings.highlightColor)

            // Seed options
            putString("seed_mode", settings.seedMode.name)
            putLong("fixed_seed_value", settings.fixedSeedValue)
            putLong("custom_seed_value", settings.customSeedValue)
            putLong("last_used_seed_value", settings.lastUsedSeedValue)

            // SSH Remote Control
            putString("ssh_host", settings.sshHost)
            putInt("ssh_port", settings.sshPort)
            putString("ssh_username", settings.sshUsername)
            putString("ssh_command", settings.sshCommand)

            // Remove legacy plain-text sensitive keys and gemini references
            remove("gemini_key")
            remove("gemini_model")
            remove("chatgpt_key")
            remove("claude_key")
            remove("grok_key")
            remove("comfy_deploy_api_key")
            remove("runpod_api_key")
            remove("fal_ai_api_key")
            remove("ssh_password")
            remove("trigger_cmd_token")
            
            apply()
        }
    }


    fun getLastPrompt(): String {
        return prefs.getString("last_prompt", "") ?: ""
    }

    fun saveLastPrompt(prompt: String) {
        prefs.edit().putString("last_prompt", prompt).apply()
    }

    fun getLastNegativePrompt(): String {
        return prefs.getString("last_negative_prompt", "") ?: ""
    }

    fun saveLastNegativePrompt(negativePrompt: String) {
        prefs.edit().putString("last_negative_prompt", negativePrompt).apply()
    }

    fun getGalleryItems(): List<GalleryItem> {
        val json = prefs.getString("gallery_items", "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<GalleryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveGalleryItems(items: List<GalleryItem>) {
        val json = gson.toJson(items)
        prefs.edit().putString("gallery_items", json).apply()
    }
}
