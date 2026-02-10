import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.4/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.4/firebase-auth.js";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  getFirestore,
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
getAuth(app);
const db = getFirestore(app);
const functions = getFunctions(app, "southamerica-east1");
const requestDoorbell = httpsCallable(functions, "requestDoorbell");

const elements = {
  ringButton: document.getElementById("ringButton"),
  availability: document.getElementById("availability"),
  status: document.getElementById("status"),
  error: document.getElementById("error"),
  homeId: document.getElementById("homeId"),
  schedule: document.getElementById("schedule"),
  doorbellStatus: document.getElementById("doorbellStatus"),
  scanQrButton: document.getElementById("scanQrButton"),
  scanStatus: document.getElementById("scanStatus"),
  scannerModal: document.getElementById("scannerModal"),
  scannerVideo: document.getElementById("scannerVideo"),
  scannerFeedback: document.getElementById("scannerFeedback"),
  closeScannerModal: document.getElementById("closeScannerModal"),
  stopScannerButton: document.getElementById("stopScannerButton"),
  unsupportedBadge: document.getElementById("nativeScannerUnsupported"),
  manualQrInput: document.getElementById("manualQrInput"),
  applyManualQrButton: document.getElementById("applyManualQrButton"),
  downloadApp: document.getElementById("downloadApp"),
};

const sessionId = getSessionId();
let homeConfig = null;
let pressCount = Number(localStorage.getItem("timbrPressCount") ?? "0");
let resolvedHomeId = null;
let currentQrToken = null;
let isWithinRange = false;
let hasLocationCheck = false;
let ringInFlight = false;

const scannerState = {
  stream: null,
  rafId: null,
  detector: null,
  canvas: document.createElement("canvas"),
  scanning: false,
  decodeLock: false,
  lastDecodedValue: null,
  lastDecodedAt: 0,
};

bootstrap();

async function bootstrap() {
  registerServiceWorker();
  bindEvents();
  evaluateScannerSupport();
  await applyInputAndRefresh(readQrInputFromUrl());
}

function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;
  window.addEventListener("load", async () => {
    try {
      await navigator.serviceWorker.register("/sw.js");
    } catch (error) {
      console.warn("SW registration failed", error);
    }
  });
}

function bindEvents() {
  elements.ringButton.addEventListener("click", ringDoorbell);
  elements.scanQrButton.addEventListener("click", openScanner);
  elements.closeScannerModal.addEventListener("click", closeScanner);
  elements.stopScannerButton.addEventListener("click", closeScanner);
  elements.scannerModal.addEventListener("click", (event) => {
    if (event.target === elements.scannerModal) closeScanner();
  });
  elements.applyManualQrButton.addEventListener("click", () => {
    applyInputAndRefresh(elements.manualQrInput.value.trim());
  });
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) stopScannerStream();
  });
  window.addEventListener("pagehide", stopScannerStream);
  window.addEventListener("beforeunload", stopScannerStream);
}

function evaluateScannerSupport() {
  const hasCamera = !!navigator.mediaDevices?.getUserMedia;
  const hasDetector = "BarcodeDetector" in window;
  if (!hasCamera || !hasDetector) {
    elements.unsupportedBadge.classList.remove("hidden");
    elements.scanStatus.textContent =
      "Escaneo nativo no disponible en este navegador.";
  }
}

function readQrInputFromUrl() {
  const params = new URLSearchParams(window.location.search);
  return params.get("homeId") || params.get("qr") || "";
}

async function applyInputAndRefresh(rawInput) {
  clearMessages();
  const parsed = parseQrInput(rawInput);
  if (!parsed.isValid) {
    updateNoQrState();
    return;
  }

  resolvedHomeId = parsed.homeId ?? null;
  currentQrToken = parsed.qr ?? null;
  syncQueryParams();

  elements.homeId.textContent = resolvedHomeId ?? "Resolviendo…";
  elements.downloadApp.classList.remove("hidden");

  await loadHomeConfig();
  if (!homeConfig) {
    elements.error.textContent = "No encontramos el hogar para ese QR.";
  }
}

function parseQrInput(rawInput) {
  const value = (rawInput ?? "").trim();
  if (!value) {
    return { isValid: false };
  }

  try {
    const url = new URL(value, window.location.origin);
    const homeIdFromUrl = url.searchParams.get("homeId");
    const qrFromUrl = url.searchParams.get("qr");
    if (homeIdFromUrl || qrFromUrl) {
      return {
        isValid: true,
        homeId: homeIdFromUrl,
        qr: qrFromUrl,
        raw: value,
      };
    }
    const normalizedPath = url.pathname.replace(/^\/+/, "");
    if (normalizedPath && !normalizedPath.includes(".")) {
      return { isValid: true, qr: normalizedPath, raw: value };
    }
  } catch {
    // Non-URL values are treated as plain QR tokens.
  }

  if (/^[\w-]{4,128}$/.test(value)) {
    return { isValid: true, qr: value, raw: value };
  }

  return { isValid: false };
}

function syncQueryParams() {
  const url = new URL(window.location.href);
  if (resolvedHomeId) {
    url.searchParams.set("homeId", resolvedHomeId);
    url.searchParams.delete("qr");
  } else if (currentQrToken) {
    url.searchParams.set("qr", currentQrToken);
    url.searchParams.delete("homeId");
  }
  window.history.replaceState({}, "", url);
}

async function loadHomeConfig() {
  const homeId = await resolveHomeId();
  if (!homeId) {
    homeConfig = null;
    updateAvailability();
    return;
  }
  try {
    const homeSnapshot = await getDoc(doc(db, "homes", homeId));
    homeConfig = homeSnapshot.exists() ? homeSnapshot.data() : null;
    elements.homeId.textContent = homeId;
    updateAvailability();
  } catch (error) {
    homeConfig = null;
    elements.error.textContent = error.message ?? "Error cargando el hogar.";
    updateAvailability();
  }
}

async function resolveHomeId() {
  if (resolvedHomeId) return resolvedHomeId;
  if (!currentQrToken) return null;

  const homesQuery = query(
    collection(db, "homes"),
    where("publicQrId", "==", currentQrToken),
    limit(1)
  );
  const snapshot = await getDocs(homesQuery);
  const match = snapshot.docs[0];
  resolvedHomeId = match?.id ?? null;
  syncQueryParams();
  return resolvedHomeId;
}

function updateAvailability() {
  if (!homeConfig) {
    elements.availability.textContent = "Escaneá un QR válido para continuar.";
    elements.schedule.textContent = "";
    elements.doorbellStatus.textContent = "";
    elements.ringButton.disabled = true;
    return;
  }

  const enabled = isDoorbellAvailable(homeConfig);
  elements.schedule.textContent = buildScheduleText(homeConfig);
  elements.doorbellStatus.textContent = `Estado: ${enabled ? "Habilitado" : "Fuera de horario"}`;
  if (!enabled) {
    elements.availability.textContent = "El timbre está fuera de horario.";
    elements.ringButton.disabled = true;
    return;
  }

  if (!isWithinRange) {
    elements.availability.textContent = "Confirmá ubicación a menos de 100m.";
  } else {
    elements.availability.textContent = "Timbre habilitado.";
  }
  elements.ringButton.disabled = false;
}

function isDoorbellAvailable(config) {
  if (!config.isDoorbellEnabled) return false;
  const zone = config.timeZone || "America/Argentina/Buenos_Aires";
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date());

  const hour = Number(parts.find((part) => part.type === "hour")?.value ?? 0);
  const minute = Number(parts.find((part) => part.type === "minute")?.value ?? 0);
  const currentMinutes = hour * 60 + minute;

  const startMinutes = Number(config.scheduleStartMinutes ?? 480);
  const endMinutes = Number(config.scheduleEndMinutes ?? 1200);
  if (startMinutes <= endMinutes) {
    return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
  }
  return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
}

function buildScheduleText(config) {
  const from = minutesToTime(Number(config.scheduleStartMinutes ?? 480));
  const to = minutesToTime(Number(config.scheduleEndMinutes ?? 1200));
  return `Horario habilitado: ${from} a ${to}`;
}

function minutesToTime(totalMinutes) {
  const hours = Math.floor(totalMinutes / 60) % 24;
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

async function ringDoorbell() {
  clearMessages();
  if (ringInFlight) return;
  ringInFlight = true;

  try {
    const homeId = await resolveHomeId();
    if (!homeId || !homeConfig) {
      elements.error.textContent = "No hay un hogar válido para tocar timbre.";
      return;
    }

    if (!isDoorbellAvailable(homeConfig)) {
      updateAvailability();
      return;
    }

    const isAuthorized = await ensureLocationWithinRange();
    if (!isAuthorized) return;

    const result = await requestDoorbell({
      homeId,
      phone: null,
      sessionId,
    });

    pressCount += 1;
    localStorage.setItem("timbrPressCount", String(pressCount));
    elements.status.textContent = result.data?.message ?? "¡Timbre enviado!";
  } catch (error) {
    elements.error.textContent = error.message ?? "No se pudo enviar el timbre.";
  } finally {
    ringInFlight = false;
  }
}

async function ensureLocationWithinRange() {
  if (hasLocationCheck && isWithinRange) return true;
  if (!homeConfig?.latitude || !homeConfig?.longitude) {
    elements.error.textContent = "No hay coordenadas del hogar para validar distancia.";
    return false;
  }
  if (!navigator.geolocation) {
    elements.error.textContent = "Este navegador no soporta geolocalización.";
    return false;
  }

  elements.status.textContent = "Validando ubicación…";
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
    isWithinRange = distance <= 100;
    if (!isWithinRange) {
      elements.error.textContent = `Estás a ${Math.round(distance)}m. Acercate a menos de 100m.`;
      updateAvailability();
      return false;
    }

    updateAvailability();
    return true;
  } catch (error) {
    elements.error.textContent = error.message ?? "No se pudo validar la ubicación.";
    return false;
  } finally {
    elements.status.textContent = "";
  }
}

function calculateDistanceMeters(lat1, lon1, lat2, lon2) {
  const toRad = (value) => (value * Math.PI) / 180;
  const earthRadius = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return earthRadius * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

async function openScanner() {
  clearMessages();
  if (!navigator.mediaDevices?.getUserMedia || !("BarcodeDetector" in window)) {
    elements.unsupportedBadge.classList.remove("hidden");
    elements.scannerFeedback.textContent =
      "Tu navegador no soporta escaneo QR nativo. Pegá el código manualmente.";
    return;
  }

  try {
    elements.scannerModal.classList.remove("hidden");
    elements.scannerModal.setAttribute("aria-hidden", "false");
    elements.scannerFeedback.textContent = "Solicitando cámara…";

    scannerState.stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: "environment" } },
      audio: false,
    });
    elements.scannerVideo.srcObject = scannerState.stream;
    await elements.scannerVideo.play();

    scannerState.detector = new BarcodeDetector({ formats: ["qr_code"] });
    scannerState.scanning = true;
    elements.scannerFeedback.textContent = "Apuntá al QR para escanear.";
    scanFrame();
  } catch (error) {
    elements.scannerFeedback.textContent =
      error.message ?? "No se pudo iniciar la cámara.";
    stopScannerStream();
  }
}

function closeScanner() {
  stopScannerStream();
  elements.scannerModal.classList.add("hidden");
  elements.scannerModal.setAttribute("aria-hidden", "true");
}

function stopScannerStream() {
  scannerState.scanning = false;
  scannerState.decodeLock = false;
  if (scannerState.rafId) {
    cancelAnimationFrame(scannerState.rafId);
    scannerState.rafId = null;
  }
  if (scannerState.stream) {
    scannerState.stream.getTracks().forEach((track) => track.stop());
    scannerState.stream = null;
  }
  if (elements.scannerVideo.srcObject) {
    elements.scannerVideo.srcObject = null;
  }
}

async function scanFrame() {
  if (!scannerState.scanning || scannerState.decodeLock) {
    scannerState.rafId = requestAnimationFrame(scanFrame);
    return;
  }

  const video = elements.scannerVideo;
  if (!video.videoWidth || !video.videoHeight) {
    scannerState.rafId = requestAnimationFrame(scanFrame);
    return;
  }

  scannerState.decodeLock = true;
  try {
    const canvas = scannerState.canvas;
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const barcodes = await scannerState.detector.detect(canvas);
    const value = barcodes[0]?.rawValue?.trim();
    if (value) {
      const now = Date.now();
      const isDuplicate =
        value === scannerState.lastDecodedValue &&
        now - scannerState.lastDecodedAt < 1500;
      if (!isDuplicate) {
        scannerState.lastDecodedValue = value;
        scannerState.lastDecodedAt = now;
        elements.scannerFeedback.textContent = "QR detectado. Validando…";
        await applyInputAndRefresh(value);
        closeScanner();
        return;
      }
    }
  } catch (error) {
    elements.scannerFeedback.textContent = error.message ?? "Error escaneando QR.";
  } finally {
    scannerState.decodeLock = false;
  }

  scannerState.rafId = requestAnimationFrame(scanFrame);
}

function updateNoQrState() {
  elements.homeId.textContent = "No definido";
  elements.availability.textContent = "Escaneá un QR válido para continuar.";
  elements.ringButton.disabled = true;
}

function clearMessages() {
  elements.error.textContent = "";
  elements.status.textContent = "";
}

function getSessionId() {
  const existing = localStorage.getItem("timbrSessionId");
  if (existing) return existing;
  const generated = crypto.randomUUID();
  localStorage.setItem("timbrSessionId", generated);
  return generated;
}
