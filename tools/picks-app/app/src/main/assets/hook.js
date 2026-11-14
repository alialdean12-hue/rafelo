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
      return origFetch.apply(this, arguments).then(function (res) {
        try { res.clone().text().then(function (t) { report(m, abs, h, b, res.status, t); }); } catch (e) {}
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
    if (p) {
      var b = bodyText(body);
      x.addEventListener('loadend', function () {
        var t = '';
        try {
          if (x.responseType === '' || x.responseType === 'text') t = x.responseText;
          else if (x.responseType === 'json') t = JSON.stringify(x.response);
        } catch (e) {}
        var abs = p.u;
        try { abs = new URL(p.u, location.href).href; } catch (e) {}
        report(p.m, abs, p.h, b, x.status, t);
      });
    }
    return xSend.apply(this, arguments);
  };
})();
