import { initializeApp } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut
} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  Timestamp
} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

const LICENSE_PERIOD_DAYS = 30;
const CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L to avoid confusion
const CODE_LENGTH = 8;

const el = (id) => document.getElementById(id);

function generateCode() {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
  }
  return code;
}

function periodEndFromNow() {
  const end = new Date();
  end.setDate(end.getDate() + LICENSE_PERIOD_DAYS);
  return Timestamp.fromDate(end);
}

function showResult(elementId, message, isError) {
  const node = el(elementId);
  node.textContent = message;
  node.className = "result " + (isError ? "err" : "ok");
}

function showCodeResult(elementId, code) {
  const node = el(elementId);
  node.innerHTML = `Code created — share this with the customer:<div class="code-display">${code}</div>`;
  node.className = "result ok";
}

// --- Auth ---

onAuthStateChanged(auth, (user) => {
  el("login-view").style.display = user ? "none" : "block";
  el("app-view").style.display = user ? "block" : "none";
  if (user) el("current-user").textContent = user.email ?? "";
});

el("login-button").addEventListener("click", async () => {
  const email = el("login-email").value.trim();
  const password = el("login-password").value;
  try {
    await signInWithEmailAndPassword(auth, email, password);
  } catch (e) {
    showResult("login-result", e.message, true);
  }
});

el("signout-button").addEventListener("click", () => signOut(auth));

// --- Issue new code ---

el("issue-button").addEventListener("click", async () => {
  const note = el("issue-note").value.trim();
  const sessions = parseInt(el("issue-sessions").value, 10);
  if (!sessions || sessions < 1) {
    showResult("issue-result", "Enter a valid session count", true);
    return;
  }
  const code = generateCode();
  try {
    await setDoc(doc(db, "licenses", code), {
      sessionsAllotted: sessions,
      sessionsUsed: 0,
      periodStart: Timestamp.now(),
      periodEnd: periodEndFromNow(),
      active: true,
      customerNote: note
    });
    showCodeResult("issue-result", code);
    el("issue-note").value = "";
  } catch (e) {
    showResult("issue-result", e.message, true);
  }
});

// --- Renew existing code ---

el("renew-button").addEventListener("click", async () => {
  const code = el("renew-code").value.trim().toUpperCase();
  const sessions = parseInt(el("renew-sessions").value, 10);
  if (!code) {
    showResult("renew-result", "Enter a code", true);
    return;
  }
  if (!sessions || sessions < 1) {
    showResult("renew-result", "Enter a valid session count", true);
    return;
  }
  try {
    const ref = doc(db, "licenses", code);
    const snapshot = await getDoc(ref);
    if (!snapshot.exists()) {
      showResult("renew-result", "No license found with that code", true);
      return;
    }
    await updateDoc(ref, {
      sessionsAllotted: sessions,
      sessionsUsed: 0,
      periodStart: Timestamp.now(),
      periodEnd: periodEndFromNow(),
      active: true
    });
    showResult("renew-result", `Renewed — ${sessions} sessions, new 30-day period started`, false);
  } catch (e) {
    showResult("renew-result", e.message, true);
  }
});

// --- Deactivate code ---

el("deactivate-button").addEventListener("click", async () => {
  const code = el("deactivate-code").value.trim().toUpperCase();
  if (!code) {
    showResult("deactivate-result", "Enter a code", true);
    return;
  }
  try {
    const ref = doc(db, "licenses", code);
    const snapshot = await getDoc(ref);
    if (!snapshot.exists()) {
      showResult("deactivate-result", "No license found with that code", true);
      return;
    }
    await updateDoc(ref, { active: false });
    showResult("deactivate-result", "Deactivated", false);
  } catch (e) {
    showResult("deactivate-result", e.message, true);
  }
});
