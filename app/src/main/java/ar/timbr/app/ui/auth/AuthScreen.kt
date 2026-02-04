package ar.timbr.app.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ar.timbr.app.domain.model.LocationType
import ar.timbr.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expanded = remember { mutableStateOf(false) }
    val roleExpanded = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val googleClientId = stringResource(R.string.google_web_client_id)
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching { task.result }.onSuccess { account ->
                val token = account.idToken
                if (!token.isNullOrBlank()) {
                    viewModel.signInWithGoogle(token)
                }
            }
        }
    }
    val googleSignInClient = remember(googleClientId) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(googleClientId)
                .build(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        ExposedDropdownMenuBox(
            expanded = roleExpanded.value,
            onExpandedChange = { roleExpanded.value = !roleExpanded.value },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.role.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Tipo de acceso") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded.value) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            DropdownMenu(
                expanded = roleExpanded.value,
                onDismissRequest = { roleExpanded.value = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AuthRole.values().forEach { role ->
                    DropdownMenuItem(
                        text = { Text(role.label) },
                        onClick = {
                            viewModel.updateRole(role)
                            roleExpanded.value = false
                        },
                    )
                }
            }
        }
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
        if (state.mode == AuthMode.SignUp) {
            OutlinedTextField(
                value = state.fullName,
                onValueChange = viewModel::updateFullName,
                label = { Text("Nombre completo") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.role == AuthRole.Owner) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.addressQuery,
                        onValueChange = viewModel::updateAddressQuery,
                        label = { Text("Dirección exacta") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (state.isAddressLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = state.addressSuggestions.isNotEmpty(),
                        onDismissRequest = viewModel::clearAddressSuggestions,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.addressSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(text = suggestion.primaryText)
                                        if (suggestion.secondaryText.isNotBlank()) {
                                            Text(
                                                text = suggestion.secondaryText,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                },
                                onClick = { viewModel.selectAddress(suggestion.placeId) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.addressName,
                    onValueChange = viewModel::updateAddressName,
                    label = { Text("Nombre del ID de ubicación") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = expanded.value,
                    onExpandedChange = { expanded.value = !expanded.value },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.locationType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de ubicación") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    DropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LocationType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    viewModel.updateLocationType(type)
                                    expanded.value = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = "El QR se genera con un ID interno hasheado para proteger tu ubicación.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = "El usuario público debe registrarse para un segundo uso del timbre virtual.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.errorMessage != null) {
            Text(text = state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
        }

        if (state.isLoading || state.isGoogleLoading) {
            CircularProgressIndicator()
        } else {
            if (state.mode == AuthMode.SignIn) {
                Button(
                    onClick = viewModel::signIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ingresar")
                }
                if (state.role == AuthRole.Public) {
                    Button(
                        onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continuar con Google")
                    }
                }
                TextButton(onClick = viewModel::toggleMode, modifier = Modifier.fillMaxWidth()) {
                    Text("Crear cuenta")
                }
            } else {
                Button(
                    onClick = viewModel::signUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear cuenta")
                }
                if (state.role == AuthRole.Public) {
                    Button(
                        onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Crear cuenta con Google")
                    }
                }
                TextButton(onClick = viewModel::toggleMode, modifier = Modifier.fillMaxWidth()) {
                    Text("Ya tengo cuenta")
                }
            }
        }
        if (state.role == AuthRole.Owner && state.mode == AuthMode.SignUp) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "La dirección se valida con Google Maps para asegurar exactitud.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
