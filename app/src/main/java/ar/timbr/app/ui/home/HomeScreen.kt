package ar.timbr.app.ui.home

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import ar.timbr.app.R
import ar.timbr.app.domain.model.LocationType
import android.content.Context
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeConfig = state.homeConfig
    val qrBaseUrl = stringResource(R.string.public_qr_base_url)
    val qrValue = remember(homeConfig?.publicQrId, qrBaseUrl) {
        homeConfig?.publicQrId?.takeIf { it.isNotBlank() }?.let { "$qrBaseUrl/?qr=$it" }
    }
    val qrBitmap = remember(qrValue) { qrValue?.let { generateQrBitmap(it) } }
    val locationTypeExpanded = remember { mutableStateOf(false) }
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null && qrBitmap != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
        }
    }
    val canConfigureHome = state.profile?.role == "owner" || state.profile?.homeId.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Puerta", style = MaterialTheme.typography.headlineMedium)

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Text(text = "Hogar: ${state.profile?.homeId ?: "Sin asignar"}")
            val fullName = state.profile?.fullName.orEmpty()
            val email = state.profile?.email.orEmpty()
            Text(text = "Usuario: ${if (fullName.isNotBlank()) fullName else email}")
            if (email.isNotBlank()) {
                Text(text = "Email: $email")
            }
            if (state.profile?.role == "owner") {
                Text(text = "Rol: Propietario")
            } else if (state.profile != null) {
                Text(text = "Rol: Miembro")
            }
        }

        Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
        }

        if (homeConfig != null) {
            Text(text = "Ubicación", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "Nombre: ${homeConfig.addressName.ifBlank { "Sin nombre" }}")
                    Text(text = "Dirección: ${homeConfig.address.ifBlank { "Sin definir" }}")
                    Text(text = "Tipo: ${homeConfig.locationType.displayName}")
                }
            }
        }

        if (qrValue != null && qrBitmap != null) {
            Text(text = "QR para visitantes", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Código QR del hogar",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Link: $qrValue",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { downloadLauncher.launch("timbr-qr.png") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Descargar QR (PNG)")
                    }
                    TextButton(
                        onClick = {
                            shareQrBitmap(context, qrBitmap)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Compartir / Imprimir QR")
                    }
                }
            }
        }

        if (canConfigureHome) {
            Text(text = "Configurar hogar", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.homeSetupDraft.addressQuery,
                            onValueChange = viewModel::updateHomeAddressQuery,
                            label = { Text("Ubicación (dirección)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (state.homeSetupDraft.isAddressLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = state.homeSetupDraft.addressSuggestions.isNotEmpty(),
                            onDismissRequest = viewModel::clearHomeAddressSuggestions,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            state.homeSetupDraft.addressSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(text = suggestion.fullText) },
                                    onClick = { viewModel.selectHomeAddress(suggestion.placeId) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.homeSetupDraft.addressName,
                        onValueChange = viewModel::updateHomeAddressName,
                        label = { Text("Nombre de la ubicación") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = locationTypeExpanded.value,
                        onExpandedChange = { locationTypeExpanded.value = !locationTypeExpanded.value },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.homeSetupDraft.locationType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de ubicación") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationTypeExpanded.value)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        DropdownMenu(
                            expanded = locationTypeExpanded.value,
                            onDismissRequest = { locationTypeExpanded.value = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LocationType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = {
                                        viewModel.updateHomeLocationType(type)
                                        locationTypeExpanded.value = false
                                    },
                                )
                            }
                        }
                    }
                    if (state.homeSetupDraft.errorMessage != null) {
                        Text(
                            text = state.homeSetupDraft.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (state.homeSetupDraft.successMessage != null) {
                        Text(text = state.homeSetupDraft.successMessage.orEmpty())
                    }
                    Button(
                        onClick = viewModel::saveHomeLocation,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.homeSetupDraft.isSaving,
                    ) {
                        Text(if (state.homeSetupDraft.isSaving) "Guardando..." else "Guardar ubicación")
                    }
                    Text(
                        text = "El QR se genera con un ID hasheado para proteger la ubicación.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (state.profile?.role == "owner") {
            Text(text = "Horario del timbre", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "Habilitación")
                    Switch(
                        checked = state.scheduleDraft.isDoorbellEnabled,
                        onCheckedChange = viewModel::updateDoorbellEnabled,
                    )
                    OutlinedTextField(
                        value = state.scheduleDraft.startTime,
                        onValueChange = viewModel::updateStartTime,
                        label = { Text("Inicio (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.scheduleDraft.endTime,
                        onValueChange = viewModel::updateEndTime,
                        label = { Text("Fin (HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.scheduleDraft.timeZone,
                        onValueChange = viewModel::updateTimeZone,
                        label = { Text("Zona horaria (IANA)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.scheduleDraft.rateLimitBaseMinutes,
                        onValueChange = viewModel::updateRateLimitBase,
                        label = { Text("Penalidad base (min)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.scheduleDraft.rateLimitMaxMinutes,
                        onValueChange = viewModel::updateRateLimitMax,
                        label = { Text("Penalidad máxima (min)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.scheduleDraft.errorMessage != null) {
                        Text(
                            text = state.scheduleDraft.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = viewModel::saveSchedule,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.scheduleDraft.isSaving,
                    ) {
                        Text(if (state.scheduleDraft.isSaving) "Guardando..." else "Guardar horario")
                    }
                }
            }

            Text(text = "Bloqueo de números", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.blockDraft.phone,
                        onValueChange = viewModel::updateBlockedPhone,
                        label = { Text("Teléfono a bloquear") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.blockDraft.reason,
                        onValueChange = viewModel::updateBlockReason,
                        label = { Text("Motivo (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.blockDraft.errorMessage != null) {
                        Text(
                            text = state.blockDraft.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = viewModel::blockPhone,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.blockDraft.isSaving,
                    ) {
                        Text(if (state.blockDraft.isSaving) "Bloqueando..." else "Bloquear número")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Últimos timbrazos", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.ringEvents) { event ->
                val date = event.createdAt.atZone(ZoneId.systemDefault()).format(formatter)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Visitante: ${event.visitorPhone}")
                        Text(text = "Fecha: $date")
                        Text(text = "Estado: ${event.status}")
                    }
                }
            }
            if (state.ringEvents.isEmpty()) {
                item {
                    Text(text = "No hay timbrazos recientes.")
                }
            }
        }
    }
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    val cacheFile = File(context.cacheDir, "timbr-qr.png")
    FileOutputStream(cacheFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        cacheFile,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir QR"))
}
