package com.comfyport.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComfyClientTest {

    @Test
    fun testCalculateDimensions_SquareOneMegapixel() {
        val (width, height) = ComfyClient.calculateDimensions("1.0", "1:1")
        // 1MP = 1000000 pixels. Square root is 1000.
        // 1000 is divisible by 16 (16 * 62 = 992, 16 * 63 = 1008). 1000 rounds to 1008.
        assertEquals(1008, width)
        assertEquals(1008, height)
    }

    @Test
    fun testCalculateDimensions_AspectRatios() {
        val (width, height) = ComfyClient.calculateDimensions("1.0", "16:9 (Panorama)")
        // w * h = 1000000, w / h = 16 / 9 => w = 1.777 * h => h^2 * 1.777 = 1000000 => h = 750 => w = 1333
        // 750 rounds to 752 (divisible by 16: 16 * 47 = 752)
        // 1333 rounds to 1328 (divisible by 16: 16 * 83 = 1328)
        assertEquals(1328, width)
        assertEquals(752, height)
    }

    @Test
    fun testCalculateDimensions_Divisibility() {
        val aspectRatios = listOf("1:1", "16:9", "3:4", "2:3", "4:5")
        val megapixels = listOf("0.5", "1.0", "2.0")

        for (mp in megapixels) {
            for (ratio in aspectRatios) {
                val (w, h) = ComfyClient.calculateDimensions(mp, ratio)
                assertEquals("Width $w must be divisible by 16", 0, w % 16)
                assertEquals("Height $h must be divisible by 16", 0, h % 16)
                assertTrue("Width $w must be at least 16", w >= 16)
                assertTrue("Height $h must be at least 16", h >= 16)
            }
        }
    }

    @Test
    fun testResolveDimensions_CustomResolution() {
        val settings = com.comfyport.data.AppSettings(
            customWidth = 1024,
            customHeight = 768
        )
        val (w, h) = ComfyClient.resolveDimensions(settings)
        assertEquals(1024, w)
        assertEquals(768, h)
        assertEquals(0, w % 8)
        assertEquals(0, h % 8)
    }

    @Test
    fun testResolveDimensions_CustomResolutionRounding() {
        val settings = com.comfyport.data.AppSettings(
            customWidth = 1021,
            customHeight = 763
        )
        val (w, h) = ComfyClient.resolveDimensions(settings)
        assertEquals(1024, w)
        assertEquals(768, h)
    }

    @Test
    fun testResolutionPresets_SDXLANDSD15() {
        val sdxl = com.comfyport.data.ResolutionPresets.SDXL
        assertTrue(sdxl.isNotEmpty())
        assertEquals(1024, sdxl.first().width)
        assertEquals(1024, sdxl.first().height)

        for (preset in sdxl) {
            assertEquals("SDXL width must be divisible by 8", 0, preset.width % 8)
            assertEquals("SDXL height must be divisible by 8", 0, preset.height % 8)
        }

        val sd15 = com.comfyport.data.ResolutionPresets.SD_15
        assertTrue(sd15.isNotEmpty())
        assertEquals(512, sd15.first().width)
        assertEquals(512, sd15.first().height)

        for (preset in sd15) {
            assertEquals("SD 1.5 width must be divisible by 8", 0, preset.width % 8)
            assertEquals("SD 1.5 height must be divisible by 8", 0, preset.height % 8)
        }
    }

    @Test
    fun testFormatTypeDetection() {
        val uiJson = """{"nodes": [{"id": 1, "type": "KSampler"}]}"""
        assertEquals(com.comfyport.data.FormatType.UI_STANDARD, com.comfyport.data.FormatType.detect(uiJson))

        val uiWrapperJson = """{"workflow": {"nodes": [{"id": 1, "type": "KSampler"}]}}"""
        assertEquals(com.comfyport.data.FormatType.UI_STANDARD, com.comfyport.data.FormatType.detect(uiWrapperJson))

        val apiJson = """{"1": {"class_type": "KSampler", "inputs": {}}}"""
        assertEquals(com.comfyport.data.FormatType.API_READY, com.comfyport.data.FormatType.detect(apiJson))

        val apiWrapperJson = """{"prompt": {"1": {"class_type": "KSampler", "inputs": {}}}}"""
        assertEquals(com.comfyport.data.FormatType.API_READY, com.comfyport.data.FormatType.detect(apiWrapperJson))
    }

    @Test
    fun testEmptyConditioningInApiFormat() {
        val apiJson = """
        {
            "1": {
                "class_type": "KSampler",
                "inputs": {
                    "steps": 25,
                    "cfg": 7.0,
                    "sampler_name": "euler",
                    "scheduler": "normal",
                    "positive": ["2", 0],
                    "negative": ["3", 0],
                    "latent_image": ["4", 0]
                }
            },
            "2": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "A beautiful sunset over the neon cyberpunk ocean"
                }
            },
            "3": {
                "class_type": "EmptyConditioning",
                "inputs": {}
            },
            "4": {
                "class_type": "EmptyLatentImage",
                "inputs": {
                    "width": 1024,
                    "height": 768,
                    "batch_size": 2
                }
            }
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("API EmptyCond Test", "test_api.json", apiJson)
        assertEquals("A beautiful sunset over the neon cyberpunk ocean", preview.prompt)
        assertTrue("Preview should recognize EmptyConditioning", preview.hasEmptyConditioning)
        assertEquals("Empty Conditioning", preview.negativePrompt)
        assertEquals(1024, preview.width)
        assertEquals(768, preview.height)
        assertEquals(2, preview.batchSize)
        assertEquals(25, preview.steps)
        assertEquals(7.0f, preview.cfg ?: 0f, 0.001f)
        assertEquals("euler", preview.samplerName)
        assertEquals("normal", preview.scheduler)
    }

    @Test
    fun testEmptyConditioningInUiFormat() {
        val uiJson = """
        {
            "nodes": [
                {
                    "id": 1,
                    "type": "KSampler",
                    "inputs": [
                        {"name": "positive", "link": 101},
                        {"name": "negative", "link": 102}
                    ],
                    "widgets_values": [0, "randomize", 20, 8.0, "euler_ancestral", "karras", 1.0]
                },
                {
                    "id": 2,
                    "type": "CLIPTextEncode",
                    "outputs": [{"name": "CONDITIONING", "links": [101]}],
                    "widgets_values": ["A serene landscape in the morning light"]
                },
                {
                    "id": 3,
                    "type": "EmptyConditioning",
                    "outputs": [{"name": "CONDITIONING", "links": [102]}],
                    "widgets_values": []
                },
                {
                    "id": 4,
                    "type": "EmptyLatentImage",
                    "widgets_values": [1280, 720, 1]
                }
            ],
            "links": [
                [101, 2, 0, 1, 0, "CONDITIONING"],
                [102, 3, 0, 1, 1, "CONDITIONING"]
            ]
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("UI EmptyCond Test", "test_ui.json", uiJson)
        assertEquals("A serene landscape in the morning light", preview.prompt)
        assertTrue("UI format should recognize EmptyConditioning", preview.hasEmptyConditioning)
        assertEquals("Empty Conditioning", preview.negativePrompt)
        assertEquals(1280, preview.width)
        assertEquals(720, preview.height)
        assertEquals(20, preview.steps)
        assertEquals("euler_ancestral", preview.samplerName)
    }

    @Test
    fun testArtComfyUiFileIfPresent() {
        val file = java.io.File("art comfyui.json")
        val altFile = java.io.File("../art comfyui.json")
        val target = if (file.exists()) file else if (altFile.exists()) altFile else null
        if (target != null && target.exists()) {
            val content = target.readText()
            val format = com.comfyport.data.FormatType.detect(content)
            assertEquals(com.comfyport.data.FormatType.UI_STANDARD, format)

            val preview = ComfyClient.parseWorkflowPreview("Art ComfyUI", target.name, content)
            assertTrue("Should parse nodes", preview.nodeCount > 0)
            assertTrue("Should detect node types", preview.nodeTypes.isNotEmpty())
        }
    }

    @Test
    fun testNegativePromptExtractionAndImageMaskDetection() {
        val workflowJson = """
        {
            "1": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": 12345,
                    "steps": 30,
                    "cfg": 8.0,
                    "positive": ["2", 0],
                    "negative": ["3", 0],
                    "latent_image": ["4", 0]
                }
            },
            "2": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "A beautiful cinematic portrait of an astronaut"
                }
            },
            "3": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "blurry, low quality, distorted, extra limbs"
                }
            },
            "4": {
                "class_type": "LoadImage",
                "inputs": {
                    "image": "input_portrait.png"
                }
            },
            "5": {
                "class_type": "LoadImageMask",
                "inputs": {
                    "image": "mask_face.png",
                    "channel": "red"
                }
            }
        }
        """.trimIndent()

        val meta = ComfyClient.extractWorkflowMetadata(workflowJson)
        assertEquals("A beautiful cinematic portrait of an astronaut", meta.prompt)
        assertEquals("blurry, low quality, distorted, extra limbs", meta.negativePrompt)
        assertTrue("Should detect image input node", meta.hasImageInput)
        assertTrue("Should detect mask input node", meta.hasMaskInput)

        val preview = ComfyClient.parseWorkflowPreview("Inpainting Workflow", "inpaint.json", workflowJson)
        assertEquals("A beautiful cinematic portrait of an astronaut", preview.prompt)
        assertEquals("blurry, low quality, distorted, extra limbs", preview.negativePrompt)
        assertTrue("Preview should flag image input", preview.hasImageInput)
        assertTrue("Preview should flag mask input", preview.hasMaskInput)
    }

    @Test
    fun testModularSettingsDefaultsAndToggles() {
        val settings = com.comfyport.data.AppSettings()
        // All modular toggles default to true
        assertTrue(settings.showWorkflowSelector)
        assertTrue(settings.showResolutionControls)
        assertTrue(settings.showBatchSizeControls)
        assertTrue(settings.showNegativePrompt)
        assertTrue(settings.showSeedControls)
        assertTrue(settings.showImageMaskInput)

        // Toggles can be disabled independently
        val customized = settings.copy(
            showNegativePrompt = false,
            showImageMaskInput = false,
            seedMode = com.comfyport.data.SeedMode.Custom,
            customSeedValue = 998877L
        )
        assertFalse(customized.showNegativePrompt)
        assertFalse(customized.showImageMaskInput)
        assertTrue(customized.showWorkflowSelector)
        assertTrue(customized.showSeedControls)
        assertEquals(com.comfyport.data.SeedMode.Custom, customized.seedMode)
        assertEquals(998877L, customized.customSeedValue)
    }

    @Test
    fun testRenamedMultilineTextBoxInApiFormat() {
        val apiJson = """
        {
            "1": {
                "class_type": "KSampler",
                "inputs": {
                    "steps": 20,
                    "positive": ["2", 0],
                    "negative": ["3", 0],
                    "latent_image": ["4", 0]
                }
            },
            "2": {
                "class_type": "Text Multiline",
                "inputs": {
                    "text": "Cyberpunk city street at night, neon reflections in rain"
                },
                "_meta": {
                    "title": "POSITIVE PROMPT"
                }
            },
            "3": {
                "class_type": "PrimitiveNode",
                "inputs": {
                    "value": "blurry, deformed, oversaturated, lowres"
                },
                "_meta": {
                    "title": "NEGATIVE PROMPT"
                }
            },
            "4": {
                "class_type": "EmptyLatentImage",
                "inputs": {
                    "width": 1024,
                    "height": 1024,
                    "batch_size": 1
                }
            }
        }
        """.trimIndent()

        val meta = ComfyClient.extractWorkflowMetadata(apiJson)
        assertEquals("Cyberpunk city street at night, neon reflections in rain", meta.prompt)
        assertEquals("blurry, deformed, oversaturated, lowres", meta.negativePrompt)
        assertEquals(1024, meta.width)
        assertEquals(1024, meta.height)

        val preview = ComfyClient.parseWorkflowPreview("Renamed Textbox Test", "test.json", apiJson)
        assertEquals("Cyberpunk city street at night, neon reflections in rain", preview.prompt)
        assertEquals("blurry, deformed, oversaturated, lowres", preview.negativePrompt)


    }

    @Test
    fun testRenamedMultilineTextBoxInUiFormat() {
        val uiJson = """
        {
            "nodes": [
                {
                    "id": 1,
                    "type": "KSampler",
                    "inputs": [
                        {"name": "positive", "link": 101},
                        {"name": "negative", "link": 102}
                    ]
                },
                {
                    "id": 2,
                    "type": "Text Multiline",
                    "title": "POSITIVE PROMPT",
                    "widgets_values": ["A serene mountain lake at sunrise"]
                },
                {
                    "id": 3,
                    "type": "PrimitiveNode",
                    "title": "NEGATIVE PROMPT",
                    "widgets_values": ["ugly, noisy, text, watermark"]
                }
            ]
        }
        """.trimIndent()

        val meta = ComfyClient.extractWorkflowMetadata(uiJson)
        assertEquals("A serene mountain lake at sunrise", meta.prompt)
        assertEquals("ugly, noisy, text, watermark", meta.negativePrompt)

        val preview = ComfyClient.parseWorkflowPreview("UI Renamed Test", "test_ui.json", uiJson)
        assertEquals("A serene mountain lake at sunrise", preview.prompt)
        assertEquals("ugly, noisy, text, watermark", preview.negativePrompt)
    }

    @Test
    fun testWorkflowWithoutEmptyLatentImage() {
        // Advanced img2img / inpaint workflow where latents come from VAEEncode instead of EmptyLatentImage
        val img2imgApiJson = """
        {
            "1": {
                "class_type": "KSampler",
                "inputs": {
                    "steps": 25,
                    "cfg": 6.5,
                    "sampler_name": "euler",
                    "scheduler": "karras",
                    "positive": ["2", 0],
                    "negative": ["3", 0],
                    "latent_image": ["5", 0]
                }
            },
            "2": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "Oil painting style landscape"
                }
            },
            "3": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "photorealistic, 3d render"
                }
            },
            "4": {
                "class_type": "LoadImage",
                "inputs": {
                    "image": "source_painting.png"
                },
                "_meta": {
                    "title": "INPUT IMAGE"
                }
            },
            "5": {
                "class_type": "VAEEncode",
                "inputs": {
                    "pixels": ["4", 0],
                    "vae": ["6", 2]
                }
            },
            "6": {
                "class_type": "CheckpointLoaderSimple",
                "inputs": {
                    "ckpt_name": "v1-5-pruned-emaonly.safetensors"
                }
            },
            "7": {
                "class_type": "VAEDecode",
                "inputs": {
                    "samples": ["1", 0],
                    "vae": ["6", 2]
                }
            },
            "8": {
                "class_type": "SaveImage",
                "inputs": {
                    "images": ["7", 0]
                }
            }
        }
        """.trimIndent()

        // 1. Metadata extraction should succeed without error and have null dimensions
        val meta = ComfyClient.extractWorkflowMetadata(img2imgApiJson)
        assertEquals("Oil painting style landscape", meta.prompt)
        assertEquals("photorealistic, 3d render", meta.negativePrompt)
        assertNull("Width must be null when no EmptyLatentImage is present", meta.width)
        assertNull("Height must be null when no EmptyLatentImage is present", meta.height)
        assertTrue("Image input must be detected", meta.hasImageInput)
        assertFalse("Mask input must be false", meta.hasMaskInput)

        // 2. Preview parsing should succeed without error
        val preview = ComfyClient.parseWorkflowPreview("Img2Img Pipeline", "img2img.json", img2imgApiJson)
        assertEquals("Oil painting style landscape", preview.prompt)
        assertEquals("photorealistic, 3d render", preview.negativePrompt)
        assertNull(preview.width)
        assertNull(preview.height)
        assertEquals("v1-5-pruned-emaonly.safetensors", preview.modelName)
        assertEquals(25, preview.steps)
        assertEquals(6.5f, preview.cfg ?: 0f, 0.001f)
        assertEquals("euler", preview.samplerName)
        assertEquals("karras", preview.scheduler)
        assertTrue(preview.hasImageInput)
        assertFalse(preview.hasMaskInput)
    }

    @Test
    fun testImageLoaderVsMaskLoaderSeparation() {
        val workflowJson = """
        {
            "1": {
                "class_type": "LoadImage",
                "inputs": { "image": "portrait.png" },
                "_meta": { "title": "INPUT IMAGE" }
            },
            "2": {
                "class_type": "LoadImageMask",
                "inputs": { "image": "mask.png", "channel": "alpha" },
                "_meta": { "title": "INPAINT MASK" }
            }
        }
        """.trimIndent()

        val meta = ComfyClient.extractWorkflowMetadata(workflowJson)
        assertTrue("Should detect regular image input", meta.hasImageInput)
        assertTrue("Should detect mask input", meta.hasMaskInput)
    }

    @Test
    fun testNestedWorkflowWrapperDetection() {
        val uiNested = """{"workflow": {"nodes": [{"id": 1, "type": "KSampler"}]}}"""
        assertEquals(com.comfyport.data.FormatType.UI_STANDARD, com.comfyport.data.FormatType.detect(uiNested))

        val apiNestedWorkflow = """{"workflow": {"1": {"class_type": "KSampler", "inputs": {}}}}"""
        assertEquals(com.comfyport.data.FormatType.API_READY, com.comfyport.data.FormatType.detect(apiNestedWorkflow))

        val apiNestedPrompt = """{"prompt": {"1": {"class_type": "KSampler", "inputs": {}}}}"""
        assertEquals(com.comfyport.data.FormatType.API_READY, com.comfyport.data.FormatType.detect(apiNestedPrompt))

        val apiNestedOutput = """{"output": {"1": {"class_type": "KSampler", "inputs": {}}}}"""
        assertEquals(com.comfyport.data.FormatType.API_READY, com.comfyport.data.FormatType.detect(apiNestedOutput))
    }

    @Test
    fun testWorkflowNodeInfoExtraction_ApiFormat() {
        val apiJson = """
        {
            "1": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": 42,
                    "steps": 20,
                    "positive": ["2", 0],
                    "negative": ["3", 0],
                    "latent_image": ["4", 0]
                },
                "_meta": { "title": "Main KSampler" }
            },
            "2": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "Majestic eagle soaring over alpine mountains" },
                "_meta": { "title": "Positive Prompt Box" }
            },
            "3": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "blurry, dark, noisy" },
                "_meta": { "title": "Negative Prompt Box" }
            },
            "4": {
                "class_type": "EmptyLatentImage",
                "inputs": { "width": 1024, "height": 768, "batch_size": 1 }
            },
            "5": {
                "class_type": "SaveImage",
                "inputs": { "images": ["1", 0], "filename_prefix": "ComfyPort" }
            }
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("API Test", "api.json", apiJson)
        assertEquals(5, preview.nodes.size)

        val ksamplerNode = preview.nodes.first { it.id == "1" }
        assertEquals("Main KSampler", ksamplerNode.title)
        assertEquals("KSampler", ksamplerNode.type)
        assertTrue(ksamplerNode.incomingLinks.any { it.contains("positive: from Node #2") })

        val posNode = preview.nodes.first { it.id == "2" }
        assertEquals("Positive Prompt Box", posNode.title)
        assertEquals("CLIPTextEncode", posNode.type)
        assertEquals("Majestic eagle soaring over alpine mountains", posNode.inputs["text"])
        assertTrue(posNode.outgoingLinks.any { it.contains("↳ to Node #1 (positive)") })

        val latentNode = preview.nodes.first { it.id == "4" }
        assertEquals("1024", latentNode.inputs["width"])
        assertEquals("768", latentNode.inputs["height"])
    }

    @Test
    fun testWorkflowNodeInfoExtraction_UiFormat() {
        val uiJson = """
        {
            "nodes": [
                {
                    "id": 10,
                    "type": "CLIPTextEncode",
                    "title": "Positive Text",
                    "widgets_values": ["Sunset over ocean waves"],
                    "outputs": [{"name": "CONDITIONING", "links": [101]}]
                },
                {
                    "id": 20,
                    "type": "KSampler",
                    "title": "Sampler Node",
                    "inputs": [{"name": "positive", "link": 101}],
                    "widgets_values": [12345, "randomize", 30, 7.0, "euler", "normal", 1.0]
                }
            ]
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("UI Test", "ui.json", uiJson)
        assertEquals(2, preview.nodes.size)

        val textNode = preview.nodes.first { it.id == "10" }
        assertEquals("Positive Text", textNode.title)
        assertEquals("CLIPTextEncode", textNode.type)
        assertEquals("Sunset over ocean waves", textNode.inputs["param_0"])
        assertTrue(textNode.outgoingLinks.isNotEmpty())

        val samplerNode = preview.nodes.first { it.id == "20" }
        assertEquals("Sampler Node", samplerNode.title)
        assertTrue(samplerNode.incomingLinks.any { it.contains("positive (link #101)") })
    }

    @Test
    fun testWorkflowNodeMapping_ActiveMappingHonor() {
        val apiJson = """
        {
            "1": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "Default positive text" }
            },
            "2": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "Special mapped positive text" }
            },
            "3": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "Special mapped negative text" }
            },
            "4": {
                "class_type": "EmptyLatentImage",
                "inputs": { "width": 512, "height": 512 }
            },
            "5": {
                "class_type": "EmptyLatentImage",
                "inputs": { "width": 1280, "height": 720 }
            }
        }
        """.trimIndent()

        val mapping = com.comfyport.data.WorkflowNodeMapping(
            positivePromptNodeId = "2",
            negativePromptNodeId = "3",
            emptyLatentNodeId = "5"
        )

        assertTrue(mapping.isCustomized)
        assertEquals(3, mapping.customCount)

        val previewWithMapping = ComfyClient.parseWorkflowPreview("Mapping Test", "map.json", apiJson, mapping)
        assertEquals("Special mapped positive text", previewWithMapping.prompt)
        assertEquals("Special mapped negative text", previewWithMapping.negativePrompt)
        assertEquals(1280, previewWithMapping.width)
        assertEquals(720, previewWithMapping.height)
    }

    @Test
    fun testWorkflowNodeMapping_CustomInputsInjection() {
        val apiJson = """
        {
            "10": {
                "class_type": "PadImageForOutpaint",
                "inputs": {
                    "left": 0,
                    "top": 0,
                    "right": 0,
                    "bottom": 0,
                    "feather": 20
                }
            },
            "20": {
                "class_type": "ControlNetApply",
                "inputs": {
                    "strength": 0.5,
                    "enabled": false
                }
            }
        }
        """.trimIndent()

        val mapping = com.comfyport.data.WorkflowNodeMapping(
            customInputs = listOf(
                com.comfyport.data.CustomWorkflowInput(
                    nodeId = "10",
                    widgetName = "top",
                    widgetType = "INT",
                    value = "256"
                ),
                com.comfyport.data.CustomWorkflowInput(
                    nodeId = "10",
                    widgetName = "feather",
                    widgetType = "INT",
                    value = "48"
                ),
                com.comfyport.data.CustomWorkflowInput(
                    nodeId = "20",
                    widgetName = "strength",
                    widgetType = "FLOAT",
                    value = "0.85"
                ),
                com.comfyport.data.CustomWorkflowInput(
                    nodeId = "20",
                    widgetName = "enabled",
                    widgetType = "BOOLEAN",
                    value = "true"
                )
            )
        )

        assertTrue(mapping.isCustomized)
        assertEquals(4, mapping.customCount)

        val workflowObj = com.google.gson.JsonParser.parseString(apiJson).asJsonObject
        com.comfyport.network.api.WorkflowPreparer.injectWorkflowParameters(
            workflowObj = workflowObj,
            prompt = "A majestic landscape",
            customMapping = mapping
        )

        val node10Inputs = workflowObj.getAsJsonObject("10").getAsJsonObject("inputs")
        assertEquals(256, node10Inputs.get("top").asInt)
        assertEquals(48, node10Inputs.get("feather").asInt)
        assertEquals(0, node10Inputs.get("left").asInt) // Unmodified

        val node20Inputs = workflowObj.getAsJsonObject("20").getAsJsonObject("inputs")
        assertEquals(0.85, node20Inputs.get("strength").asDouble, 0.001)
        assertTrue(node20Inputs.get("enabled").asBoolean)
    }

    @Test
    fun testVisualGraphCanvasExtraction_UiFormat() {
        val uiJson = """
        {
            "nodes": [
                {
                    "id": 1,
                    "type": "CheckpointLoaderSimple",
                    "pos": [100, 200],
                    "size": [280, 140],
                    "outputs": [
                        { "name": "MODEL", "type": "MODEL", "links": [11] },
                        { "name": "CLIP", "type": "CLIP", "links": [12] }
                    ]
                },
                {
                    "id": 2,
                    "type": "CLIPTextEncode",
                    "pos": [450, 200],
                    "size": [240, 120],
                    "inputs": [
                        { "name": "clip", "type": "CLIP", "link": 12 }
                    ],
                    "outputs": [
                        { "name": "CONDITIONING", "type": "CONDITIONING", "links": [13] }
                    ],
                    "widgets_values": ["A majestic mountain landscape"]
                }
            ],
            "links": [
                [12, 1, 1, 2, 0, "CLIP"]
            ]
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("Canvas UI Test", "test_canvas.json", uiJson)
        assertEquals(2, preview.nodes.size)
        assertEquals(1, preview.wires.size)

        val wire = preview.wires.first()
        assertEquals("12", wire.id)
        assertEquals("1", wire.fromNodeId)
        assertEquals(1, wire.fromSlotIndex)
        assertEquals("2", wire.toNodeId)
        assertEquals(0, wire.toSlotIndex)
        assertEquals("CLIP", wire.wireType)

        val node1 = preview.nodes.first { it.id == "1" }
        assertEquals(100f, node1.posX)
        assertEquals(200f, node1.posY)
        assertEquals(280f, node1.width)
        assertEquals(140f, node1.height)
        assertEquals(2, node1.outputSlots.size)
        assertEquals("MODEL", node1.outputSlots[0].name)
        assertEquals("CLIP", node1.outputSlots[1].name)

        val node2 = preview.nodes.first { it.id == "2" }
        assertEquals(450f, node2.posX)
        assertEquals(200f, node2.posY)
        assertEquals(240f, node2.width)
        assertEquals(120f, node2.height)
        assertEquals(1, node2.inputSlots.size)
        assertEquals("clip", node2.inputSlots[0].name)
        assertEquals(12, node2.inputSlots[0].linkId)
    }

    @Test
    fun testVisualGraphCanvasExtraction_ApiFormat() {
        val apiJson = """
        {
            "4": {
                "class_type": "CheckpointLoaderSimple",
                "inputs": { "ckpt_name": "v1-5-pruned.ckpt" }
            },
            "6": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                    "text": "masterpiece, anime girl",
                    "clip": ["4", 1]
                }
            },
            "3": {
                "class_type": "KSampler",
                "inputs": {
                    "model": ["4", 0],
                    "positive": ["6", 0],
                    "seed": 424242,
                    "steps": 25,
                    "cfg": 7.5
                }
            }
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("Canvas API Test", "test_api.json", apiJson)
        assertEquals(3, preview.nodes.size)
        assertTrue("Should extract graph wires connecting nodes", preview.wires.size >= 3)

        // Node 4 is source (rank 0)
        val node4 = preview.nodes.first { it.id == "4" }
        val node6 = preview.nodes.first { it.id == "6" }
        val node3 = preview.nodes.first { it.id == "3" }

        // Rank 0 should be leftmost (lowest posX)
        assertTrue("Source node 4 should be to the left of node 6", node4.posX < node6.posX)
        assertTrue("Node 6 should be to the left of sampler node 3", node6.posX < node3.posX)

        // Check wire connections
        val clipWire = preview.wires.firstOrNull { it.fromNodeId == "4" && it.toNodeId == "6" }
        assertTrue("Wire connecting node 4 to node 6 must exist", clipWire != null)
        assertEquals("CLIP", clipWire?.wireType)

        val modelWire = preview.wires.firstOrNull { it.fromNodeId == "4" && it.toNodeId == "3" }
        assertTrue("Wire connecting node 4 to node 3 must exist", modelWire != null)
        assertEquals("MODEL", modelWire?.wireType)
    }

    @Test
    fun testConvertWorkflowWithSubgraphs_ExactUserWorkflow() {
        val userWfJson = """
{
  "id": "ca55a6d4-1565-4da9-98bf-4acdf9ca0399",
  "revision": 0,
  "last_node_id": 252,
  "last_link_id": 312,
  "nodes": [
    {
      "id": 156,
      "type": "CLIPLoaderGGUF",
      "pos": [-350, -510],
      "size": [270, 90],
      "outputs": [{"name": "CLIP", "type": "CLIP", "links": [75]}],
      "widgets_values_named": {"clip_name": "Qwen3-8B-Q2_K_L.gguf", "type": "flux2"}
    },
    {
      "id": 155,
      "type": "UnetLoaderGGUF",
      "pos": [-350, -610],
      "size": [270, 60],
      "outputs": [{"name": "MODEL", "type": "MODEL", "links": [74]}],
      "widgets_values_named": {"unet_name": "flux-2-klein-9b-Q4_K_M.gguf"}
    },
    {
      "id": "75:72",
      "type": "VAELoader",
      "pos": [-350, -350],
      "size": [270, 60],
      "outputs": [{"name": "VAE", "type": "VAE", "links": [306, 307]}],
      "widgets_values_named": {"vae_name": "full_encoder_small_decoder.safetensors"}
    },
    {
      "id": 188,
      "type": "LoadImage",
      "pos": [-360, -240],
      "size": [290, 320],
      "outputs": [
        {"name": "IMAGE", "type": "IMAGE", "links": [140, 146]},
        {"name": "MASK", "type": "MASK", "links": [139]}
      ],
      "widgets_values_named": {
        "image": "clipspace-painted-masked-1789076358763.png [input]",
        "upload": "image"
      }
    },
    {
      "id": 167,
      "type": "c885c873-234a-40ca-9d51-db78be6c18f6",
      "pos": [800, -600],
      "size": [220, 190],
      "inputs": [
        {"name": "model", "type": "MODEL", "link": 74},
        {"name": "clip", "type": "CLIP", "link": 75},
        {"name": "text_1", "type": "STRING", "link": 85},
        {"name": "text", "type": "STRING", "link": null}
      ],
      "outputs": [
        {"name": "MODEL", "type": "MODEL", "links": [287]},
        {"name": "CONDITIONING_2", "type": "CONDITIONING", "links": [285]},
        {"name": "CONDITIONING_3", "type": "CONDITIONING", "links": [284]}
      ],
      "widgets_values_named": {"text_1": "", "text": ""}
    },
    {
      "id": 163,
      "type": "PrimitiveStringMultiline",
      "pos": [-350, -950],
      "size": [270, 290],
      "outputs": [{"name": "STRING", "type": "STRING", "links": [85]}],
      "widgets_values_named": {
        "value": "change the color of this tshirt to green without changing the structure or folds of the tshirt"
      }
    },
    {
      "id": 180,
      "type": "ImageUncropByMask",
      "pos": [1550, -600],
      "size": [200, 90],
      "inputs": [
        {"name": "destination", "type": "IMAGE", "link": 146},
        {"name": "source", "type": "IMAGE", "link": 293},
        {"name": "mask", "type": "MASK", "link": 258},
        {"name": "bbox", "type": "BBOX", "link": 143}
      ],
      "outputs": [{"name": "image", "type": "IMAGE", "links": [135]}]
    },
    {
      "id": 185,
      "type": "PreviewImage",
      "pos": [1940, -620],
      "size": [590, 830],
      "inputs": [{"name": "images", "type": "IMAGE", "link": 135}]
    },
    {
      "id": 247,
      "type": "8cd24183-5203-43ca-b819-48ab19eb2c5e",
      "pos": [1360, -600],
      "size": [160, 130],
      "inputs": [
        {"name": "model", "type": "MODEL", "link": 287},
        {"name": "positive", "type": "CONDITIONING", "link": 294},
        {"name": "negative", "type": "CONDITIONING", "link": 295},
        {"name": "latent_image", "type": "LATENT", "link": 291},
        {"name": "vae", "type": "VAE", "link": 307}
      ],
      "outputs": [{"name": "IMAGE", "type": "IMAGE", "links": [293]}]
    }
  ],
  "links": [
    [74, 155, 0, 167, 0, "MODEL"],
    [75, 156, 0, 167, 1, "CLIP"],
    [85, 163, 0, 167, 2, "STRING"],
    [135, 180, 0, 185, 0, "IMAGE"],
    [146, 188, 0, 180, 0, "IMAGE"],
    [287, 167, 0, 247, 0, "MODEL"],
    [293, 247, 0, 180, 1, "IMAGE"]
  ],
  "definitions": {
    "subgraphs": [
      {
        "id": "c885c873-234a-40ca-9d51-db78be6c18f6",
        "name": "Model Encoding",
        "inputNode": {"id": -10},
        "outputNode": {"id": -20},
        "inputs": [
          {"id": "inp1", "name": "model", "type": "MODEL", "linkIds": [31]},
          {"id": "inp2", "name": "clip", "type": "CLIP", "linkIds": [32]},
          {"id": "inp3", "name": "text_1", "type": "STRING", "linkIds": [83]},
          {"id": "inp4", "name": "text", "type": "STRING", "linkIds": [84]}
        ],
        "outputs": [
          {"id": "out1", "name": "MODEL", "type": "MODEL", "linkIds": [62]},
          {"id": "out2", "name": "CONDITIONING_2", "type": "CONDITIONING", "linkIds": [81]},
          {"id": "out3", "name": "CONDITIONING_3", "type": "CONDITIONING", "linkIds": [82]}
        ],
        "nodes": [
          {
            "id": 157,
            "type": "LoraLoader",
            "pos": [480, 130],
            "size": [300, 130],
            "inputs": [
              {"name": "model", "type": "MODEL", "link": 31},
              {"name": "clip", "type": "CLIP", "link": 32}
            ],
            "outputs": [
              {"name": "MODEL", "type": "MODEL", "links": [33]},
              {"name": "CLIP", "type": "CLIP", "links": [34]}
            ],
            "widgets_values_named": {
              "lora_name": "lenovo_flux_klein9b.safetensors",
              "strength_model": 1,
              "strength_clip": 1
            }
          },
          {
            "id": 158,
            "type": "LoraLoader",
            "pos": [480, 300],
            "size": [300, 130],
            "inputs": [
              {"name": "model", "type": "MODEL", "link": 33},
              {"name": "clip", "type": "CLIP", "link": 34}
            ],
            "outputs": [
              {"name": "MODEL", "type": "MODEL", "links": [62]},
              {"name": "CLIP", "type": "CLIP", "links": [58]}
            ],
            "widgets_values_named": {
              "lora_name": "klein_snofs_v1_4.safetensors",
              "strength_model": 1,
              "strength_clip": 1
            }
          },
          {
            "id": "75:74",
            "type": "CLIPTextEncode",
            "pos": [820, 160],
            "size": [280, 90],
            "inputs": [
              {"name": "clip", "type": "CLIP", "link": 58},
              {"name": "text", "type": "STRING", "link": 83}
            ],
            "outputs": [{"name": "CONDITIONING", "type": "CONDITIONING", "links": [81]}],
            "title": "CLIP Text Encode (Positive Prompt)"
          },
          {
            "id": 166,
            "type": "CLIPTextEncode",
            "pos": [820, 290],
            "size": [300, 340],
            "inputs": [
              {"name": "clip", "type": "CLIP", "link": 58},
              {"name": "text", "type": "STRING", "link": 84}
            ],
            "outputs": [{"name": "CONDITIONING", "type": "CONDITIONING", "links": [82]}],
            "title": "CLIP Text Encode (Negative Prompt)"
          }
        ],
        "links": [
          {"id": 31, "origin_id": -10, "origin_slot": 0, "target_id": 157, "target_slot": 0, "type": "MODEL"},
          {"id": 32, "origin_id": -10, "origin_slot": 1, "target_id": 157, "target_slot": 1, "type": "CLIP"},
          {"id": 33, "origin_id": 157, "origin_slot": 0, "target_id": 158, "target_slot": 0, "type": "MODEL"},
          {"id": 34, "origin_id": 157, "origin_slot": 1, "target_id": 158, "target_slot": 1, "type": "CLIP"},
          {"id": 58, "origin_id": 158, "origin_slot": 1, "target_id": "75:74", "target_slot": 0, "type": "CLIP"},
          {"id": 62, "origin_id": 158, "origin_slot": 0, "target_id": -20, "target_slot": 0, "type": "MODEL"},
          {"id": 81, "origin_id": "75:74", "origin_slot": 0, "target_id": -20, "target_slot": 1, "type": "CONDITIONING"},
          {"id": 82, "origin_id": 166, "origin_slot": 0, "target_id": -20, "target_slot": 2, "type": "CONDITIONING"},
          {"id": 83, "origin_id": -10, "origin_slot": 2, "target_id": "75:74", "target_slot": 1, "type": "STRING"},
          {"id": 84, "origin_id": -10, "origin_slot": 3, "target_id": 166, "target_slot": 1, "type": "STRING"}
        ]
      },
      {
        "id": "8cd24183-5203-43ca-b819-48ab19eb2c5e",
        "name": "Sampler",
        "inputNode": {"id": -10},
        "outputNode": {"id": -20},
        "inputs": [
          {"id": "sinp1", "name": "model", "type": "MODEL", "linkIds": [212]}
        ],
        "outputs": [
          {"id": "sout1", "name": "IMAGE", "type": "IMAGE", "linkIds": [216]}
        ],
        "nodes": [
          {
            "id": 224,
            "type": "SamplerCustomAdvanced",
            "pos": [1920, -720],
            "size": [220, 110],
            "inputs": [{"name": "model", "type": "MODEL", "link": 212}],
            "outputs": [{"name": "output", "type": "LATENT", "links": [211]}]
          },
          {
            "id": 221,
            "type": "VAEDecode",
            "pos": [2330, -820],
            "size": [140, 50],
            "inputs": [{"name": "samples", "type": "LATENT", "link": 211}],
            "outputs": [{"name": "IMAGE", "type": "IMAGE", "links": [216]}]
          }
        ],
        "links": [
          {"id": 212, "origin_id": -10, "origin_slot": 0, "target_id": 224, "target_slot": 0, "type": "MODEL"},
          {"id": 211, "origin_id": 224, "origin_slot": 0, "target_id": 221, "target_slot": 0, "type": "LATENT"},
          {"id": 216, "origin_id": 221, "origin_slot": 0, "target_id": -20, "target_slot": 0, "type": "IMAGE"}
        ]
      }
    ]
  }
}
        """.trimIndent()

        // 1. Test native UI-to-API conversion
        val apiJson = ComfyWorkflowConverter.convertUiToApi(userWfJson)
        val promptObj = com.google.gson.JsonParser.parseString(apiJson).asJsonObject

        assertTrue(promptObj.has("155"))
        assertEquals("UnetLoaderGGUF", promptObj.getAsJsonObject("155").get("class_type").asString)
        assertTrue(promptObj.has("156"))
        assertEquals("CLIPLoaderGGUF", promptObj.getAsJsonObject("156").get("class_type").asString)
        assertTrue(promptObj.has("188"))
        assertEquals("LoadImage", promptObj.getAsJsonObject("188").get("class_type").asString)

        // Verify subgraphs expanded into constituent nodes
        val hasCLIPTextEncode = promptObj.entrySet().any { it.value.asJsonObject.get("class_type")?.asString == "CLIPTextEncode" }
        assertTrue("Expanded graph must contain CLIPTextEncode", hasCLIPTextEncode)
        val hasSampler = promptObj.entrySet().any { it.value.asJsonObject.get("class_type")?.asString == "SamplerCustomAdvanced" }
        assertTrue("Expanded graph must contain SamplerCustomAdvanced", hasSampler)

        // 2. Test metadata extraction on workflow with subgraphs
        val meta = ComfyClient.extractWorkflowMetadata(userWfJson)
        assertTrue("Prompt text should be extracted from PrimitiveStringMultiline", meta.prompt?.contains("change the color of this tshirt") == true)
        assertTrue("LoadImage input should be detected", meta.hasImageInput)
        assertTrue("Mask input should be detected", meta.hasMaskInput)

        // 3. Test workflow preview parsing with subgraphs
        val preview = ComfyClient.parseWorkflowPreview("User Workflow", "wf.json", userWfJson)
        assertTrue("Model name should be extracted from UnetLoaderGGUF", preview.modelName?.contains("flux-2-klein-9b") == true)
        assertTrue("Node count should be at least 10 after expanding subgraphs", preview.nodes.size >= 10)
        assertTrue("Nodes should include CLIPTextEncode", preview.nodes.any { it.type == "CLIPTextEncode" })
        assertTrue("Nodes should include SamplerCustomAdvanced", preview.nodes.any { it.type == "SamplerCustomAdvanced" })
        assertTrue("Preview prompt should match", preview.prompt?.contains("change the color of this tshirt") == true)
    }

    @Test
    fun testFullUserUploadedWorkflow() {
        val stream = javaClass.classLoader?.getResourceAsStream("user_workflow.json")
            ?: java.io.File("src/test/resources/user_workflow.json").takeIf { it.exists() }?.inputStream()
        org.junit.Assert.assertNotNull("user_workflow.json resource must be available", stream)
        val fullJson = stream!!.bufferedReader().use { it.readText() }
        val apiJson = ComfyWorkflowConverter.convertUiToApi(fullJson)
        val promptObj = com.google.gson.JsonParser.parseString(apiJson).asJsonObject
        // 1. Verify fan-out connections to multiple inner nodes in subgraphs
        assertTrue("Node 250 must have samples input", promptObj.getAsJsonObject("250").getAsJsonObject("inputs").has("samples"))
        assertEquals("225", promptObj.getAsJsonObject("250").getAsJsonObject("inputs").getAsJsonArray("samples")[0].asString)

        assertTrue("Node 75:123 must have latent input", promptObj.getAsJsonObject("75:123").getAsJsonObject("inputs").has("latent"))
        assertEquals("75:124", promptObj.getAsJsonObject("75:123").getAsJsonObject("inputs").getAsJsonArray("latent")[0].asString)

        assertTrue("Node 75:125 must have latent input", promptObj.getAsJsonObject("75:125").getAsJsonObject("inputs").has("latent"))
        assertEquals("75:124", promptObj.getAsJsonObject("75:125").getAsJsonObject("inputs").getAsJsonArray("latent")[0].asString)

        // 2. Verify bypassed node 75:80 (mode: 4) is omitted and connection forwarded directly to 75:124
        assertFalse("Bypassed node 75:80 should not be in API prompt", promptObj.has("75:80"))
        assertTrue("Node 75:124 should receive pixels directly from node 189", promptObj.getAsJsonObject("75:124").getAsJsonObject("inputs").has("pixels"))
        assertEquals("189", promptObj.getAsJsonObject("75:124").getAsJsonObject("inputs").getAsJsonArray("pixels")[0].asString)

        // 3. Verify sampler inputs are fully connected
        val samplerInputs = promptObj.getAsJsonObject("224").getAsJsonObject("inputs")
        assertTrue(samplerInputs.has("noise"))
        assertTrue(samplerInputs.has("guider"))
        assertTrue(samplerInputs.has("sampler"))
        assertTrue(samplerInputs.has("sigmas"))
        assertTrue(samplerInputs.has("latent_image"))

        // 4. Test parameter injection into user's inpainting workflow
        ComfyClient.injectWorkflowParameters(
            workflowObj = promptObj,
            prompt = "change tshirt to navy blue",
            negativePrompt = "blurry, low quality",
            activeSeed = 424242L,
            width = 1024,
            height = 1024,
            batchSize = 1,
            uploadedImageName = "input_composite_123.png",
            uploadedMaskName = "mask_standalone_123.png"
        )

        // Verify LoadImage (node 188) received the uploaded composite image (which carries the alpha mask)
        assertTrue(promptObj.has("188"))
        val node188Inputs = promptObj.getAsJsonObject("188").getAsJsonObject("inputs")
        assertEquals("input_composite_123.png", node188Inputs.get("image").asString)
    }

    @Test
    fun testRerouteNodesEliminationAndChaining() {
        val uiWorkflowWithReroutes = """
        {
          "nodes": [
            {
              "id": 1,
              "type": "CheckpointLoaderSimple",
              "inputs": [],
              "outputs": [
                { "name": "MODEL", "type": "MODEL", "links": [10] }
              ],
              "widgets_values": ["v1-5-pruned-emaonly.ckpt"]
            },
            {
              "id": 2,
              "type": "Reroute",
              "inputs": [{ "name": "", "type": "*", "link": 10 }],
              "outputs": [{ "name": "", "type": "MODEL", "links": [20] }]
            },
            {
              "id": 3,
              "type": "Reroute",
              "inputs": [{ "name": "", "type": "*", "link": 20 }],
              "outputs": [{ "name": "", "type": "MODEL", "links": [30] }]
            },
            {
              "id": 4,
              "type": "KSampler",
              "inputs": [
                { "name": "model", "type": "MODEL", "link": 30 }
              ],
              "outputs": [],
              "widgets_values": [12345, "fixed", 20, 8.0, "euler", "normal", 1.0]
            }
          ],
          "links": [
            [10, 1, 0, 2, 0, "MODEL"],
            [20, 2, 0, 3, 0, "MODEL"],
            [30, 3, 0, 4, 0, "MODEL"]
          ]
        }
        """.trimIndent()

        val apiJson = ComfyWorkflowConverter.convertUiToApi(uiWorkflowWithReroutes)
        val promptObj = com.google.gson.JsonParser.parseString(apiJson).asJsonObject

        // 1. Verify Reroute nodes are completely omitted from the API prompt
        assertFalse("Reroute Node 2 must not be in API prompt", promptObj.has("2"))
        assertFalse("Reroute Node 3 must not be in API prompt", promptObj.has("3"))
        assertTrue("CheckpointLoader 1 must be present", promptObj.has("1"))
        assertTrue("KSampler 4 must be present", promptObj.has("4"))

        // 2. Verify KSampler's model input is rewired directly to CheckpointLoader 1, slot 0
        val ksamplerInputs = promptObj.getAsJsonObject("4").getAsJsonObject("inputs")
        assertTrue("KSampler must have model input", ksamplerInputs.has("model"))
        val modelLink = ksamplerInputs.getAsJsonArray("model")
        assertEquals("Upstream origin node must be 1", "1", modelLink[0].asString)
        assertEquals("Upstream origin slot must be 0", 0, modelLink[1].asInt)
    }

    @Test
    fun testExtractWorkflowMetadata_LinkedPromptArrayNotCrashing() {
        val apiWorkflowWithLinkedPrompt = """
        {
          "3": {
            "class_type": "KSampler",
            "inputs": {
              "positive": ["2", 0],
              "negative": ["7", 0]
            }
          },
          "2": {
            "class_type": "Reroute",
            "inputs": {
              "": ["6", 0]
            }
          },
          "6": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "beautiful sunset over mountains, 8k resolution"
            }
          },
          "7": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "blurry, low quality"
            }
          }
        }
        """.trimIndent()

        // Metadata extraction must not throw "Array must have size 1, but had size 2"
        val meta = ComfyClient.extractWorkflowMetadata(apiWorkflowWithLinkedPrompt)
        assertEquals("beautiful sunset over mountains, 8k resolution", meta.prompt)
        assertEquals("blurry, low quality", meta.negativePrompt)
    }

    @Test
    fun testArtComfyUiWorkflowConversion() {
        val stream = javaClass.classLoader?.getResourceAsStream("art_comfyui.json")
            ?: java.io.File("src/test/resources/art_comfyui.json").takeIf { it.exists() }?.inputStream()
        org.junit.Assert.assertNotNull("art_comfyui.json resource must be available", stream)
        val json = stream!!.bufferedReader().use { it.readText() }

        val apiJson = ComfyWorkflowConverter.convertUiToApi(json)
        val promptObj = com.google.gson.JsonParser.parseString(apiJson).asJsonObject

        // Verify that NOT A SINGLE Reroute node is present in the output
        val rerouteCount = promptObj.entrySet().count { it.value.asJsonObject.get("class_type")?.asString?.equals("Reroute", ignoreCase = true) == true }
        assertEquals("Zero Reroute nodes must exist in converted prompt", 0, rerouteCount)

        // Verify metadata extraction completes without exceptions
        val meta = ComfyClient.extractWorkflowMetadata(json)
        assertNotNull("Metadata extraction must succeed", meta)
    }

    @Test
    fun testInjectWorkflowParameters_LoadImageAndMaskHandling() {
        val json = """
        {
          "1": {
            "class_type": "LoadImage",
            "inputs": { "image": "default.png" }
          },
          "2": {
            "class_type": "LoadImageMask",
            "inputs": { "image": "mask_default.png", "channel": "alpha" }
          },
          "3": {
            "class_type": "KSampler",
            "inputs": { "seed": 12345, "positive": ["6", 0], "negative": ["7", 0] }
          },
          "6": {
            "class_type": "CLIPTextEncode",
            "inputs": { "text": "cat" }
          },
          "7": {
            "class_type": "CLIPTextEncode",
            "inputs": { "text": "bad" }
          }
        }
        """.trimIndent()

        val promptObj = com.google.gson.JsonParser.parseString(json).asJsonObject

        ComfyClient.injectWorkflowParameters(
            workflowObj = promptObj,
            prompt = "a majestic lion",
            negativePrompt = "ugly",
            activeSeed = 9999L,
            uploadedImageName = "uploaded_img.png",
            uploadedMaskName = "uploaded_mask.png"
        )

        assertEquals("uploaded_img.png", promptObj.getAsJsonObject("1").getAsJsonObject("inputs").get("image").asString)
        assertEquals("uploaded_mask.png", promptObj.getAsJsonObject("2").getAsJsonObject("inputs").get("image").asString)
        assertEquals(9999L, promptObj.getAsJsonObject("3").getAsJsonObject("inputs").get("seed").asLong)
        assertEquals("a majestic lion", promptObj.getAsJsonObject("6").getAsJsonObject("inputs").get("text").asString)
        assertEquals("ugly", promptObj.getAsJsonObject("7").getAsJsonObject("inputs").get("text").asString)
    }

    @Test
    fun testInjectWorkflowParameters_LoadImageMappedAsMaskProtection() {
        val json = """
        {
          "188": {
            "class_type": "LoadImage",
            "inputs": { "image": "old.png" }
          }
        }
        """.trimIndent()

        val promptObj = com.google.gson.JsonParser.parseString(json).asJsonObject

        // User mapped LoadImage (188) as loadImageMaskNodeId
        val customMapping = com.comfyport.data.WorkflowNodeMapping(
            loadImageMaskNodeId = "188"
        )

        ComfyClient.injectWorkflowParameters(
            workflowObj = promptObj,
            prompt = "test",
            uploadedImageName = "composite_img.png",
            uploadedMaskName = "standalone_mask.png",
            customMapping = customMapping
        )

        // Must receive composite_img.png, NOT standalone_mask.png!
        val nodeInputs = promptObj.getAsJsonObject("188").getAsJsonObject("inputs")
        assertEquals("composite_img.png", nodeInputs.get("image").asString)
    }

    @Test
    fun testInjectWorkflowParameters_PreservesLinkedWidthAndHeight() {
        val json = """
        {
          "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {
              "width": ["45", 0],
              "height": ["45", 1],
              "batch_size": 1
            }
          },
          "3": {
            "class_type": "KSampler",
            "inputs": {
              "latent_image": ["5", 0]
            }
          }
        }
        """.trimIndent()

        val promptObj = com.google.gson.JsonParser.parseString(json).asJsonObject

        // Inject parameters with explicit width = 1920, height = 1080
        ComfyClient.injectWorkflowParameters(
            workflowObj = promptObj,
            prompt = "test",
            width = 1920,
            height = 1080,
            batchSize = 2,
            resolutionMode = "CUSTOM"
        )

        val inputs = promptObj.getAsJsonObject("5").getAsJsonObject("inputs")
        // Width and height MUST remain JsonArrays linked to node 45!
        assertTrue(inputs.get("width").isJsonArray)
        assertEquals("45", inputs.getAsJsonArray("width").get(0).asString)
        assertEquals(0, inputs.getAsJsonArray("width").get(1).asInt)

        assertTrue(inputs.get("height").isJsonArray)
        assertEquals("45", inputs.getAsJsonArray("height").get(0).asString)
        assertEquals(1, inputs.getAsJsonArray("height").get(1).asInt)

        // batch_size was a static number, so it can be updated
        assertEquals(2, inputs.get("batch_size").asInt)
    }

    @Test
    fun testInjectWorkflowParameters_WorkflowModeLeavesStaticLatentUntouched() {
        val json = """
        {
          "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {
              "width": 768,
              "height": 512,
              "batch_size": 1
            }
          }
        }
        """.trimIndent()

        val promptObj = com.google.gson.JsonParser.parseString(json).asJsonObject

        ComfyClient.injectWorkflowParameters(
            workflowObj = promptObj,
            prompt = "test",
            width = 1024,
            height = 1024,
            resolutionMode = "WORKFLOW"
        )

        val inputs = promptObj.getAsJsonObject("5").getAsJsonObject("inputs")
        // Should remain untouched (768 and 512), not overridden to 1024
        assertEquals(768, inputs.get("width").asInt)
        assertEquals(512, inputs.get("height").asInt)
    }

    @Test
    fun testInjectWorkflowParameters_LatentDerivedFromImageSkipsFallbackLatent() {
        val json = """
        {
          "1": {
            "class_type": "LoadImage",
            "inputs": { "image": "photo.png" }
          },
          "2": {
            "class_type": "VAEEncode",
            "inputs": { "pixels": ["1", 0], "vae": ["10", 0] }
          },
          "3": {
            "class_type": "SetLatentNoiseMask",
            "inputs": { "samples": ["2", 0], "mask": ["1", 1] }
          },
          "4": {
            "class_type": "KSampler",
            "inputs": { "latent_image": ["3", 0] }
          },
          "5": {
            "class_type": "EmptyLatentImage",
            "inputs": { "width": 512, "height": 512, "batch_size": 1 }
          }
        }
        """.trimIndent()

        val promptObj = com.google.gson.JsonParser.parseString(json).asJsonObject

        assertTrue(ComfyClient.isLatentDerivedFromImage(promptObj))

        // Inject parameters with SDXL mode (width = 1024, height = 1024)
        ComfyClient.injectWorkflowParameters(
            workflowObj = promptObj,
            prompt = "an oil painting",
            width = 1024,
            height = 1024,
            resolutionMode = "SDXL"
        )

        // Because sampler's latent comes from VAEEncode/SetLatentNoiseMask, node 5 shouldn't be touched
        val node5Inputs = promptObj.getAsJsonObject("5").getAsJsonObject("inputs")
        assertEquals(512, node5Inputs.get("width").asInt)
        assertEquals(512, node5Inputs.get("height").asInt)
    }

    @Test
    fun testExtractWorkflowMetadata_DetectsWorkflowManagedResolution() {
        // Linked latent width/height
        val linkedJson = """
        {
          "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {
              "width": ["45", 0],
              "height": ["45", 1]
            }
          }
        }
        """.trimIndent()
        val meta1 = ComfyClient.extractWorkflowMetadata(linkedJson)
        assertTrue(meta1.isResolutionManagedByWorkflow)

        // Img2img with VAEEncode
        val img2imgJson = """
        {
          "1": { "class_type": "LoadImage", "inputs": { "image": "input.png" } },
          "2": { "class_type": "VAEEncode", "inputs": { "pixels": ["1", 0] } },
          "3": { "class_type": "KSampler", "inputs": { "latent_image": ["2", 0] } }
        }
        """.trimIndent()
        val meta2 = ComfyClient.extractWorkflowMetadata(img2imgJson)
        assertTrue(meta2.isResolutionManagedByWorkflow)

        // Normal txt2img
        val txt2imgJson = """
        {
          "5": { "class_type": "EmptyLatentImage", "inputs": { "width": 1024, "height": 1024 } },
          "3": { "class_type": "KSampler", "inputs": { "latent_image": ["5", 0] } }
        }
        """.trimIndent()
        val meta3 = ComfyClient.extractWorkflowMetadata(txt2imgJson)
        assertFalse(meta3.isResolutionManagedByWorkflow)
    }

    @Test
    fun testPngEncoder_PreservesUnpremultipliedRgbWhenAlphaZero() {
        val width = 2
        val height = 2
        val rgbaBytes = ByteArray(width * height * 4)

        // Pixel 0: Masked pixel (Inpaint target): Red=220, Green=110, Blue=55, Alpha=0
        rgbaBytes[0] = 220.toByte()
        rgbaBytes[1] = 110.toByte()
        rgbaBytes[2] = 55.toByte()
        rgbaBytes[3] = 0.toByte()

        // Pixel 1: Unmasked pixel (Preserved): Red=10, Green=20, Blue=30, Alpha=255
        rgbaBytes[4] = 10.toByte()
        rgbaBytes[5] = 20.toByte()
        rgbaBytes[6] = 30.toByte()
        rgbaBytes[7] = 255.toByte()

        // Pixel 2: Feathered pixel: Red=100, Green=150, Blue=200, Alpha=128
        rgbaBytes[8] = 100.toByte()
        rgbaBytes[9] = 150.toByte()
        rgbaBytes[10] = 200.toByte()
        rgbaBytes[11] = 128.toByte()

        // Pixel 3: White masked pixel: Red=255, Green=255, Blue=255, Alpha=0
        rgbaBytes[12] = 255.toByte()
        rgbaBytes[13] = 255.toByte()
        rgbaBytes[14] = 255.toByte()
        rgbaBytes[15] = 0.toByte()

        val pngBytes = PngEncoder.encodeRgba(width, height, rgbaBytes)
        assertTrue("PNG must start with PNG header", pngBytes.size > 8)
        assertEquals(0x89.toByte(), pngBytes[0])
        assertEquals('P'.code.toByte(), pngBytes[1])
        assertEquals('N'.code.toByte(), pngBytes[2])
        assertEquals('G'.code.toByte(), pngBytes[3])

        // Decode using JVM's javax.imageio.ImageIO (simulating Pillow decoding on ComfyUI server)
        val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(pngBytes))
        assertNotNull("ImageIO must decode PNG successfully", image)
        assertEquals(width, image.width)
        assertEquals(height, image.height)

        val raster = image.raster
        val pixel0 = raster.getPixel(0, 0, IntArray(4))
        // Verify R, G, B are 100% PRESERVED and NOT wiped out by premultiplication!
        assertEquals("Red must be 220 even when Alpha is 0", 220, pixel0[0])
        assertEquals("Green must be 110 even when Alpha is 0", 110, pixel0[1])
        assertEquals("Blue must be 55 even when Alpha is 0", 55, pixel0[2])
        assertEquals("Alpha must be 0", 0, pixel0[3])

        val pixel1 = raster.getPixel(1, 0, IntArray(4))
        assertEquals(10, pixel1[0])
        assertEquals(20, pixel1[1])
        assertEquals(30, pixel1[2])
        assertEquals(255, pixel1[3])

        val pixel2 = raster.getPixel(0, 1, IntArray(4))
        assertEquals(100, pixel2[0])
        assertEquals(150, pixel2[1])
        assertEquals(200, pixel2[2])
        assertEquals(128, pixel2[3])
    }

    @Test
    fun testStandaloneMaskPng_MultiChannelCompatibility() {
        val width = 3
        val height = 1
        val rgbaBytes = ByteArray(width * height * 4)

        // Pixel 0: fully masked (intensity = 255)
        // R=255, G=255, B=255, A=0
        rgbaBytes[0] = 255.toByte()
        rgbaBytes[1] = 255.toByte()
        rgbaBytes[2] = 255.toByte()
        rgbaBytes[3] = 0.toByte()

        // Pixel 1: unmasked / keep (intensity = 0)
        // R=0, G=0, B=0, A=255
        rgbaBytes[4] = 0.toByte()
        rgbaBytes[5] = 0.toByte()
        rgbaBytes[6] = 0.toByte()
        rgbaBytes[7] = 255.toByte()

        // Pixel 2: 50% feathered mask (intensity = 128)
        // R=128, G=128, B=128, A=127
        rgbaBytes[8] = 128.toByte()
        rgbaBytes[9] = 128.toByte()
        rgbaBytes[10] = 128.toByte()
        rgbaBytes[11] = 127.toByte()

        val pngBytes = PngEncoder.encodeRgba(width, height, rgbaBytes)
        val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(pngBytes))
        assertNotNull(image)

        val raster = image.raster

        // Pixel 0 (fully masked)
        val p0 = raster.getPixel(0, 0, IntArray(4))
        assertEquals("Masked pixel Red channel must be 255", 255, p0[0])
        assertEquals("Masked pixel Green channel must be 255", 255, p0[1])
        assertEquals("Masked pixel Blue channel must be 255", 255, p0[2])
        assertEquals("Masked pixel Alpha channel must be 0", 0, p0[3])
        // Verify ComfyUI calculations:
        val maskFromAlpha0 = 1.0f - (p0[3] / 255.0f)
        val maskFromRed0 = p0[0] / 255.0f
        assertEquals(1.0f, maskFromAlpha0, 0.001f)
        assertEquals(1.0f, maskFromRed0, 0.001f)

        // Pixel 1 (unmasked)
        val p1 = raster.getPixel(1, 0, IntArray(4))
        assertEquals(0, p1[0])
        assertEquals(0, p1[1])
        assertEquals(0, p1[2])
        assertEquals(255, p1[3])
        val maskFromAlpha1 = 1.0f - (p1[3] / 255.0f)
        val maskFromRed1 = p1[0] / 255.0f
        assertEquals(0.0f, maskFromAlpha1, 0.001f)
        assertEquals(0.0f, maskFromRed1, 0.001f)

        // Pixel 2 (feathered)
        val p2 = raster.getPixel(2, 0, IntArray(4))
        assertEquals(128, p2[0])
        assertEquals(128, p2[1])
        assertEquals(128, p2[2])
        assertEquals(127, p2[3])
        val maskFromAlpha2 = 1.0f - (p2[3] / 255.0f)
        val maskFromRed2 = p2[0] / 255.0f
        assertEquals(0.502f, maskFromAlpha2, 0.01f)
        assertEquals(0.502f, maskFromRed2, 0.01f)
    }

    @Test
    fun testUserWorkflow_InpaintAndLoadImageDetection() {
        val stream = javaClass.classLoader?.getResourceAsStream("user_workflow.json")
            ?: java.io.File("src/test/resources/user_workflow.json").takeIf { it.exists() }?.inputStream()
        org.junit.Assert.assertNotNull("user_workflow.json resource must be available", stream)
        val jsonStr = stream!!.bufferedReader().use { it.readText() }
        val preview = ComfyClient.parseWorkflowPreview("Flux Inpaint", "workflow.json", jsonStr)
        assertTrue("Workflow has LoadImage / mask nodes, so hasMaskInput should be detected", preview.hasMaskInput)
        assertTrue("Workflow has LoadImage, so hasImageInput should be detected", preview.hasImageInput)
    }
}



