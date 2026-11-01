package com.nokia.phone

/** JS chèn vào trang YouTube / TV (chép từ repo TV): chặn quảng cáo, phóng video kín khung. */
// Chép từ app "Duyệt web" (Webandr): tự bấm "Bỏ qua" quảng cáo YouTube, tua nhanh quảng cáo không bỏ qua được
// (tắt tiếng rồi nhảy đến cuối), ẩn banner/quảng cáo hiển thị.
const val YT_ONLY_AD_JS = """
(function(){
 if(window.__wbYtInstalled)return; window.__wbYtInstalled=true;
 if(typeof window.__wbAdSkip==='undefined')window.__wbAdSkip=true;
 var HIDE='.ytp-ad-overlay-container,.ytp-ad-text-overlay,.ytp-ad-image-overlay,.video-ads,#player-ads,ytd-promoted-sparkles-web-renderer,ytd-display-ad-renderer,ytd-in-feed-ad-layout-renderer,ytd-ad-slot-renderer,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-promoted-video-renderer{display:none!important}';
 var SKIP=['.ytp-ad-skip-button','.ytp-ad-skip-button-modern','.ytp-skip-ad-button','.videoAdUiSkipButton','.ytp-ad-skip-button-container button','.ytp-skip-ad button'];
 var mutedByUs=false,fastByUs=false;
 function tick(){
  try{
   var on=window.__wbAdSkip!==false;
   var st=document.getElementById('__wb_adcss');
   if(on&&!st&&document.documentElement){st=document.createElement('style');st.id='__wb_adcss';st.textContent=HIDE;document.documentElement.appendChild(st);}
   else if(!on&&st){st.parentNode.removeChild(st);}
   var v=document.querySelector('video.html5-main-video')||document.querySelector('video');
   var p=document.querySelector('.html5-video-player');
   var ad=!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));
   if(on&&ad){
    for(var i=0;i<SKIP.length;i++){var b=document.querySelector(SKIP[i]);if(b)b.click();}
    if(v){
     if(!v.muted){v.muted=true;mutedByUs=true;}
     if(v.duration&&isFinite(v.duration)){v.currentTime=v.duration;}else{v.playbackRate=16;fastByUs=true;}
    }
   }else if(v){
    if(mutedByUs){v.muted=false;mutedByUs=false;}
    if(fastByUs){v.playbackRate=1;fastByUs=false;}
   }
  }catch(e){}
 }
 setInterval(tick,250);
})();
"""

// Gắn mọi nút "Tìm kiếm bằng giọng nói" trên trang (YouTube, Google...) sang AndroidVoice. Dò theo aria-label/title
// thay vì class CSS vì nhãn hỗ trợ tiếp cận ít khi đổi giữa các bản cập nhật giao diện. (Học từ repo Y-utubecuatoi.)
const val VOICE_HOOK_JS = """
(function(){
 if(window.__dmVoiceHook)return; window.__dmVoiceHook=true;
 var L=['tìm kiếm bằng giọng nói','tim kiem bang giong noi','search by voice','voice search','search with your voice'];
 function txt(el){return ((el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||'')).toLowerCase();}
 function has(t){for(var i=0;i<L.length;i++){if(t.indexOf(L[i])!==-1)return true;}return false;}
 function hook(){
  Array.prototype.forEach.call(document.querySelectorAll('button,[role="button"],a,div[aria-label],span[aria-label]'),function(el){
   if(el.__dmVoice)return;
   if(!has(txt(el)))return;
   el.__dmVoice=true;
   el.addEventListener('click',function(ev){
    if(window.AndroidVoice){ev.preventDefault();ev.stopPropagation();window.AndroidVoice.startVoiceSearch();}
   },true);
  });
 }
 hook(); setInterval(hook,1000);
 try{new MutationObserver(hook).observe(document.documentElement,{childList:true,subtree:true});}catch(e){}
})();
"""

/** Nút "Mở ứng dụng" / "Open app" của YouTube được đổi thành nút "Đăng nhập" (mở trang đăng nhập Google); ẩn banner mời tải app. */
/** Ẩn toàn bộ thanh tiêu đề trên cùng của YouTube (logo, tìm kiếm, mic, đăng nhập...) và bỏ khoảng trống nó để lại. */
const val HIDE_TOPBAR_JS = """
(function(){
  if (window.__hideTopbar) return; window.__hideTopbar = true;
  var st = document.createElement('style');
  st.textContent =
    'ytm-mobile-topbar-renderer,header.mobile-topbar-header,.mobile-topbar-header,ytm-topbar-logo-renderer,ytm-home-logo,' +
    'ytm-searchbox,ytm-topbar-menu-button-renderer,#masthead-container,ytd-masthead{display:none!important}' +
    ':root{--ytm-mobile-topbar-height:0px;--ytm-topbar-height:0px;--ytm-masthead-height:0px}' +
    'ytm-app,#app{padding-top:0!important}';
  (document.head || document.documentElement).appendChild(st);
})();
"""

const val HIDE_OPEN_APP_JS = """
(function(){
  if (window.__hideOpenApp) return; window.__hideOpenApp = true;
  var st = document.createElement('style');
  st.textContent = 'ytm-open-app-banner-renderer,ytm-mealbar-promo-renderer,ytm-open-app-button-renderer,.open-app-button{display:none!important}';
  (document.head || document.documentElement).appendChild(st);
  var RX = /^(mở ứng dụng|open app|mở app|open in app)$/i;
  var LOGIN = 'https://accounts.google.com/ServiceLogin?service=youtube&hl=vi&continue=' + encodeURIComponent('https://www.youtube.com/?hl=vi&gl=VN&persist_hl=1');
  function relabel(e){
    var w = document.createTreeWalker(e, NodeFilter.SHOW_TEXT, null), n;
    while ((n = w.nextNode())) { if (/\S/.test(n.nodeValue)) { n.nodeValue = 'Đăng nhập'; } }
    if (e.getAttribute && e.getAttribute('aria-label')) e.setAttribute('aria-label', 'Đăng nhập');
  }
  function sweep(){
    var els = document.querySelectorAll('a,button,ytm-button-renderer,yt-button-shape');
    for (var i = 0; i < els.length; i++) {
      var e = els[i];
      if (e.__oa) continue;
      var t = (e.innerText || e.getAttribute('aria-label') || '').replace(/\s+/g,' ').trim();
      if (t && RX.test(t)) {
        e.__oa = true;
        relabel(e);
        e.addEventListener('click', function(ev){ ev.preventDefault(); ev.stopPropagation(); ev.stopImmediatePropagation(); location.href = LOGIN; }, true);
      }
    }
  }
  var timer = null;
  new MutationObserver(function(){ if (timer) return; timer = setTimeout(function(){ timer = null; sweep(); }, 200); })
    .observe(document.documentElement, {childList:true, subtree:true});
  sweep();
})();
"""

/** TV: phóng video phủ kín khung (ẩn phần còn lại của trang) và cố gắng phát ngay. */
const val TV_FULL_JS = """
(function(){
  if (!window.__tvFullStyle) {
    window.__tvFullStyle = true;
    var st = document.createElement('style');
    st.textContent = 'html,body{overflow:hidden!important;background:#000!important}video{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;z-index:2147483647!important;background:#000!important;object-fit:contain!important;transform:none!important}';
    (document.head || document.documentElement).appendChild(st);
  }
  // Ẩn thanh menu trên cùng và nút \"lên đầu\" của trang; không ẩn phần nào có chứa video (tránh đen màn)
  var HIDE_PAGE = 'header:not(:has(video)),nav:not(:has(video)){display:none!important}' +
    '[class*="back-to-top"],[class*="backtotop"],[class*="scroll-top"],[class*="scrollToTop"],[aria-label*="lên đầu" i],[aria-label*="back to top" i],[aria-label*="scroll to top" i]{display:none!important}';
  var st2 = document.getElementById('__tvHideStyle');
  if (!st2) { st2 = document.createElement('style'); st2.id = '__tvHideStyle'; st2.textContent = HIDE_PAGE; (document.head || document.documentElement).appendChild(st2); }
  // Quảng cáo: khi có nút "Bỏ qua" / "Skip" hiện ra thì bấm sau khoảng 0.5 giây, như người dùng bấm tay
  var SKIP_RX = /^(bỏ qua|bo qua|skip|skip ad|bỏ qua quảng cáo|skip ads)\s*(>|›|▸)?$/i;
  var skipTimer = null;
  function findSkip(){
    var els = document.querySelectorAll('button,a,div,span,[role=button]');
    for (var i = 0; i < els.length; i++) {
      var e = els[i];
      if (e.children.length > 2) continue;
      var t = (e.innerText || e.textContent || '').replace(/\s+/g, ' ').trim();
      if (t.length > 25 || !SKIP_RX.test(t)) continue;
      if (e.offsetWidth === 0 || e.offsetHeight === 0) continue;
      return e;
    }
    return null;
  }
  function trySkip(){
    if (skipTimer) return;
    var b = findSkip();
    if (!b) return;
    skipTimer = setTimeout(function(){
      skipTimer = null;
      var again = findSkip();
      if (again) { try { again.click(); } catch(e){} }
    }, 500);
  }
  if (!window.__tvSkipInt) { window.__tvSkipInt = true; setInterval(trySkip, 300); }
  function go(){
    var v = document.querySelector('video');
    if (v) { try { v.muted = false; var p = v.play(); if (p && p.catch) p.catch(function(){}); } catch(e){} }
  }
  go();
  if (!window.__tvFullObs) {
    window.__tvFullObs = true;
    var t = null;
    new MutationObserver(function(){ if (t) return; t = setTimeout(function(){ t = null; go(); }, 500); })
      .observe(document.documentElement, {childList:true, subtree:true});
  }
})();
"""

/**
 * YouTube: ô chọn video. ▲▼ đưa khung trắng qua từng video (trang tự cuộn theo), OK mở video đang có khung.
 * Trả về "true" khi xử lý được; "false" thì app trả phím về cho trang như cũ.
 */
const val YT_NAV_JS = """
(function(){
 if(window.__wbNavInit) return; window.__wbNavInit=true;
 var ITEM='ytm-compact-video-renderer,ytm-video-with-context-renderer,ytm-rich-item-renderer,ytm-media-item,ytm-compact-autoplay-renderer,ytd-compact-video-renderer,ytd-video-renderer,ytd-rich-item-renderer,ytm-playlist-panel-video-renderer,ytd-playlist-panel-video-renderer,ytm-rich-grid-media,ytd-grid-video-renderer';
 var cur=null;
 function vid(a){try{var u=new URL(a.href,location.href);return u.searchParams.get('v')||u.pathname}catch(e){return a.href}}
 function items(){
   var out=[],seen={};
   var as=document.querySelectorAll('a[href*="/watch"],a[href*="/shorts/"]');
   for(var i=0;i<as.length;i++){
     var a=as[i];
     var r=a.getBoundingClientRect(); if(r.width===0||r.height===0) continue;
     var k=vid(a); if(seen[k]) continue; seen[k]=1;
     out.push(a.closest(ITEM)||a);
   }
   return out;
 }
 function mark(el){
   if(cur && cur!==el){ cur.style.outline=''; cur.style.outlineOffset=''; cur.style.backgroundColor=''; }
   cur=el;
   if(el){
     el.style.outline='4px solid #ffffff'; el.style.outlineOffset='-4px';
     el.style.backgroundColor='rgba(255,255,255,0.15)';
     el.scrollIntoView({block:'center',behavior:'smooth'});
   }
 }
 window.__wbNav=function(d){
   var list=items(); if(!list.length) return false;
   if(cur && !cur.isConnected) cur=null;
   var idx=cur?list.indexOf(cur):-1;
   if(idx<0){
     idx=0;
     for(var i=0;i<list.length;i++){var t=list[i].getBoundingClientRect(); if(t.bottom>0 && t.top<innerHeight){idx=i;break;}}
     mark(list[idx]); return true;
   }
   var n=idx+d;
   if(n<0){ return true; }
   if(n>=list.length){ window.scrollBy(0, innerHeight*0.6); return true; }
   mark(list[n]); return true;
 };
 window.__wbOk=function(){
   if(!cur || !cur.isConnected) return false;
   var a=cur.tagName==='A'?cur:cur.querySelector('a[href*="/watch"],a[href*="/shorts/"]');
   if(!a) return false;
   a.click(); return true;
 };
})();
"""

/**
 * YouTube: đặt chất lượng video mặc định 480p (large). Không có 480p thì hạ xuống 360p, rồi 240p.
 * Áp dụng một lần cho mỗi video; người dùng vẫn đổi được trong menu của YouTube.
 */
const val YT_QUALITY_JS = """
(function(){
 if(window.__wbQualInit) return; window.__wbQualInit=true;
 var TARGETS=['large','medium','small'];
 var doneId=null;
 function vidId(){ try{ return new URL(location.href).searchParams.get('v') || location.pathname; }catch(e){ return location.href; } }
 function player(){ return document.getElementById('movie_player') || document.querySelector('.html5-video-player'); }
 function apply(){
   var id=vidId(); if(id===doneId) return;
   var p=player(); if(!p) return;
   if(p.classList && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting'))) return;
   var v=document.querySelector('video'); if(!v || v.readyState<1) return;
   var levels=[]; try{ levels=(p.getAvailableQualityLevels && p.getAvailableQualityLevels()) || []; }catch(e){}
   var pick=null;
   for(var i=0;i<TARGETS.length;i++){ if(levels.length===0 || levels.indexOf(TARGETS[i])!==-1){ pick=TARGETS[i]; break; } }
   if(!pick) return;
   try{
     if(p.setPlaybackQualityRange) p.setPlaybackQualityRange(pick,pick);
     if(p.setPlaybackQuality) p.setPlaybackQuality(pick);
     doneId=id;
   }catch(e){}
 }
 setInterval(apply,1000);
})();
"""
