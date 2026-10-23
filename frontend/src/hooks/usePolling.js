import { useState, useEffect, useRef } from 'react';
import { getVehicles } from '../api/vehicles';
import { getAlerts, getAlertSummary } from '../api/alerts';

/**
 * usePolling — fetches vehicles, alerts, and true database alert summary
 * from the Spring Boot backend, then polls at the given interval.
 */
export const usePolling = (intervalMs = 30000, enabled = true) => {
  const [vehicles, setVehicles] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [alertSummary, setAlertSummary] = useState(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(null);
  const intervalRef = useRef(null);

  const fetchData = async () => {
    try {
      const [vehiclesData, alertsData, summaryData] = await Promise.all([
        getVehicles(),
        getAlerts({ size: 200 }),
        getAlertSummary().catch(() => null)
      ]);

      // Spring Boot returns either a Page<T> (with .content) or a plain array
      const vehicleList = Array.isArray(vehiclesData)
        ? vehiclesData
        : (vehiclesData?.content || vehiclesData?.data || []);

      const alertList = Array.isArray(alertsData)
        ? alertsData
        : (alertsData?.content || alertsData?.data || []);

      setVehicles(vehicleList);
      setAlerts(alertList);
      if (summaryData) setAlertSummary(summaryData);
      setError(null);
    } catch (err) {
      console.error('Polling error:', err);
      // Only set error on first load — keep stale data on subsequent failures
      setError('Cannot reach backend at http://localhost:8080. Is the Spring Boot server running?');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    fetchData();
    intervalRef.current = setInterval(fetchData, intervalMs);
    return () => clearInterval(intervalRef.current);
  }, [intervalMs, enabled]);

  return { vehicles, alerts, alertSummary, loading, error, refresh: fetchData };
};
