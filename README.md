# TimbrAR

Solución completa para timbre virtual con app Android (residentes) y web pública (visitas) usando Firebase.

## 🧭 Supuestos aplicados
- Se usa Firebase Authentication (email/password para residentes + phone auth para visitantes).
- El QR público incluye el `homeId` en la URL.
- Notificaciones push se envían vía FCM usando Cloud Functions (estrictamente necesario para background).

## 🧱 Arquitectura
- **Android (Kotlin + Compose + MVVM + Hilt)** con capas `ui`, `domain`, `data`.
- **Firebase** como backend (Auth, Firestore, Messaging, Hosting, Functions).
- **Web pública estática** en `public/` con Firebase Hosting.

## 📂 Estructura
```
/app                -> App Android
/public             -> Web pública (QR)
/functions          -> Cloud Functions para notificaciones
firestore.rules     -> Reglas de seguridad
firebase.json       -> Configuración Hosting/Functions
```

## 🔥 Modelo de datos (Firestore)
```
users/{uid}
  - uid
  - fullName
  - email
  - homeId
  - role (owner/member)
  - fcmToken
  - createdAt

homes/{homeId}
  - homeId
  - publicQrId (hash del homeId)
  - address
  - addressName
  - locationType
  - placeId
  - latitude
  - longitude
  - isDoorbellEnabled
  - scheduleStartMinutes (0-1439)
  - scheduleEndMinutes (0-1439)
  - timeZone (IANA)
  - rateLimitBaseMinutes (min)
  - rateLimitMaxMinutes (min)
  - createdAt

homes/{homeId}/ringEvents/{eventId}
  - visitorPhone
  - createdAt
  - status (pending/ack)

homes/{homeId}/blockedPhones/{phone}
  - phone
  - reason
  - createdAt

homes/{homeId}/rateLimits/{identifier}
  - currentPenaltyMinutes
  - nextAllowedAt
  - pressCount
  - type (phone/session)
```

### ✅ Ventajas
- Lecturas limitadas (solo 20 últimos timbrazos por hogar).
- Seguridad: sólo residentes del hogar pueden leer timbrazos.
- Visitantes sólo crean eventos luego de validar teléfono.

## 📲 Android
1. Copiar `google-services.json` dentro de `app/`.
2. Crear usuarios residentes con email y contraseña.
3. Cada usuario debe usar el mismo `homeId` para compartir hogar.

### Pantallas
- **AuthScreen**: registro / login.
- **HomeScreen**: historial de timbres.
- **HomeScreen (Owner)**: configuración de horario habilitado del timbre.
- **HomeScreen (Owner)**: bloqueo manual de números no deseados.

## 🌐 Web pública (QR)
- URL del QR: `https://<tu-proyecto>.web.app/?qr=HASH_PUBLICO`.
- El visitante:
  1. Ingresa teléfono
  2. Recibe SMS
  3. Valida
  4. Si el horario está habilitado, presiona “Tocar timbre virtual”
  5. La primera pulsación no requiere teléfono; para la siguiente sí.
  6. Penalidades: 5 min, 10 min, 20 min… hasta 120 min y reinicia.

## ☁️ Deploy Firebase
```bash
firebase login
firebase use <tu-proyecto>

# Hosting
firebase deploy --only hosting

# Functions
cd functions
npm install
firebase deploy --only functions
```

## 🔐 Reglas
Revisá `firestore.rules` y ajustá según políticas de negocio.

## ✅ Próximos pasos recomendados
- Agregar verificación de `homeId` válido en la web (lista blanca).
- Implementar rate limiting (1 timbrazo cada X segundos).
- UX: pantalla de "timbre enviado" con feedback.

---

**Nota:** reemplazá los placeholders de Firebase en `public/app.js` y `/.firebaserc`.
