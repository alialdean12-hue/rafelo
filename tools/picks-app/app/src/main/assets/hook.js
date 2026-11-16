// Runs at the start of every shein.com page inside the app.
// It only looks at a few kinds of requests and leaves everything else untouched:
//  - the shared-cart request, to read the cart's products, sizes and quantities;
//  - product-detail requests, to know which product a partner link opened;
//  - affiliate and other site API requests, only to reuse their headers when the app
//    adds products to "اختياراتي" or to the cart.
//  - while reading a partner's list page, product ids in the page's own responses.
// Nothing is recorded or sent anywhere else.
(function () {
  if (window.__picksHook) return;
  window.__picksHook = true;
  var B = window.PicksBridge;
  if (!B) return;

  var CART = /cart\/share\/landing/;
  var AFF_API = /\/affiliate\/api\//;
  var PICK_URL = /\/affiliate\/api\/pick\/update/;
  var BFF = /\/bff-api\//;
  var DETAIL = /get_goods_detail_(static_data|realtime_data)/;
  // Headers the page's own security script may add to every request by itself.
  var SDK_HEADERS = /^(armortoken|smdeviceid|x-cs-random|anti-in|x-gw-auth)$/i;

  var affHeaders = null; // kept in this page only
  var bffHeaders = null; // kept in this page only
  var seenIds = {};

  // While the app reads a partner's list page, collect product ids from the page's own data.
  var scan = false;
  try { scan = !!B.isScanning(); } catch (e) {}
  var SCAN_SKIP = /cart|order|lure_info|recommend|user-api|search|wishlist|affiliate/i;
  var ID_RE = /"(?:goods_id|goodsId)"\s*:\s*"?(\d{5,})/g;
  function scanText(u, t) {
    if (!scan || !t || SCAN_SKIP.test(u)) return;
    var ids = {}, m, n = 0;
    ID_RE.lastIndex = 0;
    while ((m = ID_RE.exec(t)) && n < 500) { ids[m[1]] = 1; n++; }
    var list = Object.keys(ids);
    if (list.length) B.scanIds(JSON.stringify(list));
  }

  function remember(u, h) {
    if (!h || !(h.token || h.armortoken || h.armorToken)) return;
    if (AFF_API.test(u)) affHeaders = h;
    if (BFF.test(u)) bffHeaders = h;
  }

  function watchDetail(u) {
    if (!DETAIL.test(u)) return;
    var m = u.match(/[?&]goods_id=(\d+)/);
    if (m && !seenIds[m[1]]) { seenIds[m[1]] = 1; B.productSeen(m[1]); }
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
        sku: String(o.sku_code || o.itemSku || o.skuCode || ''),
        mall: String(o.mall_code || o.mallCode || '1'),
        qty: String(o.quantity || o.goods_num || o.goodsNum || o.buyNum || '1'),
        attr: String(o.goodsAttr || ''),
        img: String(o.goods_img || o.goodsImg || ''),
        usd: usdOf(dig(o, ['priceData', 'unitPrice', 'price']) || dig(o, ['showUnitPriceInfo', 'price']) || o.salePrice),
        usdBefore: usdOf(o.retailPrice || dig(o, ['priceData', 'originalPrice', 'price'])),
        local: String((o.salePrice && o.salePrice.amountWithSymbol) || ''),
        soldOut: o.soldOutStatus === true || o.is_on_sale === 0 || o.is_on_sale === '0' || o.stock === '0'
      });
    }
    for (var k in o) if (o.hasOwnProperty(k) && o[k] && typeof o[k] === 'object') extractProducts(o[k], out, seen);
  }

  function dig(o, path) {
    for (var i = 0; i < path.length; i++) { if (!o || typeof o !== 'object') return null; o = o[path[i]]; }
    return o;
  }
  function usdOf(p) { return p && p.usdAmount ? String(p.usdAmount) : ''; }

  function onCart(text) {
    var list = [], data = null;
    try { data = JSON.parse(text); extractProducts(data, list, {}); } catch (e) {}
    try { var n = dig(data, ['info', 'allProductSize']); if (n) B.cartTotal(String(n)); } catch (e) {}
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

  function lang() { return location.pathname.split('/')[1] || 'ar'; }

  function ownRequest(url, headers, withSdk, contentType, body, done) {
    var x = new XMLHttpRequest();
    x.open('POST', url);
    for (var k in headers) {
      if (/^(content-type|content-length|cookie|host)$/i.test(k)) continue;
      if (!withSdk && SDK_HEADERS.test(k)) continue;
      try { x.setRequestHeader(k, headers[k]); } catch (e) {}
    }
    x.setRequestHeader('content-type', contentType);
    x.__picksOwn = true;
    x.addEventListener('loadend', function () { done(x.status, String(x.responseText || '').slice(0, 3000)); });
    x.send(body);
  }

  // Called by the app: adds the given products to "اختياراتي".
  window.__picksPick = function (items) {
    if (!affHeaders) { B.pickResult(JSON.stringify({ mode: 'direct', ok: false, reason: 'no-headers' })); return; }
    ownRequest('/' + lang() + '/affiliate/api/pick/update', affHeaders, true, 'application/json; charset=utf-8',
      JSON.stringify({
        goodsIds: items.map(function (i) { return Number(i.id); }),
        skcNames: items.map(function (i) { return i.sn; }),
        pickType: 1,
        sourceType: 1
      }),
      function (status, resp) { B.pickResult(JSON.stringify({ mode: 'direct', ok: status === 200, status: status, resp: resp })); });
  };

  // Called by the app: adds one product to the logged-in cart with the given size and quantity.
  window.__picksAddCart = function (item, withSdk) {
    if (!bffHeaders) { B.cartAddResult(JSON.stringify({ id: item.id, ok: false, reason: 'no-headers' })); return; }
    var p = new URLSearchParams();
    p.set('goods_id', item.id);
    p.set('quantity', item.qty || '1');
    p.set('mall_code', item.mall || '1');
    p.set('sku_code', item.sku);
    p.set('itemSku', item.sku);
    p.set('isAppointMall', '0');
    p.set('skuMallCode', item.mall || '1');
    p.set('isUserSelectedMallCode', '0');
    ownRequest('/' + lang() + '/bff-api/order/add_to_cart', bffHeaders, withSdk, 'application/x-www-form-urlencoded', p.toString(),
      function (status, resp) { B.cartAddResult(JSON.stringify({ id: item.id, ok: status === 200, status: status, resp: resp })); });
  };

  function absUrl(u) { try { return new URL(u, location.href).href; } catch (e) { return String(u); } }
  function sameSite(u) { return /^https:\/\/[^\/]*shein\.com\//.test(u); }
  function interesting(u) { return CART.test(u) || AFF_API.test(u) || BFF.test(u) || (scan && sameSite(u)); }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var u = absUrl(typeof input === 'string' ? input : (input && input.url) || String(input));
      // Fast path: most requests are not ours to look at.
      if (!interesting(u)) return origFetch.apply(this, arguments);
      init = init || {};
      var h = {};
      try { new Headers(init.headers || (input && input.headers) || {}).forEach(function (v, k) { h[k] = v; }); } catch (e) {}
      remember(u, h);
      watchDetail(u);
      var args = arguments;
      var nb = rewritePick(u, init.body);
      if (nb !== null) args = [input, Object.assign({}, init, { body: nb })];
      return origFetch.apply(this, args).then(function (res) {
        if (CART.test(u) || nb !== null || scan) {
          res.clone().text().then(function (t) {
            scanText(u, t);
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
    this.__picks = interesting(abs) ? { u: abs, h: {} } : null;
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
    watchDetail(p.u);
    var args = arguments;
    var nb = rewritePick(p.u, body);
    if (nb !== null) args = [nb];
    if (CART.test(p.u) || nb !== null || scan) {
      x.addEventListener('loadend', function () {
        var t = '';
        try {
          if (x.responseType === '' || x.responseType === 'text') t = x.responseText;
          else if (x.responseType === 'json') t = JSON.stringify(x.response);
        } catch (e) {}
        scanText(p.u, t);
        if (CART.test(p.u)) onCart(t);
        if (nb !== null) B.pickResult(JSON.stringify({ mode: 'rewrite', ok: x.status === 200, status: x.status, resp: String(t).slice(0, 4000) }));
      });
    }
    return xSend.apply(this, args);
  };
})();
