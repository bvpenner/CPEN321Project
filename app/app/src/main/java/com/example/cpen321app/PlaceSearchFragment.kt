package com.example.cpen321app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cpen321app.databinding.FragmentPlaceSearchBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PlaceSearchFragment : Fragment() {
    private var _binding: FragmentPlaceSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var placesClient: PlacesClient
    private lateinit var adapter: PlaceSearchAdapter
    private var sessionToken = AutocompleteSessionToken.newInstance()
    
    private val searchFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    var onPlaceSelected: ((Place.Field) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.MAPS_API_KEY)
        }
        placesClient = Places.createClient(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearchView()
        setupSearchFlow()
    }

    private fun setupRecyclerView() {
        adapter = PlaceSearchAdapter { prediction ->
            handlePlaceSelection(prediction)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PlaceSearchFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearchView() {
        binding.apply {
            searchView.apply {
                doAfterTextChanged { text ->
                    searchFlow.value = text?.toString() ?: ""
                }

                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        performSearch(searchFlow.value)
                        true
                    } else false
                }
            }

            clearButton.setOnClickListener {
                searchView.text?.clear()
                adapter.submitList(emptyList())
                progressBar.visibility = View.GONE
            }

            filterChipGroup.setOnCheckedChangeListener { _, _ ->
                performSearch(searchFlow.value)
            }
        }
    }

    private fun setupSearchFlow() {
        viewLifecycleOwner.lifecycleScope.launch {
            searchFlow
                .debounce(300)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .onEach { query ->
                    performSearch(query)
                }
                .catch { e ->
                    Log.e(TAG, "Error in search flow", e)
                    showError("Search error occurred")
                }
                .collect()
        }
    }

    private fun performSearch(query: String) {
        if (query.length < 2) return

        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val predictions = withContext(Dispatchers.IO) {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setSessionToken(sessionToken)
                        .setTypeFilter(getSelectedTypeFilter())
                        .build()

                    val response = placesClient.findAutocompletePredictions(request).await()
                    response.autocompletePredictions
                }

                adapter.submitList(predictions)
                
                if (predictions.isEmpty()) {
                    showNoResults()
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Place search failed", e)
                showError("Place search failed: ${e.statusCode}")
            } catch (e: Exception) {
                Log.e(TAG, "Error performing search", e)
                showError("Error performing search")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun getSelectedTypeFilter(): TypeFilter {
        return when (binding.filterChipGroup.checkedChipId) {
            R.id.chipAddress -> TypeFilter.ADDRESS
            R.id.chipEstablishment -> TypeFilter.ESTABLISHMENT
            R.id.chipRegions -> TypeFilter.REGIONS
            else -> TypeFilter.GEOCODE
        }
    }

    private fun handlePlaceSelection(prediction: AutocompletePrediction) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.TYPES,
                    Place.Field.VIEWPORT
                )

                val request = FetchPlaceRequest.builder(prediction.placeId, placeFields)
                    .setSessionToken(sessionToken)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    placesClient.fetchPlace(request).await()
                }

                response.place?.let { place ->
                    onPlaceSelected?.invoke(place)
                    // Generate new token after successful place selection
                    sessionToken = AutocompleteSessionToken.newInstance()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching place details", e)
                showError("Error fetching place details")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showError(message: String) {
        binding.apply {
            errorText.text = message
            errorText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showNoResults() {
        binding.apply {
            errorText.text = "No results found"
            errorText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }

    companion object {
        private const val TAG = "PlaceSearchFragment"
    }
} 