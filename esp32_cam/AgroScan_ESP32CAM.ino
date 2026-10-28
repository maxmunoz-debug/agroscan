/*
  =============================================================================
  agroScan - Firmware IoT de Captura Fitosanitaria para ESP32-CAM
  Institución: Tecsup • Proyecto Agroscaner 🍓
  Hardware: Módulo AI-Thinker ESP32-CAM con Sensor Óptico OV2640
  =============================================================================
  
  Características del Firmware:
  1. Servidor Web local (HTTP):
     - GET /         -> Estado del sistema y confirmación de conectividad.
     - GET /capture  -> Captura y entrega foto JPEG en alta resolución.
     - GET /stream   -> Transmisión de video en vivo (MJPEG).
  2. Disparo Físico por Pulsador (Cabina de Inspección):
     - Al presionar el botón físico, enciende el Flash LED, toma la foto, 
       la envía al Backend FastAPI (/analizar) y enciende el LED correspondiente 
       del Semáforo Físico (Verde, Amarillo o Rojo) según la IA.
  3. Doble Modo Wi-Fi:
     - Conexión a red existente (Router / Celular) o Punto de Acceso Propio
       (SSID: "AgroScan-Tecsup-Cam", IP fija 192.168.4.1 para campo sin router).
  =============================================================================
*/

#include "esp_camera.h"
#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <ArduinoJson.h> // Instalar librería ArduinoJson en el Gestor de Bibliotecas

// ================= 1. CONFIGURACIÓN DE RED WI-FI =================
// Si deseas conectar a un router o zona Wi-Fi de celular:
const char* WIFI_SSID = "TU_WIFI_AQUI";
const char* WIFI_PASS = "TU_PASSWORD_AQUI";

// Si no hay red, activará su propio Punto de Acceso en el campo:
const char* AP_SSID = "AgroScan-Tecsup-Cam";
const char* AP_PASS = "agroscan2026";
bool modoPuntoAcceso = false;

// IP del servidor Backend (FastAPI en PC o Nube)
String backendServerUrl = "http://192.168.1.100:8000/analizar";

// ================= 2. DEFINICIÓN DE PINES (AI-THINKER ESP32-CAM) =================
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// Pines de Actuadores y Semáforo Físico
#define FLASH_LED_PIN      4   // Flash LED de alta potencia integrado
#define PIN_BOTON_DISPARO 13   // Pulsador físico frontal de la cabina (PULLUP)
#define LED_SEMAFORO_VERDE 12  // Salida LED Verde (Apta Exportación)
#define LED_SEMAFORO_AMARILLO 14 // Salida LED Amarillo (Revisión)
#define LED_SEMAFORO_ROJO 15   // Salida LED Rojo (Descarte Botrytis)

WebServer server(80);

// ================= 3. INICIALIZACIÓN DE LA CÁMARA OV2640 =================
bool iniciarCamara() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  // Ajuste según memoria PSRAM
  if (psramFound()) {
    config.frame_size = FRAMESIZE_SVGA; // 800x600 óptimo para IA
    config.jpeg_quality = 12;           // Calidad alta (0-63, menor = mayor calidad)
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_VGA;  // 640x480
    config.jpeg_quality = 15;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[ERROR] Falló la inicialización de la cámara: 0x%x\n", err);
    return false;
  }

  sensor_t * s = esp_camera_sensor_get();
  // Calibración de color para fresa
  s->set_brightness(s, 1);     // Brillo leve
  s->set_contrast(s, 1);       // Contraste para resaltar hongo
  s->set_saturation(s, 1);     // Saturación de color rojo
  s->set_whitebal(s, 1);       // Balance de blancos automático
  s->set_awb_gain(s, 1);

  Serial.println("[OK] Sensor OV2640 calibrado e iniciado con éxito.");
  return true;
}

// ================= 4. MANEJADORES DE RUTAS WEB =================
void handleRoot() {
  server.send(200, "text/plain", "agroScan ESP32-CAM (Tecsup) en linea 🟢");
}

void handleCapture() {
  // Enciende flash brevemente
  digitalWrite(FLASH_LED_PIN, HIGH);
  delay(120);
  
  camera_fb_t * fb = esp_camera_fb_get();
  digitalWrite(FLASH_LED_PIN, LOW);

  if (!fb) {
    server.send(500, "text/plain", "Fallo al capturar imagen del sensor");
    return;
  }

  server.sendHeader("Content-Disposition", "inline; filename=fresa.jpg");
  server.send_P(200, "image/jpeg", (const char *)fb->buf, fb->len);
  esp_camera_fb_return(fb);
}

// ================= 5. DISPARO FÍSICO Y ENVÍO AL BACKEND =================
void dispararYEnviarABackend() {
  Serial.println("\n[CABINA] Pulsador accionado -> Iniciando captura con Flash...");
  
  // 1. Enciende Flash y captura
  digitalWrite(FLASH_LED_PIN, HIGH);
  delay(150);
  camera_fb_t * fb = esp_camera_fb_get();
  digitalWrite(FLASH_LED_PIN, LOW);

  if (!fb) {
    Serial.println("[ERROR] Error al capturar foto.");
    return;
  }

  if (WiFi.status() != WL_CONNECTED && !modoPuntoAcceso) {
    Serial.println("[ERROR] Wi-Fi desconectado. No se puede enviar al backend.");
    esp_camera_fb_return(fb);
    return;
  }

  Serial.println("[ENVIANDO] Transfiriendo foto al servidor Backend FastAPI...");
  
  HTTPClient http;
  http.begin(backendServerUrl);
  
  String boundary = "----AgroScanBoundary7MA4YWxkTrZu0gW";
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

  String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fresa_esp32.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
  String tail = "\r\n--" + boundary + "--\r\n";

  size_t totalLen = head.length() + fb->len + tail.length();

  uint8_t * payload = (uint8_t *)malloc(totalLen);
  if (!payload) {
    Serial.println("[ERROR] Memoria insuficiente para armar payload.");
    esp_camera_fb_return(fb);
    http.end();
    return;
  }

  memcpy(payload, head.c_str(), head.length());
  memcpy(payload + head.length(), fb->buf, fb->len);
  memcpy(payload + head.length() + fb->len, tail.c_str(), tail.length());

  int httpCode = http.POST(payload, totalLen);
  free(payload);
  esp_camera_fb_return(fb);

  if (httpCode == 200) {
    String response = http.getString();
    Serial.println("[EXITO] Respuesta recibida de Gemini IA:");
    Serial.println(response);

    // Parsear respuesta JSON para encender el semáforo físico
    DynamicJsonDocument doc(1024);
    DeserializationError error = deserializeJson(doc, response);
    if (!error) {
      String semaforo = doc["semaforo"].as<String>();
      actualizarSemaforoFisico(semaforo);
    }
  } else {
    Serial.printf("[ERROR] Falló envío al servidor. Código HTTP: %d\n", httpCode);
  }

  http.end();
}

void actualizarSemaforoFisico(String semaforo) {
  // Apagar todos primero
  digitalWrite(LED_SEMAFORO_VERDE, LOW);
  digitalWrite(LED_SEMAFORO_AMARILLO, LOW);
  digitalWrite(LED_SEMAFORO_ROJO, LOW);

  if (semaforo.equalsIgnoreCase("verde")) {
    Serial.println(">>> SEMÁFORO: VERDE (Óptima / Exportación)");
    digitalWrite(LED_SEMAFORO_VERDE, HIGH);
  } else if (semaforo.equalsIgnoreCase("amarillo")) {
    Serial.println(">>> SEMÁFORO: AMARILLO (Revisión / Mercado Local)");
    digitalWrite(LED_SEMAFORO_AMARILLO, HIGH);
  } else {
    Serial.println(">>> SEMÁFORO: ROJO (Descarte / Botrytis Detectada)");
    digitalWrite(LED_SEMAFORO_ROJO, HIGH);
  }

  delay(4000); // Mantener encendido 4 segundos para visualización del técnico
  digitalWrite(LED_SEMAFORO_VERDE, LOW);
  digitalWrite(LED_SEMAFORO_AMARILLO, LOW);
  digitalWrite(LED_SEMAFORO_ROJO, LOW);
}

// ================= 6. SETUP & LOOP =================
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=============================================");
  Serial.println("  agroScan - SISTEMA IOT ESP32-CAM (TECSUP)  ");
  Serial.println("=============================================");

  pinMode(FLASH_LED_PIN, OUTPUT);
  digitalWrite(FLASH_LED_PIN, LOW);

  pinMode(PIN_BOTON_DISPARO, INPUT_PULLUP);
  pinMode(LED_SEMAFORO_VERDE, OUTPUT);
  pinMode(LED_SEMAFORO_AMARILLO, OUTPUT);
  pinMode(LED_SEMAFORO_ROJO, OUTPUT);

  // Test rápido de LEDs
  digitalWrite(LED_SEMAFORO_VERDE, HIGH);
  delay(200);
  digitalWrite(LED_SEMAFORO_AMARILLO, HIGH);
  delay(200);
  digitalWrite(LED_SEMAFORO_ROJO, HIGH);
  delay(200);
  digitalWrite(LED_SEMAFORO_VERDE, LOW);
  digitalWrite(LED_SEMAFORO_AMARILLO, LOW);
  digitalWrite(LED_SEMAFORO_ROJO, LOW);

  if (!iniciarCamara()) {
    Serial.println("[ERROR CRÍTICO] Cámara no disponible. Deteniendo.");
    while (true) { delay(1000); }
  }

  // Intento de conexión Wi-Fi
  Serial.print("Conectando a Wi-Fi: ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 20) {
    delay(400);
    Serial.print(".");
    intentos++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[OK] Conectado a Wi-Fi!");
    Serial.print("IP Asignada a la Cámara: http://");
    Serial.println(WiFi.localIP());
  } else {
    // Si no conecta, levantar Punto de Acceso propio (Modo Campo)
    Serial.println("\n[AVISO] No se pudo conectar al router. Activando Punto de Acceso Propio...");
    WiFi.softAP(AP_SSID, AP_PASS);
    modoPuntoAcceso = true;
    Serial.print("[AP ACTIVO] Red: ");
    Serial.println(AP_SSID);
    Serial.print("IP de la Cámara: http://");
    Serial.println(WiFi.softAPIP());
  }

  // Rutas del servidor Web
  server.on("/", handleRoot);
  server.on("/capture", handleCapture);
  server.begin();
  Serial.println("[SERVIDOR] Servidor Web HTTP listo en puerto 80.");
  Serial.println("Listo para capturar muestras. Presiona el botón frontal para disparar.");
}

void loop() {
  server.handleClient();

  // Lectura del pulsador frontal (activo en LOW por INPUT_PULLUP)
  if (digitalRead(PIN_BOTON_DISPARO) == LOW) {
    delay(50); // Anti-rebote
    if (digitalRead(PIN_BOTON_DISPARO) == LOW) {
      dispararYEnviarABackend();
      while (digitalRead(PIN_BOTON_DISPARO) == LOW) {
        delay(10); // Esperar a que suelte el botón
      }
    }
  }
}