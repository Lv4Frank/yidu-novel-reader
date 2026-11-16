// 本地 mock 大模型服务：用于 Android 端到端测试（OpenAI 兼容，SSE 流式）。
// 用法：node tools/mock_server.js [port]
// 手机端配置 Base URL: http://127.0.0.1:8799/v1 （配合 adb reverse tcp:8799 tcp:8799）
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.argv[2] || '8799', 10);
const LOG = path.join(__dirname, 'mock_requests.log');
const counters = {};

function log(obj) {
  try {
    fs.appendFileSync(LOG, new Date().toISOString() + ' ' + JSON.stringify(obj) + '\n');
  } catch (e) {}
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => {
      try { resolve(JSON.parse(data)); } catch (e) { resolve({}); }
    });
  });
}

function extractTexts(body) {
  const messages = body.messages || [];
  let user = '';
  for (const m of messages) {
    if (m.role === 'user') user = typeof m.content === 'string' ? m.content : JSON.stringify(m.content);
  }
  const texts = [];
  for (const line of user.split('\n')) {
    const m = line.match(/^\s*\d+\.\s?(.*)$/);
    if (m && m[1].trim()) texts.push(m[1].trim());
  }
  return { texts, system: messages.find((m) => m.role === 'system')?.content || '' };
}

function makeTranslation(text, index) {
  if (/[\u4e00-\u9fff]/.test(text) && !/[a-zA-Z]{4,}/.test(text)) {
    return '【模拟英译】' + text;
  }
  return '【模拟译】' + text;
}

function buildItems(body) {
  const { texts } = extractTexts(body);
  return texts.map((t, i) => makeTranslation(t, i));
}

function sse(res, obj) {
  res.write('data: ' + JSON.stringify(obj) + '\n\n');
}

function done(res) {
  sse(res, { id: 'mock', object: 'chat.completion.chunk', choices: [{ index: 0, delta: {}, finish_reason: 'stop' }] });
  res.write('data: [DONE]\n\n');
  res.end();
}

function streamJson(res, items, opts) {
  const json = JSON.stringify(items);
  let i = 0;
  const step = opts && opts.step ? opts.step : 5;
  const delay = opts && opts.delay ? opts.delay : 12;
  function next() {
    if (i >= json.length) {
      sse(res, { id: 'mock', choices: [{ index: 0, delta: {}, finish_reason: 'stop' }] });
      res.write('data: [DONE]\n\n');
      res.end();
      return;
    }
    const chunk = json.slice(i, i + step);
    i += step;
    sse(res, { id: 'mock', choices: [{ index: 0, delta: { content: chunk }, finish_reason: null }] });
    setTimeout(next, delay);
  }
  next();
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*' });
    res.end();
    return;
  }
  if (req.url.startsWith('/v1/models')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ data: [{ id: 'mock-normal' }, { id: 'mock-truncate' }, { id: 'mock-429' }, { id: 'mock-thinking' }, { id: 'mock-slow' }] }));
    return;
  }
  if (!req.url.startsWith('/v1/chat/completions')) {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: { message: 'not found' } }));
    return;
  }
  const body = await readBody(req);
  const { texts, system } = extractTexts(body);
  const model = body.model || 'mock-normal';
  const auth = req.headers['authorization'] || '';
  log({ model, stream: !!body.stream, count: texts.length, chars: texts.join('').length, auth, thinking: body.thinking || null, effort: body.reasoning_effort || null, max_tokens: body.max_tokens || null, hasGlossary: /术语表/.test(system), systemHead: system.replace(/\s+/g, ' ').slice(0, 160) });

  if (model.includes('429') && !counters['429-' + texts.join('').slice(0, 20)]) {
    counters['429-' + texts.join('').slice(0, 20)] = true;
    res.writeHead(429, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: { message: 'mock rate limit, retry works' } }));
    return;
  }
  if (model.includes('truncate')) {
    const key = 'trunc-' + texts.length + '-' + texts[0].slice(0, 12);
    if (!counters[key]) {
      counters[key] = true;
      const items = buildItems(body);
      const cut = Math.max(1, Math.floor(items.length / 2));
      const partial = items.slice(0, cut);
      if (body.stream) {
        res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' });
        const json = JSON.stringify(partial).slice(0, -1); // 故意缺右括号，模拟截断
        let i = 0;
        const timer = setInterval(() => {
          if (i >= json.length) {
            clearInterval(timer);
            sse(res, { id: 'mock', choices: [{ index: 0, delta: { content: '' }, finish_reason: 'length' }] });
            res.write('data: [DONE]\n\n');
            res.end();
            return;
          }
          sse(res, { id: 'mock', choices: [{ index: 0, delta: { content: json.slice(i, i + 8) }, finish_reason: null }] });
          i += 8;
        }, 8);
      } else {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ choices: [{ index: 0, message: { role: 'assistant', content: JSON.stringify(partial) }, finish_reason: 'length' }] }));
      }
      return;
    }
  }

  if (body.stream === false) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      choices: [{ index: 0, message: { role: 'assistant', content: JSON.stringify(buildItems(body)) }, finish_reason: 'stop' }]
    }));
    return;
  }

  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
  const items = buildItems(body);
  if (model.includes('thinking')) {
    sse(res, { id: 'mock', choices: [{ index: 0, delta: { reasoning_content: '让我先分析这些段落……' }, finish_reason: null }] });
  }
  if (model.includes('slow')) {
    setTimeout(() => streamJson(res, items, { step: 4, delay: 120 }), 600);
  } else {
    streamJson(res, items, { step: 6, delay: 10 });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  log({ event: 'listening', port: PORT });
  console.log('mock llm server listening on http://127.0.0.1:' + PORT + '/v1');
});
