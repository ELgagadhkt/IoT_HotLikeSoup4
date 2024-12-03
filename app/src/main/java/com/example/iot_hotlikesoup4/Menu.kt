package com.example.iot_hotlikesoup4

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

@Composable
fun Menu(navController: NavController) {
    val database = Firebase.database

    // State variables
    var currentTemperature by remember { mutableStateOf("0") }
    var lastUpdate by remember { mutableStateOf("No update yet") }
    var switchState by remember { mutableStateOf(false) }
    var selectedChauffage by remember { mutableStateOf("") }
    var chauffageTitles by remember { mutableStateOf(listOf<String>()) }
    var valueTemp by remember { mutableStateOf("0") }

    // Fetching data based on selected "chauffage"
    fun fetchChauffageData(selectedChauffage: String) {
        if (selectedChauffage.isNotEmpty()) {
            val myRef = database.getReference("chauffages").child(selectedChauffage)
            myRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val suiviTempSnapshot = snapshot.child("SuiviTemp")
                    val temperatures =
                        mutableListOf<Pair<String, Double>>() // Pair of datetime and temperature

                    // Iterate through all SuiviTemp entries
                    for (child in suiviTempSnapshot.children) {
                        val datetime = child.child("dateTime").getValue<String>() ?: ""
                        val temperature = child.child("temperature").getValue<Double>() ?: 0.0
                        temperatures.add(datetime to temperature)
                    }

                    // Sort temperatures by datetime (in descending order)
                    temperatures.sortByDescending { it.first }

                    if (temperatures.isNotEmpty()) {
                        // Get the latest temperature and datetime
                        val latest = temperatures.first()
                        currentTemperature = latest.second.toString()  // Temperature
                        lastUpdate = latest.first  // Datetime
                    } else {
                        currentTemperature = "No data"
                        lastUpdate = "No update yet"
                    }

                    // Also update the switch state from the "commande" field
                    val status = snapshot.child("commande").getValue<Boolean>() ?: false
                    switchState = status
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Failed to read value.", error.toException())
                }
            })
        } else {
            // Reset values if no "chauffage" selected
            currentTemperature = "0"
            switchState = false
            lastUpdate = "No update yet"
        }
    }

    fun fetchValueTemp(selectedChauffage: String) {
        if (selectedChauffage.isNotEmpty()) {
            val myRef = Firebase.database.getReference("chauffages").child(selectedChauffage).child("valueTemp")
            myRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val temp = snapshot.getValue<Double>() ?: 0.0
                    valueTemp = temp.toString()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Failed to fetch valueTemp.", error.toException())
                    valueTemp = "Error"
                }
            })
        } else {
            valueTemp = "0"
        }
    }

    // Fetch titles of "chauffages"
    val databaseRef = FirebaseDatabase.getInstance().reference
    getChauffage(databaseRef) { titles ->
        chauffageTitles = titles
    }

    LaunchedEffect(selectedChauffage) {
        fetchChauffageData(selectedChauffage)
        fetchValueTemp(selectedChauffage)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        MainContent(
            modifier = Modifier.padding(innerPadding),
            onUpdateTemperature = { temperature ->
                // Convert the temperature input to Double before sending to Firebase
                val temp =
                    temperature.toDoubleOrNull() ?: 0.0  // Default to 0.0 if conversion fails
                if (selectedChauffage.isNotEmpty()) {
                    val updatedData = mapOf(
                        "valueTemp" to temp,  // Send as Double
                        "commande" to switchState
                    )

                    // Use updateChildren to update only specific fields
                    database.getReference("chauffages").child(selectedChauffage)
                        .updateChildren(updatedData)
                }
            },
            onToggleSwitch = { newState ->
                if (selectedChauffage.isNotEmpty()) {
                    switchState = newState
                    database.getReference("chauffages").child(selectedChauffage).child("commande")
                        .setValue(newState)
                }
            },
            currentTemperature = if (selectedChauffage.isEmpty()) "No chauffage selected" else currentTemperature,
            lastUpdate = if (selectedChauffage.isEmpty()) "No chauffage selected" else lastUpdate,
            isSwitchOn = switchState,
            navController = navController,
            chauffageTitles = chauffageTitles,
            selectedChauffage = selectedChauffage,
            onSelectChauffage = { selectedChauffage = it },
            valueTemp = if (selectedChauffage.isEmpty()) "No chauffage selected" else valueTemp,
        )
    }
}


@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onUpdateTemperature: (String) -> Unit,
    onToggleSwitch: (Boolean) -> Unit,
    currentTemperature: String,
    lastUpdate: String,
    isSwitchOn: Boolean,
    navController: NavController,
    chauffageTitles: List<String>,
    selectedChauffage: String,
    onSelectChauffage: (String) -> Unit,
    valueTemp: String
) {
    var temperatureInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display current temperature and last update
        Text(
            text = "Current Temperature: $currentTemperature",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Last Update: $lastUpdate",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        // Input field for temperature
        TextField(
            value = temperatureInput,
            onValueChange = { if (selectedChauffage.isNotEmpty()) temperatureInput = it },
            label = { Text("Enter new temperature") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            enabled = selectedChauffage.isNotEmpty(),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary, // Couleur de l'indicateur actif
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface // Couleur de l'indicateur inactif
            )
        )

        Text(
            text = "Valeur cible actuelle : $valueTemp",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp) // Ajoutez un peu de marge au-dessus si nécessaire
        )

        // Button to update temperature
        Button(
            onClick = { onUpdateTemperature(temperatureInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedChauffage.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary, // Couleur de fond
                contentColor = MaterialTheme.colorScheme.onPrimary // Couleur du texte
            )
        ) {
            Text(text = "Update Temperature")
        }

        // Switch for controlling status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Status: ${if (isSwitchOn) "ON" else "OFF"}")
            Switch(
                checked = isSwitchOn,
                onCheckedChange = { onToggleSwitch(it) },
                enabled = selectedChauffage.isNotEmpty(),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary, // Couleur du "thumb" quand activé
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant, // Couleur du "thumb" quand désactivé
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer, // Couleur du fond quand activé
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant // Couleur du fond quand désactivé
                ),
                modifier = Modifier.padding(8.dp) // Ajoutez un peu de padding si nécessaire
            )
        }

        // List of "chauffages"
        Text(
            text = "Choose your chauffage:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            items(chauffageTitles) { title ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectChauffage(title) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedChauffage == title,
                        onClick = { onSelectChauffage(title) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = title,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Navigation button
        Button(
            onClick = { navController.navigate("screen3/${selectedChauffage}") },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedChauffage.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary, // Couleur de fond
                contentColor = MaterialTheme.colorScheme.onPrimary // Couleur du texte
            )
        ) {
            Text("Suivi de la température")
        }

        Button(
            onClick = { navController.navigate("screen4/${selectedChauffage}") },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedChauffage.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary, // Couleur de fond
                contentColor = MaterialTheme.colorScheme.onPrimary // Couleur du texte
            )
        ) {
            Text("Suivi des programmes")
        }
    }
}

fun getChauffage(database: DatabaseReference, onResult: (List<String>) -> Unit) {
    database.child("chauffages").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val titles = mutableListOf<String>()

            // Iterate through the "chauffages" keys
            for (child in snapshot.children) {
                val key = child.key
                if (key != null) {
                    titles.add(key)
                }
            }

            // Return the list of "chauffages"
            onResult(titles)
        }

        override fun onCancelled(error: DatabaseError) {
            onResult(emptyList())
        }
    })
}