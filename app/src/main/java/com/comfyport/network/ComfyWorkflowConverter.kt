package com.comfyport.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Native, on-device ComfyUI workflow converter.
 * Converts UI-format workflows (including those with ComfyUI v0.4+ subgraphs/group nodes)
 * into API-format prompts ready for ComfyUI's /prompt execution endpoint,
 * completely offline and without requiring any server-side converter extensions.
 */
object ComfyWorkflowConverter {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    data class RawLink(
        val id: String,
        val originNodeId: String,
        val originSlot: Int,
        val targetNodeId: String,
        val targetSlot: Int,
        val type: String = "*"
    )

    data class RawInput(
        val name: String,
        val type: String = "*",
        val linkId: String? = null,
        val widgetName: String? = null
    )

    data class RawOutput(
        val name: String,
        val type: String = "*",
        val slotIndex: Int = 0,
        val links: List<String> = emptyList()
    )

    data class RawNode(
        val id: String,
        val type: String,
        val title: String = "",
        val mode: Int = 0, // 0 = normal, 2 = mute, 4 = bypass
        val inputs: MutableList<RawInput> = mutableListOf(),
        val outputs: MutableList<RawOutput> = mutableListOf(),
        val widgetsValuesNamed: JsonObject? = null,
        val widgetsValues: JsonArray? = null,
        val posX: Float = 0f,
        val posY: Float = 0f,
        val width: Float = 220f,
        val height: Float = 120f,
        val properties: JsonObject? = null
    )

    data class SubgraphPortDef(
        val id: String,
        val name: String,
        val type: String,
        val linkIds: List<String> = emptyList(),
        val slotIndex: Int = 0
    )

    data class SubgraphDef(
        val id: String,
        val name: String,
        val inputNodeId: String = "-10",
        val outputNodeId: String = "-20",
        val inputs: List<SubgraphPortDef>,
        val outputs: List<SubgraphPortDef>,
        val nodes: List<RawNode>,
        val links: List<RawLink>
    )

    /**
     * Converts a UI-format ComfyUI workflow JSON string into an API-format prompt JSON string.
     */
    fun convertUiToApi(uiJsonString: String): String {
        val rootElement = try {
            JsonParser.parseString(uiJsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON format in workflow", e)
        }

        if (!rootElement.isJsonObject) {
            throw IllegalArgumentException("Workflow JSON must be an object")
        }

        var rootObj = rootElement.asJsonObject
        if (rootObj.has("workflow") && rootObj.get("workflow").isJsonObject) {
            val innerWf = rootObj.getAsJsonObject("workflow")
            if (innerWf.has("nodes")) rootObj = innerWf
        }

        // 1. Flatten all subgraphs if present and eliminate virtual Reroute nodes
        val (flattenedNodes, flattenedLinks) = flattenGraph(rootObj, removeReroutes = true)

        // 2. Build link lookup tables
        // Maps (targetNodeId, targetSlot) -> RawLink
        val targetSlotToLink = mutableMapOf<Pair<String, Int>, RawLink>()
        // Maps linkId -> RawLink
        val idToLink = mutableMapOf<String, RawLink>()

        for (link in flattenedLinks) {
            targetSlotToLink[Pair(link.targetNodeId, link.targetSlot)] = link
            idToLink[link.id] = link
        }

        // 3. Build API prompt object
        val promptObj = JsonObject()

        for (node in flattenedNodes) {
            // Exclude muted nodes (mode == 2) and virtual Reroute nodes
            if (node.mode == 2 || node.type.equals("Reroute", ignoreCase = true)) continue

            val nodeApiObj = JsonObject()
            nodeApiObj.addProperty("class_type", node.type)

            val inputsApiObj = JsonObject()

            // A. Populate widget values
            if (node.widgetsValuesNamed != null && node.widgetsValuesNamed.size() > 0) {
                for ((k, v) in node.widgetsValuesNamed.entrySet()) {
                    inputsApiObj.add(k, v)
                }
            } else if (node.widgetsValues != null && node.widgetsValues.size() > 0) {
                // If named values are absent, map widget values
                val widgetInps = node.inputs.filter { it.widgetName != null }
                if (widgetInps.isNotEmpty()) {
                    for ((idx, inp) in widgetInps.withIndex()) {
                        if (idx < node.widgetsValues.size()) {
                            inputsApiObj.add(inp.widgetName ?: inp.name, node.widgetsValues[idx])
                        }
                    }
                } else {
                    // Fallback to standard parameter mappings
                    val paramNames = getFallbackParamNames(node.type)
                    for ((idx, el) in node.widgetsValues.withIndex()) {
                        val paramName = if (idx < paramNames.size) paramNames[idx] else "param_$idx"
                        inputsApiObj.add(paramName, el)
                    }
                }
            }

            // B. Populate connected inputs from links (links take precedence over static widget values)
            for ((slotIdx, inp) in node.inputs.withIndex()) {
                val link = (inp.linkId?.let { idToLink[it] }) ?: targetSlotToLink[Pair(node.id, slotIdx)]
                if (link != null) {
                    val linkArr = JsonArray()
                    linkArr.add(link.originNodeId)
                    linkArr.add(link.originSlot)
                    inputsApiObj.add(inp.name, linkArr)
                }
            }

            nodeApiObj.add("inputs", inputsApiObj)

            // Preserve _meta title if available
            if (node.title.isNotBlank()) {
                val metaObj = JsonObject()
                metaObj.addProperty("title", node.title)
                nodeApiObj.add("_meta", metaObj)
            }

            promptObj.add(node.id, nodeApiObj)
        }

        return gson.toJson(promptObj)
    }

    /**
     * Returns a flattened UI-standard workflow JSON string where all subgraphs
     * have been expanded into top-level nodes and links with rewired connections.
     */
    fun flattenUiWorkflow(uiJsonString: String): String {
        val rootElement = try {
            JsonParser.parseString(uiJsonString)
        } catch (e: Exception) {
            return uiJsonString
        }

        if (!rootElement.isJsonObject) return uiJsonString
        val rootObj = rootElement.asJsonObject

        var targetObj = rootObj
        val isWrapped = rootObj.has("workflow") && rootObj.get("workflow").isJsonObject
        if (isWrapped) {
            targetObj = rootObj.getAsJsonObject("workflow")
        }

        if (!targetObj.has("nodes") || !targetObj.get("nodes").isJsonArray) {
            return uiJsonString
        }

        val (flattenedNodes, flattenedLinks) = flattenGraph(targetObj)

        val newNodesArray = JsonArray()
        for (n in flattenedNodes) {
            val nodeObj = JsonObject()
            nodeObj.addProperty("id", n.id)
            nodeObj.addProperty("type", n.type)
            if (n.title.isNotBlank()) nodeObj.addProperty("title", n.title)
            nodeObj.addProperty("mode", n.mode)

            val posArr = JsonArray()
            posArr.add(n.posX)
            posArr.add(n.posY)
            nodeObj.add("pos", posArr)

            val sizeArr = JsonArray()
            sizeArr.add(n.width)
            sizeArr.add(n.height)
            nodeObj.add("size", sizeArr)

            val inpsArr = JsonArray()
            for (inp in n.inputs) {
                val inpObj = JsonObject()
                inpObj.addProperty("name", inp.name)
                inpObj.addProperty("type", inp.type)
                if (inp.linkId != null) {
                    inpObj.addProperty("link", inp.linkId.toIntOrNull() ?: -1)
                }
                if (inp.widgetName != null) {
                    val wObj = JsonObject()
                    wObj.addProperty("name", inp.widgetName)
                    inpObj.add("widget", wObj)
                }
                inpsArr.add(inpObj)
            }
            nodeObj.add("inputs", inpsArr)

            val outsArr = JsonArray()
            for (out in n.outputs) {
                val outObj = JsonObject()
                outObj.addProperty("name", out.name)
                outObj.addProperty("type", out.type)
                val outLinksArr = JsonArray()
                for (l in out.links) {
                    outLinksArr.add(l.toIntOrNull() ?: -1)
                }
                outObj.add("links", outLinksArr)
                outsArr.add(outObj)
            }
            nodeObj.add("outputs", outsArr)

            if (n.widgetsValuesNamed != null) nodeObj.add("widgets_values_named", n.widgetsValuesNamed)
            if (n.widgetsValues != null) nodeObj.add("widgets_values", n.widgetsValues)
            if (n.properties != null) nodeObj.add("properties", n.properties)

            newNodesArray.add(nodeObj)
        }

        val newLinksArray = JsonArray()
        for (l in flattenedLinks) {
            val lArr = JsonArray()
            val idNum = l.id.toIntOrNull()
            if (idNum != null) lArr.add(idNum) else lArr.add(l.id)

            val origNum = l.originNodeId.toIntOrNull()
            if (origNum != null) lArr.add(origNum) else lArr.add(l.originNodeId)

            lArr.add(l.originSlot)

            val tgtNum = l.targetNodeId.toIntOrNull()
            if (tgtNum != null) lArr.add(tgtNum) else lArr.add(l.targetNodeId)

            lArr.add(l.type)
            newLinksArray.add(lArr)
        }

        val resultObj = targetObj.deepCopy()
        resultObj.add("nodes", newNodesArray)
        resultObj.add("links", newLinksArray)
        // Subgraphs are flattened, so definitions can be cleared
        resultObj.remove("definitions")

        return if (isWrapped) {
            val wrappedCopy = rootObj.deepCopy()
            wrappedCopy.add("workflow", resultObj)
            gson.toJson(wrappedCopy)
        } else {
            gson.toJson(resultObj)
        }
    }

    /**
     * Flattens subgraphs iteratively into top-level nodes and links.
     * When [removeReroutes] is true, virtual Reroute nodes are collapsed and removed.
     */
    fun flattenGraph(rootObj: JsonObject, removeReroutes: Boolean = false): Pair<List<RawNode>, List<RawLink>> {
        val topNodes = parseNodes(rootObj.getAsJsonArray("nodes"))
        val topLinks = parseLinks(rootObj.getAsJsonArray("links"))

        val currentNodes = topNodes.toMutableList()
        val currentLinks = topLinks.toMutableList()

        val subgraphs = parseSubgraphs(rootObj.getAsJsonObject("definitions"))
        if (subgraphs.isNotEmpty()) {
            var pass = 0
            while (currentNodes.any { it.type in subgraphs } && pass < 20) {
                pass++
                val instanceNode = currentNodes.first { it.type in subgraphs }
                currentNodes.remove(instanceNode)
                val subgraph = subgraphs[instanceNode.type] ?: continue

                // Build unique ID mapping for internal nodes to prevent any ID collision
                val innerIdMap = mutableMapOf<String, String>()
                for (inner in subgraph.nodes) {
                    val isColliding = currentNodes.any { it.id == inner.id } || inner.id == instanceNode.id
                    val uniqueId = if (isColliding) "${instanceNode.id}:${inner.id}" else inner.id
                    innerIdMap[inner.id] = uniqueId
                }

                // Offset internal nodes relative to the subgraph instance's canvas position
                val minInnerX = subgraph.nodes.minOfOrNull { it.posX } ?: 0f
                val minInnerY = subgraph.nodes.minOfOrNull { it.posY } ?: 0f

                val expandedInnerNodes = subgraph.nodes.map { inner ->
                    val newId = innerIdMap[inner.id] ?: inner.id
                    inner.copy(
                        id = newId,
                        posX = instanceNode.posX + (inner.posX - minInnerX),
                        posY = instanceNode.posY + (inner.posY - minInnerY)
                    )
                }
                currentNodes.addAll(expandedInnerNodes)

                // 1. Rewire internal links between internal nodes
                for (link in subgraph.links) {
                    if (link.originNodeId != subgraph.inputNodeId && link.targetNodeId != subgraph.outputNodeId) {
                        val fromId = innerIdMap[link.originNodeId] ?: link.originNodeId
                        val toId = innerIdMap[link.targetNodeId] ?: link.targetNodeId
                        currentLinks.add(
                            link.copy(
                                id = "${instanceNode.id}_${link.id}",
                                originNodeId = fromId,
                                targetNodeId = toId
                            )
                        )
                    }
                }

                // 2. Rewire connections entering the subgraph (from outer graph to internal nodes)
                val outerInLinks = currentLinks.filter { it.targetNodeId == instanceNode.id }
                currentLinks.removeAll(outerInLinks)

                for ((slotIdx, outerLinkGroup) in outerInLinks.groupBy { it.targetSlot }) {
                    val inputPort = subgraph.inputs.getOrNull(slotIdx) ?: continue
                    val internalLinks = if (inputPort.linkIds.isNotEmpty()) {
                        subgraph.links.filter { it.originNodeId == subgraph.inputNodeId && it.id in inputPort.linkIds }
                    } else {
                        subgraph.links.filter { it.originNodeId == subgraph.inputNodeId && it.originSlot == slotIdx }
                    }
                    for (internalLink in internalLinks) {
                        val remappedTargetId = innerIdMap[internalLink.targetNodeId] ?: internalLink.targetNodeId
                        for (link in outerLinkGroup) {
                            currentLinks.add(
                                link.copy(
                                    targetNodeId = remappedTargetId,
                                    targetSlot = internalLink.targetSlot
                                )
                            )
                        }
                    }
                }

                // Also check if any outer inputs to instanceNode had static widget values to propagate
                if (instanceNode.widgetsValuesNamed != null) {
                    for ((slotIdx, inp) in instanceNode.inputs.withIndex()) {
                        val widgetVal = instanceNode.widgetsValuesNamed.get(inp.name)
                        if (widgetVal != null) {
                            val inputPort = subgraph.inputs.getOrNull(slotIdx)
                            if (inputPort != null) {
                                val internalLinks = if (inputPort.linkIds.isNotEmpty()) {
                                    subgraph.links.filter { it.originNodeId == subgraph.inputNodeId && it.id in inputPort.linkIds }
                                } else {
                                    subgraph.links.filter { it.originNodeId == subgraph.inputNodeId && it.originSlot == slotIdx }
                                }
                                for (internalLink in internalLinks) {
                                    val remappedTargetId = innerIdMap[internalLink.targetNodeId]
                                    val targetNode = currentNodes.firstOrNull { it.id == remappedTargetId }
                                    if (targetNode?.widgetsValuesNamed != null) {
                                        targetNode.widgetsValuesNamed.add(inp.name, widgetVal)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Rewire connections leaving the subgraph (from internal nodes to outer graph)
                val outerOutLinks = currentLinks.filter { it.originNodeId == instanceNode.id }
                currentLinks.removeAll(outerOutLinks)

                for ((slotIdx, outLinksForSlot) in outerOutLinks.groupBy { it.originSlot }) {
                    val outputPort = subgraph.outputs.getOrNull(slotIdx) ?: continue
                    val internalLinks = if (outputPort.linkIds.isNotEmpty()) {
                        subgraph.links.filter { it.targetNodeId == subgraph.outputNodeId && it.id in outputPort.linkIds }
                    } else {
                        subgraph.links.filter { it.targetNodeId == subgraph.outputNodeId && it.targetSlot == slotIdx }
                    }
                    for (internalLink in internalLinks) {
                        val remappedOriginId = innerIdMap[internalLink.originNodeId] ?: internalLink.originNodeId
                        for (link in outLinksForSlot) {
                            currentLinks.add(
                                link.copy(
                                    originNodeId = remappedOriginId,
                                    originSlot = internalLink.originSlot
                                )
                            )
                        }
                    }
                }

                currentLinks.removeAll { it.originNodeId == instanceNode.id || it.targetNodeId == instanceNode.id }
            }
        }

        // Handle bypassed nodes (mode == 4): forward input connections directly to output targets
        val bypassedNodes = currentNodes.filter { it.mode == 4 }
        for (bypassedNode in bypassedNodes) {
            currentNodes.remove(bypassedNode)
            val inLinks = currentLinks.filter { it.targetNodeId == bypassedNode.id }
            val outLinks = currentLinks.filter { it.originNodeId == bypassedNode.id }
            currentLinks.removeAll(inLinks)
            currentLinks.removeAll(outLinks)

            for (outLink in outLinks) {
                val matchingInLink = inLinks.firstOrNull { it.targetSlot == outLink.originSlot } ?: inLinks.firstOrNull()
                if (matchingInLink != null) {
                    currentLinks.add(
                        outLink.copy(
                            originNodeId = matchingInLink.originNodeId,
                            originSlot = matchingInLink.originSlot
                        )
                    )
                }
            }
        }

        // Handle Reroute nodes (virtual routing nodes that must not exist in API prompt)
        if (removeReroutes) {
            var reroutePass = 0
            while (currentNodes.any { it.type.equals("Reroute", ignoreCase = true) } && reroutePass < 100) {
                reroutePass++
                val rerouteNode = currentNodes.first { it.type.equals("Reroute", ignoreCase = true) }
                currentNodes.remove(rerouteNode)
                val inLinks = currentLinks.filter { it.targetNodeId == rerouteNode.id }
                val outLinks = currentLinks.filter { it.originNodeId == rerouteNode.id }
                currentLinks.removeAll(inLinks)
                currentLinks.removeAll(outLinks)

                val primaryInLink = inLinks.firstOrNull()
                if (primaryInLink != null) {
                    for (outLink in outLinks) {
                        currentLinks.add(
                            outLink.copy(
                                originNodeId = primaryInLink.originNodeId,
                                originSlot = primaryInLink.originSlot
                            )
                        )
                    }
                }
            }
        }

        return Pair(currentNodes, currentLinks)
    }

    private fun parseNodes(nodesArray: JsonArray?): List<RawNode> {
        if (nodesArray == null) return emptyList()
        val result = mutableListOf<RawNode>()

        for (el in nodesArray) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val id = obj.get("id")?.asString ?: continue
            val type = obj.get("type")?.asString ?: "Unknown"
            val title = obj.get("title")?.asString?.ifBlank { null }
                ?: (if (obj.has("properties") && obj.getAsJsonObject("properties").has("Node name for S&R"))
                    obj.getAsJsonObject("properties").get("Node name for S&R").asString else null)
                ?: type
            val mode = obj.get("mode")?.asInt ?: 0

            var posX = 0f
            var posY = 0f
            if (obj.has("pos")) {
                val p = obj.get("pos")
                if (p.isJsonArray && p.asJsonArray.size() >= 2) {
                    posX = p.asJsonArray[0].asFloat
                    posY = p.asJsonArray[1].asFloat
                } else if (p.isJsonObject) {
                    posX = p.asJsonObject.get("0")?.asFloat ?: 0f
                    posY = p.asJsonObject.get("1")?.asFloat ?: 0f
                }
            }

            var width = 220f
            var height = 120f
            if (obj.has("size")) {
                val s = obj.get("size")
                if (s.isJsonArray && s.asJsonArray.size() >= 2) {
                    width = s.asJsonArray[0].asFloat
                    height = s.asJsonArray[1].asFloat
                } else if (s.isJsonObject) {
                    width = s.asJsonObject.get("0")?.asFloat ?: 220f
                    height = s.asJsonObject.get("1")?.asFloat ?: 120f
                }
            }

            val inputs = mutableListOf<RawInput>()
            if (obj.has("inputs") && obj.get("inputs").isJsonArray) {
                for (inpEl in obj.getAsJsonArray("inputs")) {
                    if (!inpEl.isJsonObject) continue
                    val inpObj = inpEl.asJsonObject
                    val name = inpObj.get("name")?.asString ?: ""
                    val inpType = inpObj.get("type")?.asString ?: "*"
                    val link = inpObj.get("link")
                    val linkId = if (link != null && !link.isJsonNull) link.asString else null
                    val widgetName = if (inpObj.has("widget") && inpObj.get("widget").isJsonObject) {
                        inpObj.getAsJsonObject("widget").get("name")?.asString
                    } else null

                    inputs.add(RawInput(name = name, type = inpType, linkId = linkId, widgetName = widgetName))
                }
            }

            val outputs = mutableListOf<RawOutput>()
            if (obj.has("outputs") && obj.get("outputs").isJsonArray) {
                for ((idx, outEl) in obj.getAsJsonArray("outputs").withIndex()) {
                    if (!outEl.isJsonObject) continue
                    val outObj = outEl.asJsonObject
                    val name = outObj.get("name")?.asString ?: "out_$idx"
                    val outType = outObj.get("type")?.asString ?: "*"
                    val linksList = mutableListOf<String>()
                    if (outObj.has("links") && outObj.get("links").isJsonArray) {
                        for (l in outObj.getAsJsonArray("links")) {
                            if (l.isJsonPrimitive) linksList.add(l.asString)
                        }
                    }
                    outputs.add(RawOutput(name = name, type = outType, slotIndex = idx, links = linksList))
                }
            }

            val widgetsNamed = if (obj.has("widgets_values_named") && obj.get("widgets_values_named").isJsonObject) {
                obj.getAsJsonObject("widgets_values_named").deepCopy()
            } else null

            val widgetsArr = if (obj.has("widgets_values") && obj.get("widgets_values").isJsonArray) {
                obj.getAsJsonArray("widgets_values").deepCopy()
            } else if (widgetsNamed != null) {
                val arr = JsonArray()
                for ((_, v) in widgetsNamed.entrySet()) {
                    arr.add(v.deepCopy())
                }
                arr
            } else null

            val properties = if (obj.has("properties") && obj.get("properties").isJsonObject) {
                obj.getAsJsonObject("properties").deepCopy()
            } else null

            result.add(
                RawNode(
                    id = id,
                    type = type,
                    title = title,
                    mode = mode,
                    inputs = inputs,
                    outputs = outputs,
                    widgetsValuesNamed = widgetsNamed,
                    widgetsValues = widgetsArr,
                    posX = posX,
                    posY = posY,
                    width = width,
                    height = height,
                    properties = properties
                )
            )
        }

        return result
    }

    private fun parseLinks(linksArray: JsonArray?): List<RawLink> {
        if (linksArray == null) return emptyList()
        val result = mutableListOf<RawLink>()

        for (el in linksArray) {
            if (el.isJsonArray) {
                val arr = el.asJsonArray
                if (arr.size() >= 5) {
                    val id = arr[0].asString
                    val fromNode = arr[1].asString
                    val fromSlot = arr[2].asInt
                    val toNode = arr[3].asString
                    val toSlot = arr[4].asInt
                    val type = if (arr.size() > 5 && !arr[5].isJsonNull) arr[5].asString else "*"
                    result.add(RawLink(id = id, originNodeId = fromNode, originSlot = fromSlot, targetNodeId = toNode, targetSlot = toSlot, type = type))
                }
            } else if (el.isJsonObject) {
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: ""
                val fromNode = obj.get("origin_id")?.asString ?: ""
                val fromSlot = obj.get("origin_slot")?.asInt ?: 0
                val toNode = obj.get("target_id")?.asString ?: ""
                val toSlot = obj.get("target_slot")?.asInt ?: 0
                val type = obj.get("type")?.asString ?: "*"
                result.add(RawLink(id = id, originNodeId = fromNode, originSlot = fromSlot, targetNodeId = toNode, targetSlot = toSlot, type = type))
            }
        }

        return result
    }

    private fun parseSubgraphs(definitionsObj: JsonObject?): Map<String, SubgraphDef> {
        if (definitionsObj == null || !definitionsObj.has("subgraphs") || !definitionsObj.get("subgraphs").isJsonArray) {
            return emptyMap()
        }

        val result = mutableMapOf<String, SubgraphDef>()
        val arr = definitionsObj.getAsJsonArray("subgraphs")

        for (el in arr) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val id = obj.get("id")?.asString ?: continue
            val name = obj.get("name")?.asString ?: id

            val inNodeId = if (obj.has("inputNode") && obj.get("inputNode").isJsonObject) {
                obj.getAsJsonObject("inputNode").get("id")?.asString ?: "-10"
            } else "-10"

            val outNodeId = if (obj.has("outputNode") && obj.get("outputNode").isJsonObject) {
                obj.getAsJsonObject("outputNode").get("id")?.asString ?: "-20"
            } else "-20"

            val inPorts = mutableListOf<SubgraphPortDef>()
            if (obj.has("inputs") && obj.get("inputs").isJsonArray) {
                for ((idx, pEl) in obj.getAsJsonArray("inputs").withIndex()) {
                    if (!pEl.isJsonObject) continue
                    val pObj = pEl.asJsonObject
                    val pId = pObj.get("id")?.asString ?: ""
                    val pName = pObj.get("name")?.asString ?: "input_$idx"
                    val pType = pObj.get("type")?.asString ?: "*"
                    val linkIds = mutableListOf<String>()
                    if (pObj.has("linkIds") && pObj.get("linkIds").isJsonArray) {
                        pObj.getAsJsonArray("linkIds").forEach { linkIds.add(it.asString) }
                    }
                    inPorts.add(SubgraphPortDef(id = pId, name = pName, type = pType, linkIds = linkIds, slotIndex = idx))
                }
            }

            val outPorts = mutableListOf<SubgraphPortDef>()
            if (obj.has("outputs") && obj.get("outputs").isJsonArray) {
                for ((idx, pEl) in obj.getAsJsonArray("outputs").withIndex()) {
                    if (!pEl.isJsonObject) continue
                    val pObj = pEl.asJsonObject
                    val pId = pObj.get("id")?.asString ?: ""
                    val pName = pObj.get("name")?.asString ?: "output_$idx"
                    val pType = pObj.get("type")?.asString ?: "*"
                    val linkIds = mutableListOf<String>()
                    if (pObj.has("linkIds") && pObj.get("linkIds").isJsonArray) {
                        pObj.getAsJsonArray("linkIds").forEach { linkIds.add(it.asString) }
                    }
                    outPorts.add(SubgraphPortDef(id = pId, name = pName, type = pType, linkIds = linkIds, slotIndex = idx))
                }
            }

            val innerNodes = parseNodes(obj.getAsJsonArray("nodes"))
            val innerLinks = parseLinks(obj.getAsJsonArray("links"))

            result[id] = SubgraphDef(
                id = id,
                name = name,
                inputNodeId = inNodeId,
                outputNodeId = outNodeId,
                inputs = inPorts,
                outputs = outPorts,
                nodes = innerNodes,
                links = innerLinks
            )
        }

        return result
    }

    private fun getFallbackParamNames(type: String): List<String> {
        return when {
            type.contains("KSampler", ignoreCase = true) -> listOf("seed", "control_after_generate", "steps", "cfg", "sampler_name", "scheduler", "denoise")
            type.contains("EmptyLatentImage", ignoreCase = true) -> listOf("width", "height", "batch_size")
            type.contains("CLIPTextEncode", ignoreCase = true) -> listOf("text")
            type.contains("CheckpointLoader", ignoreCase = true) -> listOf("ckpt_name")
            type.contains("VAELoader", ignoreCase = true) -> listOf("vae_name")
            type.contains("LoraLoader", ignoreCase = true) -> listOf("lora_name", "strength_model", "strength_clip")
            type.contains("SaveImage", ignoreCase = true) || type.contains("PreviewImage", ignoreCase = true) -> listOf("filename_prefix")
            type.contains("LoadImage", ignoreCase = true) -> listOf("image", "upload")
            type.contains("PrimitiveString", ignoreCase = true) -> listOf("value")
            type.contains("RandomNoise", ignoreCase = true) -> listOf("noise_seed", "control_after_generate")
            type.contains("Flux2Scheduler", ignoreCase = true) -> listOf("steps", "width", "height")
            type.contains("CFGGuider", ignoreCase = true) -> listOf("cfg")
            type.contains("KSamplerSelect", ignoreCase = true) -> listOf("sampler_name")
            else -> emptyList()
        }
    }
}
