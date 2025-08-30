package com.mehmetmertmazici.domainchecker


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mehmetmertmazici.domainchecker.adapter.DomainAdapter
import com.mehmetmertmazici.domainchecker.databinding.ActivityMainBinding
import com.mehmetmertmazici.domainchecker.model.Domain
import com.mehmetmertmazici.domainchecker.network.ApiClient
import com.mehmetmertmazici.domainchecker.ui.WhoisDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var domainAdapter: DomainAdapter
    private var searchJob: Job? = null
    private val domains = mutableListOf<Domain>()

    companion object {
        private const val TAG = "MainActivity"
        private const val SEARCH_DELAY = 500L
        private const val MIN_SEARCH_LENGTH = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        setupSearch()
    }

    private fun setupUI() {
        binding.toolbar.title = "Domain Checker"
        setSupportActionBar(binding.toolbar)

        binding.btnClear.setOnClickListener {
            binding.etSearchDomain.text?.clear()
            clearResults()
        }
    }

    private fun setupRecyclerView() {
        domainAdapter = DomainAdapter(
            domains = domains,
            onDomainClick = { domain ->
                if (domain.status == "registered") {
                    showWhoisDialog(domain.domain)
                }
            }
        )

        binding.recyclerViewDomains.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = domainAdapter
        }
    }

    private fun setupSearch() {
        binding.etSearchDomain.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""

                binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                searchJob?.cancel()

                if (query.length >= MIN_SEARCH_LENGTH) {
                    searchJob = lifecycleScope.launch {
                        delay(SEARCH_DELAY)
                        searchDomains(query)
                    }
                } else {
                    clearResults()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun searchDomains(query: String) {
        Log.d(TAG, "Searching for domain: $query")

        showLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.searchDomains(query)

                if (response.code == 1 && response.status == "success") {
                    val domainList = response.message?.domains ?: emptyList()
                    updateDomainList(domainList)
                    showEmptyState(domainList.isEmpty())
                } else {
                    showError("Arama sırasında bir hata oluştu")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                showError("Bağlantı hatası: ${e.message}")
            } finally {
                showLoading(false)
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
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerViewDomains.visibility = if (show) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        binding.layoutEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerViewDomains.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }


    private fun showWhoisDialog(domain: String) {
        lifecycleScope.launch {
            try {
                val whoisData = ApiClient.apiService.getWhoisInfo(domain)

                // Düz metin verisini doğrudan WhoisDialog'a gönderin
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