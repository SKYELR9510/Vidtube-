// Se ejecuta en una ventana oculta con dirección de youtube.com (BotGuard revisa el origen).
// Genera el token "no soy un bot" (PO Token) que YouTube pide para entregar el video completo.
import { getChallenge, BotGuardClient } from 'bgutils-js/botguard';
import { WebPoMinter } from 'bgutils-js/webpo';

const REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';
const GOOG_KEY = 'AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw';
let minterPromise = null;
let expiresAt = 0;

async function createMinter() {
  const challenge = await getChallenge({ requestKey: REQUEST_KEY, fetchFunction: (u, o) => fetch(u, o), useYouTubeAPI: true });
  const js = challenge.interpreterJavascript?.privateDoNotAccessOrElseSafeScriptWrappedValue;
  if (js) new Function(js)();
  else if (challenge.interpreterUrl?.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue) {
    const url = challenge.interpreterUrl.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue;
    new Function(await (await fetch(url.startsWith('//') ? 'https:' + url : url)).text())();
  }
  const bg = await BotGuardClient.create({ program: challenge.program, globalName: challenge.globalName, globalObject: window });
  const webPoSignalOutput = [];
  const botguardResponse = await bg.snapshot({ webPoSignalOutput });
  const r = await fetch('https://www.youtube.com/api/jnn/v1/GenerateIT', {
    method: 'POST',
    headers: { 'content-type': 'application/json+protobuf', 'x-goog-api-key': GOOG_KEY, 'x-user-agent': 'grpc-web-javascript/0.1' },
    body: JSON.stringify([REQUEST_KEY, botguardResponse]),
  });
  const it = await r.json();
  if (!it?.[0]) throw new Error('YouTube no entregó token de integridad');
  expiresAt = Date.now() + Math.max(60, (it[1] || 3600) - 120) * 1000;
  return WebPoMinter.create({ integrityToken: it[0], estimatedTtlSecs: it[1], mintRefreshThreshold: it[2], websafeFallbackToken: it[3] }, webPoSignalOutput);
}

window.__vtMint = async (identifier) => {
  if (!minterPromise || Date.now() > expiresAt) minterPromise = createMinter().catch((e) => { minterPromise = null; throw e; });
  const minter = await minterPromise;
  return minter.mintAsWebsafeString(identifier);
};
