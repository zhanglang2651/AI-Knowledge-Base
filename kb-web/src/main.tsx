/**
 * 应用入口。
 *
 * @author zhanglang
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles/global.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('未找到 #root 挂载节点，请检查 index.html');
}

ReactDOM.createRoot(container).render(
  // StrictMode 在开发环境会双调用 effect，用于暴露副作用清理不彻底的问题。
  // 本项目所有轮询 / 流式请求都在 effect 返回值里做了清理，因此不受影响。
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
