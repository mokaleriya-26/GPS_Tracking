import { useState, useEffect } from 'react';
import { getVehicles } from '../api/vehicles';
import { getAlerts } from '../api/alerts';

export const usePolling = (intervalMs = 30000) => {
  const [vehicles, setVehicles] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = async () => {
    try {
      const [vehiclesData, alertsData] = await Promise.all([
        getVehicles(),
        getAlerts()
      ]);
      // Assuming DRF returns the array directly, or inside 'results' if paginated
      setVehicles(vehiclesData.results || vehiclesData);
      setAlerts(alertsData.results || alertsData);
      setError(null);
    } catch (err) {
      console.error('Polling error:', err);
      setError('Failed to fetch data from the server.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const intervalId = setInterval(fetchData, intervalMs);
    return () => clearInterval(intervalId);
  }, [intervalMs]);

  return { vehicles, alerts, loading, error };
};
