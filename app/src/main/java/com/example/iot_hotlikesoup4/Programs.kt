package com.example.iot_hotlikesoup4

import android.app.TimePickerDialog
import android.util.Log
import android.widget.TimePicker
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Programs(navController: NavController, selectedChauffage: String?) {
    val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    val programsList = remember { mutableStateListOf<Map<String, Any>>() }

    // Champs pour le formulaire
    var newDay by remember { mutableStateOf(0) } // Changer en type entier
    var newTimeOn by remember { mutableStateOf("") }
    var newTimeOff by remember { mutableStateOf("") }
    var newActive by remember { mutableStateOf(false) }

    // Dropdown Menu états
    val daysOfWeek = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
    val daysOfWeekIndices = mapOf(
        "Dimanche" to 0,
        "Lundi" to 1,
        "Mardi" to 2,
        "Mercredi" to 3,
        "Jeudi" to 4,
        "Vendredi" to 5,
        "Samedi" to 6
    )
    var expanded by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf("") }

    // Carte pour convertir l'index en jour de la semaine
    val dayOfWeekMap = mapOf(
        0 to "Dimanche",
        1 to "Lundi",
        2 to "Mardi",
        3 to "Mercredi",
        4 to "Jeudi",
        5 to "Vendredi",
        6 to "Samedi"
    )

    // Variables pour le TimePicker
    val calendar = Calendar.getInstance()
    var timeOnPicker by remember { mutableStateOf("") }
    var timeOffPicker by remember { mutableStateOf("") }

    // Fonction pour afficher le TimePicker et mettre à jour l'heure
    fun showTimePicker(isStartTime: Boolean) {
        val timePickerDialog = TimePickerDialog(
            navController.context,
            { _: TimePicker, hour: Int, minute: Int ->
                val time = String.format("%02d:%02d", hour, minute)
                if (isStartTime) {
                    timeOnPicker = time
                    newTimeOn = time
                } else {
                    timeOffPicker = time
                    newTimeOff = time
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // Format 24 heures
        )
        timePickerDialog.show()
    }

    // Charger les programmes depuis Firebase
    LaunchedEffect(selectedChauffage) {
        selectedChauffage?.let {
            val chauffageRef = database.child("chauffages").child(it)
            chauffageRef.child("programs").get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // Parcourir les enfants et vérifier la validité des données
                    snapshot.children.forEach { programSnapshot ->
                        val program = programSnapshot.value as? Map<String, Any>
                        if (program != null && program.containsKey("day") && program.containsKey("timeOn") && program.containsKey(
                                "timeOff"
                            ) && program.containsKey("active")
                        ) {
                            programsList.add(program) // Ajouter seulement les programmes valides
                        }
                    }
                }
            }.addOnFailureListener { error ->
                Log.d("Programs", "Erreur : ${error.message}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Liste des programmes
        itemsIndexed(programsList) { index, program ->
            val dayIndex =
                (program["day"] as? Number)?.toInt() ?: 0 // Récupérer le jour en tant qu'entier
            val day = dayOfWeekMap[dayIndex] ?: "Inconnu" // Convertir l'entier en texte
            val timeOn = program["timeOn"] as? String ?: ""
            val timeOff = program["timeOff"] as? String ?: ""
            val active = program["active"] as? Boolean ?: false

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .combinedClickable(
                        onClick = {
                            // Mettre à jour l'état actif/inactif dans Firebase et la liste locale
                            val updatedProgram = program
                                .toMutableMap()
                                .apply {
                                    this["active"] = !active
                                }
                            selectedChauffage?.let {
                                database
                                    .child("chauffages")
                                    .child(it)
                                    .child("programs")
                                    .child(index.toString())
                                    .setValue(updatedProgram)
                                    .addOnSuccessListener {
                                        programsList[index] = updatedProgram
                                    }
                                    .addOnFailureListener { error ->
                                        Log.d(
                                            "Programs",
                                            "Erreur lors de la mise à jour : ${error.message}"
                                        )
                                    }
                            }
                        },
                        onLongClick = {
                            // Supprimer l'élément dans Firebase et localement
                            selectedChauffage?.let {
                                database
                                    .child("chauffages")
                                    .child(it)
                                    .child("programs")
                                    .child(index.toString())
                                    .removeValue()
                                    .addOnSuccessListener {
                                        programsList.removeAt(index)
                                    }
                                    .addOnFailureListener { error ->
                                        Log.d(
                                            "Programs",
                                            "Erreur lors de la suppression : ${error.message}"
                                        )
                                    }
                            }
                        }
                    )
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "$day, Actif: $active",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "$timeOn -> $timeOff",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Formulaire pour ajouter un nouveau programme
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown pour choisir un jour de la semaine
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(text = if (selectedDay.isEmpty()) "Sélectionnez un jour" else selectedDay)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                daysOfWeek.forEach { day ->
                    DropdownMenuItem(
                        text = { Text(text = day) },
                        onClick = {
                            selectedDay = day // Affiche le nom du jour sélectionné dans le bouton
                            newDay = daysOfWeekIndices[day]
                                ?: 0 // Utilisation directe de l'index (number)
                            expanded = false
                        },
                        enabled = true
                    )
                }
            }


            OutlinedTextField(
                value = newTimeOn,
                onValueChange = { newTimeOn = it },
                label = { Text("Heure d'activation") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { showTimePicker(true) }, // Afficher le TimePicker
                enabled = false // Désactiver la saisie manuelle
            )

            OutlinedTextField(
                value = newTimeOff,
                onValueChange = { newTimeOff = it },
                label = { Text("Heure de désactivation") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { showTimePicker(false) }, // Afficher le TimePicker
                enabled = false // Désactiver la saisie manuelle
            )

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("Actif")
                Checkbox(
                    checked = newActive,
                    onCheckedChange = { newActive = it }
                )
            }

            Button(
                onClick = {
                    selectedChauffage?.let {
                        val chauffageRef = database.child("chauffages").child(it)

                        // Lire idLastProgram
                        chauffageRef.child("idLastProgram").get().addOnSuccessListener { snapshot ->
                            val currentIdLastProgram = snapshot.getValue(Int::class.java) ?: 0
                            val newId = currentIdLastProgram + 1 // Générer un nouvel ID

                            val newProgram = mapOf(
                                "day" to newDay, // Utilisation de newDay qui contient l'index du jour
                                "timeOn" to newTimeOn,
                                "timeOff" to newTimeOff,
                                "active" to newActive
                            )

                            // Ajouter le nouveau programme
                            chauffageRef.child("programs")
                                .child(newId.toString())
                                .setValue(newProgram)
                                .addOnSuccessListener {
                                    programsList.add(newProgram)
                                    // Mettre à jour idLastProgram
                                    chauffageRef.child("idLastProgram").setValue(newId)

                                    // Réinitialiser le formulaire
                                    selectedDay = ""
                                    newDay = 0 // Réinitialiser l'index
                                    newTimeOn = ""
                                    newTimeOff = ""
                                    newActive = false
                                }
                                .addOnFailureListener { error ->
                                    Log.d("Programs", "Erreur lors de l'ajout : ${error.message}")
                                }
                        }.addOnFailureListener { error ->
                            Log.d(
                                "Programs",
                                "Erreur lors de la lecture de idLastProgram : ${error.message}"
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Ajouter un programme", color = Color.White)
            }
        }

        // Bouton de retour
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    navController.navigate("screen2")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Retour", color = Color.White)
            }
        }
    }
}