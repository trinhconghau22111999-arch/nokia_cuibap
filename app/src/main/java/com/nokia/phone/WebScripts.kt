package com.nokia.phone

/**
 * Bộ theo dõi dùng chung cho trang YouTube (thay cho nhiều setInterval chạy đều):
 * - MỘT MutationObserver duy nhất, gom nhiều thay đổi thành một lần xử lý (~500 ms), mỗi việc có nhịp tối thiểu riêng.
 * - Tạm ngưng khi trang ẩn (document.hidden), chạy lại ngay khi quay về.
 * - Báo đổi trang (pushState/replaceState/popstate/yt-navigate-*) để việc cần phản ứng ngay không phải hẹn giờ.
 * - Việc nào trả về true thì được chạy lại sau 250 ms (dùng cho lúc đang có quảng cáo).
 */
const val YT_BUS_JS = """
(function(){
 if(window.__wbBus)return;
 var subs=[],navs=[],adds=[],timer=0,due=0,pEl=null,pObs=null,lastHref=location.href;
 function kick(d){
  var at=Date.now()+d;
  if(timer){ if(at>=due)return; clearTimeout(timer); }
  due=at; timer=setTimeout(run,d);
 }
 function reset(){ for(var i=0;i<subs.length;i++)subs[i].last=0; }
 // Quảng cáo bật/tắt chỉ đổi class của khung phát, không thêm/bớt phần tử: theo dõi riêng class của khung phát
 function syncPlayer(){
  var p=document.querySelector('.html5-video-player');
  if(p===pEl)return;
  if(pObs){try{pObs.disconnect();}catch(e){} pObs=null;}
  pEl=p;
  if(p){ try{ pObs=new MutationObserver(function(){ if(!document.hidden)kick(80); }); pObs.observe(p,{attributes:true,attributeFilter:['class']}); }catch(e){} }
 }
 function run(){
  timer=0;
  if(document.hidden)return;
  syncPlayer();
  var t=Date.now(),again=false,wait=0;
  for(var i=0;i<subs.length;i++){
   var s=subs[i],left=s.gap-(t-s.last);
   if(left>0){ if(!wait||left<wait)wait=left; continue; }
   s.last=t;
   try{ if(s.fn()===true)again=true; }catch(e){}
  }
  if(again)kick(250); else if(wait)kick(wait+20);
 }
 function onMut(ms){
  if(document.hidden)return;
  for(var i=0;i<ms.length;i++){
   var ns=ms[i].addedNodes;
   for(var j=0;j<ns.length;j++){
    var n=ns[j];
    if(n.nodeType!==1||n.childElementCount>8)continue;
    for(var k=0;k<adds.length;k++){ try{adds[k](n);}catch(e){} }
   }
  }
  kick(500);
 }
 function navd(){
  if(location.href===lastHref)return;
  lastHref=location.href; reset();
  for(var i=0;i<navs.length;i++){ try{navs[i]();}catch(e){} }
  kick(150);
 }
 function later(){ setTimeout(navd,0); }
 ['pushState','replaceState'].forEach(function(m){
  var o=history[m]; if(typeof o!=='function')return;
  history[m]=function(){ var r=o.apply(this,arguments); later(); return r; };
 });
 addEventListener('popstate',later);
 ['yt-navigate-finish','yt-page-data-updated'].forEach(function(n){
  addEventListener(n,function(){ later(); reset(); kick(200); },true);
 });
 document.addEventListener('visibilitychange',function(){ if(!document.hidden){ reset(); kick(0); } });
 // Cuộn làm hiện thêm phần tử mà không đổi cây trang: chỉ cần khi đang xem video
 addEventListener('scroll',function(){ if(!document.hidden&&location.pathname.indexOf('/watch')===0)kick(400); },{capture:true,passive:true});
 try{ new MutationObserver(onMut).observe(document.documentElement,{childList:true,subtree:true}); }catch(e){}
 window.__wbBus={
  add:function(fn,gap){ subs.push({fn:fn,gap:gap||0,last:0}); },
  onNav:function(fn){ navs.push(fn); },
  onAdd:function(fn){ adds.push(fn); },
  kick:kick,reset:reset
 };
 kick(300);
})();
"""

/** JS chèn vào trang YouTube / TV (chép từ repo TV): chặn quảng cáo, phóng video kín khung. */
// Chép từ app "Duyệt web" (Webandr): tự bấm "Bỏ qua" quảng cáo YouTube, tua nhanh quảng cáo không bỏ qua được
// (tắt tiếng rồi nhảy đến cuối), ẩn banner/quảng cáo hiển thị.
const val YT_ONLY_AD_JS = YT_BUS_JS + """
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
   return on&&ad;
  }catch(e){}
  return false;
 }
 // Không còn hẹn giờ 250 ms chạy mãi: chạy khi trang đổi (bộ theo dõi dùng chung), và chỉ lặp mỗi 250 ms trong lúc ĐANG có quảng cáo
 var bus=window.__wbBus; tick();
 if(bus) bus.add(tick,100); else setInterval(tick,250);
})();
"""

// Gắn mọi nút "Tìm kiếm bằng giọng nói" trên trang (YouTube, Google...) sang AndroidVoice. Dò theo aria-label/title
// thay vì class CSS vì nhãn hỗ trợ tiếp cận ít khi đổi giữa các bản cập nhật giao diện. (Học từ repo Y-utubecuatoi.)
const val VOICE_HOOK_JS = YT_BUS_JS + """
(function(){
 if(window.__dmVoiceHook)return; window.__dmVoiceHook=true;
 var L=['tìm kiếm bằng giọng nói','tim kiem bang giong noi','search by voice','voice search','search with your voice'];
 function txt(el){return ((el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||'')).toLowerCase();}
 function has(t){for(var i=0;i<L.length;i++){if(t.indexOf(L[i])!==-1)return true;}return false;}
 // Lọc ngay bằng bộ chọn thuộc tính (trình duyệt tự làm, nhanh) thay vì đọc nhãn của mọi nút trên trang
 var TAGS=['button','[role="button"]','a'],SEL=[];
 for(var i=0;i<L.length;i++){
  for(var j=0;j<TAGS.length;j++)SEL.push(TAGS[j]+'[aria-label*="'+L[i]+'" i]',TAGS[j]+'[title*="'+L[i]+'" i]');
  SEL.push('div[aria-label*="'+L[i]+'" i]','span[aria-label*="'+L[i]+'" i]');
 }
 var SELS=SEL.join(',');
 function attach(el){
  if(el.__dmVoice)return;
  el.__dmVoice=true;
  el.addEventListener('click',function(ev){
   if(window.AndroidVoice){ev.preventDefault();ev.stopPropagation();window.AndroidVoice.startVoiceSearch();}
  },true);
 }
 function hook(){
  var list;
  try{ list=document.querySelectorAll(SELS); }
  catch(e){ // bộ chọn không hợp lệ: quay về cách dò cũ
   Array.prototype.forEach.call(document.querySelectorAll('button,[role="button"],a,div[aria-label],span[aria-label]'),function(el){ if(!el.__dmVoice&&has(txt(el)))attach(el); });
   return;
  }
  for(var k=0;k<list.length;k++)attach(list[k]);
 }
 hook();
 // Chạy theo bộ theo dõi dùng chung (không còn setInterval 1 giây + observer riêng)
 if(window.__wbBus)window.__wbBus.add(hook,500);
 else{ setInterval(hook,1000); try{new MutationObserver(hook).observe(document.documentElement,{childList:true,subtree:true});}catch(e){} }
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

const val HIDE_OPEN_APP_JS = YT_BUS_JS + """
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
      if (e.__oa || e.__oaChk) continue;
      var t = (e.innerText || e.getAttribute('aria-label') || '').replace(/\s+/g,' ').trim();
      if (t && RX.test(t)) {
        e.__oa = true;
        relabel(e);
        e.addEventListener('click', function(ev){ ev.preventDefault(); ev.stopPropagation(); ev.stopImmediatePropagation(); location.href = LOGIN; }, true);
      } else if (t) {
        // Đã đọc chữ và không phải nút cần đổi: lần sau bỏ qua, khỏi đọc innerText (buộc tính lại bố cục) lặp lại
        e.__oaChk = true;
      }
    }
  }
  // Chạy theo bộ theo dõi dùng chung (không còn observer + hẹn giờ 200 ms riêng)
  if (window.__wbBus) window.__wbBus.add(sweep, 300);
  else {
    var timer = null;
    new MutationObserver(function(){ if (timer) return; timer = setTimeout(function(){ timer = null; sweep(); }, 200); })
      .observe(document.documentElement, {childList:true, subtree:true});
  }
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
  // Chỉ quét quảng cáo trong 5 giây đầu sau khi mở kênh; hết 5 giây thì ngừng hẳn
  if (!window.__tvSkipIv) {
    var skipUntil = Date.now() + 5000;
    window.__tvSkipIv = setInterval(function(){
      if (Date.now() > skipUntil) { clearInterval(window.__tvSkipIv); return; }
      trySkip();
    }, 300);
  }
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
const val YT_NAV_JS = YT_BUS_JS + """
(function(){
 if(window.__wbNavInit) return; window.__wbNavInit=true;
  // Ẩn thanh "Tiếp theo / Danh sách kết hợp". Không quét mọi div/section/span nữa: hỏi trình duyệt phần tử nào đang nằm
  // tại vài điểm trên màn hình (elementsFromPoint) rồi mới đọc chữ; chạy theo bộ theo dõi dùng chung, tối đa ~1 lần/0,8 giây
  var bus=window.__wbBus;
  function every(fn,gap,fallbackMs){ if(bus) bus.add(fn,gap); else setInterval(fn,fallbackMs); }
  document.addEventListener('loadedmetadata', function(){
    try{ var mp=document.getElementById('movie_player'); if(mp){ if(mp.setOption) mp.setOption('captions','track',{}); if(mp.unloadModule) mp.unloadModule('captions'); } }catch(e){}
    if(window.__wbApplyLand) window.__wbApplyLand();
    if(bus){ bus.reset(); bus.kick(300); }
  }, true);
  var NX_X=[0.2,0.5,0.8], NX_Y=[0.06,0.16,0.26,0.36,0.46,0.56,0.66,0.76,0.86,0.95];
  function hideNext(){
    if(location.pathname.indexOf('/watch')!==0||!document.elementsFromPoint) return;
    var W=innerWidth, H=innerHeight;
    for(var a=0;a<NX_X.length;a++){
      for(var b=0;b<NX_Y.length;b++){
        var st=document.elementsFromPoint(W*NX_X[a],H*NX_Y[b]);
        for(var i=0;i<st.length;i++){
          var e=st[i], tg=e.tagName;
          if(tg!=='DIV'&&tg!=='SECTION'&&tg!=='SPAN') continue;
          if(e.children.length>5 || e.offsetHeight===0 || e.offsetHeight>300) continue;
          var t=(e.textContent||'').trim();
          if(t.indexOf('Tiếp theo')!==0) continue;
          while(e.parentElement && e.parentElement.offsetHeight>0 && e.parentElement.offsetHeight<=300 && (e.parentElement.textContent||'').trim().indexOf('Tiếp theo')===0) e=e.parentElement;
          e.style.setProperty('display','none','important');
        }
      }
    }
  }
  every(hideNext,800,2000);
 // Xoay ngang (do app báo, không dựa media query vì khung web có thể rộng hơn cao ngay cả khi máy để dọc):
 // chỉ ở trang xem video mới khóa cuộn và ghim video sát mép trên; trang tìm kiếm/danh sách luôn cuộn được
  var wasLand=false, lt1=0, lt2=0;
 function applyNow(){ try{ var p=location.pathname; var on=window.__wbLand===true&&(p.indexOf('/watch')===0||p.indexOf('/shorts')===0);
   document.documentElement.classList.toggle('__wbLand',on); if(on&&!wasLand){window.scrollTo(0,0);setTimeout(function(){window.scrollTo(0,0)},400)} wasLand=on;
   // Xem dọc: video phóng ra sát cả hai mép trái/phải; video hẹp hơn khung (viền đen hai bên) thì phóng vừa chiều ngang
   var port=window.__wbLand===false&&p.indexOf('/watch')===0, cover=false;
   if(port){ var v=document.querySelector('video'); if(v&&v.videoWidth>0&&v.videoHeight>0){
     var bx=v.closest('.html5-video-player')||v.parentElement; var cw=bx.clientWidth, ch=bx.clientHeight;
     if(cw>0&&ch>0) cover=(v.videoWidth/v.videoHeight) < (cw/ch)-0.02; } else port=false; }
   document.documentElement.classList.toggle('__wbPort',port); document.documentElement.classList.toggle('__wbCover',port&&cover);
  }catch(e){} }
  // Gọi từ app hoặc sự kiện: chạy ngay, rồi chạy bù 2 lần (bố cục/kích thước video chỉ ổn định sau khi class đổi)
  window.__wbApplyLand=function(){ applyNow(); clearTimeout(lt1); clearTimeout(lt2); lt1=setTimeout(applyNow,350); lt2=setTimeout(applyNow,1300); };
  // Không còn hẹn giờ 0,8 giây: chạy khi app đổi hướng (Kotlin gọi __wbApplyLand), đổi trang, đổi kích thước, video nạp xong, quay lại trang
  if(bus) bus.onNav(function(){ window.__wbApplyLand(); });
  addEventListener('resize',function(){ window.__wbApplyLand(); });
  addEventListener('orientationchange',function(){ window.__wbApplyLand(); });
  document.addEventListener('fullscreenchange',function(){ window.__wbApplyLand(); });
  document.addEventListener('visibilitychange',function(){ if(!document.hidden) window.__wbApplyLand(); });
  window.__wbApplyLand();
 // Lưới an toàn: nếu trang vẫn bị chuyển sang trang kênh tác giả (do cách điều hướng nội bộ của YouTube) thì tự quay lại ngay
 // Trang chủ / tìm kiếm / feed: ẩn thanh chip (Tất cả, Âm nhạc...) và dải trắng phía trên, đưa video đầu tiên lên sát đầu trang
 var WL='a[href*="/watch"],a[href*="/shorts/"]';
  var trimKey='', trimA=null, trimStable=0;
  function trimTop(){ try{
    var p=location.pathname; if(!(p==='/'||p.indexOf('/results')===0||p.indexOf('/feed')===0)) return;
    var a=document.querySelector(WL); if(!a) return;
    // Đổi trang hoặc video đầu tiên bị thay thế thì dọn lại; dọn xong và 3 lần liên tiếp không còn gì để dọn thì ngừng
    var key=p+location.search;
    if(key!==trimKey||a!==trimA){ trimKey=key; trimA=a; trimStable=0; }
    if(trimStable>=3) return;
    var changed=false, el=a;
    while(el&&el!==document.body&&el.parentElement){
      var par=el.parentElement, sib=par.firstElementChild;
      while(sib&&sib!==el){
        if(!sib.__wbTop && !sib.querySelector(WL)){ sib.__wbTop=1; sib.style.setProperty('display','none','important'); changed=true; }
        sib=sib.nextElementSibling; }
      if(el!==a && !el.__wbPad && el.offsetHeight>300){ el.__wbPad=1; el.style.setProperty('padding-top','0','important'); el.style.setProperty('margin-top','0','important'); changed=true; }
      el=par; }
    trimStable=changed?0:trimStable+1;
  }catch(e){} }
  every(trimTop,500,1200);
  // Lưới an toàn: trang kênh tác giả thì quay lại ngay. Không hẹn giờ 300 ms nữa: chạy lúc đổi trang (pushState/popstate/yt-navigate)
  var lastChBack=0;
  function chGuard(){ try{ if(/^\/(@|channel\/|c\/|user\/)/.test(location.pathname) && history.length>1 && Date.now()-lastChBack>1500){ lastChBack=Date.now(); history.back(); } }catch(e){} }
  if(bus) bus.onNav(chGuard);
  every(chGuard,300,300);
  chGuard();
 // Ẩn thanh "Tiếp theo / Danh sách kết hợp" tự phát ở cuối video
 if(!document.getElementById('__wbNoAutonav')){
   var stA=document.createElement('style'); stA.id='__wbNoAutonav';
   stA.textContent='ytm-autonav-bar,ytm-autonav-renderer,ytm-autonav-endscreen,.ytp-autonav-endscreen,ytd-compact-autoplay-renderer,[class*="autonav-bar"],[class*="autoplay-bar"],ytm-pivot-bar-renderer,ytm-pivot-bar-item-renderer,[class*="pivot-bar"]{display:none!important}html.__wbLand,html.__wbLand body{margin-top:0!important;padding-top:0!important;overflow:hidden!important}html.__wbLand ytm-app,html.__wbLand ytm-watch,html.__wbLand ytm-single-column-watch-next-results-renderer,html.__wbLand #player-container-id,html.__wbLand .player-container,html.__wbLand #player,html.__wbLand ytm-player,html.__wbLand .html5-video-player{margin-top:0!important;padding-top:0!important;top:0!important}html.__wbLand #player-container-id,html.__wbLand .player-container,html.__wbLand ytm-player{position:fixed!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;max-height:100vh!important;z-index:9999!important}html.__wbLand .html5-video-container{position:absolute!important;top:0!important;left:0!important;width:100%!important;height:100%!important;margin:0!important}html.__wbLand video{position:absolute!important;top:0!important;left:0!important;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;object-fit:contain!important;transform:none!important}ytm-feed-filter-chip-bar-renderer,ytm-chip-cloud-renderer,ytm-search-filter-chip-bar-renderer,[class*="chip-cloud"],[class*="chip-bar"]{display:none!important}html.__wbLand,html.__wbLand body{background:#000!important}html.__wbPort .html5-video-container{position:absolute!important;top:0!important;left:0!important;width:100%!important;height:100%!important;margin:0!important}html.__wbPort video{position:absolute!important;top:0!important;left:0!important;width:100%!important;height:100%!important;max-width:none!important;max-height:none!important;transform:none!important;object-fit:contain!important}html.__wbPort.__wbCover video{object-fit:cover!important}html.__wbPort ytm-slim-video-metadata-section-renderer,html.__wbPort ytm-slim-video-information-renderer,html.__wbPort ytm-slim-owner-renderer,html.__wbPort ytm-slim-video-action-bar-renderer,html.__wbPort ytm-comments-entry-point-header-renderer,html.__wbPort ytm-comments-entry-point-teaser-renderer,html.__wbPort ytm-comments-entry-point-renderer,html.__wbPort ytm-video-description-header-renderer,html.__wbPort ytm-structured-description-content-renderer,html.__wbPort ytm-expandable-video-description-body-renderer,html.__wbPort ytm-info-panel-container-renderer,html.__wbPort ytm-engagement-panel,html.__wbPort ytm-comments-sheet,html.__wbPort [section-identifier="comments-entry-point"],html.__wbPort [section-identifier="video-metadata"],html.__wbPort [class*="slim-video-information"],html.__wbPort [class*="slim-video-metadata"],html.__wbPort [class*="slim-owner"],html.__wbPort [class*="video-action-bar"],html.__wbPort [class*="comments-entry-point"],html.__wbPort [class*="video-description"]{display:none!important}html.__wbPort ytm-app,html.__wbPort ytm-watch,html.__wbPort #player-container-id,html.__wbPort .player-container,html.__wbPort ytm-player{margin-top:0!important;padding-top:0!important;top:0!important}html.__wbPort #player-container-id,html.__wbPort .player-container,html.__wbPort ytm-player,html.__wbPort #player,html.__wbPort .html5-video-player{height:56.25vw!important;max-height:none!important}html.__wbLand ytm-custom-control,html.__wbLand .player-controls-background,html.__wbLand .player-controls-content,html.__wbLand .player-controls-middle{position:absolute!important;top:0!important;left:0!important;width:100%!important;height:100%!important;margin:0!important;transform:none!important}.ytp-subtitles-button,.ytp-settings-button,.ytp-fullscreen-button,button[aria-label*="Phụ đề"],button[aria-label*="Cài đặt"],button[aria-label*="Toàn màn hình"],button[aria-label*="Subtitles"],button[aria-label*="Settings"],button[aria-label*="Full screen"],button[aria-label*="Fullscreen"],button.fullscreen-icon,.fullscreen-icon,button[aria-label*="full screen" i],button[aria-label*="fullscreen" i],button[aria-label*="toàn màn hình" i],button[aria-label*="phóng to" i],button[aria-label*="thu nhỏ" i],button[aria-label*="minimize" i],button[aria-label*="miniplayer" i],button[aria-label*="trình phát thu nhỏ" i],.ytp-miniplayer-button,.player-control-minimize,.collapse-button,ytm-closed-captioning-button,[class*="closed-caption" i],[class*="captions-button" i],[class*="subtitles-button" i],button[aria-label*="phụ đề" i],button[aria-label*="subtitle" i],button[aria-label*="caption" i],.ytp-caption-window-container,.ytp-caption-segment,.caption-window{display:none!important;pointer-events:none!important;visibility:hidden!important}';
   (document.head||document.documentElement).appendChild(stA);
 }
  // Ẩn thông báo nổi "Đã tắt/bật phụ đề": kiểm tra ngay lúc phần tử mới được thêm vào trang (qua bộ theo dõi dùng chung, không observer riêng)
  try{
    var capRe=/^(Đã (tắt|bật) phụ đề|Captions? (on|off)|Subtitles? (on|off))/i;
    var capHide=function(n){
      var t=(n.textContent||'').trim();
      if(t.length<60 && capRe.test(t)) n.style.setProperty('display','none','important');
    };
    if(bus) bus.onAdd(capHide);
    else new MutationObserver(function(ms){
      for(var i=0;i<ms.length;i++){
        var ns=ms[i].addedNodes;
        for(var j=0;j<ns.length;j++){ var n=ns[j]; if(n.nodeType===1) capHide(n); }
      }
    }).observe(document.documentElement,{childList:true,subtree:true});
  }catch(e){}
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
     el.style.outline='4px solid #00e676'; el.style.outlineOffset='-4px';
     el.scrollIntoView({block:'center',inline:'nearest',behavior:'smooth'});
     if(window.scrollX) window.scrollTo({left:0,top:window.scrollY,behavior:'auto'});
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
 * YouTube: đặt chất lượng 480p (large) cho MỖI video, đúng một lần khi video bắt đầu tải.
 * Không có 480p thì dùng 360p (medium), rồi 240p (small). Không kiểm tra liên tục.
 */
const val YT_QUALITY_JS = """
(function(){
 if(window.__wbQualInit) return; window.__wbQualInit=true;
 var TARGETS=['large','medium','small'];
 function apply(p){
   var levels=[]; try{ levels=(p.getAvailableQualityLevels && p.getAvailableQualityLevels()) || []; }catch(e){}
   if(levels.length===0) return false;
   var pick=null;
   for(var i=0;i<TARGETS.length;i++){ if(levels.indexOf(TARGETS[i])!==-1){ pick=TARGETS[i]; break; } }
   if(!pick) return true;
   try{
     if(p.setPlaybackQualityRange) p.setPlaybackQualityRange(pick,pick);
     if(p.setPlaybackQuality) p.setPlaybackQuality(pick);
   }catch(e){}
   return true;
 }
 document.addEventListener('loadedmetadata', function(e){
   var v=e.target; if(!v || v.tagName!=='VIDEO') return;
   var p=document.getElementById('movie_player') || document.querySelector('.html5-video-player');
   if(!p) return;
   var tries=0;
   var iv=setInterval(function(){
     tries++;
     if(p.classList && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting'))){ clearInterval(iv); return; }
     if(apply(p) || tries>=6) clearInterval(iv);
   }, 500);
 }, true);
})();
"""
