package com.comfyport.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowAutoMappingTest {

    @Test
    fun testAutoDetect_StandardTxt2Img_ApiFormat() {
        val workflowJson = """
        {
            "3": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": 12345,
                    "steps": 20,
                    "cfg": 8.0,
                    "sampler_name": "euler",
                    "scheduler": "normal",
                    "denoise": 1.0,
                    "model": ["4", 0],
                    "positive": ["6", 0],
                    "negative": ["7", 0],
                    "latent_image": ["5", 0]
                }
            },
            "4": {
                "class_type": "CheckpointLoaderSimple",
                "inputs": { "ckpt_name": "v1-5-pruned-emaonly.ckpt" }
            },
            "5": {
                "class_type": "EmptyLatentImage",
                "inputs": { "width": 512, "height": 512, "batch_size": 1 }
            },
            "6": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "masterpiece, a beautiful mountain landscape", "clip": ["4", 1] }
            },
            "7": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "ugly, blurry, low quality", "clip": ["4", 1] }
            },
            "8": {
                "class_type": "VAEDecode",
                "inputs": { "samples": ["3", 0], "vae": ["4", 2] }
            },
            "9": {
                "class_type": "SaveImage",
                "inputs": { "filename_prefix": "ComfyUI", "images": ["8", 0] }
            }
        }
        """.trimIndent()

        val mapping = ComfyClient.autoDetectNodeMapping(workflowJson)

        assertTrue(mapping.isCustomized)
        assertEquals("6", mapping.positivePromptNodeId)
        assertEquals("7", mapping.negativePromptNodeId)
        assertEquals("5", mapping.emptyLatentNodeId)
        assertEquals("3", mapping.seedNodeId)
        assertEquals("9", mapping.outputNodeId)
        assertNull(mapping.loadImageNodeId)
        assertNull(mapping.loadImageMaskNodeId)
    }

    @Test
    fun testAutoDetect_InpaintWorkflow_ApiFormat() {
        val workflowJson = """
        {
            "3": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": 9999,
                    "positive": ["6", 0],
                    "negative": ["7", 0],
                    "latent_image": ["12", 0]
                }
            },
            "6": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "a red hat" }
            },
            "7": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "blurry" }
            },
            "10": {
                "class_type": "LoadImage",
                "inputs": { "image": "input.png" }
            },
            "11": {
                "class_type": "LoadImageMask",
                "inputs": { "image": "mask.png", "channel": "alpha" }
            },
            "12": {
                "class_type": "VAEEncodeForInpaint",
                "inputs": { "pixels": ["10", 0], "mask": ["11", 0] }
            },
            "15": {
                "class_type": "SaveImage",
                "inputs": { "images": ["3", 0] }
            }
        }
        """.trimIndent()

        val mapping = ComfyClient.autoDetectNodeMapping(workflowJson)

        assertTrue(mapping.isCustomized)
        assertEquals("6", mapping.positivePromptNodeId)
        assertEquals("7", mapping.negativePromptNodeId)
        assertEquals("3", mapping.seedNodeId)
        assertEquals("10", mapping.loadImageNodeId)
        assertEquals("11", mapping.loadImageMaskNodeId)
        assertEquals("15", mapping.outputNodeId)
        assertNull(mapping.emptyLatentNodeId)
    }

    @Test
    fun testAutoDetect_FluxSinglePromptNoNegative_ApiFormat() {
        val workflowJson = """
        {
            "10": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": 42,
                    "positive": ["20", 0],
                    "negative": ["25", 0],
                    "latent_image": ["30", 0]
                }
            },
            "20": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "cyberpunk city street with neon lights" }
            },
            "25": {
                "class_type": "ConditioningZeroOut",
                "inputs": { "conditioning": ["20", 0] }
            },
            "30": {
                "class_type": "EmptySD3LatentImage",
                "inputs": { "width": 1024, "height": 1024 }
            },
            "40": {
                "class_type": "PreviewImage",
                "inputs": { "images": ["10", 0] }
            }
        }
        """.trimIndent()

        val mapping = ComfyClient.autoDetectNodeMapping(workflowJson)

        assertTrue(mapping.isCustomized)
        assertEquals("20", mapping.positivePromptNodeId)
        assertNull(mapping.negativePromptNodeId)
        assertEquals("30", mapping.emptyLatentNodeId)
        assertEquals("10", mapping.seedNodeId)
        assertEquals("40", mapping.outputNodeId)
    }

    @Test
    fun testAutoDetect_UIFormatWithReroutes() {
        val workflowJson = """
        {
            "nodes": [
                {
                    "id": "1",
                    "type": "CLIPTextEncode",
                    "title": "Positive Prompt",
                    "widgets_values": ["photograph of an astronaut riding a horse"]
                },
                {
                    "id": "2",
                    "type": "CLIPTextEncode",
                    "title": "Negative Prompt",
                    "widgets_values": ["cartoon, drawing"]
                },
                {
                    "id": "50",
                    "type": "Reroute",
                    "inputs": [{ "name": "", "link": 101 }]
                },
                {
                    "id": "3",
                    "type": "KSampler",
                    "inputs": [
                        { "name": "positive", "link": 102 },
                        { "name": "negative", "link": 103 },
                        { "name": "latent_image", "link": 104 }
                    ],
                    "widgets_values": [42, "randomize", 25, 7.5, "dpmpp_2m", "karras", 1.0]
                },
                {
                    "id": "4",
                    "type": "EmptyLatentImage",
                    "widgets_values": [1024, 1024, 1]
                },
                {
                    "id": "5",
                    "type": "SaveImage",
                    "inputs": [{ "name": "images", "link": 105 }]
                }
            ],
            "links": [
                [101, "1", 0, "50", 0, "CONDITIONING"],
                [102, "50", 0, "3", 0, "CONDITIONING"],
                [103, "2", 0, "3", 1, "CONDITIONING"],
                [104, "4", 0, "3", 2, "LATENT"]
            ]
        }
        """.trimIndent()

        val mapping = ComfyClient.autoDetectNodeMapping(workflowJson)

        assertTrue(mapping.isCustomized)
        assertEquals("1", mapping.positivePromptNodeId)
        assertEquals("2", mapping.negativePromptNodeId)
        assertEquals("4", mapping.emptyLatentNodeId)
        assertEquals("3", mapping.seedNodeId)
        assertEquals("5", mapping.outputNodeId)
    }

    @Test
    fun testParseWorkflowPreview_AutoDetectsMappingWhenNull() {
        val workflowJson = """
        {
            "3": {
                "class_type": "KSampler",
                "inputs": {
                    "seed": 111,
                    "positive": ["6", 0],
                    "negative": ["7", 0],
                    "latent_image": ["5", 0]
                }
            },
            "5": {
                "class_type": "EmptyLatentImage",
                "inputs": { "width": 832, "height": 1216, "batch_size": 2 }
            },
            "6": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "cybernetic cat" }
            },
            "7": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "watermark" }
            },
            "9": {
                "class_type": "SaveImage",
                "inputs": { "images": ["3", 0] }
            }
        }
        """.trimIndent()

        val preview = ComfyClient.parseWorkflowPreview("Test Wf", "test.json", workflowJson, null)

        val mapping = preview.activeMapping
        assertNotNull(mapping)
        assertTrue(mapping!!.isCustomized)
        assertEquals("6", mapping.positivePromptNodeId)
        assertEquals("7", mapping.negativePromptNodeId)
        assertEquals("5", mapping.emptyLatentNodeId)
        assertEquals("3", mapping.seedNodeId)
        assertEquals("9", mapping.outputNodeId)

        assertEquals("cybernetic cat", preview.prompt)
        assertEquals("watermark", preview.negativePrompt)
        assertEquals(832, preview.width)
        assertEquals(1216, preview.height)
        assertEquals(2, preview.batchSize)
    }
}
