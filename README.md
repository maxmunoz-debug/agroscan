# AgroScan - Sistema Inteligente de Inspección Fitosanitaria en Fresa

**Proyecto de Investigación e Innovación Tecnológica • Tecsup**

AgroScan es un sistema integral de agricultura de precisión diseñado para el monitoreo fitosanitario y clasificación de madurez en cultivos de fresa (*Fragaria x ananassa*), con detección automatizada del patógeno *Botrytis cinerea* (moho gris) mediante visión computacional e Inteligencia Artificial multimodal (**Google Gemini**).

---

##Arquitectura del Sistema

El ecosistema está compuesto por tres componentes sincronizados:

1. **`android/` - Aplicación Móvil Nativa (Kotlin + Jetpack Compose)**
   * Interfaz reactiva bajo especificaciones de diseño *Google Stitch*.
   * Diagnóstico fitosanitario con semáforo inteligente (🟢 Exportación, 🟡 Mercado Local / Pulpa, 🔴 Descarte Fitosanitario).
   * Herramienta de encuadre y recorte independiente de frutos.
   * Asistente Agrónomo Virtual con IA generativa para consultas técnicas de campo y dosis de fungicidas (*Switch 62.5 WG*, *Captan*, *Bacillus subtilis*).
   * Gestión dinámica de parcelas, lotes agrícolas y variedades peruanas (*Albión, Monterrey, San Andreas, Camarosa, Portola, Fronteras*).
   * Generación y exportación de **Fichas Técnicas Oficiales en formato PDF**.
   * Barra de navegación con botón flotante central de captura rápida.

2. **`backend/` - Servidor de Inferencia y Asistencia IA (Python + FastAPI)**
   * **`POST /analizar`**: Endpoint para inferencia multimodal con **Google Gemini**, devolviendo dictamen fitosanitario, porcentaje de madurez y fungicidas sugeridos en formato JSON estructurado.
   * **`POST /chat`**: Endpoint para el Asistente Agrónomo IA especializado en fresas y monitoreo en macrotúneles.

3. **`esp32_cam/` - Hardware IoT y Maqueta de Campo**
   * Firmware C++ para microcontrolador AI-Thinker **ESP32-CAM (OV2640)**.
   * Pulsador de captura en surco con flash LED de alta intensidad.
   * Semáforo físico de LEDs (Verde, Amarillo, Rojo) sincronizado a la respuesta de la IA.
   * Servidor HTTP local y carga multipart directa al backend.

---

##Puesta en Marcha

### 1. Backend (FastAPI + Gemini)
```bash
cd backend
python -m venv venv
# En Windows:
.\venv\Scripts\activate
pip install fastapi uvicorn google-genai python-dotenv pillow pydantic

# Configura tu clave de Gemini en el archivo .env
cp .env.example .env
# Edita .env con tu GEMINI_API_KEY

uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. App Móvil Android
1. Abre la carpeta `android/` en **Android Studio**.
2. Conecta tu dispositivo físico (ej. Samsung) con depuración USB habilitada.
3. Asegúrate de que el teléfono y la PC estén en la misma red Wi-Fi.
4. En la pestaña **Ajustes** de la app, ingresa la IP local de tu PC (ej. `http://192.168.1.X:8000`).
5. Presiona **Run (▶️)** para compilar e instalar.

---

##Autores e Institución
* **Instituto Tecnológico Tecsup**
* **Investigador / Desarrollador:** Max Muñoz (`maxmunoz-debug`)
* **Proyecto:** AgroScan 
