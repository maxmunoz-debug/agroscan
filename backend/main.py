import os
import io
import json
import base64
import sqlite3
import hashlib
from datetime import datetime
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

# 2. Base de datos SQLite local (Cero simulación, persistencia real)
DB_PATH = os.path.join(os.path.dirname(__file__), "agroscan.db")

def hash_password(password: str) -> str:
    """Aplica SHA-256 con salt para almacenamiento seguro de contraseñas."""
    salt = "agroscan_tecsup_salt_2026"
    return hashlib.sha256((salt + password).encode()).hexdigest()

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Tabla de Usuarios Reales (Autenticación compartida)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS usuarios (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        nombre TEXT NOT NULL,
        email TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        rol TEXT DEFAULT 'Evaluador',
        creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """)

    # Sembrar usuario inicial si está vacía
    cursor.execute("SELECT COUNT(*) FROM usuarios")
    if cursor.fetchone()[0] == 0:
        pwd_hash = hash_password("agroscan2026")
        cursor.execute("""
        INSERT INTO usuarios (nombre, email, password_hash, rol)
        VALUES ('Investigador Tecsup', 'investigacion@tecsup.edu.pe', ?, 'Evaluador')
        """, (pwd_hash,))
    
    # Tabla de Lotes Agrícolas (Con usuario_email)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS lotes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        usuario_email TEXT DEFAULT '',
        nombre TEXT NOT NULL,
        variedad TEXT NOT NULL,
        ubicacion TEXT NOT NULL,
        estado TEXT DEFAULT 'Óptimo',
        ultima_eval TEXT DEFAULT 'Sin evaluar',
        salud INTEGER DEFAULT 100,
        activo INTEGER DEFAULT 1
    )
    """)

    # Tabla de Evaluaciones Fitosanitarias Reales (Con usuario_email)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS evaluaciones (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        usuario_email TEXT DEFAULT '',
        fecha_hora TEXT NOT NULL,
        lote_id INTEGER,
        lote_nombre TEXT NOT NULL,
        variedad TEXT NOT NULL,
        fruta TEXT DEFAULT 'Fresa',
        madurez_porcentaje INTEGER DEFAULT 85,
        color_predominante TEXT DEFAULT '',
        defectos_detectados TEXT DEFAULT '',
        semaforo TEXT NOT NULL,
        dictamen TEXT NOT NULL,
        recomendacion TEXT DEFAULT '',
        fungicidas_json TEXT DEFAULT '[]',
        medidas_json TEXT DEFAULT '[]',
        notas TEXT DEFAULT ''
    )
    """)
    conn.commit()
    conn.close()

init_db()

# 3. Crear la API FastAPI
app = FastAPI(
    title="AgroScan API con IA y Base de Datos Real",
    description="Backend oficial de AgroScan con visión computacional y base de datos SQLite",
    version="2.0.0"
)

# Permitir conexiones de la Web (React), Android y ESP32
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
        "estado": "En línea con IA y base de datos SQLite activada",
        "version": "2.0.0"
    }

# ================= MODELOS PYDANTIC =================
class RegisterRequest(BaseModel):
    nombre: str
    email: str
    password: str

class LoginRequest(BaseModel):
    email: str
    password: str

class AuthResponse(BaseModel):
    id: int
    nombre: str
    email: str
    rol: str
    mensaje: str

class LoteCreate(BaseModel):
    nombre: str
    variedad: str = "Albión"
    ubicacion: str = "Sector General"
    usuario_email: str = ""

class EvaluacionCreate(BaseModel):
    usuario_email: str = ""
    lote_id: int | None = None
    lote_nombre: str
    variedad: str = "Albión"
    fruta: str = "Fresa"
    madurez_porcentaje: int = 85
    color_predominante: str = ""
    defectos_detectados: str = ""
    semaforo: str
    dictamen: str
    recomendacion: str = ""
    fungicidas_sugeridos: list[str] = []
    medidas_preventivas: list[str] = []
    notas: str = ""

class ChatRequest(BaseModel):
    pregunta: str

class ChatResponse(BaseModel):
    respuesta: str

# ================= ENDPOINTS DE AUTENTICACIÓN REAL =================
@app.post("/auth/register", response_model=AuthResponse)
def register(req: RegisterRequest):
    nombre = req.nombre.strip()
    email = req.email.strip().lower()
    password = req.password.strip()

    if len(nombre) < 2:
        raise HTTPException(status_code=400, detail="El nombre debe tener al menos 2 caracteres")
    if "@" not in email or "." not in email:
        raise HTTPException(status_code=400, detail="Formato de correo electrónico inválido")
    if len(password) < 6:
        raise HTTPException(status_code=400, detail="La contraseña debe tener al menos 6 caracteres")

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    try:
        pwd_hash = hash_password(password)
        cursor.execute("""
        INSERT INTO usuarios (nombre, email, password_hash, rol)
        VALUES (?, ?, ?, 'Evaluador')
        """, (nombre, email, pwd_hash))
        conn.commit()
        nuevo_id = cursor.lastrowid
        conn.close()
        return AuthResponse(
            id=nuevo_id,
            nombre=nombre,
            email=email,
            rol="Evaluador",
            mensaje="Usuario registrado exitosamente"
        )
    except sqlite3.IntegrityError:
        conn.close()
        raise HTTPException(status_code=400, detail="El correo electrónico ya se encuentra registrado")
    except Exception as e:
        conn.close()
        raise HTTPException(status_code=500, detail=f"Error en el servidor: {str(e)}")

@app.post("/auth/login", response_model=AuthResponse)
def login(req: LoginRequest):
    identificador = req.email.strip().lower()
    password = req.password.strip()

    if not identificador or not password:
        raise HTTPException(status_code=400, detail="Debe ingresar correo/usuario y contraseña")

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    pwd_hash = hash_password(password)

    # Permite ingresar tanto con correo como con nombre de usuario
    cursor.execute("""
    SELECT id, nombre, email, rol, password_hash FROM usuarios 
    WHERE LOWER(email) = ? OR LOWER(nombre) = ?
    """, (identificador, identificador))
    user = cursor.fetchone()
    conn.close()

    if not user or user["password_hash"] != pwd_hash:
        raise HTTPException(status_code=401, detail="Credenciales incorrectas: correo o contraseña inválidos")

    return AuthResponse(
        id=user["id"],
        nombre=user["nombre"],
        email=user["email"],
        rol=user["rol"],
        mensaje="Inicio de sesión exitoso"
    )

# ================= ENDPOINTS DE LOTES (CRUD REAL) =================
@app.get("/lotes")
def get_lotes(email: str = ""):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    if email.strip():
        cursor.execute("SELECT * FROM lotes WHERE activo = 1 AND (usuario_email = ? OR usuario_email = '' OR usuario_email IS NULL) ORDER BY id ASC", (email.strip().lower(),))
    else:
        cursor.execute("SELECT * FROM lotes WHERE activo = 1 ORDER BY id ASC")
    rows = cursor.fetchall()
    conn.close()
    return [
        {
            "id": r["id"],
            "nombre": r["nombre"],
            "variedad": r["variedad"],
            "ubicacion": r["ubicacion"],
            "estado": r["estado"],
            "ultimaEval": r["ultima_eval"],
            "salud": r["salud"],
            "usuarioEmail": r["usuario_email"]
        } for r in rows
    ]

@app.post("/lotes")
def create_lote(lote: LoteCreate):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
    INSERT INTO lotes (usuario_email, nombre, variedad, ubicacion, estado, ultima_eval, salud, activo)
    VALUES (?, ?, ?, ?, 'Óptimo', 'Recién registrado', 100, 1)
    """, (lote.usuario_email.strip().lower(), lote.nombre.strip(), lote.variedad.strip(), lote.ubicacion.strip()))
    conn.commit()
    nuevo_id = cursor.lastrowid
    conn.close()
    return {"id": nuevo_id, "mensaje": "Lote registrado exitosamente"}

@app.delete("/lotes/{lote_id}")
def delete_lote(lote_id: int):
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("UPDATE lotes SET activo = 0 WHERE id = ?", (lote_id,))
    conn.commit()
    conn.close()
    return {"mensaje": "Lote eliminado"}

# ================= ENDPOINTS DE EVALUACIONES FITOSANITARIAS =================
@app.get("/evaluaciones")
def get_evaluaciones(email: str = ""):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    if email.strip():
        cursor.execute("SELECT * FROM evaluaciones WHERE (usuario_email = ? OR usuario_email = '' OR usuario_email IS NULL) ORDER BY id DESC", (email.strip().lower(),))
    else:
        cursor.execute("SELECT * FROM evaluaciones ORDER BY id DESC")
    rows = cursor.fetchall()
    conn.close()
    res = []
    for r in rows:
        res.append({
            "id": r["id"],
            "usuarioEmail": r["usuario_email"],
            "fechaHora": r["fecha_hora"],
            "loteId": r["lote_id"],
            "loteNombre": r["lote_nombre"],
            "variedad": r["variedad"],
            "fruta": r["fruta"],
            "madurezPorcentaje": r["madurez_porcentaje"],
            "colorPredominante": r["color_predominante"],
            "defectosDetectados": r["defectos_detectados"],
            "semaforo": r["semaforo"],
            "dictamen": r["dictamen"],
            "recomendacion": r["recomendacion"],
            "fungicidasSugeridos": json.loads(r["fungicidas_json"] or "[]"),
            "medidasPreventivas": json.loads(r["medidas_json"] or "[]"),
            "notas": r["notas"]
        })
    return res

@app.post("/evaluaciones")
def create_evaluacion(evaluacion: EvaluacionCreate):
    ahora = datetime.now().strftime("%d/%m/%Y %I:%M %p")
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("""
    INSERT INTO evaluaciones (
        usuario_email, fecha_hora, lote_id, lote_nombre, variedad, fruta,
        madurez_porcentaje, color_predominante, defectos_detectados,
        semaforo, dictamen, recomendacion, fungicidas_json, medidas_json, notas
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        evaluacion.usuario_email.strip().lower(), ahora, evaluacion.lote_id, evaluacion.lote_nombre, evaluacion.variedad, evaluacion.fruta,
        evaluacion.madurez_porcentaje, evaluacion.color_predominante, evaluacion.defectos_detectados,
        evaluacion.semaforo.lower(), evaluacion.dictamen, evaluacion.recomendacion,
        json.dumps(evaluacion.fungicidas_sugeridos), json.dumps(evaluacion.medidas_preventivas), evaluacion.notas
    ))
    
    # Actualizar estado y salud del lote correspondiente
    nuevo_estado = "Óptimo" if evaluacion.semaforo.lower() == "verde" else "En Revisión" if evaluacion.semaforo.lower() == "amarillo" else "Crítico"
    salud_delta = 95 if evaluacion.semaforo.lower() == "verde" else 75 if evaluacion.semaforo.lower() == "amarillo" else 40
    
    if evaluacion.lote_id:
        cursor.execute("""
        UPDATE lotes SET estado = ?, ultima_eval = ?, salud = ? WHERE id = ?
        """, (nuevo_estado, ahora, salud_delta, evaluacion.lote_id))
    else:
        cursor.execute("""
        UPDATE lotes SET estado = ?, ultima_eval = ?, salud = ? WHERE nombre LIKE ?
        """, (nuevo_estado, ahora, salud_delta, f"%{evaluacion.lote_nombre}%"))

    conn.commit()
    nuevo_id = cursor.lastrowid
    conn.close()
    return {"id": nuevo_id, "mensaje": "Evaluación guardada y sincronizada"}

# ================= MÉTRICAS DINÁMICAS (CERO SIMULACIÓN) =================
@app.get("/metricas")
def get_metricas():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("SELECT COUNT(*) FROM lotes WHERE activo = 1")
    total_lotes = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM evaluaciones")
    total_evals = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM evaluaciones WHERE LOWER(semaforo) = 'verde'")
    optimos = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM evaluaciones WHERE LOWER(semaforo) = 'amarillo'")
    revision = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM evaluaciones WHERE LOWER(semaforo) = 'rojo'")
    criticos = cursor.fetchone()[0]
    
    cursor.execute("SELECT COUNT(*) FROM lotes WHERE activo = 1 AND estado = 'Crítico'")
    lotes_criticos = cursor.fetchone()[0]
    
    conn.close()
    
    if total_evals > 0:
        pct_sanos = round((optimos * 100) / total_evals)
        pct_optimo = round((optimos * 100) / total_evals, 1)
        pct_revision = round((revision * 100) / total_evals, 1)
        pct_critico = round((criticos * 100) / total_evals, 1)
    else:
        pct_sanos = 0
        pct_optimo = 0
        pct_revision = 0
        pct_critico = 0
        
    if total_evals == 0:
        consejo_titulo = "INICIO DE MONITOREO"
        consejo_texto = "Aún no se registran evaluaciones en el fundo. Realiza un escaneo con la cámara o sube una foto para generar las proporciones en tiempo real."
    elif criticos > 0:
        consejo_titulo = "ALERTA FITOSANITARIA: BOTRYTIS"
        consejo_texto = f"Se registraron {criticos} fruto(s) con presencia crítica de moho gris. Aplicar fungicida Switch 62.5 WG (0.8 kg/ha) o Captan, retirar fresas dañadas y mejorar ventilación."
    elif revision > 0:
        consejo_titulo = "MANEJO PREVENTIVO EN CAMPO"
        consejo_texto = f"Hay {revision} fruto(s) en revisión. Aplicar biofungicida Bacillus subtilis (Serenade ASO) y realizar cosecha selectiva matutina."
    else:
        consejo_titulo = "CULTIVO EN CONDICIÓN ÓPTIMA"
        consejo_texto = "El 100% de los frutos evaluados cumplen con estándar de exportación. Mantener fertirriego y monitoreo diario."
        
    return {
        "total_lotes": total_lotes,
        "total_evaluaciones": total_evals,
        "optimos": optimos,
        "revision": revision,
        "criticos": criticos,
        "porcentaje_sanos": pct_sanos,
        "pct_optimo": pct_optimo,
        "pct_revision": pct_revision,
        "pct_critico": pct_critico,
        "lotes_criticos": lotes_criticos,
        "consejo_titulo": consejo_titulo,
        "consejo_texto": consejo_texto
    }

# ================= ANÁLISIS MULTIMODAL CON GEMINI =================
@app.post("/analizar")
async def analizar_fruta(file: UploadFile = File(...)):
    """
    Recibe una imagen (desde Android, Web o ESP32) y devuelve el diagnóstico fitosanitario.
    """
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        image.thumbnail((1024, 1024))
        
        buffer = io.BytesIO()
        image.save(buffer, format="JPEG", quality=85)
        base64_image = base64.b64encode(buffer.getvalue()).decode("utf-8")

        prompt = """
        Eres el motor de visión artificial de 'AgroScan', un sistema de inspección de calidad y fitopatología en fresas (Fragaria x ananassa) para Tecsup.
        Analiza detalladamente la fruta en esta imagen.
        
        Debes responder ÚNICAMENTE con un objeto JSON válido con la siguiente estructura exacta:
        {
            "fruta": "Fresa",
            "madurez_porcentaje": 85,
            "color_predominante": "Rojo brillante con aquenios amarillos",
            "defectos_detectados": "Sin presencia de Botrytis cinerea ni pudrición",
            "semaforo": "verde",
            "dictamen": "Apta para exportación",
            "recomendacion": "Cosechar en las primeras horas y almacenar a 2°C.",
            "fungicidas_sugeridos": ["Switch 62.5 WG (Preventivo)", "Bacillus subtilis (Serenade)"],
            "medidas_preventivas": ["Control de humedad < 65%", "Retiro de follaje senescente"]
        }

        Reglas para el campo 'semaforo':
        - "verde": Fruta óptima, firme, color adecuado, sin enfermedades (Apta exportación).
        - "amarillo": Fruta muy madura, pintona o con defectos estéticos leves pero sin hongos (Mercado local / pulpa).
        - "rojo": Fruta con moho (Botrytis cinerea), podredumbre gris, insectos o daño severo (Descarte inmediato).
        """

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
        texto_resultado = data["candidates"][0]["content"]["parts"][0]["text"]
        resultado = json.loads(texto_resultado)
        return resultado

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error analizando la muestra: {str(e)}")

# ================= ASISTENTE AGRÓNOMO IA =================
@app.post("/chat", response_model=ChatResponse)
async def chat_agronomo(request: ChatRequest):
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
