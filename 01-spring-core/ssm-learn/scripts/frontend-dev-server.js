#!/usr/bin/env node
/**
 * Layui 前端开发预览服务（零依赖，仅用 Node 内置模块）
 *
 * 职责：
 *   1) 静态服务：托管 src/main/webapp 下的页面与资源（index.html / dashboard.html / page/* / static/*）
 *   2) 反向代理：将非静态文件的请求（/api、/druid 等）转发到后端（默认 127.0.0.1:8080）
 *
 * 目的：在开发期把“前端”与“后端”表现为两个独立服务，便于观察各自状态与日志。
 *       说明：SSM + Layui 的标准做法本就是把前端打包进后端 WAR 由同一容器托管；
 *            生产/学习时直接访问后端地址即可，此服务仅为“前后端分离式”开发体验而设。
 *
 * 可通过环境变量覆盖：FRONTEND_PORT / BACKEND_HOST / BACKEND_PORT
 */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');

const FRONTEND_PORT = Number(process.env.FRONTEND_PORT) || 8081;
const BACKEND_HOST = process.env.BACKEND_HOST || '127.0.0.1';
const BACKEND_PORT = Number(process.env.BACKEND_PORT) || 8080;
const WEBAPP = path.join(__dirname, '..', 'src', 'main', 'webapp');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8'
};

function sendFile(res, filePath) {
  const ext = path.extname(filePath).toLowerCase();
  res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
  fs.createReadStream(filePath).pipe(res);
}

function proxy(req, res) {
  const headers = Object.assign({}, req.headers);
  delete headers['connection'];
  const options = {
    host: BACKEND_HOST,
    port: BACKEND_PORT,
    path: req.url,
    method: req.method,
    headers: headers
  };
  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res);
  });
  proxyReq.on('error', (err) => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('[frontend] 后端代理失败：' + err.message +
      '\n请确认后端已启动于 ' + BACKEND_HOST + ':' + BACKEND_PORT);
  });
  req.pipe(proxyReq);
}

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent(req.url.split('?')[0]);
  if (urlPath === '/') urlPath = '/index.html';

  const filePath = path.join(WEBAPP, urlPath);
  // 防目录穿越
  if (!filePath.startsWith(WEBAPP)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.stat(filePath, (err, stat) => {
    if (!err && stat.isFile()) {
      sendFile(res, filePath);
    } else if (!err && stat.isDirectory()) {
      const idx = path.join(filePath, 'index.html');
      fs.stat(idx, (e2, s2) => {
        if (!e2 && s2.isFile()) sendFile(res, idx);
        else { res.writeHead(404); res.end('Not Found'); }
      });
    } else {
      // 非静态资源（接口 /druid 等）→ 反代到后端
      proxy(req, res);
    }
  });
});

server.listen(FRONTEND_PORT, () => {
  console.log('[frontend] Layui 静态预览服务已启动: http://localhost:' + FRONTEND_PORT + '/');
  console.log('[frontend] 反向代理后端: http://' + BACKEND_HOST + ':' + BACKEND_PORT);
});
server.on('error', (err) => {
  console.error('[frontend] 启动失败: ' + err.message);
  process.exit(1);
});
