import React, { useState, useEffect } from 'react';
import { Cpu, Activity, RefreshCw, Zap, Wind, Camera, Mic, Waves } from 'lucide-react';
import api from '../api/axios';

const SENSOR_TABS = [
  { key: 'acceleration', label: 'Acceleration', icon: <Zap size={14} />, endpoint: '/api/sensors/acceleration?size=20' },
  { key: 'gps_aqi',      label: 'GPS / AQI',    icon: <Wind size={14} />,     endpoint: '/api/sensors/gps-aqi?size=20' },
  { key: 'camera',       label: 'Camera',        icon: <Camera size={14} />,   endpoint: '/api/sensors/camera?size=20' },
  { key: 'audio',        label: 'Audio',         icon: <Mic size={14} />,      endpoint: '/api/sensors/audio?size=20' },
  { key: 'ultrasonic',   label: 'Ultrasonic',    icon: <Waves size={14} />,    endpoint: '/api/sensors/ultrasonic-distance?size=20' },
];

const formatTime = (d) => d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '—';

export default function SensorData() {
  const [tab, setTab] = useState('acceleration');
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = (endpoint) => {
    setLoading(true);
    api.get(endpoint)
      .then(r => {
        const d = r.data?.data;
        setData(Array.isArray(d) ? d : (d?.content || []));
        setError(null);
      })
      .catch(() => setError('No sensor data available yet for this type.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    const t = SENSOR_TABS.find(s => s.key === tab);
    if (t) load(t.endpoint);
  }, [tab]);

  const renderRow = (item, i) => {
    const fields = Object.entries(item).filter(([k]) => !['id', 'vehicleId', 'tripId'].includes(k));
    return (
      <tr key={item.id || i}>
        {fields.slice(0, 6).map(([k, v]) => (
          <td key={k} style={{ fontSize: 12, maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {k.includes('Time') || k.includes('At') ? formatTime(v) : String(v ?? '—')}
          </td>
        ))}
      </tr>
    );
  };

  const currentTab = SENSOR_TABS.find(s => s.key === tab);
  const fields = data.length > 0 ? Object.keys(data[0]).filter(k => !['id', 'vehicleId', 'tripId'].includes(k)).slice(0, 6) : [];

  return (
    <div className="container-fluid py-4">
      <div className="d-flex align-items-center justify-content-between mb-4">
        <div>
          <h1 className="h3 fw-bold mb-1" style={{ color: '#0f172a' }}>
            <Cpu size={22} className="me-2 text-info" />Sensor Data
          </h1>
          <p className="text-muted mb-0">Raw sensor readings from fleet vehicles</p>
        </div>
        <button className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
          onClick={() => currentTab && load(currentTab.endpoint)}>
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {/* Sensor tabs */}
      <div className="d-flex gap-2 flex-wrap mb-4">
        {SENSOR_TABS.map(s => (
          <button key={s.key} onClick={() => setTab(s.key)}
            className={`btn btn-sm d-flex align-items-center gap-1 ${tab === s.key ? 'btn-primary' : 'btn-outline-secondary'}`}
            style={{ borderRadius: 8 }}>
            {s.icon} {s.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="text-center py-5 text-muted">
          <Activity size={40} className="mb-2 opacity-25" />
          <p>{error}</p>
          <p style={{ fontSize: 12 }}>Sensor data is stored via <code>POST /api/sensors/*</code> endpoints when sensors are connected.</p>
        </div>
      )}

      {loading && <div className="text-center py-5"><div className="spinner-border text-info" /></div>}

      {!loading && !error && (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
          <div className="card-header bg-white border-0 py-3 px-4">
            <h6 className="mb-0 fw-semibold">{currentTab?.label} — Last {data.length} readings</h6>
          </div>
          <div className="table-responsive">
            <table className="table table-hover mb-0" style={{ fontSize: 12 }}>
              <thead style={{ background: '#f8fafc' }}>
                <tr>
                  {fields.map(f => (
                    <th key={f} className="border-0 py-2 px-3" style={{ fontWeight: 600, color: '#475569', fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      {f.replace(/([A-Z])/g, ' $1').trim()}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.map((item, i) => renderRow(item, i))}
                {data.length === 0 && (
                  <tr><td colSpan={6} className="text-center text-muted py-4">No readings found.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
