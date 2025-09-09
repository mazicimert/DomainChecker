package com.mehmetmertmazici.domainchecker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.mehmetmertmazici.domainchecker.adapter.DomainAdapter
import com.mehmetmertmazici.domainchecker.databinding.ActivityMainBinding
import com.mehmetmertmazici.domainchecker.databinding.LayoutDomainSuggestionsBinding
import com.mehmetmertmazici.domainchecker.model.Domain
import com.mehmetmertmazici.domainchecker.network.ApiClient
import com.mehmetmertmazici.domainchecker.ui.WhoisDialog
import com.mehmetmertmazici.domainchecker.utils.DomainCorrector
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var domainAdapter: DomainAdapter
    private var searchJob: Job? = null
    private val domains = mutableListOf<Domain>()
    private val domainCorrector = DomainCorrector()
    private var suggestionBinding: LayoutDomainSuggestionsBinding? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val MIN_SEARCH_LENGTH = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        setupSearchInput()
        setupButtons()
    }

    private fun setupUI() {


        setSupportActionBar(binding.toolbar)
        // Action bar'ın default title'ını gizle
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupRecyclerView() {
        domainAdapter = DomainAdapter(
            domains = domains,
            onDomainClick = { domain ->
                when (domain.status) {
                    "registered" -> {
                        // Kayıtlı domain - Whois göster
                        showWhoisDialog(domain.domain)
                    }
                    "available" -> {
                        // Müsait domain - İsimKayıt kayıt sayfasına yönlendir
                        openIsimkayitRegistration(domain.domain)
                    }
                }
            }
        )

        binding.recyclerViewDomains.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = domainAdapter
        }
    }

    private fun openIsimkayitRegistration(domain: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = "https://www.isimkayit.com/alan-adi-kaydi?query=$domain".toUri()
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open registration page", e)
            showError("Web sayfası açılamadı")
        }
    }

    private fun setupSearchInput() {
        binding.etSearchDomain.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                updateButtonStates(query)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Enter tuşuna basıldığında arama yap
        binding.etSearchDomain.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    private fun setupButtons() {
        binding.btnSearch.setOnClickListener {
            performSearch()
        }
    }

    private fun updateButtonStates(query: String) {
        val hasText = query.isNotEmpty()
        val isValidLength = query.length >= MIN_SEARCH_LENGTH

        // Sorgula butonunu aktif/pasif yap
        binding.btnSearch.isEnabled = isValidLength

        // Sonuçları temizle (sadece text tamamen boşsa)
        if (query.isEmpty()) {
            clearResults()
        }
    }

    private fun performSearch() {
        val query = binding.etSearchDomain.text?.toString()?.trim() ?: ""

        if (query.length < MIN_SEARCH_LENGTH) {
            showError("En az $MIN_SEARCH_LENGTH karakter girmelisiniz")
            return
        }

        if (query.isEmpty()) {
            showError("Lütfen bir domain adı girin")
            return
        }

        // Önce typo check yap
        if (hasPotentialTypo(query)) {
            val suggestions = domainCorrector.getSuggestions(query)
            if (suggestions.isNotEmpty()) {
                showDomainSuggestions(query, suggestions)
                return
            }
        }

        // Domain ve extension'ı ayır
        val (domainName, preferredExtension) = parseDomainQuery(query)

        // Normalizasyon YAPMA - direkt API'ye gönder
        binding.etSearchDomain.clearFocus()
        hideSuggestions()
        searchDomainsWithExtensionPriority(domainName, preferredExtension)
    }

    private fun parseDomainQuery(query: String): Pair<String, String?> {
        if (!query.contains('.')) {
            return Pair(query, null)
        }

        // Boş parçaları filtrele ve temizle
        val parts = query.split('.').filter { it.isNotBlank() }

        return when {
            parts.isEmpty() -> Pair("", null) // "..." gibi durumlar
            parts.size == 1 -> Pair(parts[0], null) // "google." gibi durumlar
            else -> {
                val domainName = parts[0]
                val extension = parts.drop(1).joinToString(".")
                Pair(domainName, extension)
            }
        }
    }

    private fun searchDomainsWithExtensionPriority(domainName: String, preferredExtension: String?) {
        Log.d(TAG, "Searching for domain: $domainName, preferred extension: $preferredExtension")

        searchJob?.cancel()
        showLoading(true)
        setButtonsEnabled(false)

        searchJob = lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.searchDomains(domainName)

                if (response.code == 1 && response.status == "success") {
                    val domainList = response.message?.domains ?: emptyList()

                    // Extension priority sorting
                    val sortedDomains = if (preferredExtension != null) {
                        prioritizeByExtension(domainList, preferredExtension)
                    } else {
                        domainList
                    }

                    updateDomainList(sortedDomains)
                    showEmptyState(sortedDomains.isEmpty())
                } else {
                    showError("Arama sırasında bir hata oluştu")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                showError("Bağlantı hatası: ${e.message}")
            } finally {
                showLoading(false)
                setButtonsEnabled(true)
            }
        }
    }

    private fun prioritizeByExtension(domains: List<Domain>, preferredExtension: String): List<Domain> {
        return domains.sortedWith { domain1, domain2 ->
            val ext1 = domain1.domain.substringAfter('.', "")
            val ext2 = domain2.domain.substringAfter('.', "")

            when {
                ext1 == preferredExtension && ext2 != preferredExtension -> -1
                ext1 != preferredExtension && ext2 == preferredExtension -> 1
                else -> 0
            }
        }
    }

    private fun hasPotentialTypo(query: String): Boolean {
        return when {
            // Nokta ile bitiyor: google.
            query.endsWith('.') -> true

            // Nokta yok: google (false - normal arama yapsın)
            !query.contains('.') -> false

            // Eksik uzantı kontrolü
            else -> {
                val parts = query.split('.')
                if (parts.size < 2) return false

                val extension = parts.drop(1).joinToString(".")

                // Geçerli uzantılar listesi
                val validExtensions = setOf(
                    // Türkiye
                    "com.tr", "net.tr", "org.tr", "info.tr", "biz.tr", "tv.tr",
                    "edu.tr", "gov.tr", "mil.tr", "k12.tr", "av.tr", "dr.tr",
                    "pol.tr", "bel.tr", "tsk.tr", "web.tr", "gen.tr", "tel.tr",

                    // Genel
                    "com", "net", "org", "info", "biz", "co", "io", "me", "tv", "cc",
                    "app", "dev", "tech", "online", "site", "website", "store", "shop",
                    "blog", "news", "pro", "name", "mobi", "travel", "museum",

                    // Ülke kodları
                    "de", "uk", "fr", "it", "es", "nl", "be", "at", "ch", "se",
                    "no", "dk", "fi", "pl", "cz", "hu", "ru", "cn", "jp", "kr",
                    "in", "au", "ca", "mx", "br", "ar", "za", "eg", "il", "ae"
                )

                !validExtensions.contains(extension.lowercase())
            }
        }
    }

    private fun updateDomainList(newDomains: List<Domain>) {
        domains.clear()
        domains.addAll(newDomains)
        domainAdapter.notifyDataSetChanged()
    }

    private fun clearResults() {
        domains.clear()
        domainAdapter.notifyDataSetChanged()
        showEmptyState(false)
        showLoading(false)
        hideSuggestions() // Öneri kartını da temizle
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            // Full page modal göster
            binding.loadingModal.visibility = View.VISIBLE
            startLoadingAnimation()

            // Eski loading elementi gizle
            binding.progressBar.visibility = View.GONE
            binding.recyclerViewDomains.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        } else {
            // Modal gizle
            binding.loadingModal.visibility = View.GONE
            stopLoadingAnimation()
        }
    }

    private var loadingAnimationJob: Job? = null

    private fun startLoadingAnimation() {
        loadingAnimationJob?.cancel() // Önceki animasyonu iptal et

        loadingAnimationJob = lifecycleScope.launch {
            try {
                while (binding.loadingModal.visibility == View.VISIBLE) {
                    // Dot 1
                    binding.dot1.animate().alpha(1f).setDuration(300).start()
                    delay(300)

                    // Dot 2
                    binding.dot2.animate().alpha(1f).setDuration(300).start()
                    delay(300)

                    // Dot 3
                    binding.dot3.animate().alpha(1f).setDuration(300).start()
                    delay(300)

                    // Reset
                    binding.dot1.animate().alpha(0.4f).setDuration(200).start()
                    binding.dot2.animate().alpha(0.4f).setDuration(200).start()
                    binding.dot3.animate().alpha(0.4f).setDuration(200).start()
                    delay(500)
                }
            } catch (e: CancellationException) {
                // Normal iptal durumu - hiçbir şey yapma
            }
        }
    }

    private fun stopLoadingAnimation() {
        loadingAnimationJob?.cancel()
        loadingAnimationJob = null

        // Dot'ları varsayılan duruma getir
        binding.dot1.alpha = 0.4f
        binding.dot2.alpha = 0.4f
        binding.dot3.alpha = 0.4f
    }

    private fun showDomainSuggestions(originalDomain: String, suggestions: List<DomainCorrector.Suggestion>) {
        // Önceki sonuçları temizle
        clearResults()

        // Suggestion card oluştur ve göster
        createSuggestionCard(originalDomain, suggestions)
    }

    private fun createSuggestionCard(originalDomain: String, suggestions: List<DomainCorrector.Suggestion>) {
        // Önceki suggestion card'ı temizle
        hideSuggestions()

        // Yeni suggestion card oluştur
        suggestionBinding = LayoutDomainSuggestionsBinding.inflate(LayoutInflater.from(this))

        // Content container'a ekle (RecyclerView'den önce)
        val contentContainer = binding.recyclerViewDomains.parent as android.view.ViewGroup
        contentContainer.addView(suggestionBinding!!.root, 0)

        setupSuggestionCard(originalDomain, suggestions)
    }

    private fun setupSuggestionCard(originalDomain: String, suggestions: List<DomainCorrector.Suggestion>) {
        suggestionBinding?.apply {
            // Title ve description ayarla
            tvSuggestionTitle.text = domainCorrector.getExplanation(suggestions)

            if (suggestions.isNotEmpty()) {
                tvSuggestionDescription.text = "Aşağıdaki önerilerden birini seçebilirsiniz:"

                // Suggestion chip'leri oluştur (confidence score ile)
                chipGroupSuggestions.removeAllViews()
                suggestions.forEach { suggestion ->
                    val chip = createSuggestionChip(suggestion)
                    chipGroupSuggestions.addView(chip)
                }
            } else {
                tvSuggestionDescription.text = "Benzer bir domain bulunamadı"
                chipGroupSuggestions.removeAllViews()
            }

            // Button listeners
            btnCloseSuggestions.setOnClickListener {
                hideSuggestions()
            }

            btnTryAnyway.setOnClickListener {
                hideSuggestions()
                val (domainName, preferredExtension) = parseDomainQuery(originalDomain)
                searchDomainsWithExtensionPriority(domainName, preferredExtension)
            }

            btnHelp.setOnClickListener {
                showDomainHelp()
            }
        }
    }

    private fun createSuggestionChip(suggestion: DomainCorrector.Suggestion): Chip {
        return Chip(this).apply {
            // Domain + confidence score göster
            text = "${suggestion.domain} (${(suggestion.confidenceScore * 100).toInt()}%)"
            isClickable = true
            isCheckable = false

            // Confidence score'a göre renk ayarla
            when {
                suggestion.confidenceScore >= 0.9 -> {
                    setChipBackgroundColorResource(R.color.status_available_light)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.status_available_dark))
                }
                suggestion.confidenceScore >= 0.75 -> {
                    setChipBackgroundColorResource(R.color.chip_popular_bg)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.chip_popular_text))
                }
                else -> {
                    setChipBackgroundColorResource(R.color.chip_default_bg)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.chip_default_text))
                }
            }

            chipStrokeWidth = 2f
            chipStrokeColor = androidx.core.content.ContextCompat.getColorStateList(context, R.color.primary)

            // Click listener - seçilen öneriye göre arama yap
            setOnClickListener {
                binding.etSearchDomain.setText(suggestion.domain)
                hideSuggestions()
                // Seçilen domain'i extension priority sistemi ile ara
                val (domainName, preferredExtension) = parseDomainQuery(suggestion.domain)
                searchDomainsWithExtensionPriority(domainName, preferredExtension)
            }
        }
    }

    private fun hideSuggestions() {
        suggestionBinding?.root?.let { suggestionCard ->
            val parent = suggestionCard.parent as? android.view.ViewGroup
            parent?.removeView(suggestionCard)
        }
        suggestionBinding = null
    }

    private fun showDomainHelp() {
        val helpMessage = """
            Domain Arama Yardımı:
            
            • Popüler siteler: google.com, facebook.com
            • Doğru yazım: amazon.com (amazom değil)
            • Geçerli uzantılar: .com, .net, .org
            
            Sistem otomatik olarak:
            • Yazım hatalarını düzeltir
            • Benzer domainler önerir
            • Confidence skorları gösterir
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Domain Correction Sistemi")
            .setMessage(helpMessage)
            .setPositiveButton("Anladım") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEmptyState(show: Boolean) {
        binding.layoutEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerViewDomains.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        val query = binding.etSearchDomain.text?.toString()?.trim() ?: ""

        // Sorgula butonu: enabled durumu VE geçerli text uzunluğu
        binding.btnSearch.isEnabled = enabled && query.length >= MIN_SEARCH_LENGTH

        // Temizle butonu her zaman aktif (text varsa görünür)
        binding.btnClear.isEnabled = true
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showWhoisDialog(domain: String) {
        lifecycleScope.launch {
            try {
                val whoisData = ApiClient.apiService.getWhoisInfo(domain)

                // Düz metin verisini doğrudan WhoisDialog'a gönder
                val whoisDialog = WhoisDialog.newInstance(domain, whoisData)
                whoisDialog.show(supportFragmentManager, "WhoisDialog")

            } catch (e: Exception) {
                // Ağ veya ayrıştırma hatası durumunda
                Log.e(TAG, "Whois error", e)
                showError("Whois sorgulama hatası: ${e.message}")
            }
        }
    }
}