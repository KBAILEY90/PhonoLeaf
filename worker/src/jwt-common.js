// Minimal base64url + compact-JWT helpers shared by Google ID-token
// verification (google-auth.js) and our own entitlement JWT
// (entitlement-jwt.js). No npm dependency on purpose — see the worker
// README's "Zero runtime dependencies" note.

export function base64UrlToBytes(b64url) {
  const pad = (4 - (b64url.length % 4)) % 4;
  const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat(pad);
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

export function bytesToBase64Url(bytes) {
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function base64UrlToJson(b64url) {
  return JSON.parse(new TextDecoder().decode(base64UrlToBytes(b64url)));
}

export function jsonToBase64Url(obj) {
  return bytesToBase64Url(new TextEncoder().encode(JSON.stringify(obj)));
}

/** Splits a compact JWT into its parts. Does NOT verify the signature. */
export function splitJwt(token) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('malformed JWT');
  const [headerB64, payloadB64, sigB64] = parts;
  return {
    header: base64UrlToJson(headerB64),
    payload: base64UrlToJson(payloadB64),
    signingInput: `${headerB64}.${payloadB64}`,
    signature: base64UrlToBytes(sigB64),
  };
}
