#include <WiFi.h>
#include <Firebase.h>
#include <time.h>
#include <Preferences.h>
#include <AM2302-Sensor.h>

// Configurations WiFi et Firebase
#define WIFI_SSID "Sausage Factory"
#define WIFI_PASSWORD "peteriscute"
#define REFERENCE_URL "https://iot---hot-like-soup-default-rtdb.europe-west1.firebasedatabase.app/"
#define FIREBASE_AUTH "hello!"
#define AUTH_TOKEN "hello!"

Preferences preferences;

//Firebase fb(REFERENCE_URL);
Firebase fb(REFERENCE_URL, AUTH_TOKEN);

// Définition des pins pour les sorties digitales
int pin1PilotMotor = 14;  // 14 = port D2   => Port general D2
int pin2PilotMotor = 32;  // 32 = port D3   => Port general D2
constexpr unsigned int SENSOR_PIN = 15; // 15 = port D4   => Port general D4
AM2302::AM2302_Sensor am2302{SENSOR_PIN};

const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 3600;      // Offset pour GMT+1 (Paris)
const int daylightOffset_sec = 3600;  // Offset pour l'heure d'été

int idSuivi = 10;

float currentTemperature;
float deltaTempMax; //Ecart max accepte entre la temperature de commande et la temperature actuelle
bool status; //true = chauffage on, false = off
bool commande;

struct ProgramData {
  String id;
  String timeOn;
  String timeOff;
};
//------------------------------------------------------------------------
// -------------------------------- SETUP --------------------------------
void setup() {

  pinMode(pin1PilotMotor, OUTPUT);
  pinMode(pin2PilotMotor, OUTPUT);

  Serial.begin(115200);
  preferences.begin("SuiviTemp", false);

  // ---------- Wifi setup ----------
  #if !defined(ARDUINO_UNOWIFIR4)
    WiFi.mode(WIFI_STA);
  #else
    pinMode(LED_BUILTIN, OUTPUT);
    digitalWrite(LED_BUILTIN, HIGH);
  #endif
    WiFi.disconnect();
    delay(1000);

    /* Connect to WiFi */
    Serial.println();
    Serial.println();
    Serial.print("Connecting to: ");
    Serial.println(WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    while (WiFi.status() != WL_CONNECTED) {
      Serial.print("-");
      delay(500);
    }
    Serial.println();
    Serial.println("WiFi Connected");
    Serial.println();

  #if defined(ARDUINO_UNOWIFIR4)
    digitalWrite(LED_BUILTIN, LOW);
  #endif

  // ---------- Temperature sensor setup ----------
  if (am2302.begin()) {
      // this delay is needed to receive valid data,
      // when the loop directly read again
      delay(3000);
   }
   else {
      while (true) {
      Serial.println("Error: sensor check. => Please check sensor connection!");
      delay(10000);
      }
   }

  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);

  // ---------- Status setup ---------- 
  // The ESP32 has to be turned on when chauffage is off
  status = false;
  fb.setBool("chauffages/pg/status", status);
  commande = false;
  fb.setBool("chauffages/pg/commande", commande);
  if(fb.getFloat("chauffages/pg/SuiviTemp/0/temperature") == NULL) {
    fb.setFloat("chauffages/pg/idLastSuivi", 0);
    Serial.println("Setting up Suivi Temperature");
  }
}

//-----------------------------------------------------------------------
// -------------------------------- LOOP --------------------------------
void loop() {
  temperatureProcess();
  checkPrograms();
  checkCommand();

  delay(360000); // 5 minutes delay
  //delay(5000); // 5 sec delay
}



void motorCycle() { // This pushes the button twice
  int timeToPush = 3000;

  // Step 1 : Push
  digitalWrite(pin1PilotMotor, HIGH);
  digitalWrite(pin2PilotMotor, LOW);
  delay(timeToPush);
  // Step 2 : Go back
  digitalWrite(pin1PilotMotor, LOW);
  digitalWrite(pin2PilotMotor, HIGH);
  delay(timeToPush);

  // Step 3 : Push
  digitalWrite(pin1PilotMotor, HIGH);
  digitalWrite(pin2PilotMotor, LOW);
  delay(timeToPush);
  // Step 4 : Go back
  digitalWrite(pin1PilotMotor, LOW);
  digitalWrite(pin2PilotMotor, HIGH);
  delay(timeToPush);


  // Step 5 : Turn motor off
  digitalWrite(pin1PilotMotor, LOW);
  digitalWrite(pin2PilotMotor, LOW);
}



void temperatureProcess() { // Reads the temperature and writes it in database (SuiviTemp)
  struct tm timeinfo;
  String datetime;
  if (getLocalTime(&timeinfo)) { // If time is ok
    char buffer[30];
    strftime(buffer, sizeof(buffer), "%d/%m/%Y %H:%M:%S", &timeinfo);
    datetime = String(buffer);
  }
  else {
    Serial.println("Échec de la récupération du temps.");
  }

  auto status = am2302.read();
  currentTemperature = am2302.get_Temperature();
  float currentHumidity = am2302.get_Humidity();
  Serial.print("\n\nstatus of sensor read(): ");
  Serial.println(status); 
  Serial.print("Temperature: ");
  Serial.println(currentTemperature); 
  Serial.print("Humidity:    ");
  Serial.println(currentHumidity);

  int idLastSuivi = fb.getInt("chauffages/pg/idLastSuivi");
  idLastSuivi++;
  Serial.print("idLastSuivi:    ");
  Serial.println(idLastSuivi);
  fb.setFloat("chauffages/pg/SuiviTemp/" + String(idLastSuivi) + "/temperature", currentTemperature);
  fb.setString("chauffages/pg/SuiviTemp/" + String(idLastSuivi) + "/dateTime", datetime);
  fb.setInt("chauffages/pg/idLastSuivi", idLastSuivi);
}



void checkCommand() { // This keeps watch on the manual command
  status = fb.getBool("chauffages/pg/status");
  Serial.print("status:    ");
  Serial.println(status);
  commande = fb.getBool("chauffages/pg/commande");
  Serial.print("commande:    ");
  Serial.println(commande);

  if(commande == false) { //On veut avoir un chauffage eteint
    if (status == true) { //On a un chauffage allume
      motorCycle(); //On eteint le chauffage
      status = false;
      status = fb.setBool("chauffages/pg/status", status);
      Serial.println("TURNING HEAT OFF (command off)");
    }
  }
  else { //On veut avoir un chauffage allume
    asservissementTemperature();
  }
}



void asservissementTemperature() { // Reads the command value in db and turns on or off chauffage
  float objectifTemp = fb.getFloat("chauffages/pg/valueTemp") - deltaTempMax;
  if(currentTemperature < objectifTemp) { //Il fait trop froid
    if (status == false) { //On a un chauffage eteint
      motorCycle(); //On allume le chauffage
      status = true;
      status = fb.setBool("chauffages/pg/status", status);
      Serial.println("TURNING HEAT ON (too cold)");
    }
  }
  else { //Il fait assez chaud
    if (status == true) { //On a un chauffage allume
      motorCycle(); //On eteint le chauffage
      status = false;
      status = fb.setBool("chauffages/pg/status", status);
      Serial.println("TURNING HEAT OFF (hot enough)");
    }
  }
}



void checkPrograms() { 
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("Échec de la récupération du temps.");
    return;
  }
  // Jour actuel (0=Dimanche, 1=Lundi, 2 = mardi etc)
  int currentDay = timeinfo.tm_wday;

  // Minutes depuis minuit
  int currentTime = timeinfo.tm_hour * 60 + timeinfo.tm_min;

  int idLastProgram = fb.getInt("chauffages/pg/idLastProgram");
  bool programInProgress = fb.getBool("chauffages/pg/programInProgress");
  bool commande = false;

  for (int i = 0; i <= idLastProgram; i++) {
    String basePath = "chauffages/pg/programs/" + String(i);

    // Récupération des données du programme
    int programDay = fb.getInt(basePath + "/day");
    String timeOn = fb.getString(basePath + "/timeOn");
    String timeOff = fb.getString(basePath + "/timeOff");

    // Conversion des heures en minutes depuis minuit
    int timeOnMinutes = convertTimeToMinutes(timeOn);
    int timeOffMinutes = convertTimeToMinutes(timeOff);

    // Vérification des conditions du programme
    if (currentDay == programDay && currentTime >= timeOnMinutes && currentTime < timeOffMinutes) {
      commande = true;
      fb.setBool("chauffages/pg/commande", true);
      fb.setBool("chauffages/pg/programInProgress", true);
      Serial.println("Programme n°" + String(i) + " :");
      Serial.println("Programme activé, asservissement ON");
      break; // Si un programme est activé, pas besoin de vérifier les autres
    }
  }

  // Si aucun programme n'est actif et un programme était en cours
  if (!commande && programInProgress) {
    fb.setBool("chauffages/pg/commande", false);
    fb.setBool("chauffages/pg/programInProgress", false);
    Serial.println("Programme terminé: asservissement OFF");
  }
}

// Fonction pour convertir une heure (format "HH:MM") en minutes depuis minuit
int convertTimeToMinutes(const String &timeString) {
  int separatorIndex = timeString.indexOf(':');
  if (separatorIndex == -1) return -1;

  int hours = timeString.substring(0, separatorIndex).toInt();
  int minutes = timeString.substring(separatorIndex + 1).toInt();

  return hours * 60 + minutes;
}
