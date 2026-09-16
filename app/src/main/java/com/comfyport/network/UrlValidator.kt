package com.comfyport.network

import java.net.URI
import java.net.URL

sealed class ValidationResult {
    object Local : ValidationResult()
    object Public : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

object UrlValidator {
    /**
     * Normalizes a server URL by trimming whitespace, ensuring http:// or https:// scheme is present,
     * and removing any trailing slashes.
     */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else if (trimmed.contains("://")) {
            // Already has a non-http(s) scheme (e.g. ftp://)
            trimmed
        } else {
            "http://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    /**
     * Extracts the host IP or domain name from a URL, stripping scheme, port, and path.
     * E.g. "http://192.168.1.100:8188/" -> "192.168.1.100"
     */
    fun extractHost(url: String): String {
        val normalized = normalizeUrl(url)
        if (normalized.isBlank()) return ""
        return try {
            val uri = URI(normalized)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                host
            } else {
                URL(normalized).host ?: ""
            }
        } catch (_: Exception) {
            val stripped = normalized.substringAfter("://").substringBefore('/').substringBefore(':')
            stripped.trim()
        }
    }

    fun validateUrl(url: String): ValidationResult {
        val normalized = normalizeUrl(url)
        if (normalized.isBlank()) {
            return ValidationResult.Error("URL cannot be empty")
        }

        if (!normalized.startsWith("http://", ignoreCase = true) && !normalized.startsWith("https://", ignoreCase = true)) {
            return ValidationResult.Error("URL must use http:// or https://")
        }

        val host: String = try {
            val uri = URI(normalized)
            val hostStr = uri.host
            if (hostStr.isNullOrEmpty()) {
                val u = URL(normalized)
                u.host ?: return ValidationResult.Error("Invalid host in URL")
            } else {
                hostStr
            }
        } catch (e: Exception) {
            return ValidationResult.Error("Malformed URL: ${e.message}")
        }

        if (host.isBlank()) {
            return ValidationResult.Error("Invalid host in URL")
        }

        val isLocal = when {
            host.equals("localhost", ignoreCase = true) -> true
            host.equals("127.0.0.1") -> true
            host.startsWith("10.") -> true
            host.startsWith("192.168.") -> true
            host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) -> true
            else -> false
        }

        return if (isLocal) {
            ValidationResult.Local
        } else {
            ValidationResult.Public
        }
    }
}
