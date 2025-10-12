package com.alexander.vibevault

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.alexander.vibevault.ui.theme.VibeVaultTheme

class StatsFragment : Fragment() {
    private val viewModel: StatsViewModel by viewModels()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                VibeVaultTheme {
                    StatsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val entries by viewModel.entries.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Statistiken", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        if (entries.isEmpty()) {
            Text(text = "Keine Einträge vorhanden")
        } else {
            LazyColumn {
                items(entries) { entry ->
                    Text(text = "${entry.timestamp}: ${entry.nicotineStrength} mg, ${entry.brand}")
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}