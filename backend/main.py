import os
import io
import json
import base64
import requests
from PIL import Image
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

# 1. Cargar la clave desde el .env
load_dotenv()
api_key = os.getenv("GEMINI_API_KEY", "").strip()

if not api_key:
    raise ValueError("Falta configurar la variable GEMINI_API_KEY en el archivo .env")

# 2. Crear la API
app = FastAPI(
    title="AgroScan API con IA",
    description="Backend oficial de AgroScan con visión computacional",
    version="1.0.0"
)

# Permitir conexiones de la Web, Android y ESP32
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/")
def home():
    return {
        "sistema": "AgroScan API",
        "estado": "En línea con IA activada 🟢",
        "version": "1.0.0"
    }

@app.post("/analizar")
async def analizar_fruta(file: UploadFile = File(...)):
    """
    Recibe una imagen (desde Android, Web o ESP32) y devuelve el diagnóstico fitosanitario.
    """
    try:
        # 1. Leer los bytes de la imagen subida
        contents = await file.read()
        
        # 2. Optimizar la imagen con Pillow (máximo 1024px para que responda en 2 segundos)
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        image.thumbnail((1024, 1024))
        
        buffer = io.BytesIO()
        image.save(buffer, format="JPEG", quality=85)
        base64_image = base64.b64encode(buffer.getvalue()).decode("utf-8")

        prompt = """
        Eres el motor de visión artificial de 'AgroScan', un sistema de inspección de calidad de frutas (especialmente fresas y frutos similares).
        Analiza detalladamente la fruta en esta imagen.
        
        Debes responder ÚNICAMENTE con un objeto JSON válido con la siguiente estructura exacta:
        {
            "fruta": "Nombre de la fruta identificada",
            "madurez_porcentaje": 85,
            "color_predominante": "Rojo intenso con base blanquecina",
            "defectos_detectados": "Sin hongos ni moho Botrytis visible",
            "semaforo": "verde",
            "dictamen": "Apta para exportación",
            "recomendacion": "Consumir o distribuir en los próximos 3 a 5 días."
        }

        Reglas para el campo 'semaforo':
        - "verde": Fruta óptima, firme, color adecuado, sin enfermedades (Apta exportación).
        - "amarillo": Fruta muy madura, pintona o con defectos estéticos leves pero sin hongos (Mercado local / pulpa).
        - "rojo": Fruta con moho (Botrytis), podredumbre, insectos o daño severo (Descarte inmediato).
        """

        # 3. Llamar al modelo oficial Gemini 3.5 Flash Lite
        url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
        headers = {
            "x-goog-api-key": api_key,
            "Content-Type": "application/json"
        }
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {
                            "inline_data": {
                                "mime_type": "image/jpeg",
                                "data": base64_image
                            }
                        }
                    ]
                }
            ],
            "generationConfig": {
                "response_mime_type": "application/json"
            }
        }

        response = requests.post(url, headers=headers, json=payload, timeout=60)
        
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail=response.text)

        data = response.json()
        
        # Extraer el texto generado por Gemini
        texto_resultado = data["candidates"][0]["content"]["parts"][0]["text"]
        resultado = json.loads(texto_resultado)

        return resultado

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error analizando la muestra: {str(e)}")

class ChatRequest(BaseModel):
    pregunta: str


class ChatResponse(BaseModel):
    respuesta: str


@app.post("/chat", response_model=ChatResponse)
async def chat_agronomo(request: ChatRequest):
    """
    Asistente Agrónomo IA oficial de agroScan (Tecsup).
    Responde consultas técnicas sobre cultivo de fresas, control de Botrytis,
    dosis de fungicidas (Captan, Switch, Bacillus subtilis) y uso del sistema.
    """
    try:
        system_instruction = (
            "Eres el Asistente Agrónomo IA de 'agroScan', desarrollado para Tecsup. "
            "Eres especialista en agronomía, cultivo de fresas (Fragaria x ananassa), "
            "fitopatología (especialmente Botrytis cinerea, oídio, antracnosis), "
            "manejo integrado de plagas, dosificación de fungicidas químicos y biológicos "
            "(como Captan 80 WDG, Switch 62.5 WG - Ciprodinil + Fludioxonil, Serenade ASO - Bacillus subtilis), "
            "y en soporte técnico del sistema agroScan (cámara ESP32-CAM, distancia de enfoque de 15 a 20 cm, iluminación adecuada). "
            "Responde de forma clara, profesional, concisa y práctica para un técnico o evaluador en campo. "
            "Usa viñetas cuando des recomendaciones de manejo o dosis."
        )

        url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"
        headers = {
            "x-goog-api-key": api_key,
            "Content-Type": "application/json"
        }

        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": f"INSTRUCCIÓN DEL SISTEMA:\n{system_instruction}\n\nCONSULTA DEL EVALUADOR:\n{request.pregunta}"}
                    ]
                }
            ]
        }

        response = requests.post(url, headers=headers, json=payload, timeout=30)
        if response.status_code != 200:
            raise HTTPException(status_code=response.status_code, detail=response.text)

        data = response.json()
        respuesta_texto = data["candidates"][0]["content"]["parts"][0]["text"]
        return ChatResponse(respuesta=respuesta_texto.strip())

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error en el asistente agrónomo: {str(e)}")