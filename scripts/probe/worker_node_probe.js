// Probe: loads the solver Web Worker bundle in node with a fake worker scope, posts one puzzle and
// prints the SolveEvent messages. Usage: node scripts/probe/worker_node_probe.js <solver-worker.js>
const path = require('path');
const bundle = path.resolve(process.argv[2]);
const messages = [];
globalThis.self = globalThis;
globalThis.postMessage = (m) => { messages.push(m); console.log('event:', m.slice(0, 300)); };
require(bundle);
const request = {
  puzzle: { title: "Fool's mate", plies: 4, goal: "checkmate", project: ["moves"], start: { board: "startpos" } },
  options: { maxSolutions: 0 },
};
const t0 = Date.now();
globalThis.onmessage({ data: JSON.stringify(request) });
console.log(`done: ${messages.length} messages in ${Date.now() - t0} ms`);
