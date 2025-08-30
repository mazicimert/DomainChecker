package com.mehmetmertmazici.domainchecker.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.mehmetmertmazici.domainchecker.databinding.DialogWhoisBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WhoisDialog : DialogFragment() {

    private var _binding: DialogWhoisBinding? = null
    private val binding get() = _binding!!

    private var domainName: String? = null
    private var whoisData: String? = null

    companion object {
        private const val ARG_DOMAIN = "domain"
        private const val ARG_WHOIS_DATA = "whois_data"

        fun newInstance(domain: String, whoisData: String): WhoisDialog {
            return WhoisDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DOMAIN, domain)
                    putString(ARG_WHOIS_DATA, whoisData)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            domainName = it.getString(ARG_DOMAIN)
            whoisData = it.getString(ARG_WHOIS_DATA)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogWhoisBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        // Remove default background to show custom rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        setupUI()

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return if (_binding != null) binding.root else null
    }

    private fun setupUI() {
        binding.apply {
            // Set domain name in title
            tvTitle.text = "Whois: $domainName"

            // Close button
            btnClose.setOnClickListener {
                dismiss()
            }

            // Copy button
            btnCopy.setOnClickListener {
                copyWhoisData()
            }

            // Show whois data
            displayWhoisData()
        }
    }

    private fun displayWhoisData() {
        val data = whoisData ?: "Whois bilgisi alınamadı"

        // Try to format the whois data nicely
        val formattedData = formatWhoisData(data)
        binding.tvWhoisContent.text = formattedData

        // Show scroll indicator if content is long
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollView = binding.scrollView
            val child = scrollView.getChildAt(0)

            val canScrollDown = child.bottom > (scrollView.height + scrollView.scrollY)
            val canScrollUp = scrollView.scrollY > 0

            binding.scrollIndicatorTop.visibility = if (canScrollUp) View.VISIBLE else View.GONE
            binding.scrollIndicatorBottom.visibility = if (canScrollDown) View.VISIBLE else View.GONE
        }
    }

    private fun formatWhoisData(rawData: String): String {
        // Raw text olarak geldiği için sadece kaçış karakterlerini düzeltelim
        return rawData.replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "")
            .trim()
    }

    private fun copyWhoisData() {
        val clipboardManager = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager

        val clipData = android.content.ClipData.newPlainText(
            "Whois: $domainName",
            whoisData ?: ""
        )

        clipboardManager.setPrimaryClip(clipData)

        // Show confirmation
        android.widget.Toast.makeText(
            requireContext(),
            "Whois bilgisi panoya kopyalandı",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    override fun onStart() {
        super.onStart()
        // Make dialog full width with some margin
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}