import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { usePolling } from './hooks/usePolling';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Vehicles from './pages/Vehicles';
import Alerts from './pages/Alerts';
import Reports from './pages/Reports';
import Drivers from './pages/Drivers';
import Trips from './pages/Trips';
import Settings from './pages/Settings';
import LiveSimulation from './pages/LiveSimulation';
import PenaltyManagement from './pages/PenaltyManagement';
import ChatbotWidget from './chatbot/ChatbotWidget';

function App() {
  // Globally poll vehicles + alerts + alertSummary; individual pages fetch their own data
  const { vehicles, alerts, alertSummary, loading } = usePolling(30000);

  if (loading && vehicles.length === 0 && alerts.length === 0) {
    return (
      <div style={{
        display: 'flex', height: '100vh', alignItems: 'center', justifyContent: 'center',
        background: 'linear-gradient(135deg, #0f172a, #1e3a5f)',
        flexDirection: 'column', gap: 16
      }}>
        <div style={{
          width: 48, height: 48, borderRadius: '50%',
          border: '4px solid rgba(37,99,235,0.3)',
          borderTopColor: '#2563eb',
          animation: 'spin 0.8s linear infinite'
        }} />
        <p style={{ color: '#94a3b8', fontSize: 14, fontFamily: 'Inter, sans-serif' }}>
          Connecting to TrackFleet…
        </p>
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout alerts={alerts} />}>
          {/* Core pages — receive polled data */}
          <Route index element={<Dashboard vehicles={vehicles} alerts={alerts} alertSummary={alertSummary} />} />
          <Route path="vehicles" element={<Vehicles vehicles={vehicles} />} />

          {/* Monitoring */}
          <Route path="alerts"      element={<Alerts />} />
          <Route path="penalties"   element={<PenaltyManagement />} />
          <Route path="drivers"     element={<Drivers />} />
          <Route path="trips"       element={<Trips />} />

          {/* Reporting */}
          <Route path="reports"     element={<Reports />} />

          {/* Simulation */}
          <Route path="simulation"  element={<LiveSimulation />} />

          {/* System */}
          <Route path="settings"    element={<Settings />} />
        </Route>
      </Routes>

      {/* Global chatbot widget — appears on all pages */}
      <ChatbotWidget />
    </BrowserRouter>
  );
}

export default App;
