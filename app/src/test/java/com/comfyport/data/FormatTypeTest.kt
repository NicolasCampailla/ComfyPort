package com.comfyport.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FormatTypeTest {

    @Test
    fun testDetect_UiStandard_Array() {
        val json = """
            [
                {
                    "id": 1,
                    "type": "LoadImage"
                }
            ]
        """.trimIndent()
        val format = FormatType.detect(json)
        assertEquals(FormatType.UI_STANDARD, format)
    }

    @Test
    fun testDetect_UiStandard_NodesAndLinks() {
        val json = """
            {
                "nodes": [
                    { "id": 1, "type": "LoadImage" }
                ],
                "links": [
                    [1, 0, 2, 0, "IMAGE"]
                ]
            }
        """.trimIndent()
        val format = FormatType.detect(json)
        assertEquals(FormatType.UI_STANDARD, format)
    }

    @Test
    fun testDetect_ApiReady() {
        val json = """
            {
                "1": {
                    "class_type": "LoadImage",
                    "inputs": {
                        "image": "example.png"
                    }
                },
                "2": {
                    "class_type": "EmptyLatentImage",
                    "inputs": {
                        "width": 512,
                        "height": 512,
                        "batch_size": 1
                    }
                }
            }
        """.trimIndent()
        val format = FormatType.detect(json)
        assertEquals(FormatType.API_READY, format)
    }

    @Test
    fun testDetect_InvalidFormat_EmptyObject() {
        val json = "{}"
        assertThrows(IllegalArgumentException::class.java) {
            FormatType.detect(json)
        }
    }

    @Test
    fun testDetect_InvalidFormat_RandomKeys() {
        val json = """
            {
                "workflow": "my-cool-workflow",
                "version": "1.0"
            }
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            FormatType.detect(json)
        }
    }

    @Test
    fun testDetect_InvalidFormat_NumericKeyWithoutClassType() {
        val json = """
            {
                "1": {
                    "inputs": {
                        "image": "example.png"
                    }
                }
            }
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            FormatType.detect(json)
        }
    }
}
