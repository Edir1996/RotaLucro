package com.rotalucro.app.reader

import android.view.accessibility.AccessibilityNodeInfo
import com.rotalucro.app.accessibility.RideAccessibilityService
import java.util.ArrayDeque

/**
 * Leitura leve da árvore de acessibilidade da 99.
 *
 * A 99 usa partes da interface em Flutter e nem sempre expõe os textos da oferta.
 * Mesmo assim, a árvore ainda é útil para detectar mudanças de tela, view IDs e,
 * quando houver texto disponível, evitar OCR desnecessário.
 */
data class BR99NodeSnapshot(
    val texts: List<String>,
    val viewIds: List<String>,
    val nodeCount: Int,
    val hasOfferHint: Boolean
)

object BR99NodeReader {
    private const val MAX_NODES = 1800
    private const val MAX_TEXTS = 160
    private const val MAX_IDS = 160

    fun read(service: RideAccessibilityService): BR99NodeSnapshot {
        val root = runCatching { service.rootInActiveWindow }.getOrNull()
            ?: service.find99WindowRoot()
            ?: return BR99NodeSnapshot(emptyList(), emptyList(), 0, false)

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val texts = LinkedHashSet<String>()
        val ids = LinkedHashSet<String>()
        var count = 0
        var offerHint = false

        while (queue.isNotEmpty() && count < MAX_NODES) {
            val node = queue.removeFirst()
            count++

            val text = normalize(node.text?.toString().orEmpty())
            val desc = normalize(node.contentDescription?.toString().orEmpty())
            val viewId = normalize(node.viewIdResourceName.orEmpty())

            if (text.isNotBlank() && texts.size < MAX_TEXTS) texts += text
            if (desc.isNotBlank() && texts.size < MAX_TEXTS) texts += desc
            if (viewId.isNotBlank() && ids.size < MAX_IDS) ids += viewId

            if (!offerHint) {
                offerHint = looksLikeOfferSignal(text) || looksLikeOfferSignal(desc) || looksLikeOfferViewId(viewId)
            }

            for (i in 0 until node.childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let(queue::addLast)
            }
        }

        return BR99NodeSnapshot(
            texts = texts.toList(),
            viewIds = ids.toList(),
            nodeCount = count,
            hasOfferHint = offerHint
        )
    }

    private fun looksLikeOfferSignal(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase()
        return lower.contains("aceitar") ||
            lower.contains("r$") ||
            (lower.contains("min") && lower.contains("km")) ||
            lower.contains("tarifa base") ||
            lower.contains("perfil premium")
    }

    private fun looksLikeOfferViewId(value: String): Boolean {
        if (value.isBlank()) return false
        val lower = value.lowercase()
        return lower.contains("order") ||
            lower.contains("offer") ||
            lower.contains("broad_order") ||
            lower.contains("flutter_root")
    }

    private fun normalize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
