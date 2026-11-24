import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { initI18n } from './i18n';
import './index.css';
import { restoreSession } from './stores/session';

const container = document.getElementById('root');
if (!container) {
  throw new Error('index.html 缺少 #root');
}

restoreSession();
initI18n();

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
