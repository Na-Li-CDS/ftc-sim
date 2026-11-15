// Connects the browser Driver Station to the simulator over a WebSocket.
import { FieldView } from './field3d.js';
import { InputManager } from './input.js';
import { DriverStation } from './ds.js';

const $ = (id) => document.getElementById(id);
let ws = null;
const send = (msg) => {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(typeof msg === 'string' ? msg : JSON.stringify(msg));
};

const view = new FieldView($('scene'));
const input = new InputManager(send);
const ds = new DriverStation(send, input);

// camera buttons
for (const b of document.querySelectorAll('[data-cam]')) {
  b.addEventListener('click', () => {
    for (const o of document.querySelectorAll('[data-cam]')) o.classList.toggle('on', o === b);
    view.setCameraMode(b.dataset.cam);
  });
}

// placing the robot
const placeBtn = $('placeBtn');
const setPlacing = (on) => {
  placeBtn.classList.toggle('on', on);
  $('placeHint').hidden = !on;
  view.setPlaceMode(on, (x, y, h) => {
    send({ type: 'place', x, y, h });
    setPlacing(false);
  });
};
placeBtn.addEventListener('click', () => setPlacing(!placeBtn.classList.contains('on')));
window.addEventListener('keydown', (e) => { if (e.key === 'Escape') setPlacing(false); });
$('resetBtn').addEventListener('click', () => send({ type: 'reset' }));
$('robotSelect').addEventListener('change', (e) => send({ type: 'robot', file: e.target.value }));

function setConnection(ok, text) {
  $('conn').className = 'conn ' + (ok ? 'ok' : 'bad');
  $('connText').textContent = text;
}

function onMessage(msg) {
  switch (msg.type) {
    case 'hello': {
      view.setField(msg.field);
      view.setRobot(msg.robot, msg.wheels, msg.wheelRadius, msg.chassisZ);
      view.setBalls(msg.balls);
      $('project').textContent = msg.project;
      $('project').title = `TeamCode: ${msg.project}`;
      const sel = $('robotSelect');
      sel.innerHTML = msg.robots.map((r) => `<option>${r}</option>`).join('');
      sel.value = msg.robotFile;
      sel.title = msg.robot.description || msg.robot.name;
      break;
    }
    case 'state': {
      view.updateState(msg);
      ds.setTimer(msg.ds);
      $('battery').textContent = `${msg.battery.toFixed(1)} V`;
      $('rtf').textContent = `${Math.round(msg.rtf * 100)}%`;
      const p = view.pose();
      if (p) {
        $('pose').textContent = `x ${(p.x / 0.0254).toFixed(1).padStart(6)} in   y ${(p.y / 0.0254).toFixed(1).padStart(6)} in   heading ${p.h.toFixed(1).padStart(6)}°`;
      }
      break;
    }
    case 'devices':
      ds.setDevices(msg, (led) => view.setHubLed(led));
      break;
    case 'telemetry':
      ds.setTelemetry(msg);
      break;
    case 'ds': {
      const wasIdle = ds.ds.state === 'IDLE';
      ds.setDs(msg);
      if (wasIdle && msg.state === 'INIT') ds.clearTelemetry();
      break;
    }
    case 'build':
      ds.setBuild(msg);
      break;
    case 'log':
      ds.addLog(msg);
      break;
    case 'rumble':
      input.rumble(msg.gamepad, msg.strong, msg.weak, msg.durations);
      break;
    default:
  }
}

function connect() {
  ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`);
  ws.onopen = () => setConnection(true, 'Connected');
  ws.onmessage = (e) => {
    try { onMessage(JSON.parse(e.data)); } catch (err) { console.error(err); }
  };
  ws.onclose = () => {
    setConnection(false, 'Disconnected – retrying');
    setTimeout(connect, 1000);
  };
}
connect();
