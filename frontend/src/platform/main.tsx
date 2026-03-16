import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '../index.css'
import PlatformApp from './App'

createRoot(document.getElementById('platform-root')!).render(
  <StrictMode>
    <PlatformApp />
  </StrictMode>,
)
