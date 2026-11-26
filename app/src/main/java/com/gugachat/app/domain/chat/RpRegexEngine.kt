package com.gugachat.app.domain.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RpContentSegment(
    val isHtml: Boolean,
    val content: String,
)

data class RpRegexPreset(
    val id: String,
    val zhName: String,
    val enName: String,
    val zhDesc: String,
    val enDesc: String,
    val script: RpRegexScript,
)

data class RpStatusWidgetTemplate(
    val id: String,
    val zhName: String,
    val enName: String,
    val zhDesc: String,
    val enDesc: String,
    val html: String,
)

object RpRegexEngine {
    private val VALID_JS_FLAGS_RE = Regex("^[gimsuydv]*$")
    private val FENCE_LINE_RE = Regex("^ {0,3}(`{3,}|~{3,})([^\\n]*)$")
    private val EXTERNAL_SCRIPT_PAIR_RE = Regex(
        "<script\\b[^>]*\\bsrc\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)[^>]*>[\\s\\S]*?</script\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val EXTERNAL_SCRIPT_SELF_RE = Regex(
        "<script\\b[^>]*\\bsrc\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)[^>]*/\\s*>",
        RegexOption.IGNORE_CASE,
    )
    private val SANDBOX_MACRO_RE = Regex(
        "\\{\\{\\s*(char|user|scene|time|weather|chapter|hp|maxHp|mp|maxMp|gold|stats_json|cast_json|flags_json)\\s*\\}\\}",
        RegexOption.IGNORE_CASE,
    )
    private val HEAD_EXTRACT_RE = Regex("<head\\b[^>]*>([\\s\\S]*?)</head\\s*>", RegexOption.IGNORE_CASE)
    private val BODY_EXTRACT_RE = Regex("<body\\b[^>]*>([\\s\\S]*?)</body\\s*>", RegexOption.IGNORE_CASE)
    private val DOCTYPE_OPEN_RE = Regex("<!DOCTYPE\\s+html\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val HTML_CLOSE_RE = Regex("</html\\s*>", RegexOption.IGNORE_CASE)
    private val SCRIPT_CLOSE_RE = Regex("</script\\s*>", RegexOption.IGNORE_CASE)
    private val STYLE_CLOSE_RE = Regex("</style\\s*>", RegexOption.IGNORE_CASE)
    private val COMMENT_CLOSE = "-->"

    private val BLOCK_CONTAINER_TAGS = setOf(
        "div",
        "section",
        "details",
        "table",
        "article",
        "header",
        "main",
        "nav",
        "aside",
        "figure",
        "html",
    )
    private val RAW_BLOCK_START_RE = Regex(
        "(?:<!DOCTYPE\\s+html\\b[^>]*>|<(div|section|details|table|article|header|main|nav|aside|figure|html|style|script)\\b[^>]*>)",
        RegexOption.IGNORE_CASE,
    )

    val REGEX_PRESETS: List<RpRegexPreset> = listOf(
        RpRegexPreset(
            id = "status_card",
            zhName = "状态栏玻璃卡片",
            enName = "Glass Status Card",
            zhDesc = "将 <status>...</status> 键值对内容渲染为酒馆毛玻璃状态卡",
            enDesc = "Transforms <status>...</status> key-value blocks into a glassmorphic status card",
            script = RpRegexScript(
                id = "preset_status_card",
                name = "状态栏玻璃卡片",
                enabled = true,
                findRegex = "<status>([\\s\\S]*?)</status>",
                replaceString = """
<style>
.gu-st-card{border-radius:14px;padding:12px 14px;background:linear-gradient(145deg,rgba(255,255,255,0.10),rgba(255,255,255,0.03));border:1px solid var(--gu-border,rgba(148,163,184,0.28));backdrop-filter:blur(10px);box-shadow:0 6px 20px rgba(0,0,0,0.12);margin:6px 0;}
.gu-st-head{display:flex;align-items:center;justify-content:space-between;font-size:11px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:var(--gu-primary,#6b8aff);margin-bottom:8px;padding-bottom:6px;border-bottom:1px dashed var(--gu-border,rgba(148,163,184,0.25));}
.gu-st-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:6px 10px;}
.gu-st-row{display:flex;flex-direction:column;padding:6px 8px;border-radius:9px;background:rgba(128,138,168,0.09);border:1px solid rgba(128,138,168,0.14);}
.gu-st-k{font-size:10.5px;color:var(--gu-muted,#9ca3af);margin-bottom:2px;}
.gu-st-v{font-size:12.5px;font-weight:600;color:var(--gu-ink,#111827);word-break:break-word;}
.gu-st-raw{white-space:pre-wrap;font-size:12.5px;color:var(--gu-ink,#111827);line-height:1.55;}
</style>
<div class="gu-st-card"><div class="gu-st-head"><span>✦ STATUS</span><span>LIVE</span></div><div class="gu-st-body gu-st-raw">$1</div></div>
<script>(function(){var roots=document.querySelectorAll('.gu-st-body');roots.forEach(function(el){if(el.dataset.done)return;el.dataset.done='1';var lines=(el.textContent||'').split(/\r?\n/).map(function(s){return s.trim();}).filter(Boolean);var pairs=[];lines.forEach(function(line){var m=line.match(/^([^:：|]+)\s*[:：]\s*(.+)$/);if(m)pairs.push([m[1].trim(),m[2].trim()]);});if(pairs.length>=1){el.className='gu-st-body gu-st-grid';el.innerHTML='';pairs.forEach(function(p){var row=document.createElement('div');row.className='gu-st-row';var k=document.createElement('span');k.className='gu-st-k';k.textContent=p[0];var v=document.createElement('span');v.className='gu-st-v';v.textContent=p[1];row.appendChild(k);row.appendChild(v);el.appendChild(row);});}});})();</script>
                """.trimIndent(),
                placement = "ai_output",
                markdownOnly = true,
                promptOnly = false,
                flags = "gims",
            ),
        ),
        RpRegexPreset(
            id = "phone_chat",
            zhName = "手机聊天界面",
            enName = "Phone Chat UI",
            zhDesc = "将 <phone>...</phone> 转换为仿真微信/短信气泡对话窗",
            enDesc = "Renders <phone>...</phone> blocks as a realistic mobile chat bubble window",
            script = RpRegexScript(
                id = "preset_phone_chat",
                name = "手机聊天界面",
                enabled = true,
                findRegex = "<phone>([\\s\\S]*?)</phone>",
                replaceString = """
<style>
.gu-phone{border-radius:18px;overflow:hidden;border:1px solid var(--gu-border,rgba(148,163,184,0.32));background:var(--gu-surface,#181b24);box-shadow:0 8px 24px rgba(0,0,0,0.16);margin:6px 0;}
.gu-phone-bar{display:flex;align-items:center;justify-content:space-between;padding:8px 14px;background:rgba(128,138,168,0.12);border-bottom:1px solid var(--gu-border,rgba(148,163,184,0.22));font-size:12px;font-weight:700;color:var(--gu-ink,#111827);}
.gu-phone-dot{font-size:10px;color:var(--gu-muted,#9ca3af);font-weight:500;}
.gu-phone-log{padding:10px 12px;display:flex;flex-direction:column;gap:8px;white-space:pre-wrap;font-size:13px;}
.gu-msg{display:flex;flex-direction:column;max-width:84%;}
.gu-msg.me{align-self:flex-end;align-items:flex-end;}
.gu-msg.other{align-self:flex-start;align-items:flex-start;}
.gu-msg-who{font-size:10.5px;color:var(--gu-muted,#9ca3af);margin:0 4px 2px;}
.gu-bubble{padding:8px 11px;border-radius:14px;line-height:1.45;word-break:break-word;}
.gu-msg.me .gu-bubble{background:var(--gu-primary,#5b6cff);color:#fff;border-bottom-right-radius:4px;}
.gu-msg.other .gu-bubble{background:rgba(128,138,168,0.16);color:var(--gu-ink,#111827);border-bottom-left-radius:4px;}
.gu-sys-ts{align-self:center;font-size:10.5px;color:var(--gu-muted,#9ca3af);padding:2px 8px;border-radius:99px;background:rgba(128,138,168,0.10);}
</style>
<div class="gu-phone"><div class="gu-phone-bar"><span>💬 消息终端</span><span class="gu-phone-dot">5G · 98%</span></div><div class="gu-phone-log">$1</div></div>
<script>(function(){var logs=document.querySelectorAll('.gu-phone-log');var meName=(window.RP_STATE&&window.RP_STATE.playerName)||'';logs.forEach(function(el){if(el.dataset.done)return;el.dataset.done='1';var lines=(el.textContent||'').split(/\r?\n/).map(function(s){return s.trim();}).filter(Boolean);if(!lines.length)return;el.textContent='';lines.forEach(function(line){var m=line.match(/^([^:：]{1,18})\s*[:：]\s*(.+)$/);if(!m){var ts=document.createElement('div');ts.className='gu-sys-ts';ts.textContent=line;el.appendChild(ts);return;}var who=m[1].trim(),msg=m[2].trim();var isMe=(who==='我'||who.toLowerCase()==='me'||who.toLowerCase()==='user'||(meName&&who===meName));var row=document.createElement('div');row.className='gu-msg '+(isMe?'me':'other');var label=document.createElement('div');label.className='gu-msg-who';label.textContent=who;var b=document.createElement('div');b.className='gu-bubble';b.textContent=msg;row.appendChild(label);row.appendChild(b);el.appendChild(row);});});})();</script>
                """.trimIndent(),
                placement = "ai_output",
                markdownOnly = true,
                promptOnly = false,
                flags = "gims",
            ),
        ),
        RpRegexPreset(
            id = "quote_highlight",
            zhName = "内心独白高亮卡",
            enName = "Inner Thought Card",
            zhDesc = "将【内心：...】或 <thought>...</thought> 美化为柔和斜体引言卡片",
            enDesc = "Styles 【内心：...】 and <thought>...</thought> into subtle italic thought cards",
            script = RpRegexScript(
                id = "preset_quote_highlight",
                name = "内心独白高亮卡",
                enabled = true,
                findRegex = "(?:<thought>([\\s\\S]*?)</thought>|【内心[：:]\\s*([\\s\\S]*?)】)",
                replaceString = "<div style=\"margin:8px 0;padding:10px 14px;border-left:3px solid var(--gu-accent,#c84b5b);background:rgba(128,138,168,0.10);border-radius:0 12px 12px 0;font-style:italic;color:var(--gu-muted,#6b7280);font-size:13.5px;line-height:1.6;\"><span style=\"font-style:normal;font-size:10.5px;letter-spacing:0.08em;opacity:0.78;display:block;margin-bottom:3px;color:var(--gu-accent,#c84b5b);\">✦ INNER THOUGHT</span>$1$2</div>",
                placement = "ai_output",
                markdownOnly = true,
                promptOnly = false,
                flags = "gims",
            ),
        ),
        RpRegexPreset(
            id = "cleanup_ooc",
            zhName = "隐藏 OOC / 系统杂注",
            enName = "Strip OOC & System Notes",
            zhDesc = "仅在前端显示时隐藏多余的 (OOC: ...) 与 [系统提示: ...] 旁白",
            enDesc = "Hides stray (OOC: ...) and [系统提示: ...] notes on display only",
            script = RpRegexScript(
                id = "preset_cleanup_ooc",
                name = "隐藏 OOC / 系统杂注",
                enabled = true,
                findRegex = "(?:\\(\\s*OOC\\s*[:：][^)]*\\)|（\\s*OOC\\s*[:：][^）]*）|\\[\\s*(?:系统提示|OOC)\\s*[:：][^\\]]*\\]|【\\s*(?:系统提示|OOC)\\s*[:：][^】]*】)",
                replaceString = "",
                placement = "ai_output",
                markdownOnly = true,
                promptOnly = false,
                flags = "gi",
            ),
        ),
    )

    val STATUS_WIDGET_TEMPLATES: List<RpStatusWidgetTemplate> = listOf(
        RpStatusWidgetTemplate(
            id = "palace_hud",
            zhName = "深宫凤仪状态栏",
            enName = "Imperial Palace HUD",
            zhDesc = "绯金宫廷描金风格：展示幕次场景、四维属性条与在场人物实时好感",
            enDesc = "Royal crimson & gold imperial status bar with Chapter/Scene, stat gauges & live NPC affinity",
            html = """
<style>
.pal-hud{border-radius:12px;padding:8px 11px;background:linear-gradient(155deg,#3b0d17 0%,#591524 55%,#2b0a12 100%);border:1px solid rgba(229,193,123,0.55);color:#f9ecd2;box-shadow:inset 0 0 0 1px rgba(229,193,123,0.18),0 4px 14px rgba(45,8,16,0.32);font-family:"Songti SC","Noto Serif SC",Georgia,serif;}
.pal-top{display:flex;align-items:center;justify-content:space-between;gap:8px;padding-bottom:5px;margin-bottom:5px;border-bottom:1px solid rgba(229,193,123,0.28);}
.pal-chap{font-size:12px;line-height:1.25;font-weight:700;color:#f3d28f;letter-spacing:0.05em;}
.pal-scene{font-size:10px;line-height:1.25;color:rgba(249,236,210,0.82);margin-top:1px;}
.pal-seal{font-size:10px;padding:2px 7px;border-radius:999px;border:1px solid rgba(229,193,123,0.5);color:#f3d28f;background:rgba(229,193,123,0.10);white-space:nowrap;}
.pal-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:4px 8px;margin-bottom:6px;}
.pal-stat-hd{display:flex;justify-content:space-between;gap:2px;font-size:10px;line-height:1.25;color:#f5dfc0;margin-bottom:2px;}
.pal-stat-hd span:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.pal-bar{height:4px;border-radius:99px;background:rgba(255,255,255,0.12);overflow:hidden;}
.pal-fill{height:100%;border-radius:99px;background:linear-gradient(90deg,#c99b49,#f6db9c);}
.pal-cast{display:flex;flex-direction:column;gap:4px;padding-top:5px;border-top:1px dashed rgba(229,193,123,0.24);}
.pal-npc{display:flex;align-items:center;gap:6px;font-size:11px;line-height:1.25;}
.pal-npc-name{font-weight:700;color:#f8dfa8;min-width:48px;}
.pal-npc-mood{font-size:9.5px;padding:1px 5px;border-radius:5px;background:rgba(229,193,123,0.14);color:#f4d799;white-space:nowrap;}
.pal-npc-bar{flex:1;height:4px;border-radius:99px;background:rgba(255,255,255,0.12);overflow:hidden;}
.pal-npc-fill{height:100%;background:linear-gradient(90deg,#e05a6d,#f3c47b);}
.pal-npc-val{font-size:10px;color:rgba(249,236,210,0.88);min-width:44px;text-align:right;}
.pal-acts{display:flex;gap:6px;margin-top:6px;flex-wrap:wrap;}
.pal-btn{font-family:inherit;font-size:10.5px;line-height:1.25;padding:3px 9px;border-radius:999px;border:1px solid rgba(229,193,123,0.5);background:rgba(229,193,123,0.14);color:#f9e6be;cursor:pointer;}
.pal-btn:active{transform:scale(0.97);}
</style>
<div class="pal-hud">
  <div class="pal-top">
    <div>
      <div class="pal-chap">❖ {{chapter}}</div>
      <div class="pal-scene">{{scene}} · {{time}}</div>
    </div>
    <div class="pal-seal">银两 {{gold}}</div>
  </div>
  <div class="pal-stats" id="pal-stats"></div>
  <div class="pal-cast" id="pal-cast"></div>
  <div class="pal-acts">
    <button class="pal-btn" onclick="GuGuRp.sendAction('我不动声色地环顾席间神色，暗自记下众人动向。')">察言观色</button>
    <button class="pal-btn" onclick="GuGuRp.sendAction('我轻声向身旁宫女打听方才传来的宫中风声。')">打探风声</button>
  </div>
</div>
<script>
(function(){
  function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');}
  var st=window.RP_STATE||{};
  var statsEl=document.getElementById('pal-stats');
  var entries=Object.entries(st.stats||{});
  if(statsEl&&entries.length){
    entries.slice(0,6).forEach(function(kv){
      var v=Number(kv[1])||0,pct=Math.max(6,Math.min(100,v));
      var box=document.createElement('div');
      box.innerHTML='<div class="pal-stat-hd"><span>'+esc(kv[0])+'</span><span>'+v+'</span></div><div class="pal-bar"><div class="pal-fill" style="width:'+pct+'%"></div></div>';
      statsEl.appendChild(box);
    });
  }
  var castEl=document.getElementById('pal-cast');
  var npcs=(st.cast||[]).filter(function(c){return !c.isPlayer;});
  if(castEl&&npcs.length){
    npcs.slice(0,3).forEach(function(c){
      var aff=Number(c.affinity)||0,pct=Math.max(4,Math.min(100,Math.round((aff+100)/2)));
      var row=document.createElement('div');
      row.className='pal-npc';
      row.innerHTML='<span class="pal-npc-name">'+esc(c.name)+'</span><span class="pal-npc-mood">'+esc(c.mood||'平静')+'</span><div class="pal-npc-bar"><div class="pal-npc-fill" style="width:'+pct+'%"></div></div><span class="pal-npc-val">'+aff+'·'+esc(c.tierZh||'')+'</span>';
      castEl.appendChild(row);
    });
  } else if(castEl){castEl.style.display='none';}
})();
</script>
            """.trimIndent(),
        ),
        RpStatusWidgetTemplate(
            id = "tavern_glass",
            zhName = "酒馆玻璃态状态卡",
            enName = "Tavern Glass Card",
            zhDesc = "SillyTavern 现代毛玻璃卡片：HP/MP 进度条、场景天气、随身物品与一键快捷行动",
            enDesc = "Sleek SillyTavern glassmorphic status card with HP/MP bars, inventory chips & quick actions",
            html = """
<style>
.tv-card{border-radius:16px;padding:12px 14px;background:linear-gradient(145deg,rgba(107,138,255,0.14),rgba(24,27,36,0.06));border:1px solid var(--gu-border,rgba(148,163,184,0.28));backdrop-filter:blur(12px);color:var(--gu-ink,#111827);}
.tv-head{display:flex;align-items:flex-start;justify-content:space-between;gap:8px;margin-bottom:9px;}
.tv-title{font-size:13px;font-weight:700;color:var(--gu-ink,#111827);}
.tv-sub{font-size:11px;color:var(--gu-muted,#6b7280);margin-top:2px;}
.tv-gold{font-size:11px;font-weight:700;padding:3px 9px;border-radius:999px;background:rgba(245,158,11,0.16);color:#d97706;white-space:nowrap;}
.tv-bars{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:9px;}
.tv-meter-top{display:flex;justify-content:space-between;font-size:10.5px;font-weight:600;color:var(--gu-muted,#6b7280);margin-bottom:3px;}
.tv-track{height:6px;border-radius:99px;background:rgba(128,138,168,0.18);overflow:hidden;}
.tv-hp{height:100%;border-radius:99px;background:linear-gradient(90deg,#f43f5e,#fb7185);}
.tv-mp{height:100%;border-radius:99px;background:linear-gradient(90deg,#3b82f6,#818cf8);}
.tv-chips{display:flex;flex-wrap:wrap;gap:5px;margin-bottom:9px;}
.tv-chip{font-size:10.5px;padding:3px 8px;border-radius:8px;background:rgba(128,138,168,0.12);border:1px solid rgba(128,138,168,0.18);color:var(--gu-ink,#111827);}
.tv-acts{display:flex;gap:6px;flex-wrap:wrap;}
.tv-btn{font-size:11px;font-weight:600;padding:5px 10px;border-radius:10px;border:1px solid var(--gu-primary,#6b8aff);background:rgba(107,138,255,0.14);color:var(--gu-primary,#6b8aff);cursor:pointer;}
.tv-btn:active{opacity:0.75;}
</style>
<div class="tv-card">
  <div class="tv-head">
    <div>
      <div class="tv-title">📍 {{scene}}</div>
      <div class="tv-sub">{{time}} · {{weather}}</div>
    </div>
    <div class="tv-gold">✦ {{gold}}</div>
  </div>
  <div class="tv-bars">
    <div>
      <div class="tv-meter-top"><span>HP</span><span>{{hp}}/{{maxHp}}</span></div>
      <div class="tv-track"><div class="tv-hp" id="tv-hp-fill" style="width:100%"></div></div>
    </div>
    <div>
      <div class="tv-meter-top"><span>MP</span><span>{{mp}}/{{maxMp}}</span></div>
      <div class="tv-track"><div class="tv-mp" id="tv-mp-fill" style="width:100%"></div></div>
    </div>
  </div>
  <div class="tv-chips" id="tv-inv"></div>
  <div class="tv-acts">
    <button class="tv-btn" onclick="GuGuRp.sendAction('我仔细打量四周环境与人物神色。')">观察四周</button>
    <button class="tv-btn" onclick="GuGuRp.sendAction('我轻声开口，试探对方此刻真正的想法。')">主动搭话</button>
  </div>
</div>
<script>
(function(){
  var st=window.RP_STATE||{};
  var hpPct=st.maxHp>0?Math.max(0,Math.min(100,Math.round(st.hp*100/st.maxHp))):100;
  var mpPct=st.maxMp>0?Math.max(0,Math.min(100,Math.round(st.mp*100/st.maxMp))):100;
  var hpEl=document.getElementById('tv-hp-fill');if(hpEl)hpEl.style.width=hpPct+'%';
  var mpEl=document.getElementById('tv-mp-fill');if(mpEl)mpEl.style.width=mpPct+'%';
  var invEl=document.getElementById('tv-inv');
  var items=st.inventory||[];
  if(invEl&&items.length){
    items.slice(0,6).forEach(function(it){
      var sp=document.createElement('span');sp.className='tv-chip';sp.textContent='🎒 '+it;invEl.appendChild(sp);
    });
  } else if(invEl){invEl.style.display='none';}
})();
</script>
            """.trimIndent(),
        ),
        RpStatusWidgetTemplate(
            id = "cyber_hud",
            zhName = "赛博神经终端 HUD",
            enName = "Cyberpunk Neural HUD",
            zhDesc = "霓虹青色扫描线终端面板：实时显示神经负载、信用点、悬赏任务与系统旗标",
            enDesc = "Neon cyan scanline terminal showing neural load, credits, active missions & story flags",
            html = """
<style>
.cy-hud{position:relative;border-radius:12px;padding:12px 13px;background:linear-gradient(160deg,#071019 0%,#0c1926 100%);border:1px solid rgba(34,211,238,0.52);color:#cffafe;font-family:ui-monospace,"SFMono-Regular",Menlo,monospace;overflow:hidden;box-shadow:0 0 18px rgba(34,211,238,0.14);}
.cy-hud::before{content:"";position:absolute;inset:0;pointer-events:none;background:repeating-linear-gradient(0deg,rgba(34,211,238,0.05) 0,rgba(34,211,238,0.05) 1px,transparent 1px,transparent 4px);}
.cy-top{display:flex;justify-content:space-between;align-items:center;font-size:11px;color:#22d3ee;border-bottom:1px solid rgba(34,211,238,0.28);padding-bottom:6px;margin-bottom:8px;}
.cy-cred{color:#facc15;font-weight:700;}
.cy-loc{font-size:11.5px;color:#e0f2fe;margin-bottom:8px;}
.cy-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:8px;}
.cy-lbl{display:flex;justify-content:space-between;font-size:10px;color:#67e8f9;margin-bottom:3px;}
.cy-bar{height:5px;background:rgba(34,211,238,0.15);border-radius:2px;overflow:hidden;}
.cy-fill-hp{height:100%;background:#f43f5e;}
.cy-fill-mp{height:100%;background:#22d3ee;}
.cy-list{font-size:11px;color:#bae6fd;line-height:1.5;margin-bottom:8px;}
.cy-tags{display:flex;flex-wrap:wrap;gap:5px;margin-bottom:8px;}
.cy-tag{font-size:10px;padding:2px 7px;border:1px solid rgba(34,211,238,0.38);border-radius:4px;background:rgba(34,211,238,0.10);color:#67e8f9;}
.cy-btn{font-family:inherit;font-size:10.5px;padding:4px 9px;border-radius:4px;border:1px solid #22d3ee;background:rgba(34,211,238,0.15);color:#ecfeff;cursor:pointer;}
</style>
<div class="cy-hud">
  <div class="cy-top"><span>[NEURAL_LINK // ONLINE]</span><span class="cy-cred">¤ {{gold}} CR</span></div>
  <div class="cy-loc">&gt; {{scene}} | {{time}}</div>
  <div class="cy-grid">
    <div>
      <div class="cy-lbl"><span>VITALS</span><span>{{hp}}/{{maxHp}}</span></div>
      <div class="cy-bar"><div class="cy-fill-hp" id="cy-hp" style="width:100%"></div></div>
    </div>
    <div>
      <div class="cy-lbl"><span>NEURAL</span><span>{{mp}}/{{maxMp}}</span></div>
      <div class="cy-bar"><div class="cy-fill-mp" id="cy-mp" style="width:100%"></div></div>
    </div>
  </div>
  <div class="cy-list" id="cy-quests"></div>
  <div class="cy-tags" id="cy-flags"></div>
  <button class="cy-btn" onclick="GuGuRp.sendAction('我启动义眼热感与频段扫描，探测附近信号与潜伏目标。')">[SCAN_AREA]</button>
</div>
<script>
(function(){
  var st=window.RP_STATE||{};
  var hp=document.getElementById('cy-hp');if(hp&&st.maxHp>0)hp.style.width=Math.round(st.hp*100/st.maxHp)+'%';
  var mp=document.getElementById('cy-mp');if(mp&&st.maxMp>0)mp.style.width=Math.round(st.mp*100/st.maxMp)+'%';
  var qEl=document.getElementById('cy-quests');
  var qs=st.quests||[];
  if(qEl&&qs.length){qEl.textContent='OBJ: '+qs[0];}else if(qEl){qEl.style.display='none';}
  var fEl=document.getElementById('cy-flags');
  var fl=Object.entries(st.flags||{});
  var stEntries=Object.entries(st.stats||{});
  var chips=fl.map(function(e){return e[0]+'='+e[1];}).concat(stEntries.slice(0,4).map(function(e){return e[0]+':'+e[1];}));
  if(fEl&&chips.length){
    chips.slice(0,6).forEach(function(t){var s=document.createElement('span');s.className='cy-tag';s.textContent=t;fEl.appendChild(s);});
  } else if(fEl){fEl.style.display='none';}
})();
</script>
            """.trimIndent(),
        ),
        RpStatusWidgetTemplate(
            id = "romance_diary",
            zhName = "心动恋爱手账卡",
            enName = "Romance Diary Card",
            zhDesc = "暖粉橙光手账风：展示攻略对象心情、心动好感进度、关系阶段与赠礼/互动按钮",
            enDesc = "Warm blush-pink Chengguang romance diary with mood, heart gauge, tier & interactive triggers",
            html = """
<style>
.rm-card{border-radius:18px;padding:13px 14px;background:linear-gradient(145deg,rgba(255,241,245,0.96),rgba(255,228,236,0.92));border:1px solid rgba(244,114,182,0.38);color:#4a1d32;box-shadow:0 6px 18px rgba(244,114,182,0.14);}
.rm-card.dark{background:linear-gradient(145deg,rgba(66,24,43,0.92),rgba(42,18,32,0.92));color:#fce7f3;border-color:rgba(244,114,182,0.42);}
.rm-top{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px;}
.rm-who{font-size:14px;font-weight:700;display:flex;align-items:center;gap:6px;}
.rm-mood{font-size:10.5px;padding:2px 8px;border-radius:999px;background:rgba(244,114,182,0.20);color:#db2777;font-weight:600;}
.rm-card.dark .rm-mood{color:#f9a8d4;}
.rm-scene{font-size:11px;opacity:0.78;margin-bottom:8px;}
.rm-heart-hd{display:flex;justify-content:space-between;font-size:11px;font-weight:600;margin-bottom:4px;}
.rm-track{height:7px;border-radius:999px;background:rgba(244,114,182,0.20);overflow:hidden;margin-bottom:10px;}
.rm-fill{height:100%;border-radius:999px;background:linear-gradient(90deg,#fb7185,#f472b6);}
.rm-acts{display:flex;gap:7px;flex-wrap:wrap;}
.rm-btn{font-size:11.5px;font-weight:600;padding:5px 11px;border-radius:999px;border:1px solid rgba(244,114,182,0.48);background:rgba(244,114,182,0.16);color:inherit;cursor:pointer;}
.rm-btn:active{transform:scale(0.97);}
</style>
<div class="rm-card" id="rm-root">
  <div class="rm-top">
    <div class="rm-who"><span>♡</span><span id="rm-name">{{char}}</span></div>
    <span class="rm-mood" id="rm-mood">心动中</span>
  </div>
  <div class="rm-scene">✿ {{scene}} · {{weather}}</div>
  <div class="rm-heart-hd"><span id="rm-tier">羁绊阶段</span><span id="rm-aff">0</span></div>
  <div class="rm-track"><div class="rm-fill" id="rm-fill" style="width:50%"></div></div>
  <div class="rm-acts">
    <button class="rm-btn" onclick="GuGuRp.sendAction('我微笑着递上一份精心准备的小礼物，轻声问对方喜不喜欢。')">🎁 赠礼</button>
    <button class="rm-btn" onclick="GuGuRp.sendAction('我放缓脚步靠近些许，温柔地注视着对方的眼睛聊起近况。')">💬 温柔闲聊</button>
  </div>
</div>
<script>
(function(){
  var st=window.RP_STATE||{};
  var root=document.getElementById('rm-root');
  if(root&&st.isDark)root.classList.add('dark');
  var npc=(st.cast||[]).find(function(c){return !c.isPlayer;});
  if(npc){
    var n=document.getElementById('rm-name');if(n)n.textContent=npc.name;
    var m=document.getElementById('rm-mood');if(m)m.textContent=npc.mood||'平静';
    var t=document.getElementById('rm-tier');if(t)t.textContent='关系阶段 · '+(npc.tierZh||'初识');
    var aff=Number(npc.affinity)||0;
    var a=document.getElementById('rm-aff');if(a)a.textContent='♥ '+aff;
    var f=document.getElementById('rm-fill');if(f)f.style.width=Math.max(5,Math.min(100,Math.round((aff+100)/2)))+'%';
  }
})();
</script>
            """.trimIndent(),
        ),
    )

    fun findRegexPreset(id: String): RpRegexPreset? =
        REGEX_PRESETS.firstOrNull { it.id == id }

    fun findStatusWidgetTemplate(id: String): RpStatusWidgetTemplate =
        STATUS_WIDGET_TEMPLATES.firstOrNull { it.id == id } ?: STATUS_WIDGET_TEMPLATES.first()

    fun findStatusWidgetTemplateOrNull(id: String): RpStatusWidgetTemplate? =
        STATUS_WIDGET_TEMPLATES.firstOrNull { it.id == id }

    fun parseRegexLiteral(raw: String, fallbackFlags: String = "gms"): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("/")) {
            var slashIdx = trimmed.lastIndexOf('/')
            while (slashIdx > 0) {
                val tail = trimmed.substring(slashIdx + 1)
                if (VALID_JS_FLAGS_RE.matches(tail) && !isEscapedAt(trimmed, slashIdx)) {
                    val inner = unescapeForwardSlashes(trimmed.substring(1, slashIdx))
                    return inner to tail
                }
                slashIdx = trimmed.lastIndexOf('/', slashIdx - 1)
            }
        }
        return raw to fallbackFlags
    }

    fun formatRegexLiteral(pattern: String, flags: String): String {
        val cleanFlags = flags.filter { it in "gimsuy" }
        val escaped = escapeForwardSlashes(pattern)
        return "/$escaped/$cleanFlags"
    }

    fun applyScripts(
        text: String,
        scripts: List<RpRegexScript>,
        placement: String,
        forDisplay: Boolean,
        rp: RpConfig? = null,
    ): String {
        if (text.isEmpty() || scripts.isEmpty()) return text
        val targetPlacement = placement.trim().lowercase()
        var current = text
        for (script in scripts) {
            if (!script.enabled || script.findRegex.isBlank()) continue
            val scriptPlacement = script.placement.trim().lowercase().ifEmpty { "ai_output" }
            val placementMatches = scriptPlacement == "both" ||
                targetPlacement == "both" ||
                scriptPlacement == targetPlacement
            if (!placementMatches) continue

            val modeMatches = if (forDisplay) {
                !script.promptOnly || script.markdownOnly
            } else {
                !script.markdownOnly || script.promptOnly
            }
            if (!modeMatches) continue

            val (pattern, effectiveFlags) = parseRegexLiteral(script.findRegex, script.flags)
            if (pattern.isEmpty()) continue

            val lowerFlags = effectiveFlags.lowercase()
            val options = buildSet {
                if ('i' in lowerFlags) add(RegexOption.IGNORE_CASE)
                if ('m' in lowerFlags) add(RegexOption.MULTILINE)
                if ('s' in lowerFlags) add(RegexOption.DOT_MATCHES_ALL)
            }
            val isGlobal = 'g' in lowerFlags
            val replaceTemplate = if (rp != null) {
                RpEngine.replaceMacros(script.replaceString, rp)
            } else {
                script.replaceString
            }

            current = try {
                val regex = Regex(pattern, options)
                if (isGlobal) {
                    regex.replace(current) { m -> expandReplacement(replaceTemplate, m) }
                } else {
                    val first = regex.find(current)
                    if (first != null) {
                        val rep = expandReplacement(replaceTemplate, first)
                        current.substring(0, first.range.first) + rep + current.substring(first.range.last + 1)
                    } else {
                        current
                    }
                }
            } catch (_: Throwable) {
                current
            }
        }
        return current
    }

    fun splitMarkdownAndHtml(text: String, enableHtmlCards: Boolean): List<RpContentSegment> {
        if (!enableHtmlCards || text.isBlank()) {
            return listOf(RpContentSegment(isHtml = false, content = text))
        }

        val rawSegments = ArrayList<RpContentSegment>()
        var cursor = 0
        var mdStart = 0
        val n = text.length

        fun flushMarkdown(endIdx: Int) {
            if (endIdx > mdStart) {
                rawSegments.add(RpContentSegment(isHtml = false, content = text.substring(mdStart, endIdx)))
            }
        }

        while (cursor < n) {
            // Check fenced code block at line start
            val atLineStart = cursor == 0 || text[cursor - 1] == '\n'
            if (atLineStart) {
                val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) n else it }
                val line = text.substring(cursor, lineEnd)
                val fenceMatch = FENCE_LINE_RE.matchEntire(line)
                if (fenceMatch != null) {
                    val fenceMarker = fenceMatch.groupValues[1]
                    val fenceChar = fenceMarker[0]
                    val fenceLen = fenceMarker.length
                    val info = fenceMatch.groupValues[2].trim().lowercase()
                    val bodyStart = if (lineEnd < n) lineEnd + 1 else n
                    val closeBounds = findClosingFence(text, bodyStart, fenceChar, fenceLen)
                    if (closeBounds != null) {
                        val (bodyEnd, nextCursor) = closeBounds
                        if (info == "html" || info.startsWith("html ")) {
                            val htmlBody = text.substring(bodyStart, bodyEnd).trim()
                            if (htmlBody.isNotEmpty()) {
                                flushMarkdown(cursor)
                                rawSegments.add(RpContentSegment(isHtml = true, content = htmlBody))
                                cursor = nextCursor
                                mdStart = nextCursor
                                continue
                            }
                        }
                        // Non-HTML fenced block: skip over it untouched as markdown
                        cursor = nextCursor
                        continue
                    }
                }
            }

            // Check raw HTML container block starting at cursor
            if (text[cursor] == '<') {
                val blockEnd = matchRawHtmlBlockEnd(text, cursor)
                if (blockEnd != null && blockEnd > cursor) {
                    val snippet = text.substring(cursor, blockEnd)
                    // Avoid turning a lone <style> or <script> without any sibling HTML block into a card
                    val isAuxOnly = isAuxTagBlock(snippet)
                    if (!isAuxOnly || hasAdjacentHtmlContainer(text, blockEnd, rawSegments)) {
                        flushMarkdown(cursor)
                        rawSegments.add(RpContentSegment(isHtml = true, content = snippet))
                        cursor = blockEnd
                        mdStart = blockEnd
                        continue
                    }
                }
            }

            cursor++
        }

        flushMarkdown(n)

        if (rawSegments.none { it.isHtml }) {
            return listOf(RpContentSegment(isHtml = false, content = text))
        }

        // Merge adjacent HTML segments separated only by whitespace (e.g. <style>...</style> + <div>...</div> + <script>...</script>)
        val merged = ArrayList<RpContentSegment>()
        var i = 0
        while (i < rawSegments.size) {
            val seg = rawSegments[i]
            if (seg.isHtml) {
                val combined = StringBuilder(seg.content)
                var j = i + 1
                while (j < rawSegments.size) {
                    val next = rawSegments[j]
                    if (next.isHtml) {
                        combined.append('\n').append(next.content)
                        j++
                    } else if (next.content.isBlank() && j + 1 < rawSegments.size && rawSegments[j + 1].isHtml) {
                        combined.append(next.content).append(rawSegments[j + 1].content)
                        j += 2
                    } else {
                        break
                    }
                }
                merged.add(RpContentSegment(isHtml = true, content = combined.toString().trim()))
                i = j
            } else {
                val trimmedMd = seg.content.trim()
                if (trimmedMd.isNotEmpty()) {
                    merged.add(RpContentSegment(isHtml = false, content = trimmedMd))
                }
                i++
            }
        }

        return merged.ifEmpty { listOf(RpContentSegment(isHtml = false, content = text)) }
    }

    fun buildSandboxHtmlDocument(
        fragmentHtml: String,
        rp: RpConfig? = null,
        isDark: Boolean = false,
        customCss: String = "",
        primaryHex: String = "#5B6CFF",
        accentHex: String = "#E11D48",
        surfaceHex: String = if (isDark) "#181B24" else "#FFFFFF",
        inkHex: String = if (isDark) "#F3F4F6" else "#111827",
        mutedHex: String = if (isDark) "#9CA3AF" else "#6B7280",
        borderHex: String = if (isDark) "#2D3343" else "#E5E7EB",
    ): String {
        val sanitized = stripExternalScripts(fragmentHtml)
        val expanded = if (rp != null) expandSandboxMacros(sanitized, rp) else sanitized

        val extractedHead: String
        val extractedBody: String
        val bodyMatch = BODY_EXTRACT_RE.find(expanded)
        if (bodyMatch != null) {
            extractedHead = HEAD_EXTRACT_RE.find(expanded)?.groupValues?.get(1).orEmpty()
            extractedBody = bodyMatch.groupValues[1]
        } else {
            extractedHead = ""
            extractedBody = expanded
        }

        val stateJson = buildRpStateJson(rp, isDark).replace("</", "<\\/")
        val safeCustomCss = customCss.replace("</style", "<\\/style", ignoreCase = true)
        val colorScheme = if (isDark) "dark" else "light"

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">")
            appendLine("<style>")
            appendLine(":root{")
            appendLine("  --gu-primary:$primaryHex;")
            appendLine("  --gu-accent:$accentHex;")
            appendLine("  --gu-surface:$surfaceHex;")
            appendLine("  --gu-ink:$inkHex;")
            appendLine("  --gu-muted:$mutedHex;")
            appendLine("  --gu-border:$borderHex;")
            appendLine("  --bg:$surfaceHex;")
            appendLine("  --fg:$inkHex;")
            appendLine("  color-scheme:$colorScheme;")
            appendLine("}")
            appendLine("*{box-sizing:border-box;}")
            appendLine("html,body{margin:0;padding:0;background:transparent;color:var(--gu-ink);font-family:-apple-system,BlinkMacSystemFont,\"PingFang SC\",\"Noto Sans SC\",sans-serif;font-size:14px;line-height:1.5;overflow-x:hidden;-webkit-text-size-adjust:100%;}")
            appendLine("img,svg,video{max-width:100%;height:auto;}")
            appendLine("table{max-width:100%;border-collapse:collapse;}")
            if (safeCustomCss.isNotBlank()) {
                appendLine(safeCustomCss)
            }
            appendLine("</style>")
            if (extractedHead.isNotBlank()) {
                appendLine(extractedHead)
            }
            appendLine("<script>")
            appendLine("window.RP_STATE=$stateJson;")
            appendLine("window.GuGuRp={")
            appendLine("  sendAction:function(text){try{if(window.GuGuBridge&&typeof window.GuGuBridge.sendAction==='function'){window.GuGuBridge.sendAction(String(text||''));}}catch(e){}},")
            appendLine("  setFlag:function(k,v){try{if(window.GuGuBridge&&typeof window.GuGuBridge.setFlag==='function'){window.GuGuBridge.setFlag(String(k||''),String(v==null?'':v));}}catch(e){}},")
            appendLine("  adjustAffinity:function(name,delta){try{if(window.GuGuBridge&&typeof window.GuGuBridge.adjustAffinity==='function'){window.GuGuBridge.adjustAffinity(String(name||''),Number(delta)||0);}}catch(e){}}")
            appendLine("};")
            appendLine("</script>")
            appendLine("</head>")
            appendLine("<body>")
            append("<div id=\"gu-sandbox-root\">")
            append(extractedBody)
            appendLine("</div>")
            appendLine("<script>")
            appendLine("(function(){")
            appendLine("  var lastH=0;")
            appendLine("  function reportHeight(){")
            appendLine("    try{")
            appendLine("      var el=document.getElementById('gu-sandbox-root')||document.body;")
            appendLine("      var h=Math.ceil(Math.max(el.scrollHeight||0,el.getBoundingClientRect().height||0));")
            appendLine("      if(h>0&&h!==lastH){lastH=h;if(window.GuGuBridge&&typeof window.GuGuBridge.onHeight==='function'){window.GuGuBridge.onHeight(h);}}")
            appendLine("    }catch(e){}")
            appendLine("  }")
            appendLine("  window.addEventListener('load',reportHeight);")
            appendLine("  document.addEventListener('DOMContentLoaded',reportHeight);")
            appendLine("  if(typeof ResizeObserver!=='undefined'){var ro=new ResizeObserver(reportHeight);ro.observe(document.documentElement);if(document.body)ro.observe(document.body);}")
            appendLine("  setTimeout(reportHeight,50);")
            appendLine("  setTimeout(reportHeight,220);")
            appendLine("})();")
            appendLine("</script>")
            appendLine("</body>")
            append("</html>")
        }
    }

    private fun stripExternalScripts(html: String): String {
        var out = EXTERNAL_SCRIPT_SELF_RE.replace(html, "")
        out = EXTERNAL_SCRIPT_PAIR_RE.replace(out, "")
        return out
    }

    private fun expandSandboxMacros(text: String, rp: RpConfig): String {
        if (!text.contains("{{")) return text
        val leadChar = rp.cast.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() } ?: rp.title
        val userName = rp.playerName.ifBlank { "旅人" }
        val chapter = rp.state.chapterTitle.ifBlank { "第${rp.state.actNumber}幕" }
        val statsJson by lazy { buildStatsJson(rp.state.stats).toString().replace("</", "<\\/") }
        val castJson by lazy { buildCastJson(rp.cast).toString().replace("</", "<\\/") }
        val flagsJson by lazy { buildFlagsJson(rp.state.flags).toString().replace("</", "<\\/") }

        return SANDBOX_MACRO_RE.replace(text) { m ->
            when (m.groupValues[1].lowercase()) {
                "char" -> leadChar
                "user" -> userName
                "scene" -> rp.state.scene
                "time" -> rp.state.time
                "weather" -> rp.state.weather
                "chapter" -> chapter
                "hp" -> rp.state.hp.toString()
                "maxhp" -> rp.state.maxHp.toString()
                "mp" -> rp.state.mp.toString()
                "maxmp" -> rp.state.maxMp.toString()
                "gold" -> rp.state.gold.toString()
                "stats_json" -> statsJson
                "cast_json" -> castJson
                "flags_json" -> flagsJson
                else -> m.value
            }
        }
    }

    private fun buildStatsJson(stats: Map<String, Int>): JsonObject = buildJsonObject {
        stats.forEach { (k, v) -> put(k, v) }
    }

    private fun buildFlagsJson(flags: Map<String, String>): JsonObject = buildJsonObject {
        flags.forEach { (k, v) -> put(k, v) }
    }

    private fun buildCastJson(cast: List<RpCharacter>): JsonArray = buildJsonArray {
        cast.forEach { c ->
            val tier = RpEngine.affinityTier(c.affinity)
            add(
                buildJsonObject {
                    put("id", c.id)
                    put("name", c.name)
                    put("role", c.role)
                    put("affinity", c.affinity)
                    put("tierCode", tier.code)
                    put("tierZh", tier.zhLabel)
                    put("tierEn", tier.enLabel)
                    put("mood", c.mood)
                    put("expression", RpEngine.resolveExpressionKey(c.mood))
                    put("status", c.status)
                    put("inScene", c.inScene)
                    put("isPlayer", c.isPlayer)
                    put("stageSlot", c.stageSlot)
                },
            )
        }
    }

    private fun buildRpStateJson(rp: RpConfig?, isDark: Boolean): String {
        val obj = if (rp == null) {
            buildJsonObject {
                put("title", "")
                put("playerName", "")
                put("leadChar", "")
                put("scene", "")
                put("time", "")
                put("weather", "")
                put("mood", "")
                put("chapter", "")
                put("actNumber", 1)
                put("hp", 100)
                put("maxHp", 100)
                put("mp", 50)
                put("maxMp", 50)
                put("level", 1)
                put("gold", 0)
                put("stats", buildJsonObject {})
                put("inventory", buildJsonArray {})
                put("quests", buildJsonArray {})
                put("flags", buildJsonObject {})
                put("cast", buildJsonArray {})
                put("isDark", isDark)
            }
        } else {
            val leadChar = rp.cast.firstOrNull { !it.isPlayer }?.name?.takeIf { it.isNotBlank() } ?: rp.title
            val chapter = rp.state.chapterTitle.ifBlank { "第${rp.state.actNumber}幕" }
            buildJsonObject {
                put("title", rp.title)
                put("playerName", rp.playerName)
                put("leadChar", leadChar)
                put("playStyle", rp.playStyle.name)
                put("scene", RpEngine.replaceMacros(rp.state.scene, rp))
                put("time", rp.state.time)
                put("weather", rp.state.weather)
                put("mood", rp.state.mood)
                put("chapter", RpEngine.replaceMacros(chapter, rp))
                put("actNumber", rp.state.actNumber)
                put("hp", rp.state.hp)
                put("maxHp", rp.state.maxHp)
                put("mp", rp.state.mp)
                put("maxMp", rp.state.maxMp)
                put("level", rp.state.level)
                put("gold", rp.state.gold)
                put("stats", buildStatsJson(rp.state.stats))
                put("inventory", buildJsonArray { rp.state.inventory.forEach { add(JsonPrimitive(RpEngine.replaceMacros(it, rp))) } })
                put("quests", buildJsonArray { rp.state.quests.forEach { add(JsonPrimitive(RpEngine.replaceMacros(it, rp))) } })
                put("flags", buildFlagsJson(rp.state.flags))
                put("cast", buildCastJson(rp.cast))
                put("isDark", isDark)
            }
        }
        return obj.toString()
    }

    private fun expandReplacement(template: String, match: MatchResult): String {
        val sb = StringBuilder(template.length + match.value.length)
        val len = template.length
        val maxGroup = match.groups.size - 1
        var i = 0
        while (i < len) {
            if (i + 9 <= len && template.regionMatches(i, "{{match}}", 0, 9, ignoreCase = true)) {
                sb.append(match.value)
                i += 9
                continue
            }
            val ch = template[i]
            if (ch == '$' && i + 1 < len) {
                val next = template[i + 1]
                when {
                    next == '$' -> {
                        sb.append('$')
                        i += 2
                        continue
                    }
                    next == '&' -> {
                        sb.append(match.value)
                        i += 2
                        continue
                    }
                    next in '0'..'9' -> {
                        val twoDigit = if (i + 2 < len && template[i + 2] in '0'..'9') {
                            (next - '0') * 10 + (template[i + 2] - '0')
                        } else {
                            -1
                        }
                        if (twoDigit in 1..maxGroup) {
                            sb.append(match.groups[twoDigit]?.value.orEmpty())
                            i += 3
                            continue
                        }
                        val oneDigit = next - '0'
                        if (oneDigit in 0..maxGroup) {
                            sb.append(match.groups[oneDigit]?.value.orEmpty())
                            i += 2
                            continue
                        }
                    }
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun findClosingFence(text: String, start: Int, fenceChar: Char, fenceLen: Int): Pair<Int, Int>? {
        var pos = start
        val n = text.length
        while (pos <= n) {
            val lineEnd = text.indexOf('\n', pos).let { if (it < 0) n else it }
            val line = text.substring(pos, lineEnd)
            val trimmed = line.trimStart(' ')
            val leadingSpaces = line.length - trimmed.length
            if (leadingSpaces <= 3 && trimmed.length >= fenceLen &&
                trimmed.takeWhile { it == fenceChar }.length >= fenceLen &&
                trimmed.dropWhile { it == fenceChar }.isBlank()
            ) {
                val nextCursor = if (lineEnd < n) lineEnd + 1 else n
                return pos to nextCursor
            }
            if (lineEnd >= n) break
            pos = lineEnd + 1
        }
        return null
    }

    private fun matchRawHtmlBlockEnd(text: String, start: Int): Int? {
        val m = RAW_BLOCK_START_RE.matchAt(text, start) ?: return null
        val matchedText = m.value

        if (DOCTYPE_OPEN_RE.matches(matchedText)) {
            val htmlClose = HTML_CLOSE_RE.find(text, m.range.last + 1) ?: return null
            return htmlClose.range.last + 1
        }

        val tagName = m.groupValues[1].lowercase()
        if (matchedText.trimEnd().endsWith("/>")) {
            return m.range.last + 1
        }

        if (tagName == "style") {
            val close = STYLE_CLOSE_RE.find(text, m.range.last + 1) ?: return null
            return close.range.last + 1
        }
        if (tagName == "script") {
            val close = SCRIPT_CLOSE_RE.find(text, m.range.last + 1) ?: return null
            return close.range.last + 1
        }
        if (tagName !in BLOCK_CONTAINER_TAGS) return null

        // Scan forward balancing nested same-name tags while skipping comments, <script>, and <style>
        var depth = 1
        var pos = m.range.last + 1
        val n = text.length
        while (pos < n) {
            val nextLt = text.indexOf('<', pos)
            if (nextLt < 0) return null
            if (text.startsWith("<!--", nextLt)) {
                val endComment = text.indexOf(COMMENT_CLOSE, nextLt + 4)
                pos = if (endComment < 0) n else endComment + COMMENT_CLOSE.length
                continue
            }
            // Skip nested <script>...</script> and <style>...</style> bodies cleanly
            if (tagName != "script" && startsWithTag(text, nextLt, "script")) {
                val openEnd = findTagCloseBracket(text, nextLt) ?: return null
                val closeScript = SCRIPT_CLOSE_RE.find(text, openEnd + 1) ?: return null
                pos = closeScript.range.last + 1
                continue
            }
            if (tagName != "style" && startsWithTag(text, nextLt, "style")) {
                val openEnd = findTagCloseBracket(text, nextLt) ?: return null
                val closeStyle = STYLE_CLOSE_RE.find(text, openEnd + 1) ?: return null
                pos = closeStyle.range.last + 1
                continue
            }

            if (startsWithClosingTag(text, nextLt, tagName)) {
                val closeEnd = findTagCloseBracket(text, nextLt) ?: return null
                depth--
                pos = closeEnd + 1
                if (depth == 0) return pos
                continue
            }
            if (startsWithTag(text, nextLt, tagName)) {
                val openEnd = findTagCloseBracket(text, nextLt) ?: return null
                val tagStr = text.substring(nextLt, openEnd + 1)
                if (!tagStr.trimEnd().endsWith("/>")) {
                    depth++
                }
                pos = openEnd + 1
                continue
            }
            pos = nextLt + 1
        }
        return null
    }

    private fun isAuxTagBlock(snippet: String): Boolean {
        val lower = snippet.trimStart().lowercase()
        return lower.startsWith("<style") || lower.startsWith("<script")
    }

    private fun hasAdjacentHtmlContainer(
        text: String,
        afterEnd: Int,
        priorSegments: List<RpContentSegment>,
    ): Boolean {
        // Check if preceded immediately (ignoring blank markdown) by an HTML segment
        for (idx in priorSegments.indices.reversed()) {
            val seg = priorSegments[idx]
            if (seg.isHtml) return true
            if (seg.content.isNotBlank()) break
        }
        // Look ahead past whitespace for an HTML container block
        var p = afterEnd
        while (p < text.length && text[p].isWhitespace()) p++
        while (p < text.length && text[p] == '<') {
            val nextEnd = matchRawHtmlBlockEnd(text, p) ?: return false
            val nextSnippet = text.substring(p, nextEnd)
            if (!isAuxTagBlock(nextSnippet)) return true
            p = nextEnd
            while (p < text.length && text[p].isWhitespace()) p++
        }
        return false
    }

    private fun startsWithTag(text: String, index: Int, tagName: String): Boolean {
        val after = index + 1 + tagName.length
        if (after > text.length) return false
        if (text[index] != '<') return false
        if (!text.regionMatches(index + 1, tagName, 0, tagName.length, ignoreCase = true)) return false
        if (after == text.length) return false
        val boundary = text[after]
        return boundary == '>' || boundary == '/' || boundary.isWhitespace()
    }

    private fun startsWithClosingTag(text: String, index: Int, tagName: String): Boolean {
        val after = index + 2 + tagName.length
        if (after > text.length) return false
        if (text[index] != '<' || text[index + 1] != '/') return false
        if (!text.regionMatches(index + 2, tagName, 0, tagName.length, ignoreCase = true)) return false
        if (after == text.length) return false
        val boundary = text[after]
        return boundary == '>' || boundary.isWhitespace()
    }

    private fun findTagCloseBracket(text: String, start: Int): Int? {
        var inSingle = false
        var inDouble = false
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                '>' -> if (!inSingle && !inDouble) return i
            }
            i++
        }
        return null
    }

    private fun isEscapedAt(s: String, index: Int): Boolean {
        var backslashes = 0
        var p = index - 1
        while (p >= 0 && s[p] == '\\') {
            backslashes++
            p--
        }
        return backslashes % 2 == 1
    }

    private fun unescapeForwardSlashes(pattern: String): String {
        val sb = StringBuilder(pattern.length)
        var i = 0
        while (i < pattern.length) {
            if (pattern[i] == '\\' && i + 1 < pattern.length && pattern[i + 1] == '/' && !isEscapedAt(pattern, i)) {
                sb.append('/')
                i += 2
            } else {
                sb.append(pattern[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun escapeForwardSlashes(pattern: String): String {
        val sb = StringBuilder(pattern.length + 4)
        var i = 0
        while (i < pattern.length) {
            if (pattern[i] == '/' && !isEscapedAt(pattern, i)) {
                sb.append("\\/")
            } else {
                sb.append(pattern[i])
            }
            i++
        }
        return sb.toString()
    }
}
