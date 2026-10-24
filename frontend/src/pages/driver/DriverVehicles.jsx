import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Badge, Button, Spinner, Alert } from 'react-bootstrap';
import { Truck, CheckCircle2, History, Gauge, MapPin, RefreshCw, AlertCircle, Calendar } from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverVehicles() {
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState('ALL'); // ALL, CURRENT, HISTORICAL

  const fetchVehicles = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await driverPortalApi.getVehicles();
      setVehicles(data || []);
    } catch (err) {
      console.error('Failed to load driver vehicles:', err);
      setError('Unable to fetch vehicle history. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchVehicles();
  }, []);

  const filteredVehicles = vehicles.filter(v => {
    if (filter === 'CURRENT') return v.assignmentStatus === 'CURRENT';
    if (filter === 'HISTORICAL') return v.assignmentStatus === 'HISTORICAL';
    return true;
  });

  const currentCount = vehicles.filter(v => v.assignmentStatus === 'CURRENT').length;
  const historicalCount = vehicles.filter(v => v.assignmentStatus === 'HISTORICAL').length;

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Header */}
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Vehicles</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Current assigned vehicles and historical fleet units operated across your trips.
          </p>
        </div>

        <div className="d-flex align-items-center gap-2">
          {/* Filter Pills */}
          <div className="btn-group bg-white p-1 rounded-3 border" style={{ borderColor: '#e2e8f0' }}>
            <Button
              variant={filter === 'ALL' ? 'success' : 'light'}
              size="sm"
              onClick={() => setFilter('ALL')}
              className="fw-semibold"
              style={{
                fontSize: 12,
                borderRadius: 6,
                backgroundColor: filter === 'ALL' ? '#059669' : 'transparent',
                borderColor: filter === 'ALL' ? '#059669' : 'transparent',
                color: filter === 'ALL' ? '#ffffff' : '#64748b',
              }}
            >
              All ({vehicles.length})
            </Button>
            <Button
              variant={filter === 'CURRENT' ? 'success' : 'light'}
              size="sm"
              onClick={() => setFilter('CURRENT')}
              className="fw-semibold"
              style={{
                fontSize: 12,
                borderRadius: 6,
                backgroundColor: filter === 'CURRENT' ? '#059669' : 'transparent',
                borderColor: filter === 'CURRENT' ? '#059669' : 'transparent',
                color: filter === 'CURRENT' ? '#ffffff' : '#64748b',
              }}
            >
              Assigned ({currentCount})
            </Button>
            <Button
              variant={filter === 'HISTORICAL' ? 'success' : 'light'}
              size="sm"
              onClick={() => setFilter('HISTORICAL')}
              className="fw-semibold"
              style={{
                fontSize: 12,
                borderRadius: 6,
                backgroundColor: filter === 'HISTORICAL' ? '#059669' : 'transparent',
                borderColor: filter === 'HISTORICAL' ? '#059669' : 'transparent',
                color: filter === 'HISTORICAL' ? '#ffffff' : '#64748b',
              }}
            >
              Historical ({historicalCount})
            </Button>
          </div>

          <Button
            variant="outline-secondary"
            size="sm"
            onClick={fetchVehicles}
            disabled={loading}
            className="d-flex align-items-center"
            style={{ borderRadius: 8, height: 34, width: 34, padding: 0, justifyContent: 'center' }}
            title="Refresh list"
          >
            <RefreshCw size={13} className={loading ? 'spin' : ''} />
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="danger" className="mb-4 rounded-3 d-flex align-items-center justify-content-between">
          <div className="d-flex align-items-center gap-2">
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
          <Button variant="outline-danger" size="sm" onClick={fetchVehicles}>
            Retry
          </Button>
        </Alert>
      )}

      {loading ? (
        <div className="py-5 text-center text-muted" style={{ minHeight: '40vh' }}>
          <Spinner animation="border" variant="success" size="sm" className="mb-2" />
          <div style={{ fontSize: 13 }}>Loading assigned vehicles…</div>
        </div>
      ) : filteredVehicles.length === 0 ? (
        <div className="card border-0 shadow-sm p-5 text-center" style={{ borderRadius: 14 }}>
          <Truck size={36} className="text-muted mx-auto mb-2 opacity-50" />
          <h5 className="fw-bold text-dark">No Vehicles Found</h5>
          <p className="text-muted small mb-0">
            {filter === 'ALL'
              ? 'No vehicle records or past trip logs are associated with this driver identity.'
              : `No ${filter.toLowerCase()} vehicles found matching your current filter.`}
          </p>
        </div>
      ) : (
        <Row className="g-3">
          {filteredVehicles.map((veh) => {
            const isCurrent = veh.assignmentStatus === 'CURRENT';

            return (
              <Col key={veh.id} xs={12} md={6} lg={4}>
                <div
                  className="card border-0 shadow-sm h-100"
                  style={{
                    borderRadius: 14,
                    border: isCurrent ? '1.5px solid #a7f3d0 !important' : '1px solid #e2e8f0 !important',
                    background: isCurrent ? 'linear-gradient(180deg, #ffffff 0%, #f0fdf4 100%)' : '#ffffff',
                    transition: 'all 0.15s ease',
                  }}
                >
                  <div className="card-body p-4 d-flex flex-column justify-content-between">
                    <div>
                      <div className="d-flex align-items-start justify-content-between mb-3">
                        <div className="d-flex align-items-center gap-2.5">
                          <div
                            style={{
                              width: 40,
                              height: 40,
                              borderRadius: 10,
                              background: isCurrent ? '#ecfdf5' : '#f1f5f9',
                              color: isCurrent ? '#059669' : '#64748b',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              flexShrink: 0,
                            }}
                          >
                            <Truck size={20} />
                          </div>
                          <div>
                            <h3 className="fw-bold text-dark m-0" style={{ fontSize: 17 }}>
                              {veh.code}
                            </h3>
                            <span className="text-muted" style={{ fontSize: 12 }}>
                              {veh.model || veh.vehicleType || 'Commercial Unit'}
                            </span>
                          </div>
                        </div>

                        <span
                          className="badge"
                          style={{
                            backgroundColor: isCurrent ? '#ecfdf5' : '#f1f5f9',
                            color: isCurrent ? '#059669' : '#64748b',
                            border: `1px solid ${isCurrent ? '#a7f3d0' : '#e2e8f0'}`,
                            fontSize: 10,
                            fontWeight: 700,
                            padding: '4px 8px',
                          }}
                        >
                          {isCurrent ? (
                            <>
                              <CheckCircle2 size={11} className="me-1" />
                              ASSIGNED
                            </>
                          ) : (
                            <>
                              <History size={11} className="me-1" />
                              HISTORICAL
                            </>
                          )}
                        </span>
                      </div>

                      {/* Info Rows */}
                      <div className="bg-light p-3 rounded-3 mb-3 border" style={{ borderColor: '#f1f5f9' }}>
                        <div className="d-flex justify-content-between align-items-center mb-1.5" style={{ fontSize: 12 }}>
                          <span className="text-muted">Registration Plate:</span>
                          <strong className="text-dark font-monospace">{veh.registrationNumber}</strong>
                        </div>
                        <div className="d-flex justify-content-between align-items-center mb-1.5" style={{ fontSize: 12 }}>
                          <span className="text-muted">Vehicle Status:</span>
                          <span
                            className="badge"
                            style={{
                              backgroundColor: veh.status === 'ACTIVE' ? '#ecfdf5' : '#f1f5f9',
                              color: veh.status === 'ACTIVE' ? '#059669' : '#475569',
                              fontSize: 10,
                              fontWeight: 700,
                            }}
                          >
                            {veh.status || 'ACTIVE'}
                          </span>
                        </div>
                        <div className="d-flex justify-content-between align-items-center" style={{ fontSize: 12 }}>
                          <span className="text-muted">Trips with You:</span>
                          <strong className="text-dark">{veh.tripCountWithDriver || 0} Trips</strong>
                        </div>
                      </div>
                    </div>

                    {veh.lastKnownLocation && (
                      <div className="text-muted d-flex align-items-center gap-1.5 pt-2 border-top" style={{ fontSize: 11 }}>
                        <MapPin size={12} className="text-danger flex-shrink-0" />
                        <span className="text-truncate">{veh.lastKnownLocation}</span>
                      </div>
                    )}
                  </div>
                </div>
              </Col>
            );
          })}
        </Row>
      )}
    </div>
  );
}
