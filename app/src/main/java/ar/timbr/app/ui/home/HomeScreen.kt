package ar.timbr.app.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

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
            Text(text = "Usuario: ${state.profile?.fullName ?: ""}")
        }

        Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
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
