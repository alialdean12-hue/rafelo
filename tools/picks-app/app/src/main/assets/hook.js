// Runs at the start of every shein.com page inside the app.
// It only looks at two kinds of requests and leaves every other request untouched:
//  - the shared-cart request, to read the cart's products;
//  - affiliate requests, to reuse their headers when adding products to "اختياراتي".
// Nothing is recorded or sent anywhere else.
(function () {
  if (window.__picksHook) return;
  window.__picksHook = true;
  var B = window.PicksBridge;
  if (!B) return;

  var CART = /cart\/share\/landing/;
  var AFF_API = /\/affiliate\/api\//;
  var PICK_URL = /\/affiliate\/api\/pick\/update/;
  var affHeaders = null; // kept in this page only

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
      if (!CART.test(u) && !AFF_API.test(u)) return origFetch.apply(this, arguments);
      init = init || {};
      var h = {};
      try { new Headers(init.headers || (input && input.headers) || {}).forEach(function (v, k) { h[k] = v; }); } catch (e) {}
      remember(u, h);
      var args = arguments;
      var nb = rewritePick(u, init.body);
      if (nb !== null) args = [input, Object.assign({}, init, { body: nb })];
      return origFetch.apply(this, args).then(function (res) {
        if (CART.test(u) || nb !== null) {
          res.clone().text().then(function (t) {
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
    this.__picks = (CART.test(abs) || AFF_API.test(abs)) ? { u: abs, h: {} } : null;
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
    if (CART.test(p.u) || nb !== null) {
      x.addEventListener('loadend', function () {
        var t = '';
        try {
          if (x.responseType === '' || x.responseType === 'text') t = x.responseText;
          else if (x.responseType === 'json') t = JSON.stringify(x.response);
        } catch (e) {}
        if (CART.test(p.u)) onCart(t);
        if (nb !== null) B.pickResult(JSON.stringify({ mode: 'rewrite', ok: x.status === 200, status: x.status, resp: String(t).slice(0, 4000) }));
      });
    }
    return xSend.apply(this, args);
  };
})();
