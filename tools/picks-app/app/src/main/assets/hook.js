// Runs at the start of every shein.com page inside the app.
// It watches the site's own API calls so the app can:
//  1. read the products of a shared cart, and
//  2. record what the "اختار" button sends, so the next version can repeat it.
// Header values and body fields that look like secrets are replaced before anything leaves the page.
(function () {
  if (window.__picksHook) return;
  window.__picksHook = true;
  var B = window.PicksBridge;
  if (!B) return;

  var NOISE = /abt|multi_language|srmdata|\/configs?\/|i18n|privacy|cookie_banner|userinfo_ugid|apolloConfig|zpnv|metric|journeyBranch|\/ccc\/|\/devices\/|\/risk\/|csrf|mapping_config|sample_config|cardConfig|tab_exclusive|pop_batch|strategy-center|assist_mark|update_country/;
  var SECRET_KEY = /token|cookie|auth|sign|csrf|armor|session|passw|pwd|secret|smdata|device|email|phone|mobile/i;

  function redactHeaders(h) {
    var o = {};
    for (var k in h) o[k] = SECRET_KEY.test(k) ? '[محذوف]' : String(h[k]).slice(0, 200);
    return o;
  }

  // Last headers the affiliate pages sent. Kept in this page only, never passed to the app.
  var AFF_API = /\/affiliate\/api\//;
  var PICK_URL = /\/affiliate\/api\/pick\/update/;
  var affHeaders = null;
  function remember(u, h) {
    if (AFF_API.test(u) && h && (h.token || h.armortoken || h.armorToken)) affHeaders = h;
  }

  // Adds the given products to the request the site is about to send when "اختار" is tapped.
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

  // Called by the app: picks the given products directly, reusing the headers the site just sent.
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
    x.__picksDirect = true;
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

  function redactText(s) {
    if (!s) return '';
    s = String(s);
    // JSON-style  "someToken":"value"
    s = s.replace(/"([^"]*)"\s*:\s*"([^"]*)"/g, function (all, k) {
      return SECRET_KEY.test(k) ? '"' + k + '":"[محذوف]"' : all;
    });
    // form-style  someToken=value
    s = s.replace(/(^|[&?])([^=&?]+)=([^&]*)/g, function (all, sep, k) {
      return SECRET_KEY.test(k) ? sep + k + '=[محذوف]' : all;
    });
    s = s.replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, '[بريد محذوف]');
    return s;
  }

  function extractProducts(o, out, seen) {
    if (!o || typeof o !== 'object') return;
    if (Array.isArray(o)) { for (var i = 0; i < o.length; i++) extractProducts(o[i], out, seen); return; }
    var id = o.goods_id || o.goodsId;
    var name = o.goods_name || o.goodsName;
    if (id && name && !seen[id]) {
      seen[id] = 1;
      out.push({
        id: String(id),
        name: String(name),
        sn: String(o.goods_sn || o.goodsSn || ''),
        sku: String(o.sku_code || o.skuCode || ''),
        mall: String(o.mall_code || o.mallCode || ''),
        img: String(o.goods_img || o.goodsImg || '')
      });
    }
    for (var k in o) if (o.hasOwnProperty(k)) extractProducts(o[k], out, seen);
  }

  function report(m, u, h, b, status, resp) {
    try {
      if (/cart\/share\/landing/.test(u) && resp) {
        var list = [];
        try { extractProducts(JSON.parse(resp), list, {}); } catch (e) {}
        B.cartData(JSON.stringify(list), redactText(resp).slice(0, 8000));
      }
      if (!/\/(bff-api|api)\//.test(u) || NOISE.test(u)) return;
      B.log(JSON.stringify({
        m: m, u: u.slice(0, 400), s: status,
        h: redactHeaders(h || {}),
        b: redactText(b).slice(0, 2000),
        r: redactText(resp).slice(0, 1500)
      }));
    } catch (e) {}
  }

  function bodyText(b) {
    if (b == null) return '';
    if (typeof b === 'string') return b;
    try {
      if (b instanceof URLSearchParams) return b.toString();
      if (b instanceof FormData) {
        var parts = [];
        b.forEach(function (v, k) { parts.push(k + '=' + (typeof v === 'string' ? v : '[file]')); });
        return parts.join('&');
      }
    } catch (e) {}
    return '[non-text]';
  }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      init = init || {};
      var u = typeof input === 'string' ? input : (input && input.url) || String(input);
      var m = String(init.method || (input && input.method) || 'GET').toUpperCase();
      var h = {};
      try { new Headers(init.headers || (input && input.headers) || {}).forEach(function (v, k) { h[k] = v; }); } catch (e) {}
      var b = bodyText(init.body);
      var abs = u;
      try { abs = new URL(u, location.href).href; } catch (e) {}
      remember(abs, h);
      var args = arguments;
      var nb = rewritePick(abs, init.body);
      var rewritten = false;
      if (nb !== null) {
        args = [input, Object.assign({}, init, { body: nb })];
        b = nb; rewritten = true;
      }
      return origFetch.apply(this, args).then(function (res) {
        try {
          res.clone().text().then(function (t) {
            if (rewritten) B.pickResult(JSON.stringify({ mode: 'rewrite', ok: res.status === 200, status: res.status, resp: String(t).slice(0, 4000) }));
            report(m, abs, h, b, res.status, t);
          });
        } catch (e) {}
        return res;
      });
    };
  }

  var XP = XMLHttpRequest.prototype;
  var xOpen = XP.open, xSend = XP.send, xHeader = XP.setRequestHeader;
  XP.open = function (m, u) {
    this.__picks = { m: String(m).toUpperCase(), u: String(u), h: {} };
    return xOpen.apply(this, arguments);
  };
  XP.setRequestHeader = function (k, v) {
    if (this.__picks) this.__picks.h[k] = v;
    return xHeader.apply(this, arguments);
  };
  XP.send = function (body) {
    var x = this, p = x.__picks;
    var args = arguments;
    if (p) {
      var abs0 = p.u;
      try { abs0 = new URL(p.u, location.href).href; } catch (e) {}
      if (!x.__picksDirect) remember(abs0, p.h);
      var nb = rewritePick(abs0, body);
      if (nb !== null) { body = nb; args = [nb]; p.rewritten = true; }
      var b = bodyText(body);
      x.addEventListener('loadend', function () {
        var t = '';
        try {
          if (x.responseType === '' || x.responseType === 'text') t = x.responseText;
          else if (x.responseType === 'json') t = JSON.stringify(x.response);
        } catch (e) {}
        var abs = p.u;
        try { abs = new URL(p.u, location.href).href; } catch (e) {}
        if (p.rewritten) B.pickResult(JSON.stringify({ mode: 'rewrite', ok: x.status === 200, status: x.status, resp: String(t).slice(0, 4000) }));
        report(p.m, abs, p.h, b, x.status, t);
      });
    }
    return xSend.apply(this, args);
  };
})();
