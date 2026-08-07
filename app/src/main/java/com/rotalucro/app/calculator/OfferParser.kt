package com.rotalucro.app.calculator

/**
 * Parser inicial para a tela de oferta da 99.
 * A ordem exata dos campos pode variar conforme a versão do app da 99.
 * Por isso, este parser é deliberadamente conservador e será refinado
 * com textos reais capturados do aparelho de teste.
 */
object OfferParser {
    private val moneyRegex = Regex("(?:R\\$\\s*)?(\\d{1,4}(?:[.,]\\d{1,2})?)")
    private val distanceRegex = Regex("(\\d+(?:[.,]\\d+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val minuteRegex = Regex("(\\d+)\\s*(?:min|minuto|minutos)", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>): RideOffer? {
        val normalized = texts
            .map { it.trim().replace("\u00A0", " ") }
            .filter { it.isNotBlank() }
            .distinct()

        val fare = normalized
            .asSequence()
            .filter { it.contains("R$") }
            .mapNotNull { text ->
                moneyRegex.find(text)?.groupValues?.getOrNull(1)?.toBrazilianDouble()
            }
            .firstOrNull { it > 0 }
            ?: return null

        val distances = normalized.flatMap { text ->
            distanceRegex.findAll(text).mapNotNull {
                it.groupValues.getOrNull(1)?.toBrazilianDouble()
            }.toList()
        }.filter { it > 0 && it < 500 }

        val minutes = normalized.flatMap { text ->
            minuteRegex.findAll(text).mapNotNull {
                it.groupValues.getOrNull(1)?.toIntOrNull()
            }.toList()
        }.filter { it in 1..600 }

        if (distances.isEmpty() || minutes.isEmpty()) return null

        return RideOffer(
            fare = fare,
            pickupDistanceKm = distances.getOrElse(0) { 0.0 },
            tripDistanceKm = distances.getOrElse(1) { distances.first() },
            pickupMinutes = minutes.getOrElse(0) { 0 },
            tripMinutes = minutes.getOrElse(1) { minutes.first() },
            sourceText = normalized
        )
    }

    private fun String.toBrazilianDouble(): Double? {
        val cleaned = trim()
        return when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
            else -> cleaned.toDoubleOrNull()
        }
    }
}
