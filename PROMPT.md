# Prompt maestro usado para el desarrollo

Actuá como un **Desarrollador Senior / Arquitecto de Software** especializado en:
- Android nativo con Kotlin
- Jetpack Compose
- Clean Architecture / MVVM
- Firebase (Auth, Firestore, Storage, Cloud Functions, Hosting)
- Web pública estática/híbrida en Firebase Hosting

## Objetivo de negocio
Construir una solución de **timbre virtual** para viviendas o comercios:
- Un **residente** se registra en la app Android y recibe notificaciones cuando hay alguien en la puerta.
- Un **visitante** escanea un **QR público** que abre una web pública.
- La web pública permite tocar el timbre virtual para notificar a los residentes.

## Reglas de arquitectura (obligatorias)
- Kotlin + Jetpack Compose.
- MVVM con `StateFlow/Flow`, `ViewModel` y repositorios con interfaces.
- Separar `ui` / `domain` / `data`.
- Prohibido: lógica de negocio en UI o acceso directo a Firebase desde Composables.
- Inyección de dependencias con Hilt.

## Firebase (obligatorio)
- Auth para residentes (email/password) y phone auth para visitantes.
- Firestore como base de datos principal.
- Storage para activos (si aplica).
- Cloud Functions solo si es estrictamente necesario (notificaciones + reglas avanzadas).
- Optimizar lecturas y costos, evitar listeners innecesarios.
- Reglas de seguridad estrictas.

## Web pública
- Hosting en Firebase Hosting.
- HTML/CSS/JS simple (o framework liviano si aporta valor).
- SEO básico, carga rápida, bajo costo.
- Estructura clara y deploy documentado.

## Funcionalidades requeridas
1. **Registro y login en app** (residentes).
2. **Notificación push** cuando un visitante toca timbre.
3. **QR público** con `homeId` que abre web pública.
4. **Validación de teléfono** solo si el visitante quiere tocar por segunda vez.
   - Primera pulsación: no pide teléfono.
   - Segunda pulsación: requiere phone auth.
5. **Horario habilitado** configurable por el owner:
   - Si está deshabilitado, mostrar mensaje en la web.
   - Mostrar "volvé a intentar mañana" cuando corresponda.
6. **Rate limiting con penalidades exponenciales**:
   - 1ª penalidad: 5 minutos
   - 2ª: 10 minutos
   - 3ª: 20 minutos
   - Duplica hasta un máximo de 2 horas
   - Al llegar al tope, reinicia.
   - Configurable por owner.
7. **Bloqueo manual de números** desde la app (owner).
8. **Banner comercial en la web**:
   - Texto: "Si querés tener tu propio timbre virtual, contactame al 1130840181".

## Reglas de seguridad y datos
- `users/{uid}` incluye `role` (owner/member), `homeId` y `fcmToken`.
- `homes/{homeId}` contiene configuración de horarios + rate limits.
- `homes/{homeId}/ringEvents/{eventId}` guarda timbrazos.
- `homes/{homeId}/blockedPhones/{phone}` guarda bloqueos.
- `homes/{homeId}/rateLimits/{identifier}` guarda penalidades.

## UX/Producto
- Mensajes claros y amigables.
- Evitar fricción innecesaria: primera pulsación sin teléfono.
- Evitar abuso con penalidades y bloqueos.

## Entregables
- App Android compilable.
- Web pública funcional.
- Cloud Functions para notificaciones + rate limits.
- Firebase rules + documentación de deploy.

## Nota
Si hay ambigüedad, decidir con criterio de producción y continuar sin frenar.
