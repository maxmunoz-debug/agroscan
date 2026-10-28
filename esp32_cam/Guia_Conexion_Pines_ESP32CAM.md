# 🍓 Guía de Conexión y Carga de Firmware: ESP32-CAM (agroScan - Tecsup)

Esta guía detalla el cableado físico y la programación del módulo **AI-Thinker ESP32-CAM** con sensor **OV2640** para la maqueta/cabina de inspección fitosanitaria.

---

## 1. Conexión del Programador USB a Serial (FTDI o CP2102)

Dado que la ESP32-CAM no tiene puerto micro-USB integrado, se programa usando un módulo FTDI o USB-UART:

| Pin del Programador (FTDI) | Pin de la ESP32-CAM | Notas / Importante |
| :--- | :--- | :--- |
| **VCC (5V)** | **5V** | ⚠️ Conectar a 5V (3.3V suele causar reinicios por falta de corriente con Wi-Fi). |
| **GND** | **GND** | Tierra común. |
| **TX** | **U0R (GPIO 3)** | Transmisión cruzada a Recepción. |
| **RX** | **U0T (GPIO 1)** | Recepción cruzada a Transmisión. |
| **GND (Cable jumper)** | **GPIO 0 (IO0)** | **¡PUENTE DE FLASHEO!** Conectar solo al momento de subir el código. Para ejecutar, desconectar y presionar el botón RESET. |

---

## 2. Conexión del Semáforo Físico y Pulsador de la Cabina

| Componente | Pin ESP32-CAM | Conexión Física |
| :--- | :--- | :--- |
| **Pulsador de Disparo** | **GPIO 13** | Un extremo a GPIO 13 y el otro a **GND** (utiliza resistencia interna PULLUP). |
| **LED Verde (Óptimo)** | **GPIO 12** | Ánodo (+) con resistencia de 220Ω a GPIO 12; Cátodo (-) a GND. |
| **LED Amarillo (Revisión)** | **GPIO 14** | Ánodo (+) con resistencia de 220Ω a GPIO 14; Cátodo (-) a GND. |
| **LED Rojo (Descarte/Botrytis)** | **GPIO 15** | Ánodo (+) con resistencia de 220Ω a GPIO 15; Cátodo (-) a GND. |
| **Flash LED de Alta Potencia** | **GPIO 4** | **Ya integrado en la placa**. El firmware lo activa automáticamente 150 ms para iluminar la fresa sin sombras. |

---

## 3. Configuración en Arduino IDE

1. **Gestor de Placas:**
   * Abre Arduino IDE -> `Herramientas` -> `Placa` -> `esp32` -> Selecciona **`AI Thinker ESP32-CAM`**.
2. **Ajustes de compilación recomendados:**
   * **CPU Frequency:** `240MHz (WiFi/BT)`
   * **Flash Frequency:** `80MHz`
   * **Flash Mode:** `QIO`
   * **Partition Scheme:** `Huge APP (3MB No OTA/1MB SPIFFS)` *(Obligatorio para que quepa el driver de cámara y el Wi-Fi)*.
   * **Upload Speed:** `115200` o `921600`
3. **Bibliotecas Requeridas:**
   * En `Herramientas` -> `Administrar Bibliotecas`, busca e instala:
     * **`ArduinoJson`** (por *Benoit Blanchon* - versión 6.x o 7.x).

---

## 4. Pasos para la Carga del Código

1. Conecta el jumper entre **GPIO 0** y **GND**.
2. Conecta el cable USB a la laptop.
3. Presiona el botón pequeño **RESET (RST)** debajo de la placa ESP32-CAM una vez.
4. En Arduino IDE, abre `AgroScan_ESP32CAM.ino` y presiona el botón **Subir (Flecha)**.
5. Cuando la terminal muestre `Connecting.......____.....`, la carga comenzará y llegará al `100% (Done uploading)`.
6. **IMPORTANTE:** Desconecta el jumper de **GPIO 0** y presiona **RESET**.
7. Abre el **Monitor Serial** a `115200 baudios`. Verás el test de bienvenida de los LEDs y la IP asignada.

---

## 5. Pruebas y Enlace con la App Android

* **Modo Red Local:** Si se conecta a tu router, en el Monitor Serial dirá `IP Asignada: http://192.168.1.xxx/`.
* **Modo Campo (Sin Router):** Si no encuentra la red, creará su propio Wi-Fi:
  * **Nombre Wi-Fi:** `AgroScan-Tecsup-Cam`
  * **Contraseña:** `agroscan2026`
  * **IP Fija:** `http://192.168.4.1/`
* En la app Android, ve a **Ajustes** -> **Dispositivo y Conectividad IoT**, coloca la IP y presiona **Verificar** para ver la luz verde 🟢.