# TimbrAR

Solución de timbre virtual con app Android para residentes y **PWA para visitantes** (instalable, con escaneo QR desde cámara cuando el navegador lo soporta).

## Nota de supuestos aplicados
- Se priorizó una arquitectura de bajo costo operativo: web estática en Firebase Hosting + consultas puntuales a Firestore + 1 Cloud Function para enviar evento de timbre.
- Para escaneo QR en web se usa `BarcodeDetector` nativo (costo cero de librería y mejor performance). Si no existe soporte, hay fallback claro a carga manual del código QR/link.

## Arquitectura de alto nivel
- **Android**: Kotlin + Compose + MVVM + Hilt (residentes).
- **Web visitantes**: PWA estática (`public/index.html`, `public/app.js`, `public/sw.js`, `public/manifest.webmanifest`).
- **Firebase**:
  - Hosting: entrega de PWA.
  - Firestore: resolución de `qr -> homeId`, configuración del hogar.
  - Functions: `requestDoorbell`.

## Flujo de visitantes PWA (producción)
1. Visitante abre la PWA (instalable).
2. Toca **Escanear QR** (cámara trasera `facingMode: environment`).
3. Parseo robusto del resultado:
   - URL absoluta (`https://dominio/?qr=abc`)
   - URL relativa (`/?homeId=H123`)
   - token plano (`abc123`)
4. La PWA resuelve `homeId` por:
   - `homeId` directo, o
   - lookup Firestore por `publicQrId`.
5. Valida horario y geolocalización (<100m).
6. Ejecuta `requestDoorbell`.

## PWA instalada (Android/iOS)
### Android (Chrome/Edge)
- Instalable vía banner o menú “Agregar a pantalla principal”.
- Funciona standalone con `display: standalone`.

### iOS (Safari)
- Instalación manual desde “Compartir” → “Agregar a pantalla de inicio”.
- Limitaciones de iOS a considerar:
  - Menor consistencia en permisos de cámara/geolocalización en modo standalone.
  - No existe `beforeinstallprompt` estándar.
  - El soporte de APIs de escaneo nativo puede variar por versión.

## Soporte de escaneo QR nativo
- **Requerido**: HTTPS + permiso de cámara.
- Se usa `BarcodeDetector` + `getUserMedia`.
- Si no hay soporte nativo: se muestra aviso y fallback con input manual (URL/código/token).

## Hardening aplicado al escáner
- Cierre de cámara en `visibilitychange`, `pagehide`, `beforeunload` y al cerrar modal.
- Prevención de detecciones duplicadas (ventana temporal de deduplicación).
- Bloqueo de concurrencia de decode (`decodeLock`).
- Estados visuales explícitos: solicitando cámara, escaneando, QR detectado, error.

## Firebase Hosting listo para producción
`firebase.json` incluye:
- `public: "public"`
- Headers para:
  - `/sw.js` sin caché + `Service-Worker-Allowed`
  - `/manifest.webmanifest` con `Content-Type` correcto
  - headers de seguridad base para todo el sitio

## Deploy a producción
```bash
firebase login
firebase use <tu-proyecto>
firebase deploy --only hosting
```

Si también actualizás backend:
```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

## Costos Firebase (decisiones)
- PWA estática: costo de hosting mínimo.
- Firestore con lookup puntual por QR y lectura puntual de `homes/{homeId}`.
- Sin listeners en tiempo real para visitantes (evita lecturas recurrentes).
- Cloud Function sólo al presionar timbre.

## Riesgos técnicos actuales + mitigación
1. **Compatibilidad `BarcodeDetector` desigual**
   - Mitigación: fallback manual + QR con URL corta legible.
2. **Permisos de cámara/geolocalización denegados**
   - Mitigación: mensajes claros y fallback manual.
3. **Abuso del botón de timbre**
   - Mitigación recomendada: rate limiting server-side por `sessionId + IP hash` en Function.
4. **Costo por QR inválidos masivos**
   - Mitigación recomendada: cache local temporal de tokens inválidos y/o Cloud Armor si escala.

## Próximos pasos priorizados
1. Agregar analítica de embudo visitante (scan iniciado, scan exitoso, timbre enviado).
2. Incorporar fallback opcional con librería JS (si negocio exige escaneo en más navegadores).
3. Añadir tests e2e de flujo QR con Playwright contra emuladores Firebase.
4. Versionar configuración Firebase por entorno (`dev/stg/prod`) para operación segura.

---

Recordatorio: completar `firebaseConfig` en `public/app.js` con los valores reales del proyecto.
