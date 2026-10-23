import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AlertToastProvider } from './context/AlertToastContext';
import AlertToastContainer from './components/AlertToastContainer';
import { usePolling } from './hooks/usePolling';

// Admin layout & pages
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

// Auth page
import Login from './pages/Login';

// Driver layout & pages
import DriverLayout from './components/DriverLayout';
import DriverDashboard from './pages/driver/DriverDashboard';
import DriverProfile from './pages/driver/DriverProfile';
import DriverVehicles from './pages/driver/DriverVehicles';
import DriverAlerts from './pages/driver/DriverAlerts';
import DriverPenalties from './pages/driver/DriverPenalties';
import DriverTrips from './pages/driver/DriverTrips';
import DriverReports from './pages/driver/DriverReports';

// Protected Route Guard for Admin
function AdminRoute({ children }) {
  const { isAuthenticated, isAdmin, isDriver } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (isDriver) {
    return <Navigate to="/driver/dashboard" replace />;
  }
  return children;
}

// Protected Route Guard for Driver
function DriverRoute({ children }) {
  const { isAuthenticated, isDriver } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (!isDriver) {
    return <Navigate to="/" replace />;
  }
  return children;
}

// Public Route Guard for Login
function PublicLoginRoute() {
  const { isAuthenticated, isAdmin, isDriver } = useAuth();

  if (isAuthenticated) {
    if (isDriver) {
      return <Navigate to="/driver/dashboard" replace />;
    }
    return <Navigate to="/" replace />;
  }
  return <Login />;
}

// App Content with Polling and Routes
function AppContent() {
  const { isAuthenticated, isAdmin } = useAuth();
  // Only poll fleet-wide vehicles & alerts when logged in as an ADMIN
  const { vehicles, alerts, alertSummary, loading } = usePolling(30000, isAuthenticated && isAdmin);

  return (
    <>
      {/* Real-time alert pop-ups (toasts) across the application */}
      <AlertToastContainer />

      <Routes>
        {/* Public Login Route */}
        <Route path="/login" element={<PublicLoginRoute />} />

        {/* Driver Portal Routes */}
        <Route
          path="/driver"
          element={
            <DriverRoute>
              <DriverLayout />
            </DriverRoute>
          }
        >
          <Route index element={<Navigate to="/driver/dashboard" replace />} />
          <Route path="dashboard" element={<DriverDashboard />} />
          <Route path="profile" element={<DriverProfile />} />
          <Route path="vehicles" element={<DriverVehicles />} />
          <Route path="alerts" element={<DriverAlerts />} />
          <Route path="penalties" element={<DriverPenalties />} />
          <Route path="trips" element={<DriverTrips />} />
          <Route path="reports" element={<DriverReports />} />
        </Route>

        {/* Admin Fleet Management Routes */}
        <Route
          path="/"
          element={
            <AdminRoute>
              <Layout alerts={alerts} />
            </AdminRoute>
          }
        >
          <Route index element={<Dashboard vehicles={vehicles} alerts={alerts} alertSummary={alertSummary} />} />
          <Route path="vehicles" element={<Vehicles vehicles={vehicles} />} />
          <Route path="alerts" element={<Alerts />} />
          <Route path="penalties" element={<PenaltyManagement />} />
          <Route path="drivers" element={<Drivers />} />
          <Route path="trips" element={<Trips />} />
          <Route path="reports" element={<Reports />} />
          <Route path="simulation" element={<LiveSimulation />} />
          <Route path="settings" element={<Settings />} />
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>

      {/* Admin Assistant Chatbot */}
      {isAdmin && <ChatbotWidget />}
    </>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AlertToastProvider>
          <AppContent />
        </AlertToastProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
