package com.mehmetmertmazici.domainchecker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mehmetmertmazici.domainchecker.R
import com.mehmetmertmazici.domainchecker.databinding.ItemDomainBinding
import com.mehmetmertmazici.domainchecker.model.Domain
import com.google.android.material.chip.Chip

class DomainAdapter(
    private val domains: List<Domain>,
    private val onDomainClick: (Domain) -> Unit
) : RecyclerView.Adapter<DomainAdapter.DomainViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainViewHolder {
        val binding = ItemDomainBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DomainViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DomainViewHolder, position: Int) {
        holder.bind(domains[position])
    }

    override fun getItemCount(): Int = domains.size

    inner class DomainViewHolder(
        private val binding: ItemDomainBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(domain: Domain) {
            binding.apply {
                tvDomainName.text = domain.domain

                setupStatusIndicator(domain)
                setupPriceInfo(domain)
                setupCategories(domain)
                setupAddons(domain)
                setupClickability(domain)

                root.setOnClickListener {
                    onDomainClick(domain)
                }
            }
        }

        private fun setupStatusIndicator(domain: Domain) {
            binding.apply {
                when (domain.status) {
                    "available" -> {
                        tvStatus.text = "Müsait"
                        tvStatus.setBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.status_available)
                        )
                        indicatorStatus.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.status_available)
                        )
                    }
                    "registered" -> {
                        tvStatus.text = "Kayıtlı"
                        tvStatus.setBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.status_registered)
                        )
                        indicatorStatus.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.status_registered)
                        )
                    }
                    else -> {
                        tvStatus.text = "Bilinmiyor"
                        tvStatus.setBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.status_unknown)
                        )
                        indicatorStatus.setCardBackgroundColor(
                            ContextCompat.getColor(itemView.context, R.color.status_unknown)
                        )
                    }
                }
            }
        }

        private fun setupPriceInfo(domain: Domain) {
            binding.apply {
                val price = domain.price
                if (price != null) {
                    val registerPrice = price.register?.get("1")
                    if (registerPrice != null) {
                        tvPrice.text = "€${registerPrice}"
                        tvPrice.visibility = View.VISIBLE

                        val renewPrice = price.renew?.get("1")
                        if (renewPrice != null && renewPrice != registerPrice) {
                            tvRenewalPrice.text = "Yenileme: €${renewPrice}"
                            tvRenewalPrice.visibility = View.VISIBLE
                        } else {
                            tvRenewalPrice.visibility = View.GONE
                        }
                    } else {
                        tvPrice.visibility = View.GONE
                        tvRenewalPrice.visibility = View.GONE
                    }
                } else {
                    tvPrice.visibility = View.GONE
                    tvRenewalPrice.visibility = View.GONE
                }
            }
        }

        private fun setupCategories(domain: Domain) {
            binding.chipGroupCategories.removeAllViews()

            domain.price?.categories?.forEach { category ->
                val chip = Chip(itemView.context).apply {
                    text = category
                    isClickable = false
                    isCheckable = false

                    when (category.lowercase()) {
                        "popular" -> {
                            setChipBackgroundColorResource(R.color.chip_popular_bg)
                            setTextColor(ContextCompat.getColor(context, R.color.chip_popular_text))
                        }
                        "other" -> {
                            setChipBackgroundColorResource(R.color.chip_other_bg)
                            setTextColor(ContextCompat.getColor(context, R.color.chip_other_text))
                        }
                        else -> {
                            setChipBackgroundColorResource(R.color.chip_default_bg)
                            setTextColor(ContextCompat.getColor(context, R.color.chip_default_text))
                        }
                    }
                }
                binding.chipGroupCategories.addView(chip)
            }
        }

        private fun setupAddons(domain: Domain) {
            binding.apply {
                val addons = domain.price?.addons
                if (addons != null) {
                    layoutAddons.visibility = View.VISIBLE

                    iconDns.visibility = if (addons.dns) View.VISIBLE else View.GONE
                    iconEmail.visibility = if (addons.email) View.VISIBLE else View.GONE
                    iconIdProtect.visibility = if (addons.idprotect) View.VISIBLE else View.GONE

                    tvAddonsLabel.visibility = View.VISIBLE
                } else {
                    layoutAddons.visibility = View.GONE
                }
            }
        }

        private fun setupClickability(domain: Domain) {
            binding.apply {
                when (domain.status) {
                    "registered" -> {
                        tvClickHint.text = "Whois bilgisi için tıklayın"
                        tvClickHint.visibility = View.VISIBLE
                    }
                    "available" -> {
                        tvClickHint.text = "Kayıt için tıklayın"
                        tvClickHint.visibility = View.VISIBLE
                        tvClickHint.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_available_dark))
                    }
                    else -> {
                        tvClickHint.visibility = View.GONE
                    }
                }
            }
        }
    }
}