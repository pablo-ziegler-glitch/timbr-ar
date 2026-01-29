const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2/options");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();
setGlobalOptions({ region: "southamerica-east1" });

exports.requestDoorbell = onCall(async (request) => {
  const { homeId, phone, sessionId } = request.data || {};
  if (!homeId) {
    throw new HttpsError("invalid-argument", "homeId es obligatorio.");
  }

  const homeRef = admin.firestore().collection("homes").doc(homeId);
  const homeSnapshot = await homeRef.get();
  if (!homeSnapshot.exists) {
    throw new HttpsError("not-found", "No encontramos el hogar.");
  }

  const homeConfig = homeSnapshot.data();
  if (!isDoorbellAvailable(homeConfig)) {
    throw new HttpsError(
      "failed-precondition",
      "El timbre está deshabilitado en este horario. Intentá nuevamente mañana."
    );
  }

  const now = admin.firestore.Timestamp.now();
  const baseMinutes = Number(homeConfig.rateLimitBaseMinutes ?? 5);
  const maxMinutes = Number(homeConfig.rateLimitMaxMinutes ?? 120);

  if (!phone) {
    if (!sessionId) {
      throw new HttpsError(
        "failed-precondition",
        "Para continuar necesitás una sesión válida."
      );
    }
    const sessionKey = `session_${sessionId}`;
    const sessionRef = homeRef.collection("rateLimits").doc(sessionKey);
    const sessionSnapshot = await sessionRef.get();
    if (sessionSnapshot.exists && (sessionSnapshot.get("pressCount") ?? 0) >= 1) {
      throw new HttpsError(
        "failed-precondition",
        "Para volver a tocar, primero validá tu teléfono."
      );
    }

    await sessionRef.set(
      {
        pressCount: admin.firestore.FieldValue.increment(1),
        lastRequestedAt: now,
        type: "session",
      },
      { merge: true }
    );

    await homeRef.collection("ringEvents").add({
      visitorPhone: null,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      status: "pending",
    });

    return { message: "¡Timbre enviado!" };
  }

  const normalizedPhone = normalizePhone(phone);
  if (!normalizedPhone) {
    throw new HttpsError("invalid-argument", "Teléfono inválido.");
  }

  const blockedSnapshot = await homeRef
    .collection("blockedPhones")
    .doc(normalizedPhone)
    .get();
  if (blockedSnapshot.exists) {
    throw new HttpsError("permission-denied", "Este número está bloqueado.");
  }

  const rateRef = homeRef.collection("rateLimits").doc(normalizedPhone);
  const rateSnapshot = await rateRef.get();
  const nextAllowedAt = rateSnapshot.get("nextAllowedAt");
  if (nextAllowedAt && nextAllowedAt.toMillis() > now.toMillis()) {
    const diffMinutes = Math.ceil(
      (nextAllowedAt.toMillis() - now.toMillis()) / 60000
    );
    throw new HttpsError(
      "resource-exhausted",
      `Podés tocar de nuevo en ${diffMinutes} min.`
    );
  }

  const currentPenalty = Number(rateSnapshot.get("currentPenaltyMinutes") ?? 0);
  const newPenalty =
    currentPenalty >= maxMinutes
      ? baseMinutes
      : Math.min(maxMinutes, Math.max(baseMinutes, currentPenalty * 2 || baseMinutes));

  const nextAllowed = admin.firestore.Timestamp.fromMillis(
    now.toMillis() + newPenalty * 60 * 1000
  );

  await rateRef.set(
    {
      currentPenaltyMinutes: newPenalty,
      nextAllowedAt: nextAllowed,
      lastRequestedAt: now,
      pressCount: admin.firestore.FieldValue.increment(1),
      type: "phone",
    },
    { merge: true }
  );

  await homeRef.collection("ringEvents").add({
    visitorPhone: normalizedPhone,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    status: "pending",
  });

  return { message: "¡Timbre enviado!" };
});

exports.notifyDoorbell = onDocumentCreated(
  "homes/{homeId}/ringEvents/{eventId}",
  async (event) => {
    const { homeId } = event.params;
    const data = event.data?.data();
    if (!data) {
      return;
    }

    const homeSnapshot = await admin.firestore().collection("homes").doc(homeId).get();
    if (!homeSnapshot.exists) {
      return;
    }
    const homeConfig = homeSnapshot.data();
    if (!isDoorbellAvailable(homeConfig)) {
      return;
    }

    const usersSnapshot = await admin
      .firestore()
      .collection("users")
      .where("homeId", "==", homeId)
      .get();

    const tokens = usersSnapshot.docs
      .map((doc) => doc.get("fcmToken"))
      .filter((token) => typeof token === "string" && token.length > 0);

    if (!tokens.length) {
      return;
    }

    const message = {
      notification: {
        title: "Alguien está en la puerta",
        body: `Teléfono: ${data.visitorPhone || "Visitante"}`,
      },
      tokens,
    };

    const response = await admin.messaging().sendEachForMulticast(message);
    const invalidTokens = response.responses
      .map((res, idx) => ({ res, token: tokens[idx] }))
      .filter((item) => !item.res.success)
      .map((item) => item.token);

    if (invalidTokens.length) {
      const batch = admin.firestore().batch();
      usersSnapshot.docs.forEach((doc) => {
        if (invalidTokens.includes(doc.get("fcmToken"))) {
          batch.update(doc.ref, { fcmToken: admin.firestore.FieldValue.delete() });
        }
      });
      await batch.commit();
    }
  }
);

function isDoorbellAvailable(config) {
  if (!config?.isDoorbellEnabled) return false;
  const timeZone = config.timeZone || "America/Argentina/Buenos_Aires";
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = formatter.formatToParts(now);
  const hour = Number(parts.find((p) => p.type === "hour")?.value ?? 0);
  const minute = Number(parts.find((p) => p.type === "minute")?.value ?? 0);
  const currentMinutes = hour * 60 + minute;
  const startMinutes = Number(config.scheduleStartMinutes ?? 480);
  const endMinutes = Number(config.scheduleEndMinutes ?? 1200);

  if (startMinutes <= endMinutes) {
    return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
  }
  return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
}

function normalizePhone(phone) {
  const digits = String(phone).replace(/\D/g, "");
  return digits.length >= 8 ? digits : "";
}
