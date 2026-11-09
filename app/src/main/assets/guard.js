// Obex route guard.
// Runs on every instagram.com page before Instagram's own code.
// Instagram changes pages without reloading, so this watches every page change and
// sends you back to your inbox if you end up somewhere blocked.
// Keep the rules in sync with UrlPolicy.kt.
(function () {
  if (window.__rbGuard) return;
  window.__rbGuard = true;

  var INBOX = '/direct/inbox/';
  var redirecting = false;
  var lockedReel = null; // the one reel you opened. Swiping on to another sends you back.
  var lastPath = null;

  // Messages to and from the app. Android only gives instagram.com pages this "RB" object
  // (WebViewCompat.addWebMessageListener), so no other website can talk to the app.
  var bridge = window.RB && window.RB.postMessage ? window.RB : null;
  var currentMode = bridge ? null : 'messages'; // null = still waiting for the app to say which tab is open

  function send(message) {
    try { if (bridge) bridge.postMessage(JSON.stringify(message)); } catch (e) {}
  }

  if (bridge && bridge.addEventListener) {
    bridge.addEventListener('message', function (event) {
      try {
        var data = JSON.parse(event.data);
        if (data.mode && data.mode !== currentMode) {
          currentMode = data.mode;
          lastPath = null; // re-check the current page with the new tab
          check();
        }
      } catch (e) {}
    });
  }
  send({ type: 'hello' });

  function mode() {
    return currentMode;
  }

  function isBlocked(path) {
    if (!path || path === '/') return mode() !== 'stories';
    if (path === '/reels' || path === '/reels/' || path.indexOf('/reels/audio') === 0) return true;
    // The Explore page is blocked. Search (people, keyword results, hashtags) is allowed.
    if (path === '/explore' || path.indexOf('/explore/') === 0) {
      return !(path.indexOf('/explore/search') === 0 || path.indexOf('/explore/tags/') === 0);
    }
    return false;
  }

  function reelId(path) {
    var m = path.match(/^\/reels?\/([A-Za-z0-9_-]{5,})\/?$/);
    return m && m[1] !== 'audio' ? m[1] : null;
  }

  function go(path) {
    if (redirecting) return;
    redirecting = true;
    location.replace(path);
  }

  function ensureCss() {
    if (!window.__RB_CSS || document.getElementById('rb-css')) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var style = document.createElement('style');
    style.id = 'rb-css';
    style.textContent = window.__RB_CSS;
    parent.appendChild(style);
  }

  function check() {
    if (redirecting) return;
    ensureCss();
    var path = location.pathname;
    var root = document.documentElement;
    if (root) {
      root.classList.toggle('rb-stories', path === '/' && mode() === 'stories');
      // Keep the home page hidden until we know whether it's allowed.
      root.classList.toggle('rb-pending', path === '/' && mode() === null);
    }
    if (path === '/' && mode() === null) return; // decide once the app replies

    if (path === lastPath) return;
    var previous = lastPath;
    lastPath = path;

    if (isBlocked(path)) { go(INBOX); return; }

    var id = reelId(path);
    if (id) {
      if (lockedReel && lockedReel !== id && previous && reelId(previous)) {
        go('/reel/' + lockedReel + '/');
        return;
      }
      lockedReel = id;
    } else {
      lockedReel = null;
    }

    send({ type: 'path', path: path });
  }

  // Watch the website's own page changes.
  ['pushState', 'replaceState'].forEach(function (fn) {
    var original = history[fn];
    history[fn] = function () {
      var result = original.apply(this, arguments);
      check();
      return result;
    };
  });
  window.addEventListener('popstate', check);

  // Stop taps on links to blocked places before they go anywhere.
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (!a) return;
    try {
      var url = new URL(a.getAttribute('href'), location.href);
      if (/(^|\.)instagram\.com$/.test(url.hostname) && isBlocked(url.pathname)) {
        e.preventDefault();
        e.stopPropagation();
      }
    } catch (err) {}
  }, true);

  // ---------- Visual tidy-ups ----------

  var reportedProfile = null;

  // Instagram's own bottom bar (DM arrow + profile picture) duplicates our dock, so hide it.
  // It has no stable name, so find it by what it is: a fixed bar at the bottom holding the Messages link.
  function hideInstagramBar() {
    var links = document.querySelectorAll('a[href="/direct/inbox/"]');
    for (var i = 0; i < links.length; i++) {
      var el = links[i];
      if (el.closest('main')) continue;
      for (var p = el.parentElement, depth = 0; p && p !== document.body && depth < 12; p = p.parentElement, depth++) {
        if (getComputedStyle(p).position !== 'fixed') continue;
        var r = p.getBoundingClientRect();
        if (r.height > 0 && r.height < 100 && r.bottom >= innerHeight - 4) {
          var avatar = p.querySelector('a[href] img[alt]');
          var a = avatar && avatar.closest('a');
          if (a) {
            var key = a.getAttribute('href') + '|' + avatar.src;
            if (key !== reportedProfile) {
              reportedProfile = key;
              send({ type: 'profile', path: a.getAttribute('href'), avatar: avatar.src });
            }
          }
          p.classList.add('rb-hidden');
        }
        break;
      }
    }
  }

  // Instagram sizes the full-screen reel box for a 9:16 screen. The S25 Ultra is taller, so the box
  // comes out narrower than the screen and leaves a black bar on the right. Stretch it to full width.
  function fixReelWidth() {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      if (videos[i].getBoundingClientRect().width < innerWidth - 2) continue;
      for (var p = videos[i].parentElement, depth = 0; p && p !== document.body && depth < 14; p = p.parentElement, depth++) {
        var w = p.getBoundingClientRect().width;
        if (w > innerWidth * 0.8 && w < innerWidth - 1) {
          p.style.setProperty('width', '100%', 'important');
          p.style.setProperty('max-width', '100%', 'important');
        }
      }
    }
  }

  // Stories open muted on the web. Tap Instagram's unmute button once when you start watching.
  // If you mute again yourself, it stays muted until you leave stories.
  // Also applies to single posts and reels (e.g. videos opened from Search): unmuted once per video.
  var unmutedThisVisit = false;
  var unmuteKey = null;
  function unmuteStories() {
    var path = location.pathname;
    var key = path.indexOf('/stories/') === 0 ? 'stories'
      : /^\/(p|reels?)\/[A-Za-z0-9_-]{5,}\/?$/.test(path) ? path
      : null;
    if (key !== unmuteKey) { unmuteKey = key; unmutedThisVisit = false; }
    if (!key) { unmuteDmViewer(); return; }
    if (unmutedThisVisit) return;
    // Hidden feed videos have mute buttons too, so pick the one actually on screen.
    var icons = document.querySelectorAll('svg[aria-label="Audio is muted"]');
    for (var i = 0; i < icons.length; i++) {
      var button = icons[i].closest('[role="button"], button');
      if (button && button.getBoundingClientRect().width > 0) {
        button.click();
        unmutedThisVisit = true;
        return;
      }
    }
  }

  // Videos opened from a DM (sent to you or by you) play in a full-screen viewer that stays on the
  // /direct/ address. Unmute each one once when it opens; if you mute it yourself, it stays muted.
  var dmUnmuted = typeof WeakSet === 'function' ? new WeakSet() : null;
  function unmuteDmViewer() {
    if (!dmUnmuted || location.pathname.indexOf('/direct/') !== 0) return;
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      var r = v.getBoundingClientRect();
      // Only the big viewer, not the small previews inside the chat.
      if (r.width < innerWidth - 2 || r.height < innerHeight * 0.5 || r.top > innerHeight || r.bottom < 0) continue;
      if (dmUnmuted.has(v)) continue;
      // Find the visible mute button that belongs to this video's viewer.
      var icons = document.querySelectorAll('svg[aria-label="Audio is muted"]');
      for (var j = 0; j < icons.length; j++) {
        var button = icons[j].closest('[role="button"], button');
        if (!button || button.getBoundingClientRect().width === 0) continue;
        for (var p = button.parentElement, depth = 0; p && depth < 12; p = p.parentElement, depth++) {
          if (p.contains(v)) {
            button.click();
            dmUnmuted.add(v);
            return;
          }
        }
      }
      if (!v.muted) dmUnmuted.add(v); // already playing with sound
    }
  }

  // The DM video viewer is a vertical snap-scrolling list (the reel you opened, then more reels and
  // recommendations) that never changes the web address, so the route guard can't see swipes there.
  // Pin that list to the reel you opened: no swiping, and any scroll gets snapped straight back.
  var lockedScrollers = typeof WeakMap === 'function' ? new WeakMap() : null;
  function lockDmViewer() {
    if (!lockedScrollers || location.pathname.indexOf('/direct/') !== 0) return;
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      var r = videos[i].getBoundingClientRect();
      if (r.width < innerWidth - 2 || r.height < innerHeight * 0.5) continue;
      for (var p = videos[i].parentElement, depth = 0; p && depth < 16; p = p.parentElement, depth++) {
        var cs = getComputedStyle(p);
        var isFeed = /(auto|scroll|hidden)/.test(cs.overflowY) && cs.scrollSnapType.indexOf('y') !== -1 &&
          p.scrollHeight > p.clientHeight * 1.5;
        if (!isFeed) continue;
        if (lockedScrollers.has(p)) return;
        // Wait until the opening animation has settled on a reel.
        var top = p.getBoundingClientRect().top;
        if (Math.abs(r.top - top) > 4) return;
        lockScroller(p, p.scrollTop);
        return;
      }
    }
  }
  function lockScroller(el, pos) {
    lockedScrollers.set(el, pos);
    el.style.setProperty('overflow-y', 'hidden', 'important');
    el.style.setProperty('overscroll-behavior', 'none', 'important');
    el.style.setProperty('touch-action', 'pan-x pinch-zoom', 'important');
    el.addEventListener('scroll', function () {
      if (el.scrollTop !== pos) el.scrollTop = pos;
    }, { passive: true });
    // Block the swipe gesture itself so Instagram's own code can't move to the next reel.
    var startY = null;
    el.addEventListener('touchstart', function (e) { startY = e.touches[0].clientY; }, { passive: true, capture: true });
    el.addEventListener('touchmove', function (e) {
      if (startY !== null && Math.abs(e.touches[0].clientY - startY) > 8) {
        // Allow scrolling inside the comments sheet and other inner scroll areas.
        var t = e.target;
        for (var q = t; q && q !== el; q = q.parentElement) {
          if (q.scrollHeight > q.clientHeight + 4 && /(auto|scroll)/.test(getComputedStyle(q).overflowY)) return;
        }
        e.preventDefault();
      }
    }, { passive: false, capture: true });
  }

  // The "Feed blocked" message on the Stories tab. Lives outside Instagram's own page structure.
  function addFeedBlockedMessage() {
    if (document.getElementById('rb-feed-blocked') || !document.body) return;
    var box = document.createElement('div');
    box.id = 'rb-feed-blocked';
    box.innerHTML =
      '<div class="rb-icon"><svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><line x1="5.6" y1="5.6" x2="18.4" y2="18.4"/></svg></div>' +
      '<div class="rb-title">Feed blocked</div>' +
      '<div class="rb-sub">Just stories from people you follow.</div>';
    document.body.appendChild(box);
  }

  // Tag the sticker/GIF pop-up so hide.css can round it and space out its tabs.
  function tagStickerPanel() {
    var lists = document.querySelectorAll('[role="dialog"] [role="tablist"]');
    for (var i = 0; i < lists.length; i++) {
      for (var p = lists[i].parentElement, depth = 0; p && depth < 6; p = p.parentElement, depth++) {
        var cs = getComputedStyle(p);
        if (cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && parseFloat(cs.borderRadius) > 0) {
          p.classList.add('rb-sticker-panel');
          break;
        }
      }
    }
  }

  function tidy() {
    var root = document.documentElement;
    if (root) root.classList.toggle('rb-direct', location.pathname.indexOf('/direct/') === 0);
    try { tagStickerPanel(); } catch (e) {}
    try { lockDmViewer(); } catch (e) {}
    try { addFeedBlockedMessage(); } catch (e) {}
    try { hideInstagramBar(); } catch (e) {}
    try { fixReelWidth(); } catch (e) {}
    try { unmuteStories(); } catch (e) {}
  }

  // Search: pressing Enter on the keyboard does nothing on Instagram's mobile site.
  // Make it open the results (people, posts and videos) for what you typed.
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' || location.pathname.indexOf('/explore/search') !== 0) return;
    var input = e.target;
    if (!input || input.tagName !== 'INPUT') return;
    var q = (input.value || '').trim();
    if (!q) return;
    e.preventDefault();
    var url = q.charAt(0) === '#'
      ? '/explore/tags/' + encodeURIComponent(q.slice(1)) + '/'
      : '/explore/search/keyword/?q=' + encodeURIComponent(q);
    input.blur();
    location.assign(url);
  }, true);

  // Safety net in case a page change slips past the hooks above.
  setInterval(function () { check(); tidy(); }, 400);
  check();
})();
