package com.example.iot_hotlikesoup4

import android.content.ContentValues.TAG
import android.util.Log
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SuiviTemp(navController: NavController, selectedChauffage: String?) {
    val db = Firebase.database
    val (suiviData, setSuiviData) = remember { mutableStateOf<List<Map<String, Any>>?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    // Get current time and calculate 24 hours ago
    val calendar = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, -24) // Subtract 24 hours
    }
    val dateLimit = calendar.time

    // Use LaunchedEffect to load data when the view is displayed
    LaunchedEffect(selectedChauffage) {
        if (!selectedChauffage.isNullOrEmpty()) {
            val myRef = db.getReference("chauffages").child(selectedChauffage).child("SuiviTemp")

            myRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val suiviListType =
                            object : GenericTypeIndicator<List<Map<String, Any>>>() {}
                        val suiviList = snapshot.getValue(suiviListType)

                        if (suiviList != null) {
                            // Filter entries to include only those within the last 24 hours and sort by date
                            val validEntries = suiviList.filterNotNull().filter { entry ->
                                val dateStr = entry["dateTime"] as? String
                                val dateFormat =
                                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                val date = dateStr?.let { dateFormat.parse(it) }
                                date?.after(dateLimit)
                                    ?: false // Only include entries within the last 24 hours
                            }.sortedBy { entry ->
                                val dateStr = entry["dateTime"] as? String
                                val dateFormat =
                                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                val date = dateStr?.let { dateFormat.parse(it) }
                                date ?: Date(0) // Sort by date
                            }

                            setSuiviData(validEntries)
                        } else {
                            errorMessage.value =
                                "Aucune donnée disponible pour le chauffage sélectionné."
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing data: ${e.localizedMessage}")
                        errorMessage.value = "Impossible de récupérer les données."
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to read value: ${error.toException()}")
                    errorMessage.value = "Impossible de récupérer les données."
                }
            })
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp), // Ajouter du padding général
            horizontalAlignment = Alignment.CenterHorizontally, // Centrer horizontalement
            verticalArrangement = Arrangement.Center // Centrer verticalement
        ) {

            when {
                suiviData == null && errorMessage.value == null -> {
                    Text("Chargement des données...")
                }

                errorMessage.value != null -> {
                    Text("Erreur: ${errorMessage.value}")
                }

                suiviData != null -> {
                    // Map data to chart entries
                    val entries = suiviData?.mapIndexed { index, suivi ->
                        val temp = (suivi["temperature"] as? Number)?.toFloat() ?: 0f
                        Entry(index.toFloat(), temp)
                    } ?: emptyList()

                    // Get only the hour (HH:mm) for the X-axis
                    val horaires = suiviData?.mapNotNull { suivi ->
                        val dateStr = suivi["dateTime"] as? String
                        val dateFormat =
                            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        val date = dateStr?.let { dateFormat.parse(it) }
                        date?.let {
                            SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(it)
                        } // Extract hour:minute
                    } ?: emptyList()

                    // Render the graph with MPAndroidChart
                    AndroidView(factory = { context ->
                        LineChart(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                600 // Set chart height
                            )

                            val blueColor = Color(0xFF0000FF) // Bleu
                            val redColor = Color(0xFFFF0000) // Rouge
                            val dataSet = LineDataSet(entries, "Température").apply {
                                color = blueColor.toArgb()
                                lineWidth = 2f
                                setCircleColor(redColor.toArgb())
                                circleRadius = 4f
                            }

                            data = LineData(dataSet)

                            xAxis.apply {
                                valueFormatter = object : ValueFormatter() {
                                    override fun getFormattedValue(value: Float): String {
                                        return if (value.toInt() in horaires.indices) {
                                            horaires[value.toInt()] // Show only hour:minute
                                        } else {
                                            ""
                                        }
                                    }
                                }
                                position = XAxis.XAxisPosition.BOTTOM
                                granularity = 1f
                                setDrawGridLines(false)
                            }

                            axisRight.isEnabled = false // Disable right Y-axis
                            legend.isEnabled = false // Disable legend
                            invalidate() // Refresh chart
                        }
                    })
                }
            }

            // Customized Button
            Button(
                onClick = {
                    navController.navigate("screen2")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary, // Couleur de fond
                    contentColor = MaterialTheme.colorScheme.onPrimary // Couleur du texte
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth() // Remplir toute la largeur disponible pour centrer le bouton
            ) {
                Text(text = "Retour")
            }
        }
    }
}