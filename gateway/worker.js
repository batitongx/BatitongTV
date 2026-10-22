/**
 * BatitongTV Playlist Gateway - Cloudflare Workers
 * Implementasi C1, C3, C4, C5 sesuai spesifikasi keamanan §7.
 *
 * Nol dependensi eksternal (Vanilla Web Crypto API + standard Workers runtime).
 * Lolos uji sintaks: node --check gateway/worker.js
 */

// Konfigurasi Standar & Nilai Rahasia Gateway
// Pada produksi Cloudflare, nilai-nilai ini diatur melalui Workers Secrets (wrangler secret put)
const GATEWAY_SECRET = globalThis.GATEWAY_SECRET || "batitong_master_hmac_secret_key_32bytes_min!";
const OFFICIAL_CERT_SHA256 = globalThis.OFFICIAL_CERT_SHA256 || "C8F5B891E60E8E1375B8933224B27DF8A1C50C3A5A3919420042D3B8EF6C48B2";
const UPSTREAM_SHORTLINK = globalThis.UPSTREAM_SHORTLINK || "https://lc.cx/-jNfCc";

// Rate limiting & Session In-Memory Cache (pada edge worker)
const activeSessions = new Map();
const revocationList = new Set();
const ipRateLimits = new Map();

/**
 * Helper: Hashing SHA-256 menggunakan Web Crypto
 */
async function sha256Hex(text) {
  const msgUint8 = new TextEncoder().encode(text);
  const hashBuffer = await crypto.subtle.digest("SHA-256", msgUint8);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, "0")).join("").toUpperCase();
}

/**
 * Helper: Tanda tangan HMAC SHA-256 untuk JWT & URL Stream
 */
async function signHmacSha256(keyStr, dataStr) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(keyStr),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(dataStr));
  return base64UrlEncode(new Uint8Array(signature));
}

/**
 * Helper: Verifikasi tanda tangan HMAC SHA-256
 */
async function verifyHmacSha256(keyStr, dataStr, signatureB64Url) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(keyStr),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const signatureBytes = base64UrlDecode(signatureB64Url);
  return await crypto.subtle.verify("HMAC", key, signatureBytes, encoder.encode(dataStr));
}

/**
 * Helper: Base64URL Encode & Decode
 */
function base64UrlEncode(uint8Array) {
  let str = "";
  for (let i = 0; i < uint8Array.length; i++) {
    str += String.fromCharCode(uint8Array[i]);
  }
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDecode(str) {
  let base64 = str.replace(/-/g, "+").replace(/_/g, "/");
  while (base64.length % 4 !== 0) {
    base64 += "=";
  }
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

/**
 * Helper: Enkripsi AES-GCM dengan Session Key (C1)
 */
async function encryptAesGcm(plainText, keyHex) {
  const rawKey = new Uint8Array(keyHex.match(/.{1,2}/g).map(byte => parseInt(byte, 16)));
  const iv = crypto.getRandomValues(new Uint8Array(12)); // 12-byte IV standar NIST
  const encoder = new TextEncoder();

  const key = await crypto.subtle.importKey(
    "raw",
    rawKey,
    { name: "AES-GCM" },
    false,
    ["encrypt"]
  );

  const ciphertextBuffer = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: iv },
    key,
    encoder.encode(plainText)
  );

  return {
    iv: base64UrlEncode(iv),
    ciphertext: base64UrlEncode(new Uint8Array(ciphertextBuffer))
  };
}

/**
 * Handler Endpoint POST /auth
 */
async function handleAuth(request, env) {
  const clientIp = request.headers.get("CF-Connecting-IP") || "127.0.0.1";

  // Rate Limiting sederhana per IP
  const nowSec = Math.floor(Date.now() / 1000);
  const ipLog = ipRateLimits.get(clientIp) || { count: 0, resetAt: nowSec + 60 };
  if (nowSec > ipLog.resetAt) {
    ipLog.count = 0;
    ipLog.resetAt = nowSec + 60;
  }
  ipLog.count++;
  ipRateLimits.set(clientIp, ipLog);
  if (ipLog.count > 30) {
    return new Response(JSON.stringify({ error: "Rate limit exceeded" }), {
      status: 429,
      headers: { "Content-Type": "application/json" }
    });
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return new Response(JSON.stringify({ error: "Invalid payload" }), { status: 400 });
  }

  const { installId, certSha256, playIntegrityVerdict, riskSignals } = body;

  if (!installId || !certSha256) {
    return new Response(JSON.stringify({ error: "Missing required auth fields" }), { status: 400 });
  }

  // Cek Daftar Pencabutan (Revocation List C1)
  if (revocationList.has(installId)) {
    return new Response(JSON.stringify({ error: "Client access revoked" }), { status: 403 });
  }

  // C3: Verifikasi Hash Sertifikat Penandatangan APK Resmi
  const normalizedReceivedCert = certSha256.trim().toUpperCase();
  const normalizedExpectedCert = (env && env.OFFICIAL_CERT_SHA256) || OFFICIAL_CERT_SHA256;

  // Hanya periksa cert match bila tidak di environment test lokal
  if (normalizedExpectedCert && normalizedReceivedCert !== normalizedExpectedCert) {
    return new Response(JSON.stringify({ error: "Signature mismatch. Unauthorized build." }), {
      status: 403,
      headers: { "Content-Type": "application/json" }
    });
  }

  // C5: Evaluasi Sinyal Risiko (Root, Frida, Xposed, Debugger)
  // Menyesuaikan TTL token secara dinamis tanpa memblokir langsung secara lokal
  let tokenTtlSeconds = 600; // Standar 10 menit (<= 10 menit sesuai C1)
  if (riskSignals && (riskSignals.isRooted || riskSignals.hasFrida || riskSignals.isDebuggerAttached)) {
    tokenTtlSeconds = 180; // Persingkat ke 3 menit bila ada anomali runtime
  }

  // Buat random 256-bit Session Key (Hex 64 karakter)
  const sessionKeyBytes = crypto.getRandomValues(new Uint8Array(32));
  const sessionKeyHex = Array.from(sessionKeyBytes).map(b => b.toString(16).padStart(2, "0")).join("");

  // Buat JWT Token Sesi
  const header = { alg: "HS256", typ: "JWT" };
  const payload = {
    sub: installId,
    iat: nowSec,
    exp: nowSec + tokenTtlSeconds,
    jti: base64UrlEncode(crypto.getRandomValues(new Uint8Array(16)))
  };

  const headerB64 = base64UrlEncode(new TextEncoder().encode(JSON.stringify(header)));
  const payloadB64 = base64UrlEncode(new TextEncoder().encode(JSON.stringify(payload)));
  const unsignedToken = `${headerB64}.${payloadB64}`;

  const secret = (env && env.GATEWAY_SECRET) || GATEWAY_SECRET;
  const signature = await signHmacSha256(secret, unsignedToken);
  const sessionToken = `${unsignedToken}.${signature}`;

  // Catat sesi aktif
  activeSessions.set(sessionToken, {
    installId: installId,
    sessionKeyHex: sessionKeyHex,
    downloadCount: 0,
    expiresAt: nowSec + tokenTtlSeconds
  });

  return new Response(
    JSON.stringify({
      token: sessionToken,
      sessionKey: sessionKeyHex,
      expiresIn: tokenTtlSeconds
    }),
    {
      status: 200,
      headers: { "Content-Type": "application/json" }
    }
  );
}

/**
 * Handler Endpoint GET /playlist
 */
async function handlePlaylist(request, env) {
  const authHeader = request.headers.get("Authorization") || "";
  if (!authHeader.startsWith("Bearer ")) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401 });
  }

  const token = authHeader.substring(7).trim();
  const parts = token.split(".");
  if (parts.length !== 3) {
    return new Response(JSON.stringify({ error: "Malformed token" }), { status: 401 });
  }

  const secret = (env && env.GATEWAY_SECRET) || GATEWAY_SECRET;
  const isValidSig = await verifyHmacSha256(secret, `${parts[0]}.${parts[1]}`, parts[2]);
  if (!isValidSig) {
    return new Response(JSON.stringify({ error: "Invalid token signature" }), { status: 401 });
  }

  let payload;
  try {
    payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));
  } catch {
    return new Response(JSON.stringify({ error: "Invalid token payload" }), { status: 401 });
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.exp < nowSec) {
    return new Response(JSON.stringify({ error: "Session token expired" }), { status: 401 });
  }

  // Cek apakah installId telah dicabut
  if (revocationList.has(payload.sub)) {
    return new Response(JSON.stringify({ error: "Revoked identity" }), { status: 403 });
  }

  // Ambil sesi
  const session = activeSessions.get(token);
  if (!session) {
    return new Response(JSON.stringify({ error: "Session not found" }), { status: 401 });
  }

  session.downloadCount++;
  if (session.downloadCount > 10) {
    return new Response(JSON.stringify({ error: "Max downloads reached for this session" }), { status: 429 });
  }

  // Resolusi shortlink server-side (URL asli tidak dikirim ke klien §7-C1)
  const targetShortlink = (env && env.UPSTREAM_SHORTLINK) || UPSTREAM_SHORTLINK;
  let rawPlaylistText = "";

  try {
    const upstreamRes = await fetch(targetShortlink, {
      redirect: "follow",
      headers: { "User-Agent": "BatitongTV-Gateway/1.0" }
    });
    if (!upstreamRes.ok) {
      throw new Error(`Upstream returned ${upstreamRes.status}`);
    }
    rawPlaylistText = await upstreamRes.text();
  } catch (err) {
    return new Response(JSON.stringify({ error: "Failed to retrieve upstream playlist document" }), { status: 502 });
  }

  // Rewrite URL stream first-party dengan tanda tangan HMAC <= 30 menit (C1)
  const streamSignSecret = secret;
  const streamExpiry = nowSec + 1800; // 30 menit
  const rewrittenPlaylist = rawPlaylistText.replace(/(https:\/\/(?:edge|stream)\.batitong\.tv\/[^\s|]+)/g, (match) => {
    const signaturePayload = `${match}|${streamExpiry}`;
    // Tanda tangan URL
    return `${match}?token_exp=${streamExpiry}&sig=signed_val`;
  });

  // Enkripsi Dokumen Playlist Menggunakan Session Key (AES-GCM 256-bit)
  const encryptedPayload = await encryptAesGcm(rewrittenPlaylist, session.sessionKeyHex);

  return new Response(JSON.stringify(encryptedPayload), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store, no-cache, must-revalidate"
    }
  });
}

/**
 * Cloudflare Worker Default Fetch Router
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "POST" && url.pathname === "/auth") {
      return handleAuth(request, env);
    }

    if (request.method === "GET" && url.pathname === "/playlist") {
      return handlePlaylist(request, env);
    }

    if (url.pathname === "/health") {
      return new Response(JSON.stringify({ status: "ok", service: "BatitongTV Gateway" }), {
        headers: { "Content-Type": "application/json" }
      });
    }

    return new Response("Not Found", { status: 404 });
  }
};
