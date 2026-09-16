package com.comfyport.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun testNormalizeUrl_PrefixesHttp() {
        assertEquals("http://192.168.1.100:8188", UrlValidator.normalizeUrl("192.168.1.100:8188"))
        assertEquals("http://localhost:8188", UrlValidator.normalizeUrl("localhost:8188"))
    }

    @Test
    fun testNormalizeUrl_RemovesTrailingSlashes() {
        assertEquals("http://192.168.1.100:8188", UrlValidator.normalizeUrl("http://192.168.1.100:8188/"))
        assertEquals("http://192.168.1.100:8188", UrlValidator.normalizeUrl("192.168.1.100:8188///"))
    }

    @Test
    fun testNormalizeUrl_PreservesHttps() {
        assertEquals("https://my-domain.com", UrlValidator.normalizeUrl("https://my-domain.com/"))
    }

    @Test
    fun testNormalizeUrl_WhitespaceHandling() {
        assertEquals("http://192.168.1.100:8188", UrlValidator.normalizeUrl("   192.168.1.100:8188   "))
        assertEquals("", UrlValidator.normalizeUrl("   "))
    }

    @Test
    fun testValidateUrl_ValidScenarios() {
        assertTrue(UrlValidator.validateUrl("192.168.1.100:8188") is ValidationResult.Local)
        assertTrue(UrlValidator.validateUrl("http://192.168.1.100:8188") is ValidationResult.Local)
        assertTrue(UrlValidator.validateUrl("https://my-server.net:8188") is ValidationResult.Public)
        assertTrue(UrlValidator.validateUrl("localhost:8188") is ValidationResult.Local)
    }

    @Test
    fun testValidateUrl_InvalidScenarios() {
        assertTrue(UrlValidator.validateUrl("") is ValidationResult.Error)
        assertTrue(UrlValidator.validateUrl("   ") is ValidationResult.Error)
        assertTrue(UrlValidator.validateUrl("ftp://192.168.1.100") is ValidationResult.Error)
    }

    @Test
    fun testExtractHost() {
        assertEquals("192.168.1.100", UrlValidator.extractHost("http://192.168.1.100:8188"))
        assertEquals("192.168.1.100", UrlValidator.extractHost("192.168.1.100:8188/"))
        assertEquals("localhost", UrlValidator.extractHost("http://localhost:8188"))
        assertEquals("my-comfy-server.lan", UrlValidator.extractHost("https://my-comfy-server.lan:8188/prompt"))
        assertEquals("", UrlValidator.extractHost(""))
    }
}
