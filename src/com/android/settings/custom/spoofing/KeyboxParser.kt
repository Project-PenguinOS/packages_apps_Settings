/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.spoofing

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

data class KeyboxValidationResult(
    val isValid: Boolean,
    val normalizedXml: String,
    val summary: String,
    val error: String? = null
)

object KeyboxParser {

    private val CERT_FACTORY: CertificateFactory by lazy {
        CertificateFactory.getInstance("X.509")
    }

    fun parseAndNormalize(input: String): KeyboxValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return KeyboxValidationResult(false, "", "", "Empty keybox input")
        }

        return try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                parseJson(trimmed)
            } else if (trimmed.startsWith("<") || trimmed.contains("<Keybox") || trimmed.contains("<Key")) {
                parseXml(trimmed)
            } else if (trimmed.contains("-----BEGIN")) {
                parsePemDirect(trimmed)
            } else {
                KeyboxValidationResult(false, "", "", "Unrecognized keybox format")
            }
        } catch (e: Exception) {
            KeyboxValidationResult(false, "", "", "Failed to parse keybox: ${e.message}")
        }
    }

    private fun parseXml(xml: String): KeyboxValidationResult {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        val keyNodes = root.getElementsByTagName("Key")
        if (keyNodes.length == 0) {
            return KeyboxValidationResult(false, "", "", "No <Key> elements found in XML")
        }

        val ecdsaKeys = mutableListOf<ParsedKey>()
        val rsaKeys = mutableListOf<ParsedKey>()

        for (i in 0 until keyNodes.length) {
            val element = keyNodes.item(i) as? Element ?: continue
            val algo = element.getAttribute("algorithm").lowercase()
            val parsed = extractKeyFromElement(element, algo) ?: continue

            if (algo.contains("ec")) {
                ecdsaKeys.add(parsed)
            } else if (algo.contains("rsa")) {
                rsaKeys.add(parsed)
            }
        }

        if (ecdsaKeys.isEmpty() && rsaKeys.isEmpty()) {
            return KeyboxValidationResult(false, "", "", "No valid cryptographic key pairs found in XML")
        }

        return buildNormalizedResult(ecdsaKeys.firstOrNull(), rsaKeys.firstOrNull())
    }

    private fun parseJson(jsonStr: String): KeyboxValidationResult {
        val root = JSONObject(jsonStr)
        var ecKey: ParsedKey? = null
        var rsaKey: ParsedKey? = null

        val ecObj = root.optJSONObject("ec") ?: root.optJSONObject("ecdsa")
        if (ecObj != null) {
            ecKey = extractKeyFromJson(ecObj, "ecdsa")
        }

        val rsaObj = root.optJSONObject("rsa")
        if (rsaObj != null) {
            rsaKey = extractKeyFromJson(rsaObj, "rsa")
        }

        if (ecKey == null && rsaKey == null) {
            val keysArray = root.optJSONArray("keys")
            if (keysArray != null) {
                for (i in 0 until keysArray.length()) {
                    val item = keysArray.optJSONObject(i) ?: continue
                    val algo = item.optString("algorithm", "").lowercase()
                    if (algo.contains("ec") && ecKey == null) {
                        ecKey = extractKeyFromJson(item, "ecdsa")
                    } else if (algo.contains("rsa") && rsaKey == null) {
                        rsaKey = extractKeyFromJson(item, "rsa")
                    }
                }
            }
        }

        if (ecKey == null && rsaKey == null) {
            return KeyboxValidationResult(false, "", "", "No EC or RSA keys found in JSON")
        }

        return buildNormalizedResult(ecKey, rsaKey)
    }

    private fun parsePemDirect(raw: String): KeyboxValidationResult {
        val certs = extractCertificatesFromText(raw)
        val privKey = extractPrivateKeyFromText(raw)

        if (certs.isEmpty() || privKey.isEmpty()) {
            return KeyboxValidationResult(false, "", "", "Could not extract both certificates and private key from PEM")
        }

        val firstCert = certs.first()
        val algo = if (firstCert.publicKey.algorithm.contains("EC", ignoreCase = true)) "ecdsa" else "rsa"
        val parsedKey = ParsedKey(algo, privKey, certs)

        return if (algo == "ecdsa") {
            buildNormalizedResult(parsedKey, null)
        } else {
            buildNormalizedResult(null, parsedKey)
        }
    }

    private data class ParsedKey(
        val algorithm: String,
        val privateKeyPem: String,
        val certificates: List<X509Certificate>
    )

    private fun extractKeyFromElement(element: Element, algo: String): ParsedKey? {
        val privNode = element.getElementsByTagName("PrivateKey").item(0) as? Element
        val privKeyPem = privNode?.textContent?.trim() ?: ""
        if (privKeyPem.isEmpty()) return null

        val certNodes = element.getElementsByTagName("Certificate")
        val certs = mutableListOf<X509Certificate>()
        for (i in 0 until certNodes.length) {
            val cText = certNodes.item(i)?.textContent ?: continue
            val cert = parsePemCertificate(cText) ?: continue
            certs.add(cert)
        }

        if (certs.isEmpty()) return null
        val normalizedAlgo = if (algo.contains("ec")) "ecdsa" else "rsa"
        return ParsedKey(normalizedAlgo, normalizePemPrivateKey(privKeyPem, normalizedAlgo), certs)
    }

    private fun extractKeyFromJson(obj: JSONObject, defaultAlgo: String): ParsedKey? {
        val privKey = obj.optString("private_key", "").ifEmpty {
            obj.optString("privateKey", "")
        }.trim()
        if (privKey.isEmpty()) return null

        val certs = mutableListOf<X509Certificate>()
        val certsArr = obj.optJSONArray("certificates") ?: obj.optJSONArray("certificate_chain")
        if (certsArr != null) {
            for (i in 0 until certsArr.length()) {
                val cStr = certsArr.optString(i, "")
                val cert = parsePemCertificate(cStr) ?: continue
                certs.add(cert)
            }
        }

        if (certs.isEmpty()) return null
        val algo = if (defaultAlgo.contains("ec")) "ecdsa" else "rsa"
        return ParsedKey(algo, normalizePemPrivateKey(privKey, algo), certs)
    }

    private fun buildNormalizedResult(ecKey: ParsedKey?, rsaKey: ParsedKey?): KeyboxValidationResult {
        val summaryParts = mutableListOf<String>()
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<AndroidAttestation>\n")
        sb.append("    <NumberOfKeyboxes>1</NumberOfKeyboxes>\n")
        sb.append("    <Keybox DeviceID=\"NeotericKeybox\">\n")

        if (ecKey != null && ecKey.certificates.isNotEmpty()) {
            sb.append("        <Key algorithm=\"ecdsa\">\n")
            sb.append("            <PrivateKey format=\"pem\">\n")
            sb.append(indentLines(ecKey.privateKeyPem, 16))
            sb.append("\n            </PrivateKey>\n")
            sb.append("            <CertificateChain>\n")
            sb.append("                <NumberOfCertificates>${ecKey.certificates.size}</NumberOfCertificates>\n")
            for (cert in ecKey.certificates) {
                sb.append("                <Certificate format=\"pem\">\n")
                sb.append(indentLines(formatCertificateAsPem(cert), 20))
                sb.append("\n                </Certificate>\n")
            }
            sb.append("            </CertificateChain>\n")
            sb.append("        </Key>\n")
            summaryParts.add("ECDSA (${ecKey.certificates.size} certs)")
        }

        if (rsaKey != null && rsaKey.certificates.isNotEmpty()) {
            sb.append("        <Key algorithm=\"rsa\">\n")
            sb.append("            <PrivateKey format=\"pem\">\n")
            sb.append(indentLines(rsaKey.privateKeyPem, 16))
            sb.append("\n            </PrivateKey>\n")
            sb.append("            <CertificateChain>\n")
            sb.append("                <NumberOfCertificates>${rsaKey.certificates.size}</NumberOfCertificates>\n")
            for (cert in rsaKey.certificates) {
                sb.append("                <Certificate format=\"pem\">\n")
                sb.append(indentLines(formatCertificateAsPem(cert), 20))
                sb.append("\n                </Certificate>\n")
            }
            sb.append("            </CertificateChain>\n")
            sb.append("        </Key>\n")
            summaryParts.add("RSA (${rsaKey.certificates.size} certs)")
        }

        sb.append("    </Keybox>\n")
        sb.append("</AndroidAttestation>\n")

        return KeyboxValidationResult(
            isValid = true,
            normalizedXml = sb.toString(),
            summary = summaryParts.joinToString(", ")
        )
    }

    private fun parsePemCertificate(text: String): X509Certificate? {
        return try {
            val pemStart = "-----BEGIN CERTIFICATE-----"
            val pemEnd = "-----END CERTIFICATE-----"
            val raw = if (text.contains(pemStart) && text.contains(pemEnd)) {
                text.substringAfter(pemStart).substringBefore(pemEnd)
            } else {
                text
            }.replace(Regex("\\s"), "")

            if (raw.isEmpty()) return null
            val bytes = Base64.getDecoder().decode(raw)
            CERT_FACTORY.generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCertificatesFromText(text: String): List<X509Certificate> {
        val list = mutableListOf<X509Certificate>()
        var pos = 0
        while (true) {
            val start = text.indexOf("-----BEGIN CERTIFICATE-----", pos)
            if (start == -1) break
            val end = text.indexOf("-----END CERTIFICATE-----", start)
            if (end == -1) break
            val block = text.substring(start, end + "-----END CERTIFICATE-----".length)
            parsePemCertificate(block)?.let { list.add(it) }
            pos = end + "-----END CERTIFICATE-----".length
        }
        return list
    }

    private fun extractPrivateKeyFromText(text: String): String {
        val startIdx = text.indexOf("-----BEGIN")
        if (startIdx == -1) return ""
        val endIdx = text.indexOf("-----END", startIdx)
        if (endIdx == -1) return ""
        val lineEnd = text.indexOf("-----", endIdx + 8)
        if (lineEnd == -1) return ""
        return text.substring(startIdx, lineEnd + 5).trim()
    }

    private fun normalizePemPrivateKey(keyPem: String, algo: String): String {
        val trimmed = keyPem.trim()
        if (trimmed.startsWith("-----BEGIN")) {
            return trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
        val cleanB64 = trimmed.replace(Regex("\\s"), "")
        val header = if (algo == "rsa") "-----BEGIN RSA PRIVATE KEY-----" else "-----BEGIN EC PRIVATE KEY-----"
        val footer = if (algo == "rsa") "-----END RSA PRIVATE KEY-----" else "-----END EC PRIVATE KEY-----"
        val wrapped = cleanB64.chunked(64).joinToString("\n")
        return "$header\n$wrapped\n$footer"
    }

    private fun formatCertificateAsPem(cert: X509Certificate): String {
        val b64 = Base64.getEncoder().encodeToString(cert.encoded)
        val wrapped = b64.chunked(64).joinToString("\n")
        return "-----BEGIN CERTIFICATE-----\n$wrapped\n-----END CERTIFICATE-----"
    }

    private fun indentLines(text: String, spaces: Int): String {
        val indent = " ".repeat(spaces)
        return text.lines().joinToString("\n") { "$indent$it" }
    }
}
