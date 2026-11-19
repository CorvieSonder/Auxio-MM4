/*
 * Copyright (c) 2026 Auxio Project
 * SettingsSearchFragment.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.settings.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.transition.MaterialSharedAxis
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentSettingsSearchBinding
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.ui.ViewBindingFragment
import org.oxycblt.auxio.util.getSystemServiceCompat
import org.oxycblt.auxio.util.systemBarInsetsCompat
import timber.log.Timber as L

/**
 * Searches settings across every preference screen and opens the screen of the chosen setting.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class SettingsSearchFragment : ViewBindingFragment<FragmentSettingsSearchBinding>() {
    private val adapter = SettingsSearchAdapter(::openEntry)
    private var index = listOf<SettingsSearchEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentSettingsSearchBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentSettingsSearchBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)
        index = SettingsSearchIndex.build(requireContext())
        L.d("Indexed ${index.size} settings")

        binding.settingsSearchToolbar.apply {
            setNavigationOnClickListener { findNavController().navigateUp() }
            menu
                .add(R.string.desc_clear_search)
                .setIcon(R.drawable.ic_close_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                binding.settingsSearchEditText.text = null
                true
            }
        }

        binding.settingsSearchRecycler.apply {
            adapter = this@SettingsSearchFragment.adapter
            setOnApplyWindowInsetsListener { _, insets ->
                updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
                insets
            }
        }

        binding.settingsSearchEditText.apply {
            addTextChangedListener { text -> updateResults(text?.toString() ?: "") }
            if (savedInstanceState == null) {
                requestFocus()
                postDelayed(200) {
                    context
                        .getSystemServiceCompat(InputMethodManager::class)
                        .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        updateResults(binding.settingsSearchEditText.text?.toString() ?: "")
    }

    override fun onDestroyBinding(binding: FragmentSettingsSearchBinding) {
        super.onDestroyBinding(binding)
        binding.settingsSearchRecycler.adapter = null
    }

    private fun updateResults(query: String) {
        val binding = requireBinding()
        val results = SettingsSearchIndex.search(index, query)
        adapter.submit(results)
        binding.settingsSearchEmpty.isVisible = query.isNotBlank() && results.isEmpty()
    }

    private fun openEntry(entry: SettingsSearchEntry) {
        L.d("Opening setting ${entry.key}")
        val binding = requireBinding()
        binding.settingsSearchEditText.clearFocus()
        requireContext()
            .getSystemServiceCompat(InputMethodManager::class)
            .hideSoftInputFromWindow(binding.root.windowToken, 0)

        val args = bundleOf(BasePreferenceFragment.ARG_HIGHLIGHT_KEY to entry.key)
        val options =
            if (entry.destination == R.id.root_preferences_fragment) {
                // Replace the existing root screen rather than stacking a second one.
                NavOptions.Builder().setPopUpTo(R.id.root_preferences_fragment, true).build()
            } else {
                null
            }
        findNavController().navigate(entry.destination, args, options)
    }
}
