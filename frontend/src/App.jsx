import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { usePolling } from './hooks/usePolling';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Vehicles from './pages/Vehicles';
import Alerts from './pages/Alerts';

function App() {
  const { vehicles, alerts, loading, error } = usePolling(30000);

  if (loading && vehicles.length === 0) {
    return <div className="flex h-screen items-center justify-center bg-background text-on-background font-body-lg">Loading fleet data...</div>;
  }

  if (error && vehicles.length === 0) {
    return <div className="flex h-screen items-center justify-center bg-background"><p className="text-error font-body-lg bg-error-container p-4 rounded-xl">{error}</p></div>;
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout alerts={alerts} />}>
          <Route index element={<Dashboard vehicles={vehicles} alerts={alerts} />} />
          <Route path="vehicles" element={<Vehicles vehicles={vehicles} />} />
          <Route path="alerts" element={<Alerts alerts={alerts} />} />
          <Route path="reports" element={<Alerts alerts={alerts} />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
