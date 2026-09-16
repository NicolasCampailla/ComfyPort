package com.comfyport

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Prompt : NavKey
@Serializable data object Progress : NavKey
@Serializable data class Result(
    val imageUrl: String,
    val seed: Long,
    val batchImages: List<String> = emptyList(),
    val prompt: String = ""
) : NavKey
@Serializable data object Settings : NavKey
@Serializable data object Gallery : NavKey
@Serializable data object Logs : NavKey
@Serializable data object WorkflowManager : NavKey

