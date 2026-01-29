package ar.timbr.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Timbr", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.fullName,
            onValueChange = viewModel::updateFullName,
            label = { Text("Nombre completo") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.homeId,
            onValueChange = viewModel::updateHomeId,
            label = { Text("ID de hogar") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::updateEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
        )

        if (state.errorMessage != null) {
            Text(text = state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = viewModel::signIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ingresar")
            }
            Button(
                onClick = viewModel::signUp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Crear cuenta")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Usá el mismo ID de hogar para múltiples usuarios de una misma vivienda.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
