import React, { useState, useEffect } from 'react';
import { Row, Col, Card, Badge, Button, Spinner, Alert } from 'react-bootstrap';
import { 
  User, Mail, Phone, ShieldCheck, Award, Calendar, 
  FileText, Truck, AlertCircle, RefreshCw, CheckCircle2 
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

const formatDateTime = (dt) => {
  if (!dt) return '—';
  try {
    return new Date(dt).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric'
    });
  } catch {
    return dt;
  }
};

export default function DriverProfile() {
  const [profile, setProfile] = useState(null);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchProfileData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [profileData, vehicleData] = await Promise.all([
        driverPortalApi.getProfile(),
        driverPortalApi.getVehicles()
      ]);
      setProfile(profileData);
      setVehicles(vehicleData || []);
    } catch (err) {
      console.error('Failed to load profile data:', err);
      setError('Unable to load your profile information. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfileData();
  }, []);

  if (loading) {
    return (
      <div className="d-flex flex-column items-center justify-content-center py-5 text-center text-muted" style={{ minHeight: '60vh' }}>
        <Spinner animation="border" variant="success" className="mb-2" />
        <div style={{ fontSize: 13 }}>Loading driver profile…</div>
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="danger" className="rounded-3 d-flex align-items-center justify-content-between p-3">
        <div className="d-flex align-items-center gap-2">
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
        <Button variant="outline-danger" size="sm" onClick={fetchProfileData}>
          <RefreshCw size={14} className="me-1" /> Retry
        </Button>
      </Alert>
    );
  }

  const currentVehicles = vehicles.filter(v => v.assignmentStatus === 'CURRENT');
  const scoreValue = profile?.safetyScore != null ? Math.round(profile.safetyScore) : 100;
  const scoreColor = scoreValue >= 85 ? '#059669' : scoreValue >= 70 ? '#d97706' : '#dc2626';

  return (
    <div style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Header */}
      <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
        <div>
          <h1 className="h3 mb-1 fw-bold" style={{ color: '#0f172a' }}>My Profile</h1>
          <p className="text-muted mb-0" style={{ fontSize: 13 }}>
            Personal credentials, licensing status, and verified fleet assignment details.
          </p>
        </div>
        <Button
          variant="outline-secondary"
          size="sm"
          onClick={fetchProfileData}
          className="d-flex align-items-center gap-1.5"
          style={{ borderRadius: 8, fontSize: 12 }}
        >
          <RefreshCw size={13} />
          <span>Refresh</span>
        </Button>
      </div>

      <Row className="g-4">
        {/* Left Column: Identity & Safety Index */}
        <Col xs={12} lg={4}>
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14 }}>
            <div className="card-body p-4 text-center d-flex flex-column justify-content-between">
              <div>
                <div
                  style={{
                    width: 76,
                    height: 76,
                    borderRadius: '50%',
                    background: 'linear-gradient(135deg, #059669, #10b981)',
                    color: '#ffffff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 28,
                    fontWeight: 800,
                    margin: '0 auto 16px',
                    boxShadow: '0 8px 20px -4px rgba(5, 150, 105, 0.35)',
                  }}
                >
                  {profile?.name ? profile.name.charAt(0).toUpperCase() : <User size={32} />}
                </div>

                <h3 className="fw-bold text-dark mb-1" style={{ fontSize: 20 }}>
                  {profile?.name || 'Driver'}
                </h3>
                <span
                  className="badge font-monospace mb-3"
                  style={{
                    backgroundColor: '#ecfdf5',
                    color: '#059669',
                    border: '1px solid #a7f3d0',
                    fontSize: 12,
                    fontWeight: 700,
                    padding: '4px 10px',
                  }}
                >
                  {profile?.code || 'ID N/A'}
                </span>

                <div className="bg-light p-3 rounded-3 text-start border mt-3" style={{ borderColor: '#f1f5f9' }}>
                  <div className="d-flex justify-content-between align-items-center mb-2" style={{ fontSize: 12 }}>
                    <span className="text-muted">Account Status:</span>
                    <span
                      className="badge"
                      style={{
                        backgroundColor: profile?.active !== false ? '#ecfdf5' : '#fef2f2',
                        color: profile?.active !== false ? '#059669' : '#dc2626',
                        fontSize: 10,
                        fontWeight: 700,
                      }}
                    >
                      {profile?.active !== false ? 'ACTIVE' : 'INACTIVE'}
                    </span>
                  </div>

                  <div className="d-flex justify-content-between align-items-center mb-2" style={{ fontSize: 12 }}>
                    <span className="text-muted">Driving License:</span>
                    <strong className="text-dark font-monospace">{profile?.licenseNumber || '—'}</strong>
                  </div>

                  <div className="d-flex justify-content-between align-items-center" style={{ fontSize: 12 }}>
                    <span className="text-muted">Joined On:</span>
                    <span className="text-dark">{formatDateTime(profile?.joinedOn)}</span>
                  </div>
                </div>
              </div>

              {/* Safety Index Tile */}
              <div
                className="p-3 rounded-3 mt-4 text-start border"
                style={{
                  background: 'linear-gradient(135deg, #ffffff 0%, #f0fdf4 100%)',
                  borderColor: '#dcfce7',
                }}
              >
                <div className="d-flex align-items-center gap-3">
                  <div
                    style={{
                      width: 42,
                      height: 42,
                      borderRadius: 10,
                      background: '#ecfdf5',
                      color: '#059669',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <ShieldCheck size={22} />
                  </div>
                  <div>
                    <div className="text-muted" style={{ fontSize: 11, textTransform: 'uppercase', fontWeight: 600 }}>
                      Safety Index
                    </div>
                    <div style={{ fontSize: 22, fontWeight: 800, color: scoreColor, lineHeight: 1.1 }}>
                      {scoreValue} <span className="text-muted" style={{ fontSize: 13, fontWeight: 400 }}>/ 100</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Col>

        {/* Right Column: Contact Details & Active Vehicle Assignments */}
        <Col xs={12} lg={8}>
          {/* Professional & Contact Details Card */}
          <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center gap-2">
              <FileText size={18} color="#059669" />
              <span className="fw-bold text-dark" style={{ fontSize: 15 }}>Contact & Credentials</span>
            </div>
            <div className="card-body p-4 pt-0">
              <Row className="g-3">
                <Col xs={12} sm={6}>
                  <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                    <div className="d-flex align-items-center gap-1.5 text-muted mb-1" style={{ fontSize: 11 }}>
                      <Mail size={13} className="text-primary" />
                      <span>Email Address</span>
                    </div>
                    <div className="fw-semibold text-dark text-truncate" style={{ fontSize: 13 }}>
                      {profile?.email || 'driver@trackfleet.internal'}
                    </div>
                  </div>
                </Col>

                <Col xs={12} sm={6}>
                  <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                    <div className="d-flex align-items-center gap-1.5 text-muted mb-1" style={{ fontSize: 11 }}>
                      <Phone size={13} className="text-success" />
                      <span>Phone Contact</span>
                    </div>
                    <div className="fw-semibold text-dark font-monospace" style={{ fontSize: 13 }}>
                      {profile?.phone || '—'}
                    </div>
                  </div>
                </Col>

                <Col xs={12} sm={6}>
                  <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                    <div className="d-flex align-items-center gap-1.5 text-muted mb-1" style={{ fontSize: 11 }}>
                      <Award size={13} className="text-warning" />
                      <span>Lifetime Trips</span>
                    </div>
                    <div className="fw-bold text-dark" style={{ fontSize: 15 }}>
                      {profile?.totalTrips || 0} Recorded Trips
                    </div>
                  </div>
                </Col>

                <Col xs={12} sm={6}>
                  <div className="bg-light p-3 rounded-3 border" style={{ borderColor: '#f1f5f9' }}>
                    <div className="d-flex align-items-center gap-1.5 text-muted mb-1" style={{ fontSize: 11 }}>
                      <Calendar size={13} className="text-info" />
                      <span>Profile Created</span>
                    </div>
                    <div className="fw-semibold text-dark" style={{ fontSize: 13 }}>
                      {formatDateTime(profile?.createdAt || profile?.joinedOn)}
                    </div>
                  </div>
                </Col>
              </Row>
            </div>
          </div>

          {/* Active Vehicle Assignments Card */}
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14 }}>
            <div className="card-header bg-white border-0 py-3 px-4 d-flex align-items-center gap-2">
              <Truck size={18} color="#059669" />
              <span className="fw-bold text-dark" style={{ fontSize: 15 }}>Active Vehicle Assignments</span>
            </div>
            <div className="card-body p-4 pt-0">
              {currentVehicles.length === 0 ? (
                <div className="p-4 bg-light rounded-3 text-center text-muted" style={{ fontSize: 13 }}>
                  <Truck size={28} className="text-muted mb-2 opacity-50" />
                  <div>No vehicles currently assigned directly in the system.</div>
                  <div className="small text-muted mt-1">Please contact your fleet administrator if you require assignment updates.</div>
                </div>
              ) : (
                <Row className="g-3">
                  {currentVehicles.map(veh => (
                    <Col key={veh.id} xs={12} sm={6}>
                      <div
                        className="p-3 rounded-3 border"
                        style={{
                          background: '#ffffff',
                          borderColor: '#a7f3d0',
                          boxShadow: '0 1px 3px rgba(0, 0, 0, 0.04)',
                        }}
                      >
                        <div className="d-flex justify-content-between align-items-start mb-2">
                          <div>
                            <span
                              className="badge"
                              style={{
                                backgroundColor: '#ecfdf5',
                                color: '#059669',
                                fontSize: 9,
                                fontWeight: 700,
                              }}
                            >
                              CURRENT PRIMARY
                            </span>
                            <h4 className="fw-bold text-dark mt-1 mb-0" style={{ fontSize: 16 }}>
                              {veh.code}
                            </h4>
                            <div className="text-muted" style={{ fontSize: 11 }}>
                              {veh.model || veh.vehicleType || 'Commercial Unit'}
                            </div>
                          </div>
                          <span className="badge bg-light text-dark font-monospace border" style={{ fontSize: 11 }}>
                            {veh.registrationNumber}
                          </span>
                        </div>

                        <div className="pt-2 border-top d-flex justify-content-between align-items-center text-muted" style={{ fontSize: 11 }}>
                          <span>Status: <strong className="text-success">{veh.status || 'ACTIVE'}</strong></span>
                          <span>Trips: <strong className="text-dark">{veh.tripCountWithDriver || 0}</strong></span>
                        </div>
                      </div>
                    </Col>
                  ))}
                </Row>
              )}
            </div>
          </div>
        </Col>
      </Row>
    </div>
  );
}
