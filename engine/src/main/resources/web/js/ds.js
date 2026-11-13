// The Driver Station side panel: OpMode selection, INIT/START/STOP, telemetry, gamepads, devices, build, log.

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const BUTTON_LABELS = ['A', 'B', 'X', 'Y', 'LB', 'RB', 'LT', 'RT', 'BACK', 'START', 'LS', 'RS', '▲', '▼', '◀', '▶', 'GUIDE'];

/** Keeps only simple formatting tags from HTML telemetry. */
function sanitizeHtml(html) {
  const allowed = new Set(['B', 'I', 'U', 'BR', 'BIG', 'SMALL', 'TT', 'STRONG', 'EM', 'FONT', 'SPAN', 'P', 'H1', 'H2', 'H3', 'H4', 'SUB', 'SUP']);
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
  const walk = (node) => {
    for (const child of Array.from(node.childNodes)) {
      if (child.nodeType === Node.ELEMENT_NODE) {
        if (!allowed.has(child.tagName)) { child.replaceWith(...child.childNodes); walk(node); return; }
        for (const attr of Array.from(child.attributes)) {
          if (attr.name === 'color' && /^#?[\w]+$/.test(attr.value)) continue;
          if (attr.name === 'style' && /^\s*(color|background-color)\s*:\s*[#\w(),.\s%]+;?\s*$/i.test(attr.value)) continue;
          child.removeAttribute(attr.name);
        }
        walk(child);
      }
    }
  };
  walk(doc.body.firstChild);
  return doc.body.firstChild.innerHTML;
}

export class DriverStation {
  constructor(send, input) {
    this.send = send;
    this.input = input;
    this.opmodes = [];
    this.ds = { state: 'IDLE' };
    this.showSamples = false;
    this.samplesTouched = false;
    this.logLines = [];

    $('opmodeSelect').addEventListener('change', (e) => {
      // Remember the choice until the simulator confirms it, so an older update can't undo it.
      this.pendingSelect = e.target.value;
      this.send({ type: 'select', opmode: e.target.value });
    });
    $('showSamples').addEventListener('change', (e) => { this.showSamples = e.target.checked; this.samplesTouched = true; this.renderOpModes(); });
    $('mainBtn').addEventListener('click', () => this.mainAction());
    $('stopBtn').addEventListener('click', () => this.send({ type: 'stop' }));
    $('autoTimer').addEventListener('change', (e) => this.send({ type: 'timer', enabled: e.target.checked }));
    $('errorDismiss').addEventListener('click', () => this.send({ type: 'clearError' }));
    $('errorFix').addEventListener('click', () => { if (this.ds.fixRobot) this.send({ type: 'robot', file: this.ds.fixRobot }); });
    $('buildBtn').addEventListener('click', () => this.send({ type: 'build' }));
    for (const b of document.querySelectorAll('.tabs button')) {
      b.addEventListener('click', () => {
        for (const o of document.querySelectorAll('.tabs button')) o.classList.toggle('on', o === b);
        for (const t of document.querySelectorAll('.tab')) t.hidden = t.dataset.tab !== b.dataset.tab;
      });
    }
    for (const slot of [1, 2]) {
      $(`gp${slot}Source`).addEventListener('change', (e) => this.input.setSource(slot, e.target.value));
      this.buildGamepadViz(slot);
    }
    input.onChange(() => this.renderGamepadSources());
    this.renderGamepadSources();
    setInterval(() => this.renderGamepads(), 50);
  }

  mainAction() {
    const st = this.ds.state;
    if (st === 'IDLE') {
      this.pendingSelect = undefined;
      this.send({ type: 'init', opmode: $('opmodeSelect').value });
    }
    else if (st === 'INIT') this.send({ type: 'start' });
    else if (st === 'RUNNING') this.send({ type: 'stop' });
  }

  // ---- OpModes -------------------------------------------------------------------------------

  setBuild(b) {
    this.opmodes = b.opmodes || this.opmodes;
    if (!this.samplesTouched) {
      // With no OpModes of your own enabled, show the SDK samples so there is something to run.
      this.showSamples = !this.opmodes.some((o) => !o.sample && !o.disabled);
      $('showSamples').checked = this.showSamples;
    }
    this.renderOpModes();
    const msg = $('buildMsg');
    if (b.building) { msg.textContent = 'Building…'; msg.className = 'build-msg'; }
    else if (b.ok === undefined) { msg.textContent = ''; }
    else { msg.textContent = b.message + (b.pending ? ' · rebuild waiting for the OpMode to stop' : ''); msg.className = 'build-msg ' + (b.ok ? 'ok' : 'fail'); }
    $('buildRoots').innerHTML = (b.roots || []).map((r) => esc(r)).join('<br>');
    const skipped = b.skipped || [];
    $('skipped').innerHTML = skipped.map((s) => `<li><code>${esc(s.file)}</code><span class="why">${esc(s.reason)}</span></li>`).join('');
    const badge = $('buildBadge');
    badge.hidden = !(skipped.length || b.ok === false);
    badge.textContent = b.ok === false ? '!' : String(skipped.length);
  }

  visibleOpModes() {
    return this.opmodes.filter((o) => (o.sample ? this.showSamples : !o.disabled || this.showSamples));
  }

  renderOpModes() {
    const sel = $('opmodeSelect');
    const list = this.visibleOpModes();
    const groups = [['AUTONOMOUS', 'Autonomous'], ['TELEOP', 'TeleOp']];
    let html = '';
    for (const [kind, label] of groups) {
      const items = list.filter((o) => o.kind === kind);
      if (!items.length) continue;
      html += `<optgroup label="${label}">` + items.map((o) =>
        `<option value="${esc(o.name)}">${esc(o.name)}${o.group ? ' · ' + esc(o.group) : ''}${o.disabled && !o.sample ? ' (@Disabled)' : ''}</option>`).join('') + '</optgroup>';
    }
    if (!html) html = '<option value="">No OpModes found</option>';
    sel.innerHTML = html;
    if (this.ds.selected && list.some((o) => o.name === this.ds.selected)) sel.value = this.ds.selected;
    else if (list.length && this.ds.state === 'IDLE' && sel.value && sel.value !== this.ds.selected) this.send({ type: 'select', opmode: sel.value });
    this.renderControls();
  }

  setDs(ds) {
    this.ds = ds;
    const sel = $('opmodeSelect');
    if (this.pendingSelect !== undefined) {
      if (ds.selected === this.pendingSelect || ds.state !== 'IDLE') this.pendingSelect = undefined;
      else ds.selected = this.pendingSelect;
    }
    if (ds.selected && Array.from(sel.options).some((o) => o.value === ds.selected)) sel.value = ds.selected;
    else if (ds.selected && !this.showSamples && this.opmodes.some((o) => o.name === ds.selected && o.sample)) {
      this.showSamples = true;
      $('showSamples').checked = true;
      this.renderOpModes();
    }
    $('autoTimer').checked = !!ds.timer;
    const err = $('errorBanner');
    err.hidden = !ds.error;
    $('errorText').textContent = ds.error || '';
    const fix = $('errorFix');
    fix.hidden = !ds.fixRobot;
    fix.textContent = ds.fixRobot ? `Use ${ds.fixRobot}` : '';
    const warn = $('warnBanner');
    warn.hidden = !ds.warning;
    warn.textContent = ds.warning || '';
    this.renderControls();
  }

  renderControls() {
    const st = this.ds.state;
    const btn = $('mainBtn');
    const hasSel = !!$('opmodeSelect').value;
    btn.className = 'big ' + { IDLE: 'init', INIT: 'start', RUNNING: 'running', STOPPING: 'stopping' }[st];
    btn.textContent = { IDLE: 'INIT', INIT: '▶  START', RUNNING: '■  STOP', STOPPING: 'Stopping…' }[st];
    btn.disabled = st === 'STOPPING' || (st === 'IDLE' && !hasSel);
    $('stopBtn').disabled = !(st === 'INIT' || st === 'RUNNING');
    $('opmodeSelect').disabled = st !== 'IDLE';
    const robotSel = $('robotSelect');
    robotSel.disabled = st !== 'IDLE';
    robotSel.closest('label').title = st === 'IDLE' ? 'Robot description (robots/*.json)' : 'Stop the OpMode to change the robot';
    const pill = $('dsState');
    pill.className = 'pill ' + st.toLowerCase();
    pill.textContent = st === 'IDLE' ? 'Stopped' : st === 'INIT' ? `Init · ${this.ds.active}` : st === 'RUNNING' ? `Running · ${this.ds.active}` : 'Stopping';
    if (st === 'IDLE') $('timer').textContent = '';
  }

  setTimer(ds) {
    const t = $('timer');
    if (ds.remaining !== undefined) {
      const r = Math.ceil(ds.remaining);
      t.textContent = `${Math.floor(r / 60)}:${String(r % 60).padStart(2, '0')}`;
      t.classList.toggle('low', r <= 5);
    } else if (ds.elapsed !== undefined) {
      const e = Math.floor(ds.elapsed);
      t.textContent = `${Math.floor(e / 60)}:${String(e % 60).padStart(2, '0')}`;
      t.classList.remove('low');
    }
  }

  // ---- telemetry & logs -----------------------------------------------------------------------

  setTelemetry(tm) {
    const el = $('telemetry');
    el.className = 'telemetry' + (tm.format === 'CLASSIC' ? ' classic' : '');
    if (!tm.lines.length) el.innerHTML = '<span class="empty">No telemetry</span>';
    else if (tm.format === 'HTML') el.innerHTML = tm.lines.map((l) => sanitizeHtml(l)).join('<br>');
    else el.textContent = tm.lines.join('\n');
    $('telemetryLog').textContent = (tm.log || []).join('\n');
  }

  clearTelemetry() {
    $('telemetry').innerHTML = '<span class="empty">No telemetry</span>';
    $('telemetryLog').textContent = '';
  }

  addLog(entry) {
    this.logLines.push(entry);
    if (this.logLines.length > 300) this.logLines.shift();
    const el = $('robotLog');
    const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 4;
    const div = document.createElement('div');
    div.className = entry.level;
    div.textContent = `${entry.level}/${entry.tag}: ${entry.msg}`;
    el.appendChild(div);
    while (el.childNodes.length > 300) el.removeChild(el.firstChild);
    if (atBottom) el.scrollTop = el.scrollHeight;
  }

  // ---- devices -------------------------------------------------------------------------------

  setDevices(d, onLed) {
    const bar = (v) => {
      if (v === null || v === undefined) return '<span class="bar"></span>';
      const w = Math.min(1, Math.abs(v)) * 23;
      return `<span class="bar"><i class="${v < 0 ? 'neg' : ''}" style="left:${v < 0 ? 23 - w : 23}px;width:${w}px"></i></span>`;
    };
    const fmt = (v, n = 2) => (v === null || v === undefined ? '–' : Number(v).toFixed(n));
    $('motorTable').tBodies[0].innerHTML = d.motors.map((m) => {
      const power = m.power !== null ? `${bar(m.power)}${fmt(m.power)}` : `${bar(m.duty)}v ${m.velocityTarget}`;
      const target = m.target !== null && m.mode === 'RUN_TO_POSITION' ? ` → ${m.target}` : '';
      const mode = { RUN_WITHOUT_ENCODER: 'NO_ENC', RUN_USING_ENCODER: 'USING_ENC', RUN_TO_POSITION: 'TO_POS', STOP_AND_RESET_ENCODER: 'RESET' }[m.mode] || m.mode;
      return `<tr title="${esc(m.hub)} motor port ${m.port}, ${esc(m.zpb)}"><td>${esc(m.name)}</td><td>${mode}</td><td>${power}</td><td>${m.pos}${target}</td><td>${m.vel}</td><td>${fmt(m.amps, 1)}</td></tr>`;
    }).join('');
    $('servoTable').tBodies[0].innerHTML = d.servos.map((s) =>
      `<tr title="${esc(s.hub)} servo port ${s.port}"><td>${esc(s.name)}${s.continuous ? ' (CR)' : ''}</td><td>${s.pos === null ? '–' : fmt(s.pos, 3)}</td><td>${fmt(s.angle, 1)}°</td><td>${s.pwm ? 'on' : 'off'}</td></tr>`).join('');
    $('sensorTable').tBodies[0].innerHTML = Object.entries(d.sensors).map(([name, v]) => {
      let text;
      if (v === null) text = 'out of range';
      else if (typeof v === 'boolean') text = v ? 'pressed' : 'released';
      else if (typeof v === 'number') text = `${(v * 100).toFixed(1)} cm`;
      else if (Array.isArray(v) && v.length === 4) text = `r ${v[0].toFixed(3)} g ${v[1].toFixed(3)} b ${v[2].toFixed(3)} a ${v[3].toFixed(3)}`;
      else if (Array.isArray(v) && v.length === 3 && Math.abs(v[0]) > 5) text = `x ${v[0].toFixed(0)} mm  y ${v[1].toFixed(0)} mm  h ${v[2].toFixed(1)}°`;
      else if (Array.isArray(v)) text = `x ${(v[0] / 0.0254).toFixed(1)} in  y ${(v[1] / 0.0254).toFixed(1)} in  h ${(v[2] * 180 / Math.PI).toFixed(1)}°`;
      else text = JSON.stringify(v);
      return `<tr><td>${esc(name)}</td><td>${esc(text)}</td></tr>`;
    }).join('');
    $('hubs').innerHTML = d.hubs.map((h) => `<span><i class="led" style="background:${esc(h.led)}"></i>${esc(h.name)} · bulk cache ${esc(h.cache)}</span>`).join('');
    if (d.hubs.length && onLed) onLed(d.hubs[0].led);
  }

  // ---- gamepads ------------------------------------------------------------------------------

  buildGamepadViz(slot) {
    const viz = document.querySelector(`#gp${slot} .gp-viz`);
    viz.innerHTML = '<div class="stick"><i></i></div><div class="stick"><i></i></div><div class="btns">' +
      BUTTON_LABELS.map((l) => `<span>${l}</span>`).join('') + '</div>';
  }

  renderGamepadSources() {
    const opts = this.input.options();
    for (const slot of [1, 2]) {
      const sel = $(`gp${slot}Source`);
      const cur = this.input.sources[slot];
      sel.innerHTML = opts.map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('');
      sel.value = opts.some((o) => o.value === cur) ? cur : 'auto';
      sel.title = `Now: ${this.input.describe(slot)}`;
    }
    document.getElementById('keys').hidden = !this.input.usesKeyboard();
  }

  renderGamepads() {
    for (const slot of [1, 2]) {
      const st = this.input.states[slot];
      if (!st) continue;
      const root = document.querySelector(`#gp${slot} .gp-viz`);
      const sticks = root.querySelectorAll('.stick');
      const place = (el, x, y) => {
        el.classList.toggle('active', Math.hypot(x, y) > 0.05);
        el.firstChild.style.transform = `translate(${x * 11}px, ${y * 11}px)`;
      };
      place(sticks[0], st.axes[0], st.axes[1]);
      place(sticks[1], st.axes[2], st.axes[3]);
      const spans = root.querySelectorAll('.btns span');
      spans.forEach((s, i) => {
        const on = i === 6 ? st.axes[4] > 0.2 : i === 7 ? st.axes[5] > 0.2 : st.buttons[i];
        s.classList.toggle('on', !!on);
      });
      document.querySelector(`#gp${slot} .gp-name`).textContent = `Gamepad ${slot}` + (st.source ? ` · ${this.input.describe(slot)}` : '');
    }
  }
}
