package com.alexander.vibevault

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.alexander.vibevault.ui.theme.VibeVaultTheme
import java.time.LocalDateTime

class AddEntryFragment : Fragment() {
    private val viewModel: AddEntryViewModel by viewModels()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                VibeVaultTheme {
                    AddEntryScreen(viewModel = viewModel, navController = findNavController())
                }
            }
        }
    }
}

@Composable
fun AddEntryScreen(viewModel: AddEntryViewModel, navController: androidx.navigation.NavController) {
    var nicotineStrength by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Neuer Eintrag", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = nicotineStrength,
            onValueChange = { nicotineStrength = it },
            label = { Text("Stärke (mg)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            label = { Text("Marke") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            val strengthValue = nicotineStrength.toFloatOrNull() ?: 0f
            viewModel.saveEntry(LocalDateTime.now(), strengthValue, brand)
            navController.popBackStack()
        }) {
            Text("Speichern")
        }
    }
}