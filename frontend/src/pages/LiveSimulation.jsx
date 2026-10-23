import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Badge, Button, Modal, ProgressBar, Alert as BsAlert } from 'react-bootstrap';
import {
  Play, Pause, Square, RotateCcw, FastForward,
  MapPin, AlertTriangle, ShieldCheck,
  Radio, CheckCircle2, XCircle, ArrowRight, User, Truck,
  Zap, AlertCircle
} from 'lucide-react';
import L from 'leaflet';
import simulationApi from '../api/simulation';
import { useAlertToast } from '../context/AlertToastContext';

// Helper for formatted time
const formatTime = (seconds) => {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
};

// Custom Truck SVG Icon for Leaflet
const createTruckIcon = (speed, isOverspeed, gpsConnected) => {
  const fillColor = !gpsConnected ? '#ef4444' : isOverspeed ? '#dc2626' : '#2563eb';
  const pulseClass = isOverspeed ? 'simulation-marker-pulse-danger' : 'simulation-marker-pulse';

  return L.divIcon({
    className: 'custom-simulation-truck-marker',
    html: `
      <div style="position: relative; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center;">
        <div class="${pulseClass}" style="position: absolute; width: 40px; height: 40px; border-radius: 50%; background: ${fillColor}33;"></div>
        <div style="
          width: 36px; height: 36px; border-radius: 50%;
          background: ${fillColor};
          border: 2.5px solid #ffffff;
          box-shadow: 0 4px 12px rgba(0,0,0,0.3);
          display: flex; align-items: center; justify-content: center;
          color: #ffffff;
          transition: all 0.3s ease;
        ">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2"/>
            <path d="M15 18H9"/>
            <path d="M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14"/>
            <circle cx="17" cy="18" r="2"/>
            <circle cx="7" cy="18" r="2"/>
          </svg>
        </div>
        <div style="
          position: absolute; bottom: -8px; background: #0f172a; color: #fff;
          font-size: 10px; font-weight: 700; padding: 1px 5px; border-radius: 4px;
          border: 1px solid #334155; white-space: nowrap; box-shadow: 0 2px 4px rgba(0,0,0,0.2);
        ">
          ${Math.round(speed)} km/h
        </div>
      </div>
    `,
    iconSize: [44, 44],
    iconAnchor: [22, 22],
    popupAnchor: [0, -22]
  });
};

const createFlagIcon = (isOrigin, label) => {
  const bg = isOrigin ? '#10b981' : '#f59e0b';
  return L.divIcon({
    className: 'custom-flag-marker',
    html: `
      <div style="display: flex; flex-direction: column; align-items: center;">
        <div style="
          background: ${bg}; color: #fff; font-size: 11px; font-weight: 700;
          padding: 2px 7px; border-radius: 4px; border: 1.5px solid #fff;
          box-shadow: 0 2px 6px rgba(0,0,0,0.25); white-space: nowrap;
        ">
          ${label}
        </div>
        <div style="width: 2px; height: 10px; background: #334155;"></div>
        <div style="width: 8px; height: 8px; border-radius: 50%; background: #334155;"></div>
      </div>
    `,
    iconSize: [60, 36],
    iconAnchor: [30, 36],
  });
};

const LiveSimulation = () => {
  const { triggerAlertToast } = useAlertToast();

  // Routes & Selection
  const [routes, setRoutes] = useState([]);
  const [selectedRouteId, setSelectedRouteId] = useState('mumbai-pune-expressway');
  const [speedMultiplier, setSpeedMultiplier] = useState(1);
  const [autoCenter, setAutoCenter] = useState(true);

  // Live Simulation State
  const [simState, setSimState] = useState(null);
  const [error, setError] = useState(null);
  const [summaryModalOpen, setSummaryModalOpen] = useState(false);
  const [finalSummary, setFinalSummary] = useState(null);

  // Map and Marker references
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const truckMarkerRef = useRef(null);
  const plannedPolylineRef = useRef(null);
  const completedPolylineRef = useRef(null);
  const originMarkerRef = useRef(null);
  const destMarkerRef = useRef(null);
  const completedCoordsRef = useRef([]);

  // Ticking timer for simulation execution
  const timerRef = useRef(null);
  const isRunningRef = useRef(false);

  // Fetch available routes and current status on mount
  useEffect(() => {
    let isMounted = true;

    Promise.all([
      simulationApi.getRoutes().catch(() => []),
      simulationApi.getStatus().catch(() => null)
    ]).then(([routesData, statusData]) => {
      if (!isMounted) return;
      if (routesData && routesData.length > 0) {
        setRoutes(routesData);
        setSelectedRouteId(routesData[0].id);
      }
      if (statusData) {
        setSimState(statusData);
        if (statusData.summary) setFinalSummary(statusData.summary);
      }
    }).catch(err => {
      if (isMounted) {
        setError(err.message || 'Failed to initialize simulation session');
      }
    });

    return () => {
      isMounted = false;
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  // Update running ref whenever state changes
  useEffect(() => {
    isRunningRef.current = simState?.status === 'RUNNING';
  }, [simState?.status]);

  // Map initialization
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const initialLat = 19.0069;
    const initialLng = 73.1018;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: 11,
      zoomControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(map);

    mapInstanceRef.current = map;

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  // Synchronize route and waypoints onto the Leaflet map
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || routes.length === 0) return;

    const currentRoute = routes.find(r => r.id === (simState?.routeId || selectedRouteId)) || routes[0];
    if (!currentRoute || !currentRoute.waypoints) return;

    const coords = currentRoute.waypoints.map(w => [
      parseFloat(w.latitude),
      parseFloat(w.longitude)
    ]);

    // Planned route polyline
    if (plannedPolylineRef.current) {
      map.removeLayer(plannedPolylineRef.current);
    }
    plannedPolylineRef.current = L.polyline(coords, {
      color: '#3b82f6',
      weight: 4,
      opacity: 0.6,
      dashArray: '8, 8',
    }).addTo(map);

    // Completed route polyline
    if (completedPolylineRef.current) {
      map.removeLayer(completedPolylineRef.current);
    }
    completedPolylineRef.current = L.polyline([], {
      color: '#10b981',
      weight: 5,
      opacity: 0.9,
    }).addTo(map);
    completedCoordsRef.current = [];

    // Origin marker
    if (originMarkerRef.current) map.removeLayer(originMarkerRef.current);
    originMarkerRef.current = L.marker(coords[0], {
      icon: createFlagIcon(true, 'START: ' + (currentRoute.origin?.split(',')[0] || 'Origin'))
    }).addTo(map);

    // Destination marker
    if (destMarkerRef.current) map.removeLayer(destMarkerRef.current);
    destMarkerRef.current = L.marker(coords[coords.length - 1], {
      icon: createFlagIcon(false, 'END: ' + (currentRoute.destination?.split(',')[0] || 'Dest'))
    }).addTo(map);

    map.fitBounds(plannedPolylineRef.current.getBounds(), { padding: [40, 40] });
  }, [routes, selectedRouteId, simState?.routeId]);

  // Synchronize truck position on telemetry updates
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !simState) return;

    const lat = parseFloat(simState.latitude);
    const lng = parseFloat(simState.longitude);
    if (isNaN(lat) || isNaN(lng)) return;

    const currentSpeed = simState.currentSpeedKmph || 0;
    const speedLimit = simState.speedLimitKmph || 60;
    const isOverspeed = currentSpeed > speedLimit;
    const isGpsConnected = simState.gpsConnected;

    // Add coordinate to completed path
    if (simState.status === 'RUNNING' || simState.status === 'COMPLETED') {
      const lastCoord = completedCoordsRef.current[completedCoordsRef.current.length - 1];
      if (!lastCoord || lastCoord[0] !== lat || lastCoord[1] !== lng) {
        completedCoordsRef.current.push([lat, lng]);
        if (completedPolylineRef.current) {
          completedPolylineRef.current.setLatLngs(completedCoordsRef.current);
        }
      }
    } else if (simState.status === 'IDLE') {
      completedCoordsRef.current = [];
      if (completedPolylineRef.current) {
        completedPolylineRef.current.setLatLngs([]);
      }
    }

    // Update or create truck marker
    const icon = createTruckIcon(currentSpeed, isOverspeed, isGpsConnected);

    if (!truckMarkerRef.current) {
      truckMarkerRef.current = L.marker([lat, lng], { icon, zIndexOffset: 1000 }).addTo(map);
    } else {
      truckMarkerRef.current.setLatLng([lat, lng]);
      truckMarkerRef.current.setIcon(icon);
    }

    // Bind rich tooltip popup
    truckMarkerRef.current.bindPopup(`
      <div style="font-family: Inter, sans-serif; min-width: 180px; padding: 4px;">
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">
          <span style="font-size: 11px; font-weight: 800; color: #1e293b;">${simState.vehicleRegistrationNumber}</span>
          <span style="font-size: 10px; padding: 2px 6px; border-radius: 4px; background: ${isOverspeed ? '#fee2e2; color: #b91c1c;' : '#dcfce7; color: #15803d;'} font-weight: 700;">
            ${isOverspeed ? 'OVERSPEED' : 'NORMAL'}
          </span>
        </div>
        <div style="font-size: 12px; color: #475569; margin-bottom: 3px;">
          <strong>Driver:</strong> ${simState.driverName || 'Justin Sunil'}
        </div>
        <div style="font-size: 12px; color: #475569; margin-bottom: 3px;">
          <strong>Speed:</strong> <span style="font-weight: 700; color: ${isOverspeed ? '#dc2626' : '#0f172a'};">${Math.round(currentSpeed)} km/h</span> (Limit: ${speedLimit} km/h)
        </div>
        <div style="font-size: 11px; color: #64748b; margin-bottom: 3px;">
          <strong>Road:</strong> ${simState.roadName || 'Highway'}
        </div>
        <div style="font-size: 10px; color: #94a3b8;">
          Lat: ${lat.toFixed(4)}, Lng: ${lng.toFixed(4)}
        </div>
      </div>
    `);

    // Auto pan if enabled
    if (autoCenter && map && simState.status === 'RUNNING') {
      map.panTo([lat, lng], { animate: true, duration: 0.5 });
    }
  }, [simState, autoCenter]);

  // Tick execution when RUNNING: invokes backend POST /api/simulation/step
  const executeStep = useCallback(async () => {
    if (!isRunningRef.current) return;
    try {
      const updated = await simulationApi.stepSimulation();
      setSimState(updated);
      if (updated?.allAlerts && updated.allAlerts.length > 0) {
        updated.allAlerts.forEach((a) => triggerAlertToast(a));
      }
      if (updated.status === 'COMPLETED') {
        if (updated.summary) {
          setFinalSummary(updated.summary);
          setSummaryModalOpen(true);
        }
      }
    } catch (err) {
      console.error('Simulation step error:', err);
    }
  }, [triggerAlertToast]);

  // Interval loop based on speedMultiplier
  useEffect(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    if (simState?.status === 'RUNNING') {
      // 1x = 3000ms, 2x = 1500ms, 5x = 600ms, 10x = 300ms
      const intervalMs = Math.max(300, Math.round(3000 / speedMultiplier));
      timerRef.current = setInterval(executeStep, intervalMs);
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [simState?.status, speedMultiplier, executeStep]);

  // Handlers for Simulation Controls
  const handleStart = async () => {
    try {
      setError(null);
      const res = await simulationApi.startSimulation(selectedRouteId, speedMultiplier);
      setSimState(res);
      completedCoordsRef.current = [];
      if (completedPolylineRef.current) completedPolylineRef.current.setLatLngs([]);
    } catch (err) {
      setError('Failed to start simulation: ' + err.message);
    }
  };

  const handlePause = async () => {
    try {
      const res = await simulationApi.pauseSimulation();
      setSimState(res);
    } catch (err) {
      setError('Failed to pause simulation: ' + err.message);
    }
  };

  const handleResume = async () => {
    try {
      const res = await simulationApi.resumeSimulation();
      setSimState(res);
    } catch (err) {
      setError('Failed to resume simulation: ' + err.message);
    }
  };

  const handleStop = async () => {
    try {
      const res = await simulationApi.stopSimulation();
      setSimState(res);
      if (res.summary) {
        setFinalSummary(res.summary);
        setSummaryModalOpen(true);
      }
    } catch (err) {
      setError('Failed to stop simulation: ' + err.message);
    }
  };

  const handleReset = async () => {
    try {
      const res = await simulationApi.resetSimulation();
      setSimState(res);
      setFinalSummary(null);
      completedCoordsRef.current = [];
      if (completedPolylineRef.current) completedPolylineRef.current.setLatLngs([]);
    } catch (err) {
      setError('Failed to reset simulation: ' + err.message);
    }
  };

  // Manual Event Triggers
  const handleTriggerEvent = async (eventType) => {
    try {
      const eventId = 'evt_' + Date.now() + '_' + Math.random().toString(36).slice(2, 9);
      const res = await simulationApi.triggerEvent(eventType, null, eventId);
      setSimState(res);
      if (res?.allAlerts && res.allAlerts.length > 0) {
        res.allAlerts.forEach((a) => triggerAlertToast(a));
      }
    } catch (err) {
      setError('Failed to trigger event: ' + err.message);
    }
  };

  const isRunning = simState?.status === 'RUNNING';
  const isPaused = simState?.status === 'PAUSED';
  const isCompleted = simState?.status === 'COMPLETED';
  const isIdle = !simState || simState.status === 'IDLE';

  return (
    <div style={{ padding: '20px 24px', background: '#f8fafc', minHeight: '100vh', fontFamily: 'Inter, sans-serif' }}>
      {/* Top Banner: SIMULATION MODE INDICATOR */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: 'linear-gradient(135deg, #1e293b, #0f172a)',
        color: '#fff', padding: '14px 20px', borderRadius: 12,
        marginBottom: 20, boxShadow: '0 4px 14px rgba(0,0,0,0.1)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{
            width: 42, height: 42, borderRadius: 10,
            background: 'linear-gradient(135deg, #2563eb, #7c3aed)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 0 16px rgba(37,99,235,0.5)'
          }}>
            <Truck size={22} color="#fff" />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <h1 style={{ fontSize: 18, fontWeight: 800, margin: 0, letterSpacing: '-0.02em' }}>
                TrackFleet Live Truck Simulation & Alert Harness
              </h1>
              <span id="simulation-mode-badge" style={{
                background: 'rgba(245, 158, 11, 0.2)', color: '#fbbf24',
                border: '1px solid #f59e0b', fontSize: 11, fontWeight: 800,
                padding: '3px 9px', borderRadius: 20, letterSpacing: '0.04em',
                display: 'inline-flex', alignItems: 'center', gap: 5,
                animation: isRunning ? 'pulse 1.8s infinite' : 'none'
              }}>
                <Radio size={12} /> SIMULATION MODE
              </span>
            </div>
            <p style={{ margin: '2px 0 0', fontSize: 12, color: '#94a3b8' }}>
              Real-time telemetry ingestion, actual PostgreSQL alert evaluation, and progressive penalty demonstration.
            </p>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {simState?.summary && (
            <Button
              id="btn-view-summary"
              variant="outline-light"
              size="sm"
              onClick={() => setSummaryModalOpen(true)}
              style={{ fontSize: 12, fontWeight: 600, borderRadius: 8 }}
            >
              📊 View Trip Summary
            </Button>
          )}
          <span style={{
            fontSize: 11, color: '#cbd5e1', background: '#334155',
            padding: '5px 10px', borderRadius: 8, fontWeight: 600
          }}>
            Isolated Academic Mode · Zero Live Dispatches
          </span>
        </div>
      </div>

      {error && (
        <BsAlert variant="danger" dismissible onClose={() => setError(null)} style={{ borderRadius: 10, fontSize: 13 }}>
          {error}
        </BsAlert>
      )}

      {/* SECTION A: SIMULATION CONTROLS */}
      <div style={{
        background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12,
        padding: '16px 20px', marginBottom: 20, boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
      }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 14 }}>
          {/* Action Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {!isRunning ? (
              <Button
                id="btn-start-simulation"
                variant="success"
                size="sm"
                onClick={isPaused ? handleResume : handleStart}
                style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  fontWeight: 700, padding: '7px 16px', borderRadius: 8,
                  boxShadow: '0 2px 8px rgba(22,163,74,0.3)'
                }}
              >
                <Play size={15} /> {isPaused ? 'Resume Simulation' : 'Start Simulation'}
              </Button>
            ) : (
              <Button
                id="btn-pause-simulation"
                variant="warning"
                size="sm"
                onClick={handlePause}
                style={{
                  display: 'flex', alignItems: 'center', gap: 6,
                  fontWeight: 700, padding: '7px 16px', borderRadius: 8,
                  color: '#78350f'
                }}
              >
                <Pause size={15} /> Pause Simulation
              </Button>
            )}

            <Button
              id="btn-stop-simulation"
              variant="danger"
              size="sm"
              disabled={isIdle || isCompleted}
              onClick={handleStop}
              style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, padding: '7px 14px', borderRadius: 8 }}
            >
              <Square size={14} /> Stop
            </Button>

            <Button
              id="btn-reset-simulation"
              variant="outline-secondary"
              size="sm"
              onClick={handleReset}
              title="Safely clear simulation records without affecting fleet data"
              style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, padding: '7px 14px', borderRadius: 8 }}
            >
              <RotateCcw size={14} /> Reset Session
            </Button>

            {/* Step button for manual frame advance */}
            {isRunning && (
              <Button
                id="btn-step-simulation"
                variant="outline-primary"
                size="sm"
                onClick={executeStep}
                title="Advance 1 simulation waypoint step immediately"
                style={{ display: 'flex', alignItems: 'center', gap: 5, fontWeight: 600, padding: '7px 12px', borderRadius: 8 }}
              >
                <FastForward size={14} /> Step Now
              </Button>
            )}
          </div>

          {/* Route & Multiplier Pickers */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: '#64748b' }}>Route:</span>
              <select
                id="route-selector"
                value={selectedRouteId}
                disabled={isRunning || isPaused}
                onChange={(e) => setSelectedRouteId(e.target.value)}
                style={{
                  padding: '6px 12px', borderRadius: 8, border: '1px solid #cbd5e1',
                  fontSize: 12, fontWeight: 600, color: '#1e293b', background: '#f8fafc',
                  outline: 'none', minWidth: 240
                }}
              >
                {routes.map(r => (
                  <option key={r.id} value={r.id}>{r.name}</option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: '#64748b' }}>Speed:</span>
              <div style={{ display: 'flex', gap: 4 }}>
                {[1, 2, 5, 10].map(m => (
                  <button
                    key={m}
                    id={`speed-multiplier-${m}x`}
                    onClick={() => setSpeedMultiplier(m)}
                    style={{
                      border: 'none', padding: '5px 9px', borderRadius: 6,
                      fontSize: 11, fontWeight: 700, cursor: 'pointer',
                      background: speedMultiplier === m ? '#2563eb' : '#f1f5f9',
                      color: speedMultiplier === m ? '#fff' : '#475569',
                      transition: 'all 0.15s'
                    }}
                  >
                    {m}x
                  </button>
                ))}
              </div>
            </div>

            <button
              onClick={() => setAutoCenter(a => !a)}
              style={{
                border: '1px solid #cbd5e1', background: autoCenter ? '#eff6ff' : '#fff',
                color: autoCenter ? '#1d4ed8' : '#64748b', borderRadius: 8,
                padding: '6px 10px', fontSize: 11, fontWeight: 600, cursor: 'pointer'
              }}
            >
              {autoCenter ? '📍 Auto-Center ON' : '🔓 Free Pan'}
            </button>
          </div>
        </div>

        {/* Progress bar across waypoints */}
        <div style={{ marginTop: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: '#64748b', marginBottom: 4 }}>
            <span>Journey Progress: <strong>{simState?.progressPercent || 0}%</strong></span>
            <span>Waypoint {simState ? (simState.currentWaypointIndex + 1) : 1} of {simState?.totalWaypoints || 25}</span>
          </div>
          <ProgressBar
            now={simState?.progressPercent || 0}
            variant={isRunning ? 'primary' : isCompleted ? 'success' : 'info'}
            style={{ height: 6, borderRadius: 3 }}
          />
        </div>
      </div>

      {/* SECTION C & TOP KPI CARDS */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 14, marginBottom: 20 }}>
        {/* Status */}
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10, padding: '12px 16px' }}>
          <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Trip Status</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
            <span style={{
              width: 10, height: 10, borderRadius: '50%',
              background: isRunning ? '#16a34a' : isPaused ? '#f59e0b' : isCompleted ? '#2563eb' : '#94a3b8',
              boxShadow: isRunning ? '0 0 8px #16a34a' : 'none'
            }} />
            <span id="telemetry-trip-status" style={{ fontSize: 16, fontWeight: 800, color: '#0f172a' }}>
              {simState?.status || 'IDLE'}
            </span>
          </div>
        </div>

        {/* Current Speed */}
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10, padding: '12px 16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Speed (km/h)</span>
            <span style={{ fontSize: 11, color: '#94a3b8' }}>Limit: {simState?.speedLimitKmph || 60}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 4 }}>
            <span id="telemetry-speed" style={{
              fontSize: 22, fontWeight: 900,
              color: (simState?.currentSpeedKmph || 0) > (simState?.speedLimitKmph || 60) ? '#dc2626' : '#1e293b'
            }}>
              {Math.round(simState?.currentSpeedKmph || 0)}
            </span>
            <span style={{ fontSize: 12, color: '#64748b' }}>km/h</span>
            {(simState?.currentSpeedKmph || 0) > (simState?.speedLimitKmph || 60) && (
              <span className="badge bg-danger ms-auto" style={{ fontSize: 10 }}>OVERSPEED</span>
            )}
          </div>
        </div>

        {/* Distance Travelled */}
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10, padding: '12px 16px' }}>
          <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Distance</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 4 }}>
            <span id="telemetry-distance" style={{ fontSize: 22, fontWeight: 900, color: '#1e293b' }}>
              {simState?.distanceTravelledKm || '0.00'}
            </span>
            <span style={{ fontSize: 12, color: '#64748b' }}>km</span>
          </div>
        </div>

        {/* Elapsed Duration */}
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10, padding: '12px 16px' }}>
          <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Elapsed Time</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, marginTop: 4 }}>
            <span id="telemetry-elapsed-time" style={{ fontSize: 22, fontWeight: 900, color: '#1e293b' }}>
              {formatTime(simState?.elapsedSeconds || 0)}
            </span>
          </div>
        </div>

        {/* GPS Connection State */}
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10, padding: '12px 16px' }}>
          <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>GPS Connectivity</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6 }}>
            {simState?.gpsConnected !== false ? (
              <span id="telemetry-gps-status" style={{
                background: '#dcfce7', color: '#15803d', border: '1px solid #86efac',
                fontSize: 12, fontWeight: 700, padding: '3px 8px', borderRadius: 6,
                display: 'inline-flex', alignItems: 'center', gap: 5
              }}>
                <CheckCircle2 size={13} /> CONNECTED
              </span>
            ) : (
              <span id="telemetry-gps-status" style={{
                background: '#fee2e2', color: '#b91c1c', border: '1px solid #fca5a5',
                fontSize: 12, fontWeight: 700, padding: '3px 8px', borderRadius: 6,
                display: 'inline-flex', alignItems: 'center', gap: 5
              }}>
                <XCircle size={13} /> DISCONNECTED
              </span>
            )}
          </div>
        </div>

        {/* Total Penalties Incurred */}
        <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: 10, padding: '12px 16px' }}>
          <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600, textTransform: 'uppercase' }}>Simulated Penalties</div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4, marginTop: 4 }}>
            <span id="telemetry-penalties" style={{ fontSize: 22, fontWeight: 900, color: '#b91c1c' }}>
              ₹{simState?.totalPenaltyAmount || '0.00'}
            </span>
            <span style={{ fontSize: 11, color: '#64748b' }}>({simState?.totalAlertsCount || 0} alerts)</span>
          </div>
        </div>
      </div>

      {/* MAIN TWO-COLUMN SECTION: MAP & TELEMETRY */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 20, marginBottom: 20 }}>
        {/* LEFT COLUMN: LIVE MAP */}
        <div style={{
          background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12,
          overflow: 'hidden', display: 'flex', flexDirection: 'column', height: 500,
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)', position: 'relative'
        }}>
          {/* Map Header */}
          <div style={{
            padding: '10px 16px', background: '#f8fafc', borderBottom: '1px solid #e2e8f0',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', zIndex: 10
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <MapPin size={16} color="#2563eb" />
              <span style={{ fontSize: 13, fontWeight: 700, color: '#1e293b' }}>
                Live GPS Route Tracking: {simState?.routeName || 'Freight Route'}
              </span>
            </div>
            <div style={{ fontSize: 11, color: '#64748b' }}>
              Current Road: <strong style={{ color: '#0f172a' }}>{simState?.roadName || 'Kalamboli Freight Hub'}</strong>
            </div>
          </div>

          {/* Leaflet DOM container */}
          <div
            id="simulation-map-container"
            ref={mapContainerRef}
            style={{ width: '100%', height: '100%', zIndex: 1 }}
          />

          {/* Watermark overlay */}
          <div style={{
            position: 'absolute', bottom: 12, left: 12, zIndex: 500,
            background: 'rgba(15, 23, 42, 0.85)', backdropFilter: 'blur(6px)',
            color: '#fff', padding: '6px 12px', borderRadius: 8, fontSize: 11,
            pointerEvents: 'none', border: '1px solid rgba(255,255,255,0.15)'
          }}>
            📍 Coordinates: <strong>{Number(simState?.latitude || 19.0069).toFixed(4)}° N, {Number(simState?.longitude || 73.1018).toFixed(4)}° E</strong>
          </div>
        </div>

        {/* RIGHT COLUMN: MANUAL EVENT TRIGGERS & TRUCK SPECS */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* SECTION 5: TRIGGER TEST EVENT (Academic Demonstration) */}
          <div style={{
            background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12,
            padding: '16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <Zap size={16} color="#f59e0b" />
              <h2 style={{ fontSize: 14, fontWeight: 800, margin: 0, color: '#0f172a' }}>Trigger Test Event</h2>
            </div>
            <p style={{ fontSize: 11, color: '#64748b', marginBottom: 12 }}>
              Faculty test controls: inject telemetry conditions to observe real backend alert processing and progressive fines.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {/* Event 1: Overspeeding */}
              <button
                id="btn-trigger-overspeed"
                disabled={!isRunning}
                onClick={() => handleTriggerEvent('OVERSPEED')}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 12px', borderRadius: 8, border: '1px solid #fecaca',
                  background: '#fff5f5', color: '#b91c1c', fontSize: 12, fontWeight: 700,
                  cursor: isRunning ? 'pointer' : 'not-allowed', opacity: isRunning ? 1 : 0.6
                }}
              >
                <span>⚡ Overspeeding (&gt;80 km/h)</span>
                <span className="badge bg-danger" style={{ fontSize: 9 }}>CRITICAL</span>
              </button>

              {/* Event 2: Harsh Braking */}
              <button
                id="btn-trigger-harsh-braking"
                disabled={!isRunning}
                onClick={() => handleTriggerEvent('HARSH_BRAKING')}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 12px', borderRadius: 8, border: '1px solid #fed7aa',
                  background: '#fffaf0', color: '#c2410c', fontSize: 12, fontWeight: 700,
                  cursor: isRunning ? 'pointer' : 'not-allowed', opacity: isRunning ? 1 : 0.6
                }}
              >
                <span>🛑 Harsh Braking (-40 km/h drop)</span>
                <span className="badge bg-warning text-dark" style={{ fontSize: 9 }}>WARNING</span>
              </button>

              {/* Event 3: Harsh Acceleration */}
              <button
                id="btn-trigger-harsh-accel"
                disabled={!isRunning}
                onClick={() => handleTriggerEvent('HARSH_ACCEL')}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 12px', borderRadius: 8, border: '1px solid #fef08a',
                  background: '#fefce8', color: '#a16207', fontSize: 12, fontWeight: 700,
                  cursor: isRunning ? 'pointer' : 'not-allowed', opacity: isRunning ? 1 : 0.6
                }}
              >
                <span>🚀 Harsh Acceleration (+35 km/h)</span>
                <span className="badge bg-warning text-dark" style={{ fontSize: 9 }}>WARNING</span>
              </button>

              {/* Event 4: GPS Disconnection */}
              <button
                id="btn-trigger-gps-disconnect"
                disabled={!isRunning}
                onClick={() => handleTriggerEvent('GPS_DISCONNECT')}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 12px', borderRadius: 8, border: '1px solid #e9d5ff',
                  background: '#faf5ff', color: '#7e22ce', fontSize: 12, fontWeight: 700,
                  cursor: isRunning ? 'pointer' : 'not-allowed', opacity: isRunning ? 1 : 0.6
                }}
              >
                <span>📡 GPS Disconnection</span>
                <span className="badge" style={{ background: '#7e22ce', color: '#fff', fontSize: 9 }}>CRITICAL</span>
              </button>

              {/* Event 5: Restore GPS Connection */}
              <button
                id="btn-trigger-restore-gps"
                disabled={!isRunning}
                onClick={() => handleTriggerEvent('RESTORE_GPS')}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 12px', borderRadius: 8, border: '1px solid #bbf7d0',
                  background: '#f0fdf4', color: '#15803d', fontSize: 12, fontWeight: 700,
                  cursor: isRunning ? 'pointer' : 'not-allowed', opacity: isRunning ? 1 : 0.6
                }}
              >
                <span>📶 Restore GPS Signal</span>
                <span className="badge bg-success" style={{ fontSize: 9 }}>RESTORE</span>
              </button>

              {/* Event 6: Resume Normal Driving */}
              <button
                id="btn-trigger-normal-driving"
                disabled={!isRunning}
                onClick={() => handleTriggerEvent('NORMAL')}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '8px 12px', borderRadius: 8, border: '1px solid #cbd5e1',
                  background: '#f8fafc', color: '#334155', fontSize: 12, fontWeight: 700,
                  cursor: isRunning ? 'pointer' : 'not-allowed', opacity: isRunning ? 1 : 0.6
                }}
              >
                <span>🚗 Normal Driving</span>
                <span className="badge bg-secondary" style={{ fontSize: 9 }}>SAFE</span>
              </button>
            </div>

            {/* Event status badge */}
            <div style={{
              marginTop: 12, padding: '8px 10px', borderRadius: 6,
              background: '#f1f5f9', fontSize: 11, color: '#334155',
              borderLeft: '3px solid #2563eb'
            }}>
              <strong>Event State:</strong> {simState?.eventProcessingStatus || 'NONE'}
              <div style={{ marginTop: 2, color: '#64748b' }}>{simState?.eventMessage}</div>
            </div>
          </div>

          {/* VEHICLE & DRIVER CARD */}
          <div style={{
            background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12,
            padding: '16px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
          }}>
            <h3 style={{ fontSize: 13, fontWeight: 700, color: '#1e293b', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
              <User size={15} color="#2563eb" /> Vehicle & Driver Telemetry
            </h3>

            <div style={{ fontSize: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Vehicle:</span>
                <strong id="telemetry-vehicle-reg">{simState?.vehicleRegistrationNumber || 'MH01SM9999'}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Model:</span>
                <span>{simState?.makeModel || 'BharatBenz 2823R'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Driver:</span>
                <strong id="telemetry-driver-name">{simState?.driverName || 'Justin Sunil'}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Driver Code:</span>
                <span>{simState?.driverCode || 'DRV_SIM_01'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Acceleration:</span>
                <span style={{ fontWeight: 700, color: (simState?.accelerationMps2 || 0) < -3.5 ? '#dc2626' : '#1e293b' }}>
                  {simState?.accelerationMps2 != null ? `${simState.accelerationMps2} m/s²` : '0.00 m/s²'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* SECTION D: LIVE ALERT FEED */}
      <div style={{
        background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12,
        padding: '18px 20px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <AlertTriangle size={18} color="#dc2626" />
            <h2 style={{ fontSize: 15, fontWeight: 800, color: '#0f172a', margin: 0 }}>
              Live Alert Feed (Generated from Telemetry via Backend Engine)
            </h2>
            <span className="badge bg-dark" style={{ fontSize: 11 }}>
              {simState?.allAlerts?.length || 0} Events
            </span>
          </div>

          <div style={{ fontSize: 12, color: '#64748b' }}>
            Progressive Penalty Rule: <span style={{ color: '#16a34a', fontWeight: 600 }}>Occurrences #1 & #2 = ₹0 Warning</span>, <span style={{ color: '#dc2626', fontWeight: 600 }}>#3+ = Fine</span>
          </div>
        </div>

        {(!simState?.allAlerts || simState.allAlerts.length === 0) ? (
          <div style={{
            textAlign: 'center', padding: '28px 20px', color: '#94a3b8',
            background: '#f8fafc', borderRadius: 8, border: '1px dashed #cbd5e1'
          }}>
            <ShieldCheck size={28} color="#10b981" style={{ marginBottom: 6 }} />
            <div style={{ fontSize: 13, fontWeight: 600, color: '#475569' }}>
              No alerts generated yet in this simulation.
            </div>
            <div style={{ fontSize: 11 }}>
              The truck is operating safely within road limits. Use "Trigger Test Event" above to test overspeeding or harsh braking.
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxHeight: 320, overflowY: 'auto' }}>
            {simState.allAlerts.map((alert, idx) => {
              const isOver = alert.alertType?.toLowerCase().includes('overspeed');
              const isBrake = alert.alertType?.toLowerCase().includes('braking');
              const isAccel = alert.alertType?.toLowerCase().includes('acceleration');
              const isGps = alert.alertType?.toLowerCase().includes('disconnect');

              return (
                <div
                  key={alert.id || idx}
                  className="simulation-alert-item"
                  style={{
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    padding: '10px 14px', borderRadius: 8,
                    background: isOver ? '#fff5f5' : isBrake ? '#fffaf0' : isAccel ? '#fefce8' : isGps ? '#faf5ff' : '#f8fafc',
                    border: '1px solid ' + (isOver ? '#fecaca' : isBrake ? '#fed7aa' : isAccel ? '#fef08a' : isGps ? '#e9d5ff' : '#e2e8f0'),
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <div style={{
                      width: 32, height: 32, borderRadius: 8,
                      background: isOver ? '#fee2e2' : isBrake ? '#ffedd5' : isAccel ? '#fef9c3' : '#f3e8ff',
                      color: isOver ? '#dc2626' : isBrake ? '#c2410c' : isAccel ? '#a16207' : '#7e22ce',
                      display: 'flex', alignItems: 'center', justifyContent: 'center'
                    }}>
                      <AlertCircle size={16} />
                    </div>

                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: 13, fontWeight: 800, color: '#0f172a' }}>
                          {alert.alertType}
                        </span>
                        <span className={`badge ${alert.severity === 'CRITICAL' ? 'bg-danger' : 'bg-warning text-dark'}`} style={{ fontSize: 10 }}>
                          {alert.severity}
                        </span>
                        {alert.occurrenceNumber && (
                          <span className="badge" style={{
                            background: alert.occurrenceNumber <= 2 ? '#dbeafe' : '#fee2e2',
                            color: alert.occurrenceNumber <= 2 ? '#1e40af' : '#991b1b',
                            fontSize: 10, fontWeight: 700
                          }}>
                            Occurrence #{alert.occurrenceNumber} · {alert.penaltyClassification || 'WARNING'}
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: 11, color: '#475569', marginTop: 2 }}>
                        {alert.message || `Alert at ${alert.roadName} (Speed: ${alert.speedKmph || 0} km/h)`}
                      </div>
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 13, fontWeight: 800, color: (alert.penaltyAmount || 0) > 0 ? '#dc2626' : '#16a34a' }}>
                      {(alert.penaltyAmount || 0) > 0 ? `₹${Number(alert.penaltyAmount).toFixed(2)} Fine` : '₹0.00 (Warning)'}
                    </div>
                    <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 2 }}>
                      {alert.occurredAt ? new Date(alert.occurredAt).toLocaleTimeString() : 'Just now'} · {alert.roadName || 'Highway'}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* SECTION E: TRIP SUMMARY MODAL */}
      <Modal
        show={summaryModalOpen}
        onHide={() => setSummaryModalOpen(false)}
        size="lg"
        centered
      >
        <Modal.Header closeButton style={{ background: '#0f172a', color: '#fff', borderBottom: 'none' }}>
          <Modal.Title style={{ fontSize: 16, fontWeight: 800, display: 'flex', alignItems: 'center', gap: 8 }}>
            📊 Simulated Journey Trip Summary
          </Modal.Title>
        </Modal.Header>
        <Modal.Body style={{ padding: 24 }}>
          {finalSummary ? (
            <div>
              <div style={{
                background: '#f8fafc', borderRadius: 10, padding: 16,
                border: '1px solid #e2e8f0', marginBottom: 20
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <div>
                    <div style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase' }}>Origin</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0f172a' }}>{finalSummary.startingLocation}</div>
                  </div>
                  <ArrowRight size={20} color="#94a3b8" />
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 11, color: '#64748b', textTransform: 'uppercase' }}>Destination</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0f172a' }}>{finalSummary.destination}</div>
                  </div>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 20 }}>
                <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: 11, color: '#64748b' }}>Total Distance</div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: '#0f172a' }}>{finalSummary.totalSimulatedDistanceKm} km</div>
                </div>
                <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: 11, color: '#64748b' }}>Trip Duration</div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: '#0f172a' }}>{finalSummary.tripDurationFormatted}</div>
                </div>
                <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0' }}>
                  <div style={{ fontSize: 11, color: '#64748b' }}>Max / Avg Speed</div>
                  <div style={{ fontSize: 18, fontWeight: 800, color: '#0f172a' }}>
                    {finalSummary.maxSpeedKmph} / {finalSummary.averageSpeedKmph} km/h
                  </div>
                </div>
              </div>

              <div style={{
                background: '#fff', border: '1px solid #e2e8f0', borderRadius: 8,
                padding: 14, marginBottom: 20
              }}>
                <h4 style={{ fontSize: 13, fontWeight: 800, marginBottom: 10, color: '#0f172a' }}>
                  Alerts & Penalties Breakdown ({finalSummary.totalAlerts} Total)
                </h4>
                {finalSummary.alertBreakdownByType && Object.keys(finalSummary.alertBreakdownByType).length > 0 ? (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {Object.entries(finalSummary.alertBreakdownByType).map(([type, count]) => (
                      <span key={type} className="badge bg-light text-dark border" style={{ fontSize: 12, padding: '6px 10px' }}>
                        {type}: <strong>{count}</strong>
                      </span>
                    ))}
                  </div>
                ) : (
                  <div style={{ fontSize: 12, color: '#16a34a', fontWeight: 600 }}>
                    Clean trip: Zero alerts triggered!
                  </div>
                )}

                <div style={{ marginTop: 14, paddingTop: 10, borderTop: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 13, fontWeight: 700, color: '#475569' }}>Total Progressive Penalties:</span>
                  <span style={{ fontSize: 18, fontWeight: 900, color: '#dc2626' }}>
                    ₹{Number(finalSummary.totalPenaltyAmount || 0).toFixed(2)}
                  </span>
                </div>
              </div>

              <div style={{ fontSize: 11, color: '#64748b', background: '#eff6ff', padding: 10, borderRadius: 6, border: '1px solid #bfdbfe' }}>
                ℹ️ <strong>Demonstration Note:</strong> All simulation records were generated and evaluated in PostgreSQL with <code>is_simulation = true</code>. Operational fleet dashboards and historical analytics remain strictly untouched.
              </div>
            </div>
          ) : (
            <div>Generating trip summary...</div>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={() => setSummaryModalOpen(false)}>
            Close
          </Button>
        </Modal.Footer>
      </Modal>

      {/* Global CSS for Leaflet marker pulses */}
      <style>{`
        @keyframes simulationPulse {
          0% { transform: scale(0.9); opacity: 0.8; }
          50% { transform: scale(1.4); opacity: 0.2; }
          100% { transform: scale(0.9); opacity: 0.8; }
        }
        .simulation-marker-pulse {
          animation: simulationPulse 2s infinite ease-in-out;
        }
        .simulation-marker-pulse-danger {
          animation: simulationPulse 0.9s infinite ease-in-out;
        }
      `}</style>
    </div>
  );
};

export default LiveSimulation;
