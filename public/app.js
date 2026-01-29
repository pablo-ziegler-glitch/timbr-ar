import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.4/firebase-app.js";
import {
  getAuth,
  RecaptchaVerifier,
  signInWithPhoneNumber,
} from "https://www.gstatic.com/firebasejs/10.12.4/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
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

const phoneInput = document.getElementById("phone");
const codeInput = document.getElementById("code");
const sendCodeButton = document.getElementById("sendCode");
const verifyCodeButton = document.getElementById("verifyCode");
const ringButton = document.getElementById("ringButton");
const availabilityEl = document.getElementById("availability");
const statusEl = document.getElementById("status");
const errorEl = document.getElementById("error");
const homeIdEl = document.getElementById("homeId");

const params = new URLSearchParams(window.location.search);
const homeId = params.get("homeId");

homeIdEl.textContent = homeId ?? "No definido";

if (!homeId) {
  ringButton.disabled = true;
  statusEl.textContent = "El QR no tiene un homeId válido.";
}

let confirmationResult = null;
let homeConfig = null;
let isVerified = false;
let pressCount = Number(localStorage.getItem("timbrPressCount") ?? "0");
const sessionId = getSessionId();

const recaptchaVerifier = new RecaptchaVerifier(
  auth,
  "recaptcha-container",
  {
    size: "invisible",
  }
);

async function loadHomeConfig() {
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
    return;
  }
  const enabled = isDoorbellAvailable(homeConfig);
  if (!enabled) {
    availabilityEl.textContent =
      "El timbre está deshabilitado en este horario. Intentá nuevamente mañana.";
    ringButton.disabled = true;
  } else {
    availabilityEl.textContent = "Timbre habilitado.";
    ringButton.disabled = pressCount > 0 && !isVerified;
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

async function sendCode() {
  statusEl.textContent = "";
  errorEl.textContent = "";
  const phoneNumber = phoneInput.value.trim();
  if (!phoneNumber) {
    errorEl.textContent = "Ingresá un teléfono válido.";
    return;
  }

  try {
    confirmationResult = await signInWithPhoneNumber(
      auth,
      phoneNumber,
      recaptchaVerifier
    );
    statusEl.textContent = "Código enviado. Revisá tu SMS.";
    isVerified = false;
    updateAvailability();
  } catch (error) {
    errorEl.textContent = error.message;
  }
}

async function verifyCode() {
  statusEl.textContent = "";
  errorEl.textContent = "";
  if (!confirmationResult) {
    errorEl.textContent = "Primero enviá el código.";
    return;
  }
  const code = codeInput.value.trim();
  if (!code) {
    errorEl.textContent = "Ingresá el código recibido.";
    return;
  }

  try {
    await confirmationResult.confirm(code);
    statusEl.textContent = "Teléfono validado. Ya podés tocar timbre.";
    isVerified = true;
    updateAvailability();
  } catch (error) {
    errorEl.textContent = error.message;
  }
}

async function ringDoorbell() {
  statusEl.textContent = "";
  errorEl.textContent = "";
  if (!homeId) {
    errorEl.textContent = "No se encontró el hogar en el QR.";
    return;
  }
  if (pressCount > 0 && !isVerified) {
    errorEl.textContent = "Para volver a tocar, primero validá tu teléfono.";
    return;
  }
  if (!homeConfig || !isDoorbellAvailable(homeConfig)) {
    availabilityEl.textContent =
      "El timbre está deshabilitado en este horario. Intentá nuevamente mañana.";
    ringButton.disabled = true;
    return;
  }

  try {
    const phoneValue = isVerified ? phoneInput.value.trim() : null;
    const result = await requestDoorbell({
      homeId,
      phone: phoneValue,
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

sendCodeButton.addEventListener("click", sendCode);
verifyCodeButton.addEventListener("click", verifyCode);
ringButton.addEventListener("click", ringDoorbell);

loadHomeConfig();

function getSessionId() {
  const existing = localStorage.getItem("timbrSessionId");
  if (existing) return existing;
  const generated = crypto.randomUUID();
  localStorage.setItem("timbrSessionId", generated);
  return generated;
}
