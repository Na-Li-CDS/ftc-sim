// Gamepad and keyboard input for gamepad1/gamepad2, sent to the simulator 50 times a second.
// Button layout follows the W3C "standard" gamepad mapping, which is also what the simulator expects:
// 0 A, 1 B, 2 X, 3 Y, 4 LB, 5 RB, 6 LT, 7 RT, 8 back, 9 start, 10 LS, 11 RS,
// 12 up, 13 down, 14 left, 15 right, 16 guide, 17 touchpad.

const N_BUTTONS = 18;
const DEADZONE = 0.06;
const KEY_BUTTONS = {
  KeyK: 0, KeyL: 1, KeyJ: 2, KeyI: 3, KeyQ: 4, KeyE: 5,
  BracketLeft: 8, BracketRight: 9, KeyT: 12, KeyG: 13, KeyF: 14, KeyH: 15, Backslash: 16,
};
const KEY_CAPTURE = new Set(['KeyW', 'KeyA', 'KeyS', 'KeyD', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'KeyZ', 'KeyC',
  ...Object.keys(KEY_BUTTONS)]);

function kindOf(id) {
  if (/xbox|xinput|045e/i.test(id)) return 'XBOX_360';
  if (/054c|dualshock|dualsense|playstation|wireless controller/i.test(id)) return 'SONY_PS4';
  if (/logitech|046d/i.test(id)) return 'LOGITECH_F310';
  return 'UNKNOWN';
}

function shortName(id) {
  const m = id.match(/^(.*?)\s*\(/);
  return (m ? m[1] : id).slice(0, 40) || 'Controller';
}

function stick(x, y) {
  const mag = Math.hypot(x, y);
  if (mag < DEADZONE) return [0, 0];
  const scale = Math.min(1, (mag - DEADZONE) / (1 - DEADZONE)) / mag;
  return [x * scale, y * scale];
}

export class InputManager {
  constructor(send) {
    this.send = send;
    this.keys = new Set();
    this.tapped = new Set();
    this.shift = false;
    this.sources = { 1: 'auto', 2: 'none' };
    this.lastSent = { 1: '', 2: '' };
    this.lastTime = { 1: 0, 2: 0 };
    this.states = { 1: null, 2: null };
    this.listeners = [];

    window.addEventListener('keydown', (e) => this.onKey(e, true));
    window.addEventListener('keyup', (e) => this.onKey(e, false));
    window.addEventListener('blur', () => { this.keys.clear(); this.shift = false; });
    window.addEventListener('gamepadconnected', () => this.emit());
    window.addEventListener('gamepaddisconnected', () => this.emit());
    setInterval(() => this.tick(), 20);
  }

  onChange(fn) { this.listeners.push(fn); }

  emit() { for (const fn of this.listeners) fn(); }

  typingIn(target) {
    const tag = target?.tagName;
    return tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA' || target?.isContentEditable;
  }

  onKey(e, down) {
    if (e.key === 'Shift') this.shift = down;
    if (this.typingIn(e.target) || e.ctrlKey || e.metaKey || e.altKey) return;
    if (!KEY_CAPTURE.has(e.code)) return;
    if (down) {
      this.keys.add(e.code);
      this.tapped.add(e.code);   // seen down at least once before the next send, even if released already
    } else {
      this.keys.delete(e.code);
    }
    if (e.code.startsWith('Arrow')) e.preventDefault();
  }

  pads() {
    return Array.from(navigator.getGamepads ? navigator.getGamepads() : []).filter((p) => p && p.connected);
  }

  /** Options for the source pickers. */
  options() {
    const opts = [{ value: 'auto', label: 'Auto' }, { value: 'keyboard', label: 'Keyboard' }];
    for (const p of this.pads()) opts.push({ value: `pad:${p.index}`, label: `${p.index + 1}: ${shortName(p.id)}` });
    opts.push({ value: 'none', label: 'None' });
    return opts;
  }

  setSource(slot, value) {
    this.sources[slot] = value;
    this.emit();
  }

  /** What actually feeds a slot right now: a Gamepad, 'keyboard', or null. */
  resolve(slot) {
    const src = this.sources[slot];
    const pads = this.pads();
    if (src === 'none') return null;
    if (src === 'keyboard') return 'keyboard';
    if (src.startsWith('pad:')) return pads.find((p) => p.index === Number(src.slice(4))) || null;
    // auto: gamepad 1 takes the first controller (or the keyboard), gamepad 2 the second controller
    const taken = slot === 2 ? this.resolve(1) : null;
    const free = pads.filter((p) => p !== taken && !Object.values(this.sources).includes(`pad:${p.index}`));
    if (slot === 1) return free[0] || 'keyboard';
    return free[0] || null;
  }

  describe(slot) {
    const r = this.resolve(slot);
    if (r === null) return 'not assigned';
    if (r === 'keyboard') return 'keyboard';
    return shortName(r.id);
  }

  read(slot) {
    const r = this.resolve(slot);
    const buttons = new Array(N_BUTTONS).fill(false);
    let axes = [0, 0, 0, 0, 0, 0];
    let kind = 'UNKNOWN';
    if (r === 'keyboard') {
      const k = (c) => (this.keys.has(c) || this.tapped.has(c) ? 1 : 0);
      const s = this.shift ? 0.4 : 1;
      axes = [(k('KeyD') - k('KeyA')) * s, (k('KeyS') - k('KeyW')) * s, (k('ArrowRight') - k('ArrowLeft')) * s,
        (k('ArrowDown') - k('ArrowUp')) * s, k('KeyZ'), k('KeyC')];
      for (const [code, i] of Object.entries(KEY_BUTTONS)) buttons[i] = this.keys.has(code) || this.tapped.has(code);
      kind = 'XBOX_360';
    } else if (r) {
      const [lx, ly] = stick(r.axes[0] || 0, r.axes[1] || 0);
      const [rx, ry] = stick(r.axes[2] || 0, r.axes[3] || 0);
      axes = [lx, ly, rx, ry, r.buttons[6]?.value || 0, r.buttons[7]?.value || 0];
      for (let i = 0; i < N_BUTTONS && i < r.buttons.length; i++) buttons[i] = !!r.buttons[i]?.pressed;
      kind = kindOf(r.id);
    }
    return { axes: axes.map((v) => Math.round(v * 1000) / 1000), buttons, kind, source: r === null ? null : (r === 'keyboard' ? 'keyboard' : 'pad') };
  }

  tick() {
    const now = performance.now();
    for (const slot of [1, 2]) {
      const st = this.read(slot);
      this.states[slot] = st;
      const msg = JSON.stringify({ type: 'gamepad', gamepad: slot, kind: st.kind, axes: st.axes, buttons: st.buttons });
      if (msg !== this.lastSent[slot] || now - this.lastTime[slot] > 250) {
        this.send(msg);
        this.lastSent[slot] = msg;
        this.lastTime[slot] = now;
      }
    }
    this.tapped.clear();
  }

  usesKeyboard() {
    return this.resolve(1) === 'keyboard' || this.resolve(2) === 'keyboard';
  }

  /** Plays a rumble effect sent by robot code (gamepad1.rumble(...)). */
  rumble(slot, strong, weak, durations) {
    const pad = this.resolve(slot);
    if (!pad || pad === 'keyboard' || !pad.vibrationActuator) return;
    let delay = 0;
    for (let i = 0; i < durations.length; i++) {
      const effect = { duration: Math.max(0, durations[i]), strongMagnitude: strong[i] || 0, weakMagnitude: weak[i] || 0 };
      setTimeout(() => { pad.vibrationActuator.playEffect('dual-rumble', effect).catch(() => {}); }, delay);
      delay += durations[i];
    }
  }
}
