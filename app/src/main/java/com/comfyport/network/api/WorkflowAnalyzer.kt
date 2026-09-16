package com.comfyport.network.api

import com.comfyport.data.*
import com.comfyport.network.AppLogger
import com.comfyport.network.ComfyWorkflowConverter
import com.comfyport.network.http.ComfyHttpClient
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Dedicated service for analyzing and extracting metadata/previews from ComfyUI workflows.
 * Pure stateless analysis functions decoupled from network execution.
 */
object WorkflowAnalyzer {

    fun getNodeTitle(nodeObj: JsonObject): String {
        val metaTitle = if (nodeObj.has("_meta") && nodeObj.get("_meta").isJsonObject) {
            nodeObj.getAsJsonObject("_meta")?.get("title")?.asString
        } else null
        val directTitle = if (nodeObj.has("title") && nodeObj.get("title").isJsonPrimitive) {
            nodeObj.get("title")?.asString
        } else null
        val propTitle = if (nodeObj.has("properties") && nodeObj.get("properties").isJsonObject) {
            nodeObj.getAsJsonObject("properties")?.get("Node name for S&R")?.asString
        } else null
        return (metaTitle ?: directTitle ?: propTitle ?: "").trim()
    }

    fun resolveDimensions(settings: AppSettings): Pair<Int, Int> {
        if (settings.customWidth > 0 && settings.customHeight > 0) {
            val w = ((settings.customWidth + 7) / 8) * 8
            val h = ((settings.customHeight + 7) / 8) * 8
            return Pair(w, h)
        }
        return calculateDimensions(settings.megapixel, settings.aspectRatio)
    }

    fun calculateDimensions(megapixel: String, aspectRatio: String, divisibleBy: Int = 16): Pair<Int, Int> {
        val mp = megapixel.toDoubleOrNull() ?: 1.0
        val totalPixels = mp * 1000000.0

        val ratioStr = aspectRatio.substringBefore(" ").trim()
        val parts = ratioStr.split(":")
        val (wRatio, hRatio) = if (parts.size == 2) {
            Pair(parts[0].toDoubleOrNull() ?: 1.0, parts[1].toDoubleOrNull() ?: 1.0)
        } else {
            Pair(1.0, 1.0)
        }

        val ratio = wRatio / hRatio
        val h = sqrt(totalPixels / ratio)
        val w = h * ratio

        val finalW = (java.lang.Math.round(w / divisibleBy) * divisibleBy).toInt().coerceAtLeast(divisibleBy)
        val finalH = (java.lang.Math.round(h / divisibleBy) * divisibleBy).toInt().coerceAtLeast(divisibleBy)

        return Pair(finalW, finalH)
    }

    private fun extractTextSafely(element: JsonElement?, rootObj: JsonObject, depth: Int = 0): String? {
        if (element == null || element.isJsonNull || depth > 8) return null
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val s = element.asString
            if (!s.isNullOrBlank()) return s
        }
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            if (arr.size() > 0 && arr[0].isJsonPrimitive) {
                val upstreamNodeId = arr[0].asString
                val upstreamNode = rootObj.getAsJsonObject(upstreamNodeId) ?: return null
                val upstreamInputs = upstreamNode.getAsJsonObject("inputs") ?: return null
                for (key in listOf("text", "value", "string", "prompt", "text_1", "text_2", "")) {
                    if (upstreamInputs.has(key)) {
                        val found = extractTextSafely(upstreamInputs.get(key), rootObj, depth + 1)
                        if (!found.isNullOrBlank()) return found
                    }
                }
            }
        }
        return null
    }

    fun extractWorkflowMetadata(workflowJsonString: String): WorkflowMetadata {
        return try {
            val root = ComfyHttpClient.gson.fromJson(workflowJsonString, JsonElement::class.java)
            if (root == null || !root.isJsonObject) return WorkflowMetadata()
            var rootObj = root.asJsonObject

            if (rootObj.has("workflow") && rootObj.get("workflow").isJsonObject) {
                val wf = rootObj.getAsJsonObject("workflow")
                if (wf.has("nodes")) rootObj = wf
            }
            if (rootObj.has("prompt") && rootObj.get("prompt").isJsonObject && !rootObj.has("nodes")) {
                rootObj = rootObj.getAsJsonObject("prompt")
            }

            if (rootObj.has("definitions") && rootObj.getAsJsonObject("definitions").has("subgraphs")) {
                try {
                    val flattenedJson = ComfyWorkflowConverter.flattenUiWorkflow(workflowJsonString)
                    val flattenedRoot = ComfyHttpClient.gson.fromJson(flattenedJson, JsonElement::class.java)
                    if (flattenedRoot != null && flattenedRoot.isJsonObject) {
                        val flatObj = flattenedRoot.asJsonObject
                        rootObj = if (flatObj.has("workflow") && flatObj.get("workflow").isJsonObject) {
                            flatObj.getAsJsonObject("workflow")
                        } else flatObj
                    }
                } catch (_: Exception) {}
            }

            var extractedPrompt: String? = null
            var extractedNegativePrompt: String? = null
            var extractedWidth: Int? = null
            var extractedHeight: Int? = null
            var extractedBatchSize: Int? = null
            var hasImageInput = false
            var hasMaskInput = false
            val isResolutionManagedByWorkflow: Boolean

            if (rootObj.has("nodes") && rootObj.get("nodes").isJsonArray) {
                val nodes = rootObj.getAsJsonArray("nodes")
                var uiHasLinkedLatent = false
                var uiHasEmptyLatent = false
                var uiHasVaeEncode = false
                val linkOriginNodeId = mutableMapOf<String, String>()
                if (rootObj.has("links") && rootObj.get("links").isJsonArray) {
                    for (linkEl in rootObj.getAsJsonArray("links")) {
                        if (linkEl.isJsonArray && linkEl.asJsonArray.size() >= 2) {
                            val lArr = linkEl.asJsonArray
                            val linkId = lArr[0].asString
                            val originId = lArr[1].asString
                            linkOriginNodeId[linkId] = originId
                        }
                    }
                }

                for (nodeEl in nodes) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val type = node.get("type")?.asString ?: ""
                    val widgetsEl = node.get("widgets_values")
                    val widgetsNamedEl = if (node.has("widgets_values_named") && node.get("widgets_values_named").isJsonObject) {
                        node.getAsJsonObject("widgets_values_named")
                    } else null

                    if (type.contains("LoadImage") || type.contains("ImageLoad")) {
                        hasImageInput = true
                        if (node.has("outputs") && node.get("outputs").isJsonArray) {
                            for (outEl in node.getAsJsonArray("outputs")) {
                                if (outEl.isJsonObject) {
                                    val outObj = outEl.asJsonObject
                                    val outType = outObj.get("type")?.asString ?: ""
                                    val outName = outObj.get("name")?.asString ?: ""
                                    if ((outType.equals("MASK", ignoreCase = true) || outName.equals("MASK", ignoreCase = true)) &&
                                        outObj.has("links") && outObj.get("links").isJsonArray && outObj.getAsJsonArray("links").size() > 0) {
                                        hasMaskInput = true
                                    }
                                }
                            }
                        }
                    }
                    if (type.contains("LoadImageMask") || type.contains("MaskEditor") ||
                        type.contains("Inpaint") || type.contains("SetLatentNoiseMask") ||
                        type.contains("CropByMask") || type.contains("UncropByMask")) {
                        hasMaskInput = true
                        hasImageInput = true
                    }

                    if (type.contains("VAEEncode", ignoreCase = true) || type.contains("EncodeForInpaint", ignoreCase = true)) {
                        uiHasVaeEncode = true
                    }

                    if (type.contains("CLIPTextEncode") || type.contains("TextEncode") || type.contains("Text") || type.contains("Primitive")) {
                        var textCandidate: String? = null
                        if (widgetsNamedEl != null) {
                            for (key in listOf("text", "value", "string", "prompt", "text_1", "text_2")) {
                                if (widgetsNamedEl.has(key) && widgetsNamedEl.get(key).isJsonPrimitive && widgetsNamedEl.get(key).asJsonPrimitive.isString) {
                                    val s = widgetsNamedEl.get(key).asString
                                    if (!s.isNullOrBlank()) {
                                        textCandidate = s
                                        break
                                    }
                                }
                            }
                        }
                        if (textCandidate == null && widgetsEl != null && widgetsEl.isJsonArray && widgetsEl.asJsonArray.size() > 0) {
                            val w = widgetsEl.asJsonArray.get(0)
                            if (w.isJsonPrimitive && w.asJsonPrimitive.isString) textCandidate = w.asString
                        }

                        if (!textCandidate.isNullOrBlank()) {
                            if (extractedPrompt == null) {
                                extractedPrompt = textCandidate
                            } else if (extractedNegativePrompt == null) {
                                extractedNegativePrompt = textCandidate
                            }
                        }
                    }

                    if (type == "EmptyLatentImage" || type.contains("Latent") || type.contains("Empty")) {
                        uiHasEmptyLatent = true
                        if (node.has("inputs") && node.get("inputs").isJsonArray) {
                            for (inpEl in node.getAsJsonArray("inputs")) {
                                if (inpEl.isJsonObject) {
                                    val inpObj = inpEl.asJsonObject
                                    val inpName = inpObj.get("name")?.asString ?: ""
                                    val link = inpObj.get("link")
                                    if ((inpName.equals("width", ignoreCase = true) || inpName.equals("height", ignoreCase = true)) &&
                                        link != null && !link.isJsonNull
                                    ) {
                                        uiHasLinkedLatent = true
                                    }
                                }
                            }
                        }

                        if (widgetsNamedEl != null) {
                            if (extractedWidth == null && widgetsNamedEl.has("width")) {
                                val w = widgetsNamedEl.get("width")
                                if (w.isJsonPrimitive && w.asJsonPrimitive.isNumber) extractedWidth = w.asInt
                            }
                            if (extractedHeight == null && widgetsNamedEl.has("height")) {
                                val h = widgetsNamedEl.get("height")
                                if (h.isJsonPrimitive && h.asJsonPrimitive.isNumber) extractedHeight = h.asInt
                            }
                            if (extractedBatchSize == null && widgetsNamedEl.has("batch_size")) {
                                val b = widgetsNamedEl.get("batch_size")
                                if (b.isJsonPrimitive && b.asJsonPrimitive.isNumber) extractedBatchSize = b.asInt
                            }
                        }
                        if (widgetsEl != null && widgetsEl.isJsonArray) {
                            val widgets = widgetsEl.asJsonArray
                            val w0 = if (widgets.size() >= 1) widgets.get(0) else null
                            val w1 = if (widgets.size() >= 2) widgets.get(1) else null
                            if (extractedWidth == null && w0 != null && w0.isJsonPrimitive && w0.asJsonPrimitive.isNumber) {
                                extractedWidth = w0.asInt
                            }
                            if (extractedHeight == null && w1 != null && w1.isJsonPrimitive && w1.asJsonPrimitive.isNumber) {
                                extractedHeight = w1.asInt
                            }
                            if (extractedBatchSize == null && widgets.size() >= 3) {
                                val w2 = widgets.get(2)
                                if (w2 != null && w2.isJsonPrimitive && w2.asJsonPrimitive.isNumber) {
                                    extractedBatchSize = w2.asInt
                                }
                            }
                        }
                    }

                    if (type.contains("KSampler", ignoreCase = true) || type.contains("Sampler", ignoreCase = true)) {
                        if (node.has("inputs") && node.get("inputs").isJsonArray) {
                            for (inpEl in node.getAsJsonArray("inputs")) {
                                if (inpEl.isJsonObject) {
                                    val inpObj = inpEl.asJsonObject
                                    val inpName = inpObj.get("name")?.asString ?: ""
                                    if (inpName == "latent_image" || inpName == "samples" || inpName == "latent") {
                                        val link = inpObj.get("link")
                                        if (link != null && !link.isJsonNull && link.isJsonPrimitive) {
                                            val originId = linkOriginNodeId[link.asString]
                                            if (originId != null) {
                                                var currentOriginNode = nodes.firstOrNull { it.isJsonObject && it.asJsonObject.get("id")?.asString == originId }?.asJsonObject
                                                var rerouteHop = 0
                                                while (currentOriginNode != null && currentOriginNode.get("type")?.asString?.equals("Reroute", ignoreCase = true) == true && rerouteHop < 10) {
                                                    rerouteHop++
                                                    val inps = currentOriginNode.getAsJsonArray("inputs")
                                                    val rLink = inps?.firstOrNull { it.isJsonObject }?.asJsonObject?.get("link")
                                                    if (rLink != null && !rLink.isJsonNull && rLink.isJsonPrimitive) {
                                                        val nextId = linkOriginNodeId[rLink.asString]
                                                        currentOriginNode = nodes.firstOrNull { it.isJsonObject && it.asJsonObject.get("id")?.asString == nextId }?.asJsonObject
                                                    } else break
                                                }
                                                val originType = currentOriginNode?.get("type")?.asString ?: ""
                                                if (originType.contains("VAEEncode", ignoreCase = true) ||
                                                    originType.contains("EncodeForInpaint", ignoreCase = true) ||
                                                    originType.contains("SetLatentNoiseMask", ignoreCase = true) ||
                                                    originType.contains("Inpaint", ignoreCase = true)
                                                ) {
                                                    uiHasVaeEncode = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                isResolutionManagedByWorkflow = uiHasLinkedLatent || (hasImageInput && uiHasVaeEncode && !uiHasEmptyLatent) || (hasImageInput && !uiHasEmptyLatent) || (uiHasVaeEncode && !uiHasEmptyLatent)
            } else {
                var posNodeId: String? = null
                var negNodeId: String? = null
                for ((_, nodeEl) in rootObj.entrySet()) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val classType = node.get("class_type")?.asString ?: ""
                    if (classType.contains("KSampler") || classType.contains("Sampler")) {
                        val inputs = node.getAsJsonObject("inputs") ?: continue
                        if (posNodeId == null && inputs.has("positive") && inputs.get("positive").isJsonArray) {
                            val arr = inputs.getAsJsonArray("positive")
                            if (arr.size() > 0) posNodeId = arr[0].asString
                        }
                        if (negNodeId == null && inputs.has("negative") && inputs.get("negative").isJsonArray) {
                            val arr = inputs.getAsJsonArray("negative")
                            if (arr.size() > 0) negNodeId = arr[0].asString
                        }
                    }
                }

                // If pos/neg node is a Reroute, resolve upstream through reroutes
                fun resolveRerouteNodeId(id: String?, depth: Int = 0): String? {
                    if (id == null || depth > 8) return id
                    val node = rootObj.getAsJsonObject(id) ?: return id
                    val cType = node.get("class_type")?.asString ?: ""
                    if (cType.equals("Reroute", ignoreCase = true)) {
                        val inps = node.getAsJsonObject("inputs")
                        val link = inps?.get("")
                        if (link != null && link.isJsonArray && link.asJsonArray.size() > 0) {
                            val nextId = link.asJsonArray[0].asString
                            return resolveRerouteNodeId(nextId, depth + 1)
                        }
                    }
                    return id
                }
                posNodeId = resolveRerouteNodeId(posNodeId)
                negNodeId = resolveRerouteNodeId(negNodeId)

                if (posNodeId != null) {
                    val posInputs = rootObj.getAsJsonObject(posNodeId)?.getAsJsonObject("inputs")
                    val txt = extractTextSafely(posInputs?.get("text"), rootObj)
                        ?: extractTextSafely(posInputs?.get("value"), rootObj)
                        ?: extractTextSafely(posInputs?.get("string"), rootObj)
                    if (!txt.isNullOrBlank()) extractedPrompt = txt
                }
                if (negNodeId != null) {
                    val negInputs = rootObj.getAsJsonObject(negNodeId)?.getAsJsonObject("inputs")
                    val txt = extractTextSafely(negInputs?.get("text"), rootObj)
                        ?: extractTextSafely(negInputs?.get("value"), rootObj)
                        ?: extractTextSafely(negInputs?.get("string"), rootObj)
                    if (!txt.isNullOrBlank()) extractedNegativePrompt = txt
                }

                if (extractedPrompt == null) {
                    val node6 = rootObj.getAsJsonObject("6")?.getAsJsonObject("inputs")
                    val txt = extractTextSafely(node6?.get("text"), rootObj)
                    if (!txt.isNullOrBlank()) extractedPrompt = txt
                }
                if (extractedNegativePrompt == null) {
                    val node7 = rootObj.getAsJsonObject("7")?.getAsJsonObject("inputs")
                    val txt = extractTextSafely(node7?.get("text"), rootObj)
                    if (!txt.isNullOrBlank()) extractedNegativePrompt = txt
                }

                var hasEmptyLatentInApi = false
                for ((id, nodeEl) in rootObj.entrySet()) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val classType = node.get("class_type")?.asString ?: ""
                    val inputs = node.getAsJsonObject("inputs") ?: continue

                    if (extractedPrompt == null && (classType.contains("CLIPTextEncode") || classType.contains("TextEncode"))) {
                        val txt = extractTextSafely(inputs.get("text"), rootObj)
                        if (!txt.isNullOrBlank()) extractedPrompt = txt
                    } else if (extractedNegativePrompt == null && (classType.contains("CLIPTextEncode") || classType.contains("TextEncode")) && id != posNodeId) {
                        val txt = extractTextSafely(inputs.get("text"), rootObj)
                        if (!txt.isNullOrBlank()) extractedNegativePrompt = txt
                    }

                    if (classType == "LoadImage" || classType.contains("LoadImage") || classType == "ImageLoad") {
                        hasImageInput = true
                    }
                    if (classType == "LoadImageMask" || classType.contains("LoadImageMask") || classType.contains("MaskEditor") ||
                        classType.contains("CropByMask") || classType.contains("UncropByMask") || classType.contains("SetLatentNoiseMask") ||
                        inputs.has("mask")) {
                        hasMaskInput = true
                        hasImageInput = true
                    }

                    if (classType == "EmptyLatentImage" || classType.contains("LatentImage") || classType.contains("EmptyLatent")) {
                        hasEmptyLatentInApi = true
                        if (extractedWidth == null && inputs.has("width")) {
                            val w = inputs.get("width")
                            if (w != null && w.isJsonPrimitive && w.asJsonPrimitive.isNumber) extractedWidth = w.asInt
                        }
                        if (extractedHeight == null && inputs.has("height")) {
                            val h = inputs.get("height")
                            if (h != null && h.isJsonPrimitive && h.asJsonPrimitive.isNumber) extractedHeight = h.asInt
                        }
                        if (extractedBatchSize == null && inputs.has("batch_size")) {
                            val b = inputs.get("batch_size")
                            if (b != null && b.isJsonPrimitive && b.asJsonPrimitive.isNumber) extractedBatchSize = b.asInt
                        }
                    }
                }

                val hasLinkedLatent = hasLinkedLatentResolution(rootObj)
                val latentDerived = isLatentDerivedFromImage(rootObj)
                isResolutionManagedByWorkflow = hasLinkedLatent || latentDerived || (hasImageInput && !hasEmptyLatentInApi)
            }

            WorkflowMetadata(
                prompt = extractedPrompt,
                negativePrompt = extractedNegativePrompt,
                width = extractedWidth,
                height = extractedHeight,
                batchSize = extractedBatchSize,
                hasImageInput = hasImageInput,
                hasMaskInput = hasMaskInput,
                isResolutionManagedByWorkflow = isResolutionManagedByWorkflow
            )
        } catch (e: Exception) {
            AppLogger.e("WorkflowAnalyzer", "Failed to extract workflow metadata: ${e.localizedMessage}")
            WorkflowMetadata()
        }
    }

    fun parseWorkflowPreview(
        label: String,
        filename: String,
        jsonString: String,
        activeMapping: WorkflowNodeMapping? = null
    ): WorkflowPreviewData {
        return try {
            val root = ComfyHttpClient.gson.fromJson(jsonString, JsonElement::class.java)
            if (root == null || !root.isJsonObject) {
                return WorkflowPreviewData(label = label, filename = filename, rawJson = jsonString, activeMapping = activeMapping)
            }
            var rootObj = root.asJsonObject
            if (rootObj.has("workflow") && rootObj.get("workflow").isJsonObject) {
                val wf = rootObj.getAsJsonObject("workflow")
                if (wf.has("nodes")) rootObj = wf
            }
            if (rootObj.has("prompt") && rootObj.get("prompt").isJsonObject && !rootObj.has("nodes")) {
                rootObj = rootObj.getAsJsonObject("prompt")
            }

            if (rootObj.has("definitions") && rootObj.getAsJsonObject("definitions").has("subgraphs")) {
                try {
                    val flattenedJson = ComfyWorkflowConverter.flattenUiWorkflow(jsonString)
                    val flattenedRoot = ComfyHttpClient.gson.fromJson(flattenedJson, JsonElement::class.java)
                    if (flattenedRoot != null && flattenedRoot.isJsonObject) {
                        val flatObj = flattenedRoot.asJsonObject
                        rootObj = if (flatObj.has("workflow") && flatObj.get("workflow").isJsonObject) {
                            flatObj.getAsJsonObject("workflow")
                        } else flatObj
                    }
                } catch (_: Exception) {}
            }

            val baseMeta = extractWorkflowMetadata(jsonString)

            var extractedPrompt: String? = baseMeta.prompt
            var extractedNegativePrompt: String? = baseMeta.negativePrompt
            var extractedWidth: Int? = baseMeta.width
            var extractedHeight: Int? = baseMeta.height
            var extractedBatchSize: Int? = baseMeta.batchSize
            var extractedModelName: String? = null
            var extractedSampler: String? = null
            var extractedScheduler: String? = null
            var extractedSteps: Int? = null
            var extractedCfg: Float? = null
            var hasEmptyConditioning = false
            val extractedLoras = mutableListOf<String>()
            val nodeTypesMap = mutableMapOf<String, Int>()
            val parsedNodes = mutableListOf<WorkflowNodeInfo>()
            val parsedWires = mutableListOf<GraphWire>()
            val nodeCount: Int

            if (rootObj.has("nodes") && rootObj.get("nodes").isJsonArray) {
                val nodes = rootObj.getAsJsonArray("nodes")
                nodeCount = nodes.size()

                if (rootObj.has("links") && rootObj.get("links").isJsonArray) {
                    for (linkEl in rootObj.getAsJsonArray("links")) {
                        if (!linkEl.isJsonArray) continue
                        val linkArr = linkEl.asJsonArray
                        if (linkArr.size() >= 5) {
                            try {
                                val id = linkArr[0].asString
                                val fromNode = linkArr[1].asString
                                val fromSlot = linkArr[2].asInt
                                val toNode = linkArr[3].asString
                                val toSlot = linkArr[4].asInt
                                val wireType = if (linkArr.size() > 5 && !linkArr[5].isJsonNull) linkArr[5].asString else "*"
                                parsedWires.add(
                                    GraphWire(
                                        id = id,
                                        fromNodeId = fromNode,
                                        fromSlotIndex = fromSlot,
                                        toNodeId = toNode,
                                        toSlotIndex = toSlot,
                                        wireType = wireType
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }

                for (nodeEl in nodes) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val id = node.get("id")?.asString ?: ""
                    val type = node.get("type")?.asString ?: "Unknown"
                    nodeTypesMap[type] = (nodeTypesMap[type] ?: 0) + 1

                    val title = node.get("title")?.asString?.ifBlank { null }
                        ?: (if (node.has("properties") && node.getAsJsonObject("properties").has("Node name for S&R"))
                            node.getAsJsonObject("properties").get("Node name for S&R").asString else null)
                        ?: type

                    if (type.contains("EmptyConditioning", ignoreCase = true) ||
                        type.contains("ConditioningZeroOut", ignoreCase = true) ||
                        type.contains("ConditioningEmpty", ignoreCase = true)) {
                        hasEmptyConditioning = true
                    }

                    var posX = 0f
                    var posY = 0f
                    if (node.has("pos")) {
                        val posEl = node.get("pos")
                        if (posEl.isJsonArray && posEl.asJsonArray.size() >= 2) {
                            posX = posEl.asJsonArray[0].asFloat
                            posY = posEl.asJsonArray[1].asFloat
                        } else if (posEl.isJsonObject) {
                            posX = posEl.asJsonObject.get("0")?.asFloat ?: 0f
                            posY = posEl.asJsonObject.get("1")?.asFloat ?: 0f
                        }
                    }

                    var width = 220f
                    var height = 120f
                    if (node.has("size")) {
                        val sizeEl = node.get("size")
                        if (sizeEl.isJsonArray && sizeEl.asJsonArray.size() >= 2) {
                            width = sizeEl.asJsonArray[0].asFloat
                            height = sizeEl.asJsonArray[1].asFloat
                        } else if (sizeEl.isJsonObject) {
                            width = sizeEl.asJsonObject.get("0")?.asFloat ?: 220f
                            height = sizeEl.asJsonObject.get("1")?.asFloat ?: 120f
                        }
                    }
                    if (width <= 0f) width = 220f
                    if (height <= 0f) height = 120f

                    val incomingLinks = mutableListOf<String>()
                    val inputSlots = mutableListOf<NodeSlot>()
                    if (node.has("inputs") && node.get("inputs").isJsonArray) {
                        for ((idx, inp) in node.getAsJsonArray("inputs").withIndex()) {
                            if (!inp.isJsonObject) continue
                            val inpObj = inp.asJsonObject
                            val name = inpObj.get("name")?.asString ?: "in_$idx"
                            val typeStr = inpObj.get("type")?.asString ?: "*"
                            val link = inpObj.get("link")
                            val linkId = if (link != null && !link.isJsonNull) link.asInt else null
                            if (linkId != null) {
                                incomingLinks.add("$name (link #$linkId)")
                            }
                            inputSlots.add(
                                NodeSlot(
                                    name = name,
                                    type = typeStr,
                                    slotIndex = idx,
                                    linkId = linkId
                                )
                            )
                        }
                    }

                    val outgoingLinks = mutableListOf<String>()
                    val outputSlots = mutableListOf<NodeSlot>()
                    if (node.has("outputs") && node.get("outputs").isJsonArray) {
                        for ((idx, out) in node.getAsJsonArray("outputs").withIndex()) {
                            if (!out.isJsonObject) continue
                            val outObj = out.asJsonObject
                            val name = outObj.get("name")?.asString ?: "out_$idx"
                            val typeStr = outObj.get("type")?.asString ?: "*"
                            val links = outObj.get("links")
                            if (links != null && links.isJsonArray && links.asJsonArray.size() > 0) {
                                outgoingLinks.add("$name (${links.asJsonArray.size()} connection(s))")
                            }
                            outputSlots.add(
                                NodeSlot(
                                    name = name,
                                    type = typeStr,
                                    slotIndex = idx
                                )
                            )
                        }
                    }

                    val inputsMap = mutableMapOf<String, String>()
                    val widgetsEl = node.get("widgets_values")
                    val widgetsNamedEl = if (node.has("widgets_values_named") && node.get("widgets_values_named").isJsonObject) {
                        node.getAsJsonObject("widgets_values_named")
                    } else null
                    val widgetList = mutableListOf<JsonElement>()
                    var detailsStr = ""
                    if (widgetsEl != null) {
                        if (widgetsEl.isJsonArray) {
                            widgetsEl.asJsonArray.forEach { widgetList.add(it) }
                        } else if (widgetsEl.isJsonObject) {
                            widgetsEl.asJsonObject.entrySet().forEach { widgetList.add(it.value) }
                        }
                    }
                    if (widgetsNamedEl != null && widgetList.isEmpty()) {
                        widgetsNamedEl.entrySet().forEach { (k, v) ->
                            widgetList.add(v)
                            inputsMap[k] = if (v.isJsonPrimitive) v.asString else v.toString()
                        }
                    }
                    val strWidgets = widgetList.map { if (it.isJsonPrimitive) it.asString else it.toString() }
                    if (strWidgets.isNotEmpty()) {
                        detailsStr = strWidgets.take(3).joinToString(" | ")
                        if (inputsMap.isEmpty()) {
                            strWidgets.forEachIndexed { idx, v -> inputsMap["param_$idx"] = v }
                        }
                    }

                    parsedNodes.add(
                        WorkflowNodeInfo(
                            id = id,
                            title = title,
                            type = type,
                            details = detailsStr,
                            inputs = inputsMap,
                            incomingLinks = incomingLinks,
                            outgoingLinks = outgoingLinks,
                            posX = posX,
                            posY = posY,
                            width = width,
                            height = height,
                            inputSlots = inputSlots,
                            outputSlots = outputSlots
                        )
                    )

                    if (widgetList.isNotEmpty() && (type.contains("Checkpoint", ignoreCase = true) || type.contains("Unet", ignoreCase = true) || (extractedModelName == null && !type.contains("CLIP", ignoreCase = true) && !type.contains("VAE", ignoreCase = true)))) {
                        for (w in widgetList) {
                            if (w.isJsonPrimitive && w.asJsonPrimitive.isString) {
                                val s = w.asString
                                if (s.endsWith(".safetensors", ignoreCase = true) ||
                                    s.endsWith(".ckpt", ignoreCase = true) ||
                                    s.endsWith(".pt", ignoreCase = true) ||
                                    s.endsWith(".gguf", ignoreCase = true)) {
                                    if (!s.contains("lora", ignoreCase = true)) {
                                        extractedModelName = s
                                        break
                                    }
                                }
                            }
                        }
                    }

                    if (type.contains("Lora", ignoreCase = true) && widgetList.isNotEmpty()) {
                        for (w in widgetList) {
                            if (w.isJsonPrimitive && w.asJsonPrimitive.isString) {
                                val s = w.asString
                                if (s.endsWith(".safetensors", ignoreCase = true) || s.contains("lora", ignoreCase = true)) {
                                    if (s !in extractedLoras) extractedLoras.add(s)
                                }
                            }
                        }
                    }

                    if (type.contains("KSampler", ignoreCase = true) || type.contains("Sampler", ignoreCase = true)) {
                        for (w in widgetList) {
                            if (w.isJsonPrimitive) {
                                val prim = w.asJsonPrimitive
                                if (extractedSteps == null && prim.isNumber && prim.asInt in 1..150) {
                                    extractedSteps = prim.asInt
                                } else if (extractedCfg == null && prim.isNumber && prim.asFloat in 0.5f..30.0f) {
                                    extractedCfg = prim.asFloat
                                } else if (extractedSampler == null && prim.isString) {
                                    val s = prim.asString.lowercase()
                                    if (s in listOf("euler", "euler_ancestral", "dpmpp_2m", "dpmpp_sde", "ddim", "uni_pc", "heun")) {
                                        extractedSampler = prim.asString
                                    }
                                } else if (extractedScheduler == null && prim.isString) {
                                    val s = prim.asString.lowercase()
                                    if (s in listOf("normal", "karras", "exponential", "sgm_uniform", "simple", "beta")) {
                                        extractedScheduler = prim.asString
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasEmptyConditioning && extractedNegativePrompt == null) {
                    extractedNegativePrompt = "Empty Conditioning"
                }
            } else {
                val keyList = rootObj.keySet().toList()
                nodeCount = keyList.size
                val outgoingMap = mutableMapOf<String, MutableList<String>>()
                val srcSlotsMap = mutableMapOf<String, MutableSet<Int>>()

                for (nodeId in keyList) {
                    val nodeObj = rootObj.getAsJsonObject(nodeId) ?: continue
                    val classType = nodeObj.get("class_type")?.asString ?: "Unknown"
                    val title = getNodeTitle(nodeObj).ifBlank { classType }
                    nodeTypesMap[classType] = (nodeTypesMap[classType] ?: 0) + 1
                    val inputs = nodeObj.getAsJsonObject("inputs")

                    val incomingLinks = mutableListOf<String>()
                    val inputsMap = mutableMapOf<String, String>()
                    val detailsList = mutableListOf<String>()
                    val inputSlots = mutableListOf<NodeSlot>()

                    if (inputs != null) {
                        for ((k, v) in inputs.entrySet()) {
                            if (v.isJsonArray) {
                                val arr = v.asJsonArray
                                if (arr.size() > 0) {
                                    val srcId = arr[0].asString
                                    val srcSlot = if (arr.size() > 1) arr[1].asInt else 0
                                    incomingLinks.add("$k: from Node #$srcId")
                                    outgoingMap.getOrPut(srcId) { mutableListOf() }.add("↳ to Node #$nodeId ($k)")
                                    srcSlotsMap.getOrPut(srcId) { mutableSetOf() }.add(srcSlot)

                                    val wireType = when {
                                        k.contains("model", ignoreCase = true) -> "MODEL"
                                        k.contains("clip", ignoreCase = true) -> "CLIP"
                                        k.contains("latent", ignoreCase = true) || k.contains("samples", ignoreCase = true) -> "LATENT"
                                        k.contains("vae", ignoreCase = true) -> "VAE"
                                        k.contains("image", ignoreCase = true) -> "IMAGE"
                                        k.contains("mask", ignoreCase = true) -> "MASK"
                                        k.contains("conditioning", ignoreCase = true) || k.contains("positive", ignoreCase = true) || k.contains("negative", ignoreCase = true) -> "CONDITIONING"
                                        else -> k.uppercase()
                                    }

                                    val slotIdx = inputSlots.size
                                    inputSlots.add(NodeSlot(name = k, type = wireType, slotIndex = slotIdx))
                                    parsedWires.add(
                                        GraphWire(
                                            id = "${srcId}_${nodeId}_$k",
                                            fromNodeId = srcId,
                                            fromSlotIndex = srcSlot,
                                            toNodeId = nodeId,
                                            toSlotIndex = slotIdx,
                                            wireType = wireType
                                        )
                                    )
                                }
                            } else if (v.isJsonPrimitive) {
                                val strVal = v.asString
                                inputsMap[k] = strVal
                                if (k in listOf("text", "value", "seed", "width", "height", "steps", "cfg", "ckpt_name", "model_name", "image")) {
                                    detailsList.add("$k: $strVal")
                                }
                            }
                        }
                    }

                    parsedNodes.add(
                        WorkflowNodeInfo(
                            id = nodeId,
                            title = title,
                            type = classType,
                            details = detailsList.joinToString(" | ").ifBlank {
                                inputsMap.entries.take(2).joinToString(" | ") { "${it.key}: ${it.value}" }
                            },
                            inputs = inputsMap,
                            incomingLinks = incomingLinks,
                            outgoingLinks = emptyList(),
                            inputSlots = inputSlots
                        )
                    )

                    if (classType.contains("EmptyConditioning", ignoreCase = true) ||
                        classType.contains("ConditioningZeroOut", ignoreCase = true) ||
                        classType.contains("ConditioningEmpty", ignoreCase = true)) {
                        hasEmptyConditioning = true
                    }

                    if (extractedModelName == null && inputs != null) {
                        for (k in listOf("ckpt_name", "unet_name", "model_name")) {
                            val v = inputs.get(k)
                            if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                                extractedModelName = v.asString
                                break
                            }
                        }
                    }

                    if (classType.contains("Lora", ignoreCase = true) && inputs != null) {
                        val v = inputs.get("lora_name")
                        if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                            val s = v.asString
                            if (s !in extractedLoras) extractedLoras.add(s)
                        }
                    }

                    if ((classType.contains("KSampler") || classType.contains("Sampler")) && inputs != null) {
                        if (extractedSteps == null && inputs.has("steps")) {
                            val st = inputs.get("steps")
                            if (st != null && st.isJsonPrimitive && st.asJsonPrimitive.isNumber) {
                                extractedSteps = st.asInt
                            }
                        }
                        if (extractedCfg == null && inputs.has("cfg")) {
                            val c = inputs.get("cfg")
                            if (c != null && c.isJsonPrimitive && c.asJsonPrimitive.isNumber) {
                                extractedCfg = c.asFloat
                            }
                        }
                        if (extractedSampler == null && inputs.has("sampler_name")) {
                            val s = inputs.get("sampler_name")
                            if (s != null && s.isJsonPrimitive && s.asJsonPrimitive.isString) {
                                extractedSampler = s.asString
                            }
                        }
                        if (extractedScheduler == null && inputs.has("scheduler")) {
                            val sc = inputs.get("scheduler")
                            if (sc != null && sc.isJsonPrimitive && sc.asJsonPrimitive.isString) {
                                extractedScheduler = sc.asString
                            }
                        }
                    }
                }

                for (i in parsedNodes.indices) {
                    val n = parsedNodes[i]
                    val outs = outgoingMap[n.id] ?: emptyList()
                    val usedSlots = srcSlotsMap[n.id] ?: emptySet()
                    val outSlots = mutableListOf<NodeSlot>()
                    val maxSlot = (usedSlots.maxOrNull() ?: -1)
                    for (slotIdx in 0..maxSlot) {
                        outSlots.add(NodeSlot(name = "output_$slotIdx", type = "*", slotIndex = slotIdx))
                    }
                    if (outSlots.isEmpty() && outs.isNotEmpty()) {
                        outSlots.add(NodeSlot(name = "output", type = "*", slotIndex = 0))
                    }
                    parsedNodes[i] = n.copy(outgoingLinks = outs, outputSlots = outSlots)
                }

                val rankMap = mutableMapOf<String, Int>()
                for (n in parsedNodes) rankMap[n.id] = 0
                var changed = true
                var pass = 0
                while (changed && pass < 10) {
                    changed = false
                    pass++
                    for (w in parsedWires) {
                        val srcRank = rankMap[w.fromNodeId] ?: 0
                        val dstRank = rankMap[w.toNodeId] ?: 0
                        if (dstRank <= srcRank) {
                            rankMap[w.toNodeId] = (srcRank + 1).coerceAtMost(6)
                            changed = true
                        }
                    }
                }

                val byRank = parsedNodes.groupBy { rankMap[it.id] ?: 0 }
                val layoutNodes = mutableListOf<WorkflowNodeInfo>()
                for (r in byRank.keys.sorted()) {
                    val colNodes = byRank[r] ?: emptyList()
                    colNodes.forEachIndexed { rowIdx, n ->
                        val nodeW = 240f
                        val nodeH = (110f + (n.inputSlots.size + n.outputSlots.size) * 16f).coerceIn(120f, 320f)
                        layoutNodes.add(
                            n.copy(
                                posX = 60f + r * 320f,
                                posY = 80f + rowIdx * 220f,
                                width = nodeW,
                                height = nodeH
                            )
                        )
                    }
                }
                parsedNodes.clear()
                parsedNodes.addAll(layoutNodes)

                if (hasEmptyConditioning && extractedNegativePrompt == null) {
                    extractedNegativePrompt = "Empty Conditioning"
                }
            }

            val resolvedMapping = if (activeMapping != null && activeMapping.isCustomized) {
                activeMapping
            } else {
                autoDetectNodeMapping(jsonString).takeIf { it.isCustomized } ?: activeMapping
            }

            if (resolvedMapping != null) {
                if (!resolvedMapping.positivePromptNodeId.isNullOrBlank()) {
                    val pNode = parsedNodes.firstOrNull { it.id == resolvedMapping.positivePromptNodeId }
                    val customTxt = pNode?.inputs?.get("text") ?: pNode?.inputs?.get("value") ?: pNode?.inputs?.get("param_0")
                    if (!customTxt.isNullOrBlank()) extractedPrompt = customTxt
                }
                if (!resolvedMapping.negativePromptNodeId.isNullOrBlank()) {
                    val nNode = parsedNodes.firstOrNull { it.id == resolvedMapping.negativePromptNodeId }
                    val customTxt = nNode?.inputs?.get("text") ?: nNode?.inputs?.get("value") ?: nNode?.inputs?.get("param_0")
                    if (!customTxt.isNullOrBlank()) extractedNegativePrompt = customTxt
                }
                if (!resolvedMapping.emptyLatentNodeId.isNullOrBlank()) {
                    val lNode = parsedNodes.firstOrNull { it.id == resolvedMapping.emptyLatentNodeId }
                    val w = lNode?.inputs?.get("width")?.toIntOrNull() ?: lNode?.inputs?.get("param_0")?.toIntOrNull()
                    val h = lNode?.inputs?.get("height")?.toIntOrNull() ?: lNode?.inputs?.get("param_1")?.toIntOrNull()
                    if (w != null && h != null) {
                        extractedWidth = w
                        extractedHeight = h
                    }
                }
            }

            val prettyJson = try {
                GsonBuilder().setPrettyPrinting().create().toJson(rootObj)
            } catch (_: Exception) {
                jsonString
            }

            WorkflowPreviewData(
                label = label,
                filename = filename,
                prompt = extractedPrompt,
                negativePrompt = extractedNegativePrompt,
                width = extractedWidth,
                height = extractedHeight,
                batchSize = extractedBatchSize,
                modelName = extractedModelName,
                samplerName = extractedSampler,
                scheduler = extractedScheduler,
                steps = extractedSteps,
                cfg = extractedCfg,
                loras = extractedLoras,
                nodeCount = nodeCount,
                nodeTypes = nodeTypesMap,
                rawJson = prettyJson,
                hasEmptyConditioning = hasEmptyConditioning,
                hasImageInput = baseMeta.hasImageInput,
                hasMaskInput = baseMeta.hasMaskInput,
                nodes = parsedNodes,
                activeMapping = resolvedMapping,
                wires = parsedWires,
                isResolutionManagedByWorkflow = baseMeta.isResolutionManagedByWorkflow
            )
        } catch (e: Exception) {
            AppLogger.e("WorkflowAnalyzer", "Failed to parse workflow preview: ${e.localizedMessage}")
            WorkflowPreviewData(label = label, filename = filename, rawJson = jsonString, activeMapping = activeMapping)
        }
    }

    fun hasLinkedLatentResolution(workflowObj: JsonObject): Boolean {
        for ((_, nodeEl) in workflowObj.entrySet()) {
            if (!nodeEl.isJsonObject) continue
            val node = nodeEl.asJsonObject
            val classType = node.get("class_type")?.asString ?: ""
            if (classType == "EmptyLatentImage" || classType.contains("LatentImage") || classType.contains("EmptyLatent")) {
                val inputs = node.getAsJsonObject("inputs") ?: continue
                if ((inputs.has("width") && inputs.get("width").isJsonArray) ||
                    (inputs.has("height") && inputs.get("height").isJsonArray)
                ) {
                    return true
                }
            }
        }
        return false
    }

    fun isLatentDerivedFromImage(workflowObj: JsonObject): Boolean {
        val samplerNodes = mutableListOf<JsonObject>()
        for ((_, nodeEl) in workflowObj.entrySet()) {
            if (!nodeEl.isJsonObject) continue
            val node = nodeEl.asJsonObject
            val classType = node.get("class_type")?.asString ?: ""
            if (classType.contains("KSampler") || classType.contains("Sampler")) {
                samplerNodes.add(node)
            }
        }

        fun tracesToImageLatent(nodeId: String, visited: MutableSet<String>): Boolean {
            if (!visited.add(nodeId)) return false
            val node = workflowObj.getAsJsonObject(nodeId) ?: return false
            val classType = node.get("class_type")?.asString ?: ""

            if (classType.contains("VAEEncode", ignoreCase = true) ||
                classType.contains("EncodeForInpaint", ignoreCase = true) ||
                classType.contains("InpaintModelConditioning", ignoreCase = true)
            ) {
                return true
            }

            val inputs = node.getAsJsonObject("inputs") ?: return false
            for (key in listOf("samples", "latent_image", "latent", "samples_to", "samples_from", "pixels")) {
                if (inputs.has(key) && inputs.get(key).isJsonArray) {
                    val arr = inputs.getAsJsonArray(key)
                    if (arr.size() > 0 && arr.get(0).isJsonPrimitive) {
                        val upstreamId = arr.get(0).asString
                        if (tracesToImageLatent(upstreamId, visited)) {
                            return true
                        }
                    }
                }
            }

            if (classType.contains("LoadImage", ignoreCase = true) || classType.contains("ImageLoad", ignoreCase = true)) {
                return true
            }

            return false
        }

        for (sampler in samplerNodes) {
            val inputs = sampler.getAsJsonObject("inputs") ?: continue
            for (latentKey in listOf("latent_image", "latent", "samples")) {
                if (inputs.has(latentKey) && inputs.get(latentKey).isJsonArray) {
                    val arr = inputs.getAsJsonArray(latentKey)
                    if (arr.size() > 0 && arr.get(0).isJsonPrimitive) {
                        val upstreamId = arr.get(0).asString
                        if (tracesToImageLatent(upstreamId, mutableSetOf())) {
                            return true
                        }
                    }
                }
            }
        }

        var hasLoadImage = false
        var hasVaeEncode = false
        var hasEmptyLatent = false

        for ((_, nodeEl) in workflowObj.entrySet()) {
            if (!nodeEl.isJsonObject) continue
            val node = nodeEl.asJsonObject
            val classType = node.get("class_type")?.asString ?: ""
            if (classType.contains("LoadImage") || classType.contains("ImageLoad")) hasLoadImage = true
            if (classType.contains("VAEEncode") || classType.contains("EncodeForInpaint")) hasVaeEncode = true
            if (classType.contains("EmptyLatent") || classType == "EmptyLatentImage") hasEmptyLatent = true
        }

        return hasLoadImage && hasVaeEncode && !hasEmptyLatent
    }

    /**
     * Automatically analyzes and detects essential node roles in a ComfyUI workflow JSON.
     * Supports both UI standard format and API prompt format.
     */
    fun autoDetectNodeMapping(workflowJsonString: String): WorkflowNodeMapping {
        return try {
            val root = ComfyHttpClient.gson.fromJson(workflowJsonString, JsonElement::class.java)
            if (root == null || !root.isJsonObject) return WorkflowNodeMapping()
            var rootObj = root.asJsonObject

            if (rootObj.has("workflow") && rootObj.get("workflow").isJsonObject) {
                val wf = rootObj.getAsJsonObject("workflow")
                if (wf.has("nodes")) rootObj = wf
            }
            if (rootObj.has("prompt") && rootObj.get("prompt").isJsonObject && !rootObj.has("nodes")) {
                rootObj = rootObj.getAsJsonObject("prompt")
            }

            if (rootObj.has("definitions") && rootObj.getAsJsonObject("definitions").has("subgraphs")) {
                try {
                    val flattenedJson = ComfyWorkflowConverter.flattenUiWorkflow(workflowJsonString)
                    val flattenedRoot = ComfyHttpClient.gson.fromJson(flattenedJson, JsonElement::class.java)
                    if (flattenedRoot != null && flattenedRoot.isJsonObject) {
                        val flatObj = flattenedRoot.asJsonObject
                        rootObj = if (flatObj.has("workflow") && flatObj.get("workflow").isJsonObject) {
                            flatObj.getAsJsonObject("workflow")
                        } else flatObj
                    }
                } catch (_: Exception) {}
            }

            var posId: String? = null
            var negId: String? = null
            var latentId: String? = null
            var seedId: String? = null
            var imageId: String? = null
            var maskId: String? = null
            var outputId: String? = null

            if (rootObj.has("nodes") && rootObj.get("nodes").isJsonArray) {
                // UI Standard Format
                val nodes = rootObj.getAsJsonArray("nodes")
                val linkOriginNodeId = mutableMapOf<String, String>()
                if (rootObj.has("links") && rootObj.get("links").isJsonArray) {
                    for (linkEl in rootObj.getAsJsonArray("links")) {
                        if (linkEl.isJsonArray && linkEl.asJsonArray.size() >= 4) {
                            val lArr = linkEl.asJsonArray
                            val linkId = lArr[0].asString
                            val originId = lArr[1].asString
                            linkOriginNodeId[linkId] = originId
                        }
                    }
                }

                fun findNodeById(id: String?): JsonObject? {
                    if (id == null) return null
                    return nodes.firstOrNull { it.isJsonObject && it.asJsonObject.get("id")?.asString == id }?.asJsonObject
                }

                fun traceOrigin(nodeId: String?, depth: Int = 0): String? {
                    if (nodeId == null || depth > 10) return nodeId
                    val node = findNodeById(nodeId) ?: return nodeId
                    val type = node.get("type")?.asString ?: ""
                    if (type.equals("Reroute", ignoreCase = true)) {
                        val inps = node.getAsJsonArray("inputs")
                        val link = inps?.firstOrNull { it.isJsonObject }?.asJsonObject?.get("link")
                        if (link != null && !link.isJsonNull) {
                            val prevId = linkOriginNodeId[link.asString]
                            return traceOrigin(prevId, depth + 1)
                        }
                    }
                    return nodeId
                }

                // 1. Trace Sampler for positive, negative, latent, and seed
                for (nodeEl in nodes) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val type = node.get("type")?.asString ?: ""
                    val nodeId = node.get("id")?.asString ?: continue

                    if (type.contains("KSampler", ignoreCase = true) || type.contains("Sampler", ignoreCase = true)) {
                        if (seedId == null) seedId = nodeId
                        val inps = node.getAsJsonArray("inputs")
                        if (inps != null) {
                            for (inpEl in inps) {
                                if (!inpEl.isJsonObject) continue
                                val inp = inpEl.asJsonObject
                                val name = inp.get("name")?.asString?.lowercase() ?: ""
                                val link = inp.get("link")
                                if (link != null && !link.isJsonNull) {
                                    val originNodeId = linkOriginNodeId[link.asString]
                                    val resolvedOriginId = traceOrigin(originNodeId)
                                    val resolvedNode = findNodeById(resolvedOriginId)
                                    val resolvedType = resolvedNode?.get("type")?.asString ?: ""

                                    if (name == "positive" && posId == null) {
                                        posId = resolvedOriginId
                                    } else if (name == "negative" && negId == null) {
                                        if (!resolvedType.contains("EmptyConditioning", ignoreCase = true) &&
                                            !resolvedType.contains("ConditioningZeroOut", ignoreCase = true)) {
                                            negId = resolvedOriginId
                                        }
                                    } else if ((name == "latent_image" || name == "latent" || name == "samples") && latentId == null) {
                                        if (resolvedType == "EmptyLatentImage" || resolvedType.contains("LatentImage") || resolvedType.contains("EmptyLatent")) {
                                            latentId = resolvedOriginId
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Fallbacks & other nodes scan
                var bestOutputScore = -1
                for (nodeEl in nodes) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val type = node.get("type")?.asString ?: ""
                    val nodeId = node.get("id")?.asString ?: continue
                    val title = getNodeTitle(node).lowercase()

                    // Positive / negative prompts fallback
                    if (type.contains("CLIPTextEncode") || type.contains("TextEncode")) {
                        if (title.contains("positive") || title.contains("pos")) {
                            if (posId == null) posId = nodeId
                        } else if (title.contains("negative") || title.contains("neg")) {
                            if (negId == null) negId = nodeId
                        } else if (posId == null) {
                            posId = nodeId
                        } else if (negId == null && nodeId != posId) {
                            negId = nodeId
                        }
                    }

                    // Empty Latent Image fallback
                    if (latentId == null && (type == "EmptyLatentImage" || type.contains("EmptyLatent") || type.contains("EmptySD3LatentImage"))) {
                        latentId = nodeId
                    }

                    // Seed fallback
                    if (seedId == null && (type.contains("Seed") || title.contains("seed"))) {
                        seedId = nodeId
                    }

                    // Load Image
                    if (imageId == null && (type == "LoadImage" || type == "ImageLoad" || (type.contains("LoadImage", ignoreCase = true) && !type.contains("Mask", ignoreCase = true)))) {
                        imageId = nodeId
                    }

                    // Load Mask
                    if (maskId == null) {
                        if (type == "LoadImageMask" || type.contains("MaskEditor") || type.contains("Inpaint") ||
                            type.contains("SetLatentNoiseMask") || type.contains("CropByMask")) {
                            maskId = nodeId
                        } else if (type == "LoadImage" || type == "ImageLoad") {
                            val outs = node.getAsJsonArray("outputs")
                            if (outs != null) {
                                for (outEl in outs) {
                                    if (outEl.isJsonObject) {
                                        val outObj = outEl.asJsonObject
                                        val outName = outObj.get("name")?.asString ?: ""
                                        val outType = outObj.get("type")?.asString ?: ""
                                        if ((outName.equals("mask", ignoreCase = true) || outType.equals("mask", ignoreCase = true)) &&
                                            outObj.has("links") && outObj.getAsJsonArray("links").size() > 0) {
                                            maskId = nodeId
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Output node
                    if (!type.contains("Mask", ignoreCase = true)) {
                        var score = -1
                        when {
                            type == "SaveImage" || type == "SaveImageWebsocket" -> score = 100
                            type.contains("SaveImage", ignoreCase = true) -> score = 90
                            type == "PreviewImage" -> score = 50
                            type.contains("Preview", ignoreCase = true) -> score = 40
                        }
                        if (score > bestOutputScore) {
                            bestOutputScore = score
                            outputId = nodeId
                        }
                    }
                }
            } else {
                // API Format
                fun resolveReroute(id: String?, depth: Int = 0): String? {
                    if (id == null || depth > 10) return id
                    val node = rootObj.getAsJsonObject(id) ?: return id
                    val cType = node.get("class_type")?.asString ?: ""
                    if (cType.equals("Reroute", ignoreCase = true)) {
                        val inps = node.getAsJsonObject("inputs")
                        val link = inps?.get("") ?: inps?.entrySet()?.firstOrNull()?.value
                        if (link != null && link.isJsonArray && link.asJsonArray.size() > 0) {
                            return resolveReroute(link.asJsonArray[0].asString, depth + 1)
                        }
                    }
                    return id
                }

                // 1. Check KSampler / Sampler for positive, negative, latent, seed
                for ((id, nodeEl) in rootObj.entrySet()) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val classType = node.get("class_type")?.asString ?: ""
                    if (classType.contains("KSampler", ignoreCase = true) || classType.contains("Sampler", ignoreCase = true)) {
                        if (seedId == null) seedId = id
                        val inps = node.getAsJsonObject("inputs")
                        if (inps != null) {
                            if (posId == null && inps.has("positive") && inps.get("positive").isJsonArray) {
                                val arr = inps.getAsJsonArray("positive")
                                if (arr.size() > 0) posId = resolveReroute(arr[0].asString)
                            }
                            if (negId == null && inps.has("negative") && inps.get("negative").isJsonArray) {
                                val arr = inps.getAsJsonArray("negative")
                                if (arr.size() > 0) {
                                    val resolved = resolveReroute(arr[0].asString)
                                    val rNode = resolved?.let { rootObj.getAsJsonObject(it) }
                                    val rClass = rNode?.get("class_type")?.asString ?: ""
                                    if (!rClass.contains("EmptyConditioning", ignoreCase = true) &&
                                        !rClass.contains("ConditioningZeroOut", ignoreCase = true)) {
                                        negId = resolved
                                    }
                                }
                            }
                            if (latentId == null && inps.has("latent_image") && inps.get("latent_image").isJsonArray) {
                                val arr = inps.getAsJsonArray("latent_image")
                                if (arr.size() > 0) {
                                    val resolved = resolveReroute(arr[0].asString)
                                    val rNode = resolved?.let { rootObj.getAsJsonObject(it) }
                                    val rClass = rNode?.get("class_type")?.asString ?: ""
                                    if (rClass == "EmptyLatentImage" || rClass.contains("LatentImage") || rClass.contains("EmptyLatent")) {
                                        latentId = resolved
                                    }
                                }
                            }
                        }
                    }
                }

                // Fallbacks & other roles scan
                var bestOutputScore = -1
                for ((id, nodeEl) in rootObj.entrySet()) {
                    if (!nodeEl.isJsonObject) continue
                    val node = nodeEl.asJsonObject
                    val classType = node.get("class_type")?.asString ?: ""
                    val inps = node.getAsJsonObject("inputs")
                    val title = getNodeTitle(node).lowercase()

                    // CLIP Text encode fallback
                    if (classType.contains("CLIPTextEncode") || classType.contains("TextEncode")) {
                        if (title.contains("positive") || title.contains("pos")) {
                            if (posId == null) posId = id
                        } else if (title.contains("negative") || title.contains("neg")) {
                            if (negId == null) negId = id
                        } else if (posId == null) {
                            posId = id
                        } else if (negId == null && id != posId) {
                            negId = id
                        }
                    }

                    // Empty Latent Image
                    if (latentId == null && (classType == "EmptyLatentImage" || classType.contains("EmptyLatent") || classType.contains("EmptySD3LatentImage"))) {
                        latentId = id
                    }

                    // Seed
                    if (seedId == null && inps != null && (inps.has("seed") || inps.has("noise_seed"))) {
                        seedId = id
                    }

                    // Load Image
                    if (imageId == null && (classType == "LoadImage" || classType == "ImageLoad" || (classType.contains("LoadImage", ignoreCase = true) && !classType.contains("Mask", ignoreCase = true)))) {
                        imageId = id
                    }

                    // Load Mask
                    if (maskId == null && (classType == "LoadImageMask" || classType.contains("MaskEditor") ||
                        classType.contains("Inpaint") || classType.contains("CropByMask") || classType.contains("SetLatentNoiseMask") ||
                        (inps != null && inps.has("mask")))) {
                        maskId = id
                    }

                    // Output node
                    if (!classType.contains("Mask", ignoreCase = true)) {
                        var score = -1
                        when {
                            classType == "SaveImage" || classType == "SaveImageWebsocket" -> score = 100
                            classType.contains("SaveImage", ignoreCase = true) -> score = 90
                            classType == "PreviewImage" -> score = 50
                            classType.contains("Preview", ignoreCase = true) -> score = 40
                        }
                        if (score > bestOutputScore) {
                            bestOutputScore = score
                            outputId = id
                        }
                    }
                }

                if (posId == null && rootObj.has("6")) posId = "6"
                if (negId == null && rootObj.has("7") && posId != "7") negId = "7"
                if (outputId == null && rootObj.has("9")) outputId = "9"
            }

            WorkflowNodeMapping(
                positivePromptNodeId = posId,
                negativePromptNodeId = negId,
                emptyLatentNodeId = latentId,
                seedNodeId = seedId,
                loadImageNodeId = imageId,
                loadImageMaskNodeId = maskId,
                outputNodeId = outputId
            )
        } catch (e: Exception) {
            AppLogger.e("WorkflowAnalyzer", "Failed to auto-detect node mapping: ${e.localizedMessage}")
            WorkflowNodeMapping()
        }
    }
}
