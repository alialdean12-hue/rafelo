// Runs at the start of every shein.com page inside the app.
// It only looks at two kinds of requests and leaves every other request untouched:
//  - the shared-cart request, to read the cart's products;
//  - affiliate requests, to reuse their headers when adding products to "اختياراتي".
// Learning build only: while the user turns recording on, other site requests are
// copied (with secrets removed) so the user can paste them to the developer.
(function () {
  if (window.__picksHook) return;
  window.__picksHook = true;
  var B = window.PicksBridge;
  if (!B) return;

  var CART = /cart\/share\/landing/;
  var AFF_API = /\/affiliate\/api\//;
  var PICK_URL = /\/affiliate\/api\/pick\/update/;
  var affHeaders = null; // kept in this page only

  // ----- temporary recording (learning build) -----
  var rec = false;
  try { rec = !!B.isRecording(); } catch (e) {}
  window.__picksSetRec = function (v) { rec = !!v; };
  var NOISE = /abt|multi_language|srmdata|\/configs?\/|i18n|privacy|cookie_banner|userinfo_ugid|apolloConfig|zpnv|metric|journeyBranch|\/devices\/|\/risk\/|csrf|mapping_config|sample_config|cardConfig|pop_batch|strategy-center|assist_mark|update_country|userInfoManager|\.(js|css|png|jpe?g|webp|gif|svg|woff2?)(\?|$)/;
  var SECRET_KEY = /token|cookie|auth|sign|csrf|armor|session|passw|pwd|secret|smdata|device|email|phone|mobile|oest|anti-in|cs-random|ad-flag|gw-auth|nickname|member_id/i;
  function redactHeaders(h) {
    var o = {};
    for (var k in h) o[k] = SECRET_KEY.test(k) ? '[محذوف]' : String(h[k]).slice(0, 200);
    return o;
  }
  function redactText(t) {
    if (!t) return '';
    t = String(t);
    t = t.replace(/"([^"]*)"\s*:\s*"([^"]*)"/g, function (all, k) { return SECRET_KEY.test(k) ? '"' + k + '":"[محذوف]"' : all; });
    t = t.replace(/(^|[&?])([^=&?]+)=([^&]*)/g, function (all, sep, k) { return SECRET_KEY.test(k) ? sep + k + '=[محذوف]' : all; });
    return t.replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, '[بريد محذوف]');
  }
  function bodyText(b) {
    if (b == null) return '';
    if (typeof b === 'string') return b;
    try { if (b instanceof URLSearchParams) return b.toString(); } catch (e) {}
    return '[non-text]';
  }
  function record(m, u, h, b, status, resp) {
    try {
      if (!rec || NOISE.test(u) || !/shein\.com\//.test(u)) return;
      B.log(JSON.stringify({ m: m, u: redactText(u).slice(0, 600), s: status, h: redactHeaders(h || {}),
        b: redactText(bodyText(b)).slice(0, 3000), r: redactText(resp).slice(0, 6000) }));
    } catch (e) {}
  }

  function remember(u, h) {
    if (AFF_API.test(u) && h && (h.token || h.armortoken || h.armorToken)) affHeaders = h;
  }

  function extractProducts(o, out, seen) {
    if (!o || typeof o !== 'object') return;
    if (Array.isArray(o)) { for (var i = 0; i < o.length; i++) extractProducts(o[i], out, seen); return; }
    var id = o.goods_id || o.goodsId;
    var name = o.goods_name || o.goodsName;
    if (id && name && !seen[id]) {
      seen[id] = 1;
      out.push({ id: String(id), name: String(name), sn: String(o.goods_sn || o.goodsSn || '') });
    }
    for (var k in o) if (o.hasOwnProperty(k) && o[k] && typeof o[k] === 'object') extractProducts(o[k], out, seen);
  }

  function onCart(text) {
    var list = [];
    try { extractProducts(JSON.parse(text), list, {}); } catch (e) {}
    B.cartData(JSON.stringify(list));
  }

  // Fallback: adds the given products to the request the site sends when "اختار" is tapped.
  function rewritePick(u, body) {
    var add = window.__picksRewrite;
    if (!add || !PICK_URL.test(u) || typeof body !== 'string') return null;
    try {
      var o = JSON.parse(body);
      if (o.pickType != 1 || !Array.isArray(o.goodsIds)) return null;
      o.skcNames = o.skcNames || [];
      add.forEach(function (i) {
        var id = Number(i.id);
        if (o.goodsIds.indexOf(id) < 0) { o.goodsIds.push(id); o.skcNames.push(i.sn); }
      });
      window.__picksRewrite = null;
      return JSON.stringify(o);
    } catch (e) { return null; }
  }

  // Called by the app: adds the given products, reusing the headers the site just sent.
  window.__picksPick = function (items) {
    if (!affHeaders) { B.pickResult(JSON.stringify({ mode: 'direct', ok: false, reason: 'no-headers' })); return; }
    var lang = (location.pathname.split('/')[1] || 'ar');
    var x = new XMLHttpRequest();
    x.open('POST', '/' + lang + '/affiliate/api/pick/update');
    for (var k in affHeaders) {
      if (/^(content-type|content-length|cookie|host)$/i.test(k)) continue;
      try { x.setRequestHeader(k, affHeaders[k]); } catch (e) {}
    }
    x.setRequestHeader('content-type', 'application/json; charset=utf-8');
    x.__picksOwn = true;
    x.addEventListener('loadend', function () {
      B.pickResult(JSON.stringify({ mode: 'direct', ok: x.status === 200, status: x.status, resp: String(x.responseText || '').slice(0, 4000) }));
    });
    x.send(JSON.stringify({
      goodsIds: items.map(function (i) { return Number(i.id); }),
      skcNames: items.map(function (i) { return i.sn; }),
      pickType: 1,
      sourceType: 1
    }));
  };

  function absUrl(u) { try { return new URL(u, location.href).href; } catch (e) { return String(u); } }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var u = absUrl(typeof input === 'string' ? input : (input && input.url) || String(input));
      // Fast path: most requests are not ours to look at.
      if (!rec && !CART.test(u) && !AFF_API.test(u)) return origFetch.apply(this, arguments);
      init = init || {};
      var m = String(init.method || (input && input.method) || 'GET').toUpperCase();
      var recNow = rec;
      var h = {};
      try { new Headers(init.headers || (input && input.headers) || {}).forEach(function (v, k) { h[k] = v; }); } catch (e) {}
      remember(u, h);
      var args = arguments;
      var nb = rewritePick(u, init.body);
      if (nb !== null) args = [input, Object.assign({}, init, { body: nb })];
      return origFetch.apply(this, args).then(function (res) {
        if (CART.test(u) || nb !== null || recNow) {
          res.clone().text().then(function (t) {
            if (recNow) record(m, u, h, nb !== null ? nb : init.body, res.status, t);
            if (CART.test(u)) onCart(t);
            if (nb !== null) B.pickResult(JSON.stringify({ mode: 'rewrite', ok: res.status === 200, status: res.status, resp: String(t).slice(0, 4000) }));
          }).catch(function () {});
        }
        return res;
      });
    };
  }

  var XP = XMLHttpRequest.prototype;
  var xOpen = XP.open, xSend = XP.send, xHeader = XP.setRequestHeader;
  XP.open = function (m, u) {
    var abs = absUrl(u);
    this.__picks = (rec || CART.test(abs) || AFF_API.test(abs)) ? { m: String(m).toUpperCase(), u: abs, h: {} } : null;
    return xOpen.apply(this, arguments);
  };
  XP.setRequestHeader = function (k, v) {
    if (this.__picks) this.__picks.h[k] = v;
    return xHeader.apply(this, arguments);
  };
  XP.send = function (body) {
    var x = this, p = x.__picks;
    if (!p || x.__picksOwn) return xSend.apply(this, arguments);
    remember(p.u, p.h);
    var args = arguments;
    var nb = rewritePick(p.u, body);
    if (nb !== null) args = [nb];
    var recNow = rec, sentBody = nb !== null ? nb : body;
    if (CART.test(p.u) || nb !== null || recNow) {
      x.addEventListener('loadend', function () {
        var t = '';
        try {
          if (x.responseType === '' || x.responseType === 'text') t = x.responseText;
          else if (x.responseType === 'json') t = JSON.stringify(x.response);
        } catch (e) {}
        if (recNow) record(p.m, p.u, p.h, sentBody, x.status, t);
        if (CART.test(p.u)) onCart(t);
        if (nb !== null) B.pickResult(JSON.stringify({ mode: 'rewrite', ok: x.status === 200, status: x.status, resp: String(t).slice(0, 4000) }));
      });
    }
    return xSend.apply(this, args);
  };
})();
