package com.example.iot_hotlikesoup4

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(navController: NavController) {
    val auth = remember { FirebaseAuth.getInstance() }
    var (email, setEmail) = remember { mutableStateOf("") } //remetre en val
    var (password, setPassword) = remember { mutableStateOf("") } //remetre en val
    val errorMessage = remember { mutableStateOf<String?>(null) }

    email = "a@b.com" //suppr
    password = "hello!" //suppr

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(text = "Connexion",style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp))

            // Champ de texte pour l'email
            OutlinedTextField(
                value = email,
                onValueChange = { setEmail(it) },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Champ de texte pour le mot de passe
            OutlinedTextField(
                value = password,
                onValueChange = { setPassword(it) },
                label = { Text("Mot de passe") },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bouton de connexion
            Button(
                onClick = {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "signInWithEmail:success")
                                // Naviguer vers l'écran de récupération des données
                                navController.navigate("screen2")
                            } else {
                                Log.w(TAG, "signInWithEmail:failure", task.exception)
                                errorMessage.value = "Erreur de connexion"
                            }
                        }
                }, colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary, // Couleur de fond
                    contentColor = MaterialTheme.colorScheme.onPrimary // Couleur du texte
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f) // Limiter la largeur à 80% de l'écran
                    .padding(horizontal = 16.dp)
            ) {
                Text("Se connecter")
            }

            // Affichage d'un message d'erreur si la connexion échoue
            if (errorMessage.value != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Erreur: ${errorMessage.value}", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}