import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.4/firebase-app.js";
import {
  getAuth,
  createUserWithEmailAndPassword,
  sendEmailVerification,
  signInWithEmailAndPassword,
} from "https://www.gstatic.com/firebasejs/10.12.4/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  limit,
  query,
  where,
} from "https://www.gstatic.com/firebasejs/10.12.4/firebase-firestore.js";
import {
  getFunctions,
  httpsCallable,
} from "https://www.gstatic.com/firebasejs/10.12.4/firebase-functions.js";

const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT.firebaseapp.com",
  projectId: "YOUR_PROJECT_ID",
  appId: "YOUR_APP_ID",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const functions = getFunctions(app, "southamerica-east1");
const requestDoorbell = httpsCallable(functions, "requestDoorbell");

const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("password");
const sendVerificationButton = document.getElementById("sendVerification");
const refreshVerificationButton = document.getElementById("refreshVerification");
const ringButton = document.getElementById("ringButton");
const availabilityEl = document.getElementById("availability");
const statusEl = document.getElementById("status");
const errorEl = document.getElementById("error");
const homeIdEl = document.getElementById("homeId");
const scheduleEl = document.getElementById("schedule");
const doorbellStatusEl = document.getElementById("doorbellStatus");
const emailModal = document.getElementById("emailModal");
const closeModalButton = document.getElementById("closeModal");
const downloadAppButton = document.getElementById("downloadApp");

const params = new URLSearchParams(window.location.search);
const homeIdParam = params.get("homeId");
const publicQrId = params.get("qr");
let resolvedHomeId = homeIdParam;

homeIdEl.textContent = resolvedHomeId ?? "No definido";
if (publicQrId || homeIdParam) {
  downloadAppButton?.classList.remove("hidden");
}

if (!homeIdParam && !publicQrId) {
  ringButton.disabled = true;
  statusEl.textContent = "El QR no tiene un identificador válido.";
}

let homeConfig = null;
let pressCount = Number(localStorage.getItem("timbrPressCount") ?? "0");
let isVerified = localStorage.getItem("timbrEmailVerified") === "true";
let isWithinRange = false;
let hasLocationCheck = false;
const sessionId = getSessionId();

async function loadHomeConfig() {
  const homeId = await resolveHomeId();
  if (!homeId) return;
  try {
    const homeSnapshot = await getDoc(doc(db, "homes", homeId));
    homeConfig = homeSnapshot.exists() ? homeSnapshot.data() : null;
    updateAvailability();
  } catch (error) {
    errorEl.textContent = error.message;
  }
}

function updateAvailability() {
  if (!homeConfig) {
    availabilityEl.textContent = "No encontramos el hogar. Revisá el QR.";
    ringButton.disabled = true;
    scheduleEl.textContent = "";
    doorbellStatusEl.textContent = "";
    return;
  }
  const enabled = isDoorbellAvailable(homeConfig);
  scheduleEl.textContent = buildScheduleText(homeConfig);
  doorbellStatusEl.textContent = `Estado: ${
    enabled ? "Habilitado" : "No habilitado"
  }`;
  if (!enabled) {
    availabilityEl.textContent =
      "El timbre está deshabilitado en este horario. Intentá nuevamente mañana.";
    ringButton.disabled = true;
  } else {
    if (!isWithinRange) {
      availabilityEl.textContent =
        "Para tocar, confirmá tu ubicación a menos de 100 metros.";
    } else if (pressCount > 0 && !isVerified) {
      availabilityEl.textContent =
        "Para tocar de nuevo, verificá tu email una única vez.";
    } else {
      availabilityEl.textContent = "Timbre habilitado.";
    }
    ringButton.disabled = false;
  }
}

function isDoorbellAvailable(config) {
  if (!config.isDoorbellEnabled) return false;
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

function buildScheduleText(config) {
  const startMinutes = Number(config.scheduleStartMinutes ?? 480);
  const endMinutes = Number(config.scheduleEndMinutes ?? 1200);
  const startLabel = minutesToTime(startMinutes);
  const endLabel = minutesToTime(endMinutes);
  return `Horario habilitado: ${startLabel} a ${endLabel}`;
}

function minutesToTime(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60) % 24;
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

function openEmailModal() {
  emailModal.classList.remove("hidden");
  emailModal.setAttribute("aria-hidden", "false");
}

function closeEmailModal() {
  emailModal.classList.add("hidden");
  emailModal.setAttribute("aria-hidden", "true");
}

async function sendVerification() {
  statusEl.textContent = "";
  errorEl.textContent = "";
  const email = emailInput.value.trim();
  const password = passwordInput.value;
  if (!email || !password) {
    errorEl.textContent = "Ingresá email y contraseña.";
    return;
  }

  try {
    let userCredential;
    try {
      userCredential = await signInWithEmailAndPassword(auth, email, password);
    } catch (error) {
      if (error.code === "auth/user-not-found") {
        userCredential = await createUserWithEmailAndPassword(
          auth,
          email,
          password
        );
      } else {
        throw error;
      }
    }
    if (!userCredential.user.emailVerified) {
      await sendEmailVerification(userCredential.user);
      statusEl.textContent =
        "Te enviamos un email de verificación. Revisá tu inbox.";
    } else {
      statusEl.textContent = "Email verificado. Ya podés tocar timbre.";
      isVerified = true;
      localStorage.setItem("timbrEmailVerified", "true");
      closeEmailModal();
      updateAvailability();
    }
    updateAvailability();
  } catch (error) {
    errorEl.textContent = error.message;
  }
}

async function refreshVerification() {
  statusEl.textContent = "";
  errorEl.textContent = "";
  const user = auth.currentUser;
  if (!user) {
    errorEl.textContent = "Primero ingresá con tu email.";
    return;
  }

  try {
    await user.reload();
    if (user.emailVerified) {
      statusEl.textContent = "Email verificado. Ya podés tocar timbre.";
      isVerified = true;
      localStorage.setItem("timbrEmailVerified", "true");
      closeEmailModal();
      updateAvailability();
    } else {
      errorEl.textContent =
        "Todavía no vemos la verificación. Revisá tu email.";
    }
  } catch (error) {
    errorEl.textContent = error.message ?? "No se pudo verificar el email.";
  }
}

async function ringDoorbell() {
  statusEl.textContent = "";
  errorEl.textContent = "";
  const homeId = await resolveHomeId();
  if (!homeId) {
    errorEl.textContent = "No se encontró el hogar en el QR.";
    return;
  }
  if (!homeConfig || !isDoorbellAvailable(homeConfig)) {
    availabilityEl.textContent =
      "El timbre está deshabilitado en este horario. Intentá nuevamente mañana.";
    ringButton.disabled = true;
    return;
  }
  const isAuthorized = await ensureLocationWithinRange();
  if (!isAuthorized) {
    return;
  }
  if (pressCount > 0 && !isVerified) {
    errorEl.textContent = "";
    openEmailModal();
    return;
  }

  try {
    const verifiedEmail = isVerified ? auth.currentUser?.email ?? null : null;
    const result = await requestDoorbell({
      homeId,
      phone: verifiedEmail,
      sessionId,
    });
    statusEl.textContent = result.data?.message ?? "¡Timbre enviado!";
    pressCount += 1;
    localStorage.setItem("timbrPressCount", String(pressCount));
    updateAvailability();
  } catch (error) {
    errorEl.textContent = error.message ?? "No se pudo enviar el timbre.";
  }
}

async function ensureLocationWithinRange() {
  if (hasLocationCheck && isWithinRange) {
    return true;
  }
  if (!homeConfig?.latitude || !homeConfig?.longitude) {
    errorEl.textContent =
      "No tenemos ubicación exacta del hogar para validar distancia.";
    return false;
  }
  if (!navigator.geolocation) {
    errorEl.textContent =
      "Tu navegador no permite validar la ubicación. Usá otro dispositivo.";
    return false;
  }
  statusEl.textContent = "Validando ubicación…";
  try {
    const position = await new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 15000,
        maximumAge: 0,
      });
    });
    const distance = calculateDistanceMeters(
      position.coords.latitude,
      position.coords.longitude,
      Number(homeConfig.latitude),
      Number(homeConfig.longitude)
    );
    hasLocationCheck = true;
    if (distance <= 100) {
      isWithinRange = true;
      statusEl.textContent = "Ubicación confirmada. Podés tocar el timbre.";
      updateAvailability();
      return true;
    }
    isWithinRange = false;
    errorEl.textContent = `Estás a ${Math.round(
      distance
    )}m. Acercate a menos de 100m para tocar.`;
    updateAvailability();
    return false;
  } catch (error) {
    errorEl.textContent =
      error.message ?? "No pudimos validar tu ubicación.";
    return false;
  } finally {
    statusEl.textContent = "";
  }
}

function calculateDistanceMeters(lat1, lon1, lat2, lon2) {
  const toRad = (value) => (value * Math.PI) / 180;
  const earthRadius = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) *
      Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return earthRadius * c;
}

async function resolveHomeId() {
  if (resolvedHomeId) return resolvedHomeId;
  if (!publicQrId) return null;
  try {
    const homesQuery = query(
      collection(db, "homes"),
      where("publicQrId", "==", publicQrId),
      limit(1)
    );
    const snapshot = await getDocs(homesQuery);
    const match = snapshot.docs[0];
    resolvedHomeId = match?.id ?? null;
    homeIdEl.textContent = resolvedHomeId ?? "No definido";
    if (!resolvedHomeId) {
      ringButton.disabled = true;
      statusEl.textContent = "No encontramos el hogar para este QR.";
    }
    return resolvedHomeId;
  } catch (error) {
    errorEl.textContent = error.message ?? "No se pudo validar el QR.";
    return null;
  }
}

sendVerificationButton.addEventListener("click", sendVerification);
refreshVerificationButton.addEventListener("click", refreshVerification);
ringButton.addEventListener("click", ringDoorbell);
closeModalButton.addEventListener("click", closeEmailModal);
emailModal.addEventListener("click", (event) => {
  if (event.target === emailModal) closeEmailModal();
});

loadHomeConfig();

function getSessionId() {
  const existing = localStorage.getItem("timbrSessionId");
  if (existing) return existing;
  const generated = crypto.randomUUID();
  localStorage.setItem("timbrSessionId", generated);
  return generated;
}
