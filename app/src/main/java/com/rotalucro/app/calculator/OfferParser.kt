package com.rotalucro.app.calculator

/**
 * Interpreta os textos expostos pela tela de oferta da 99.
 * O parser evita usar endereço, nome ou avaliação; somente valor, tempo e distância.
 */
object OfferParser {
    private val currencyRegex = Regex("R\\$\\s*[^0-9]{0,4}(\\d{1,4}(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val distanceRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("(\\d+)\\s*(?:min|minuto|minutos)", RegexOption.IGNORE_CASE)
    private val segmentRegex = Regex(
        "(\\d+)\\s*(?:min|minuto|minutos)\\s*[^0-9]{0,10}(\\d+(?:[.,]\\d+)?)\\s*km",
        RegexOption.IGNORE_CASE
    )
    private val multiplierRegex = Regex("(?:⚡\\s*)?(\\d+(?:[.,]\\d+)?)\\s*x", RegexOption.IGNORE_CASE)
    private val dynamicFareRegex = Regex(
        "R\\$\\s*(\\d+(?:[.,]\\d{1,2})?).{0,35}(?:tarifa\\s+base|base\\s+din[aâ]mica)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(texts: List<String>): RideOffer? = parseWithDiagnostics(texts).offer

    fun parseWithDiagnostics(texts: List<String>): ParseAttempt {
        val normalized = texts
            .asSequence()
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        if (normalized.isEmpty()) {
            return ParseAttempt(null, "Nenhum texto acessível foi encontrado na tela.", 0)
        }

        val joined = normalized.joinToString(" • ")
        val fareCandidates = collectFareCandidates(normalized, joined)
        val fare = fareCandidates
            .filter { it.value > 0.0 }
            .maxWithOrNull(compareBy<MoneyCandidate> { it.score }.thenBy { it.value })
            ?.value
            ?: return ParseAttempt(
                null,
                "A tela foi lida, mas o valor total da oferta não foi reconhecido.",
                normalized.size
            )

        val segments = normalized.flatMap { text ->
            segmentRegex.findAll(text).mapNotNull { match ->
                val minutes = match.groupValues.getOrNull(1)?.toIntOrNull()
                val distance = match.groupValues.getOrNull(2)?.toBrazilianDouble()
                if (minutes != null && minutes in 1..600 && distance != null && distance in 0.1..500.0) {
                    RouteSegment(minutes, distance)
                } else {
                    null
                }
            }.toList()
        }

        val distances = normalized.flatMap { text ->
            distanceRegex.findAll(text).mapNotNull {
                it.groupValues.getOrNull(1)?.toBrazilianDouble()
            }.toList()
        }.filter { it in 0.1..500.0 }

        val minutes = normalized.flatMap { text ->
            minuteRegex.findAll(text).mapNotNull {
                it.groupValues.getOrNull(1)?.toIntOrNull()
            }.toList()
        }.filter { it in 1..600 }

        val pickupDistance: Double
        val tripDistance: Double
        val pickupMinutes: Int
        val tripMinutes: Int

        if (segments.size >= 2) {
            pickupMinutes = segments[0].minutes
            pickupDistance = segments[0].distanceKm
            tripMinutes = segments[1].minutes
            tripDistance = segments[1].distanceKm
        } else {
            if (distances.size < 2 || minutes.size < 2) {
                return ParseAttempt(
                    null,
                    "Valor reconhecido, mas faltaram os dois tempos e as duas distâncias da oferta.",
                    normalized.size
                )
            }
            pickupDistance = distances[0]
            tripDistance = distances[1]
            pickupMinutes = minutes[0]
            tripMinutes = minutes[1]
        }

        val surgeMultiplier = multiplierRegex.find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBrazilianDouble()

        val dynamicBaseFare = dynamicFareRegex.find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBrazilianDouble()

        val productName = normalized.firstOrNull { value ->
            val compact = value.trim().lowercase()
            compact == "moto" || compact == "99moto" || compact == "99pop" || compact == "pop"
        }

        return ParseAttempt(
            offer = RideOffer(
                fare = fare,
                pickupDistanceKm = pickupDistance,
                tripDistanceKm = tripDistance,
                pickupMinutes = pickupMinutes,
                tripMinutes = tripMinutes,
                surgeMultiplier = surgeMultiplier,
                dynamicBaseFare = dynamicBaseFare,
                productName = productName,
                sourceText = normalized
            ),
            reason = "Oferta reconhecida com sucesso.",
            normalizedTextCount = normalized.size
        )
    }

    private fun collectFareCandidates(
        normalized: List<String>,
        joined: String
    ): List<MoneyCandidate> {
        val candidates = mutableListOf<MoneyCandidate>()

        normalized.forEachIndexed { index, text ->
            currencyRegex.findAll(text).forEach { match ->
                val value = match.groupValues.getOrNull(1)?.toBrazilianDouble() ?: return@forEach
                candidates += MoneyCandidate(
                    value = value,
                    score = scoreMoneyText(text, value, index)
                )
            }
        }

        currencyRegex.findAll(joined).forEach { match ->
            val value = match.groupValues.getOrNull(1)?.toBrazilianDouble() ?: return@forEach
            val start = (match.range.first - 45).coerceAtLeast(0)
            val end = (match.range.last + 55).coerceAtMost(joined.lastIndex)
            val context = joined.substring(start, end + 1)
            candidates += MoneyCandidate(
                value = value,
                score = scoreMoneyText(context, value, normalized.size)
            )
        }

        normalized.forEachIndexed { index, text ->
            if (text.trim() == "R$" && index + 1 < normalized.size) {
                normalized[index + 1]
                    .trim()
                    .takeIf { it.matches(Regex("\\d{1,4}(?:[.,]\\d{1,2})?")) }
                    ?.toBrazilianDouble()
                    ?.let { value ->
                        candidates += MoneyCandidate(value, 80 + value.toInt())
                    }
            }
        }

        return candidates.distinctBy { it.value to it.score }
    }

    private fun scoreMoneyText(text: String, value: Double, index: Int): Int {
        val lower = text.lowercase()
        var score = value.coerceAtMost(999.0).toInt()

        if (lower.contains("tarifa base") || lower.contains("base dinâmica") || lower.contains("base dinamica")) {
            score -= 300
        }
        if (lower.contains("taxa de serviço") || lower.contains("taxa de servico")) {
            score -= 250
        }
        if (lower.contains("aceitar") || lower.contains("moto") || lower.trim().matches(Regex("r\\$\\s*\\d.+"))) {
            score += 120
        }
        if (index <= 5) score += 20
        return score
    }

    private fun normalize(value: String): String = value
        .replace('\u00A0', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.toBrazilianDouble(): Double? {
        val cleaned = trim()
        return when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
            else -> cleaned.toDoubleOrNull()
        }
    }

    private data class RouteSegment(val minutes: Int, val distanceKm: Double)
    private data class MoneyCandidate(val value: Double, val score: Int)
}
