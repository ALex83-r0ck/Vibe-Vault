package com.alexander.vibevault

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.alexander.vibevault.ui.theme.VibeVaultTheme

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                VibeVaultTheme {
                    HomeScreen(navController = findNavController())
                }
            }
        }
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "VibeVault", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate(R.id.action_homeFragment_to_addEntryFragment) }) {
            Text("Neuen Eintrag hinzufügen")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { navController.navigate(R.id.action_homeFragment_to_statsFragment) }) {
            Text("Statistiken anzeigen")
        }
    }
}