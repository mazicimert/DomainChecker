package com.mehmetmertmazici.domainchecker.utils

import kotlin.math.*

/**
 * Basit domain yazım hatası düzeltme sistemi
 * Sadece typo correction, popüler domain önerisi yok
 */
class DomainCorrector {

    companion object {
        // Yaygın TLD yazım hataları
        private val TLD_CORRECTIONS = mapOf(
            "cmo" to "com", "ocm" to "com", "con" to "com", "comm" to "com",
            "nte" to "net", "nett" to "net", "met" to "net",
            "ogr" to "org", "rog" to "org", "orgr" to "org",
            "fo" to "info", "inof" to "info", "infoo" to "info",
            "co" to "com", "cm" to "com", "om" to "com"
        )

        // Yaygın karakter değişimleri
        private val COMMON_CHAR_SWAPS = mapOf(
            "gogle" to "google", "googel" to "google", "goohle" to "google",
            "facbook" to "facebook", "facebok" to "facebook",
            "youtub" to "youtube", "yotube" to "youtube",
            "amazom" to "amazon", "amzon" to "amazon",
            "wikipeda" to "wikipedia", "wikpedia" to "wikipedia",
            "twiter" to "twitter", "twiiter" to "twitter",
            "instgram" to "instagram", "instagam" to "instagram",
            "linkdin" to "linkedin", "linkedn" to "linkedin",
            "microsft" to "microsoft", "mircosoft" to "microsoft",
            "githb" to "github", "gitub" to "github"
        )

        // QWERTY klavye yakın tuşlar
        private val KEYBOARD_NEIGHBORS = mapOf(
            'q' to "wa", 'w' to "qeas", 'e' to "wrds", 'r' to "etfg", 't' to "rygh",
            'y' to "tugh", 'u' to "yihj", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
            'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc",
            'g' to "ftyhbv", 'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm",
            'l' to "kop", 'z' to "asx", 'x' to "zsdc", 'c' to "xdfv",
            'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
        )

        private const val MAX_EDIT_DISTANCE = 2
        private const val MIN_CONFIDENCE_SCORE = 0.7
    }

    data class Suggestion(
        val domain: String,
        val confidenceScore: Double,
        val correctionType: String
    )

    /**
     * Sadece yazım hatası düzeltme önerileri
     */
    fun getSuggestions(input: String): List<Suggestion> {
        val cleanInput = input.trim().lowercase()
        if (cleanInput.length < 3) return emptyList()

        val suggestions = mutableListOf<Suggestion>()

        // 1. TLD düzeltmeleri
        suggestions.addAll(fixTLDTypos(cleanInput))

        // 2. Eksik nokta ekleme
        suggestions.addAll(addMissingDot(cleanInput))

        // 3. Yaygın karakter swap'leri
        suggestions.addAll(fixCharacterSwaps(cleanInput))

        // 4. Klavye yakınlığı (tek karakter değişimi)
        suggestions.addAll(fixKeyboardTypos(cleanInput))

        // 5. Eksik/fazla karakter
        suggestions.addAll(fixMissingExtraChars(cleanInput))

        return suggestions
            .distinctBy { it.domain }
            .filter { it.confidenceScore >= MIN_CONFIDENCE_SCORE }
            .sortedByDescending { it.confidenceScore }
            .take(5)
    }

    /**
     * TLD yazım hatalarını düzelt
     */
    private fun fixTLDTypos(input: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val parts = input.split('.')

        if (parts.size >= 2) {
            val domainPart = parts[0]
            val tldPart = parts.drop(1).joinToString(".")

            // Exact TLD corrections
            TLD_CORRECTIONS[tldPart]?.let { correctedTLD ->
                val correctedDomain = "$domainPart.$correctedTLD"
                suggestions.add(Suggestion(correctedDomain, 0.95, "tld_correction"))
            }

            // Partial TLD corrections (eksik karakter)
            if (tldPart.length <= 3) {
                val partialCorrections = mapOf(
                    "c" to "com", "co" to "com", "cm" to "com",
                    "n" to "net", "ne" to "net", "nt" to "net",
                    "o" to "org", "or" to "org", "rg" to "org",
                    "t" to "tr", "tr" to "com.tr", // Önemli ekleme
                    "com.t" to "com.tr", "net.t" to "net.tr", "org.t" to "org.tr"
                )

                partialCorrections[tldPart]?.let { correctedTLD ->
                    val correctedDomain = "$domainPart.$correctedTLD"
                    suggestions.add(Suggestion(correctedDomain, 0.9, "partial_tld_correction"))
                }
            }
        }

        return suggestions
    }

    /**
     * Eksik uzantı tamamlama (hem nokta yoksa hem de nokta ile bitiyorsa)
     */
    private fun addMissingDot(input: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        when {
            // Sadece domain adı (nokta yok): google -> google.com, google.net
            !input.contains('.') -> {
                listOf("com", "net", "org", "com.tr", "net.tr").forEach { ext ->
                    suggestions.add(Suggestion("$input.$ext", 0.8, "missing_extension"))
                }
            }

            // Nokta ile bitiyor: google. -> google.com, google.net
            input.endsWith('.') -> {
                val domainPart = input.dropLast(1) // Son noktayı çıkar
                listOf("com", "net", "org", "com.tr", "net.tr").forEach { ext ->
                    suggestions.add(Suggestion("$domainPart.$ext", 0.85, "incomplete_extension"))
                }
            }

            // Eksik uzantı: google.c -> google.com, google.com.tr
            else -> {
                val parts = input.split('.')
                if (parts.size == 2 && parts[1].length <= 2) {
                    val domainPart = parts[0]
                    val partialExt = parts[1]

                    // Partial extension'a göre öneriler
                    val extensionSuggestions = when (partialExt.lowercase()) {
                        "c" -> listOf("com", "com.tr")
                        "n" -> listOf("net", "net.tr")
                        "o" -> listOf("org", "org.tr")
                        "t" -> listOf("tr", "com.tr", "net.tr")
                        else -> listOf("com", "net", "org")
                    }

                    extensionSuggestions.forEach { ext ->
                        suggestions.add(Suggestion("$domainPart.$ext", 0.9, "partial_extension_completion"))
                    }
                }
            }
        }

        return suggestions.take(5) // Max 5 öneri
    }

    /**
     * Bilinen karakter swap'lerini düzelt
     */
    private fun fixCharacterSwaps(input: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val parts = input.split('.')
        val domainPart = parts[0]
        val extension = if (parts.size > 1) ".${parts.drop(1).joinToString(".")}" else ""

        COMMON_CHAR_SWAPS[domainPart]?.let { corrected ->
            val finalExt = if (extension.isEmpty()) ".com" else extension
            suggestions.add(Suggestion("$corrected$finalExt", 0.9, "character_swap"))
        }

        return suggestions
    }

    /**
     * Klavye yakınlığı hatalarını düzelt (tek karakter)
     */
    private fun fixKeyboardTypos(input: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val parts = input.split('.')
        val domainPart = parts[0]
        val extension = if (parts.size > 1) ".${parts.drop(1).joinToString(".")}" else ".com"

        // Her pozisyonda tek karakter değiştir
        for (i in domainPart.indices) {
            val char = domainPart[i]
            KEYBOARD_NEIGHBORS[char]?.forEach { neighbor ->
                val corrected = domainPart.toCharArray()
                corrected[i] = neighbor
                val correctedDomain = String(corrected) + extension

                if (correctedDomain != input) {
                    suggestions.add(Suggestion(correctedDomain, 0.75, "keyboard_typo"))
                }
            }
        }

        return suggestions.take(3) // Çok fazla olmasın
    }

    /**
     * Eksik veya fazla karakter düzeltme
     */
    private fun fixMissingExtraChars(input: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val parts = input.split('.')
        val domainPart = parts[0]
        val extension = if (parts.size > 1) ".${parts.drop(1).joinToString(".")}" else ".com"

        // Yaygın eksik karakter senaryoları
        val commonInsertions = mapOf(
            "gogle" to "google",  // eksik 'o'
            "amazo" to "amazon",  // eksik 'n'
            "facbok" to "facebook", // eksik 'e'
            "youtub" to "youtube"   // eksik 'e'
        )

        commonInsertions[domainPart]?.let { corrected ->
            suggestions.add(Suggestion("$corrected$extension", 0.85, "missing_character"))
        }

        // Fazla karakter (double letter)
        if (domainPart.length > 3) {
            for (i in 0 until domainPart.length - 1) {
                if (domainPart[i] == domainPart[i + 1]) {
                    val corrected = domainPart.removeRange(i, i + 1)
                    if (corrected.length >= 3) {
                        suggestions.add(Suggestion("$corrected$extension", 0.8, "extra_character"))
                    }
                }
            }
        }

        return suggestions
    }

    /**
     * Kullanıcı dostu açıklama
     */
    fun getExplanation(suggestions: List<Suggestion>): String {
        return when {
            suggestions.isEmpty() -> "Yazım hatası bulunamadı"
            suggestions.size == 1 -> "Şunu mu demek istediniz?"
            else -> "Yazım hatası olabilir, şunlardan birini mi demek istediniz?"
        }
    }
}