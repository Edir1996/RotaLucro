package com.rotalucro.app.ocr

import com.rotalucro.app.calculator.DetectedRouteSegment
import com.rotalucro.app.calculator.RideOffer

/** A line returned by OCR. Coordinates are relative to the analyzed bitmap crop. */
data class OcrLine(
    val text: String,
    val top: Int = 0,
    val height: Int = 0,
    val left: Int = 0,
    val width: Int = 0
)

data class OcrParseResult(
    val offer: RideOffer?,
    val reason: String,
    val usefulTexts: List<String>,
    val fare: Double? = null,
    val pickup: DetectedRouteSegment? = null,
    val trip: DetectedRouteSegment? = null,
    val surgeMultiplier: Double? = null,
    val dynamicBaseFare: Double? = null
)

object OcrOfferParser {
    private val moneyRegex = Regex("""(?i)R\s*[${'$'}S]\s*([0-9]{1,3}(?:[.,][0-9]{1,2})?)""")
    private val segmentRegex = Regex("""(?i)([0-9]{1,2})\s*min\s*\(?\s*([0-9]{1,3}(?:[.,][0-9]+)?)\s*km\s*\)?""")
    private val surgeRegex = Regex("""(?i)([0-9]+(?:[.,][0-9]+)?)\s*[x×]""")

    fun parse(lines: List<OcrLine>): OcrParseResult {
        val cleaned = lines
            .map { it.copy(text = normalize(it.text)) }
            .filter { it.text.isNotBlank() }

        if (cleaned.isEmpty()) {
            return OcrParseResult(null, "OCR não encontrou textos na área da oferta.", emptyList())
        }

        val fare = chooseFare(cleaned)
        val segments = detectSegments(cleaned)
        val surge = cleaned.asSequence()
            .mapNotNull { surgeRegex.find(it.text)?.groupValues?.getOrNull(1)?.toNumber() }
            .firstOrNull { it in 1.0..10.0 }
        val dynamicBase = cleaned.asSequence()
            .filter { containsAny(it.text, "tarifa base", "base dinâmica", "base dinamica") }
            .mapNotNull { moneyRegex.find(it.text)?.groupValues?.getOrNull(1)?.toNumber() }
            .firstOrNull()

        val useful = cleaned.map { it.text }
            .filter(::isUsefulLine)
            .distinct()
            .take(20)

        if (fare == null) {
            return OcrParseResult(
                offer = null,
                reason = "A tela foi capturada, mas o valor principal da oferta não foi reconhecido.",
                usefulTexts = useful,
                pickup = segments.getOrNull(0),
                trip = segments.getOrNull(1),
                surgeMultiplier = surge,
                dynamicBaseFare = dynamicBase
            )
        }
        if (segments.size < 2) {
            return OcrParseResult(
                offer = null,
                reason = "Valor reconhecido, mas faltou identificar coleta e viagem (min/km).",
                usefulTexts = useful,
                fare = fare,
                pickup = segments.getOrNull(0),
                surgeMultiplier = surge,
                dynamicBaseFare = dynamicBase
            )
        }

        val pickup = segments[0]
        val trip = segments[1]
        val segmentLines = cleaned.filter { segmentRegex.containsMatchIn(it.text) }.sortedBy { it.top }
        val pickupText = segmentLines.getOrNull(0)?.let { chooseLocationNear(it, cleaned, segmentLines.getOrNull(1)?.top) }
        val destinationText = segmentLines.getOrNull(1)?.let { chooseLocationNear(it, cleaned, null) }
        val offer = RideOffer(
            fare = fare,
            pickupDistanceKm = pickup.distanceKm,
            tripDistanceKm = trip.distanceKm,
            pickupMinutes = pickup.minutes,
            tripMinutes = trip.minutes,
            surgeMultiplier = surge,
            dynamicBaseFare = dynamicBase,
            productName = cleaned.firstOrNull { containsAny(it.text, "moto", "99pop", "99 moto") }?.text,
            pickupLocationText = pickupText,
            destinationLocationText = destinationText,
            sourceText = useful
        )

        return OcrParseResult(
            offer = offer,
            reason = "Oferta reconhecida pelo OCR.",
            usefulTexts = useful,
            fare = fare,
            pickup = pickup,
            trip = trip,
            surgeMultiplier = surge,
            dynamicBaseFare = dynamicBase
        )
    }

    private fun chooseFare(lines: List<OcrLine>): Double? {
        data class Candidate(val value: Double, val score: Int)

        return lines.mapNotNull { line ->
            val match = moneyRegex.find(line.text) ?: return@mapNotNull null
            val value = match.groupValues[1].toNumber() ?: return@mapNotNull null
            if (value <= 0.0 || value > 999.0) return@mapNotNull null

            val lower = line.text.lowercase()
            val excluded = containsAny(lower, "tarifa", "taxa", "serviço", "servico", "bônus", "bonus", "cupom")
            val compactMoneyOnly = lower.replace(" ", "").matches(Regex("""r[${'$'}s][0-9]+(?:[.,][0-9]{1,2})?"""))
            val score =
                (if (excluded) -10_000 else 0) +
                (if (compactMoneyOnly) 2_000 else 0) +
                line.height.coerceAtMost(250) * 10 -
                line.top.coerceAtLeast(0) / 8
            Candidate(value, score)
        }.maxByOrNull { it.score }?.takeIf { it.score > -5_000 }?.value
    }

    private fun detectSegments(lines: List<OcrLine>): List<DetectedRouteSegment> {
        val hits = mutableListOf<DetectedRouteSegment>()

        fun addFrom(text: String) {
            segmentRegex.findAll(text).forEach { match ->
                val minutes = match.groupValues[1].toIntOrNull() ?: return@forEach
                val km = match.groupValues[2].toNumber() ?: return@forEach
                if (minutes in 1..180 && km in 0.1..300.0) {
                    val segment = DetectedRouteSegment(minutes, km)
                    if (hits.none { it.minutes == segment.minutes && kotlin.math.abs(it.distanceKm - segment.distanceKm) < 0.01 }) {
                        hits += segment
                    }
                }
            }
        }

        lines.forEach { addFrom(it.text) }
        if (hits.size < 2) addFrom(lines.joinToString(" ") { it.text })
        return hits.take(4)
    }


    private fun chooseLocationNear(segmentLine: OcrLine, lines: List<OcrLine>, nextSegmentTop: Int?): String? {
        val inline = segmentRegex.replace(segmentLine.text, "").trim(' ', '-', '•', '|', ':')
        if (isLocationCandidate(inline)) return inline
        val maxTop = nextSegmentTop?.minus(8) ?: (segmentLine.top + 260)
        return lines.asSequence()
            .filter { it !== segmentLine }
            .filter { it.top >= segmentLine.top - 55 && it.top <= maxTop }
            .filter { isLocationCandidate(it.text) }
            .sortedWith(compareBy<OcrLine> { kotlin.math.abs(it.top - segmentLine.top) }.thenByDescending { it.left })
            .map { it.text }
            .firstOrNull()
    }

    private fun isLocationCandidate(text: String): Boolean {
        if (text.length < 4 || text.length > 120) return false
        if (!text.any { it.isLetter() }) return false
        if (moneyRegex.containsMatchIn(text) || segmentRegex.containsMatchIn(text) || surgeRegex.containsMatchIn(text)) return false
        if (containsAny(text, "aceitar", "moto", "taxa", "tarifa", "perfil", "corridas", "premium", "oferta", "rota lucro", "rotalucro")) return false
        return true
    }

    private fun isUsefulLine(text: String): Boolean {
        return moneyRegex.containsMatchIn(text) ||
            segmentRegex.containsMatchIn(text) ||
            surgeRegex.containsMatchIn(text) ||
            containsAny(text, "Moto", "Taxa de serviço", "Tarifa base", "Perfil Premium", "Aceitar")
    }

    private fun normalize(raw: String): String = raw
        .replace('×', 'x')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(text: String, vararg needles: String): Boolean {
        val lower = text.lowercase()
        return needles.any { lower.contains(it.lowercase()) }
    }

    private fun String.toNumber(): Double? = replace(',', '.').toDoubleOrNull()
}
