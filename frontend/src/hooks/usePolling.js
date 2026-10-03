import { useState, useEffect, useRef } from 'react';
import { getVehicles } from '../api/vehicles';
import { getAlerts } from '../api/alerts';

/**
 * usePolling — fetches vehicles and alerts from the Spring Boot backend,
 * then polls at the given interval.
 *
 * Spring Boot AlertDTO fields used here:
 *   alert.alertType, alert.severity, alert.driverName, alert.vehicleRegistrationNumber,
 *   alert.occurredAt, alert.speedKmph, alert.roadName, alert.status, alert.deliveryStatus
 *
 * Spring Boot VehicleDTO fields:
 *   vehicle.id, vehicle.code, vehicle.registrationNumber, vehicle.driverName,
 *   vehicle.lastSpeedKmph, vehicle.lastLatitude, vehicle.lastLongitude, vehicle.active
 */
export const usePolling = (intervalMs = 30000) => {
  const [vehicles, setVehicles] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const intervalRef = useRef(null);

  const fetchData = async () => {
    try {
      const [vehiclesData, alertsData] = await Promise.all([
        getVehicles(),
        getAlerts({ size: 200 })
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
    fetchData();
    intervalRef.current = setInterval(fetchData, intervalMs);
    return () => clearInterval(intervalRef.current);
  }, [intervalMs]);

  return { vehicles, alerts, loading, error, refresh: fetchData };
};
