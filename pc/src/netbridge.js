// Puente de red de la PC.
// La ventana de la app trabaja con la protección de origen del navegador ACTIVADA (webSecurity).
// Las peticiones a YouTube y GitHub las hace el proceso principal (solo a sitios permitidos)
// y aquí se convierten de vuelta en una respuesta normal de fetch.
(function () {
  const native = window.vtNative && window.vtNative.net;
  if (!native) return;
  const original = window.fetch.bind(window);
  const NULL_BODY = [101, 204, 205, 304];
  let seq = 0;
  const abortError = () => new DOMException('The operation was aborted.', 'AbortError');

  async function toBody(body) {
    if (body == null) return undefined;
    if (typeof body === 'string' || body instanceof ArrayBuffer) return body;
    if (ArrayBuffer.isView(body)) return body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength);
    if (body instanceof Blob) return body.arrayBuffer();
    if (body instanceof URLSearchParams) return body.toString();
    return String(body);
  }

  window.fetch = async function (input, init) {
    init = init || {};
    const req = input instanceof Request ? input : null;
    const url = new URL(req ? req.url : String(input), location.href);
    if (url.protocol !== 'https:') return original(input, init);
    const method = String(init.method || (req && req.method) || 'GET').toUpperCase();
    const headers = {};
    if (req) req.headers.forEach((v, k) => { headers[k] = v; });
    if (init.headers) new Headers(init.headers).forEach((v, k) => { headers[k] = v; });
    let body = init.body;
    if (body === undefined && req && method !== 'GET' && method !== 'HEAD') body = await req.clone().arrayBuffer();
    body = await toBody(body);
    const signal = init.signal || (req && req.signal);
    if (signal && signal.aborted) throw abortError();

    const id = ++seq;
    const pending = native.fetch(id, url.href, method, headers, body);
    let onAbort = null;
    const aborted = signal ? new Promise((_, reject) => {
      onAbort = () => { native.abort(id); reject(abortError()); };
      signal.addEventListener('abort', onAbort, { once: true });
    }) : null;
    try {
      const r = await (aborted ? Promise.race([pending, aborted]) : pending);
      if (!r || r.error) throw new TypeError('Failed to fetch' + (r && r.error ? ': ' + r.error : ''));
      const status = r.status >= 200 && r.status <= 599 ? r.status : 502;
      const resp = new Response(NULL_BODY.includes(status) ? null : r.body, { status, statusText: r.statusText || '', headers: r.headers });
      Object.defineProperty(resp, 'url', { value: r.url || url.href });
      return resp;
    } finally {
      if (signal && onAbort) signal.removeEventListener('abort', onAbort);
    }
  };
})();
