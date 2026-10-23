import React from 'react';
import { Navbar as BootstrapNavbar, Container, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LogOut, User, Truck, ShieldCheck } from 'lucide-react';

const DriverNavbar = () => {
  const { user, logout } = useAuth();

  return (
    <BootstrapNavbar bg="white" className="border-bottom shadow-sm sticky-top">
      <Container fluid className="px-4">
        <BootstrapNavbar.Brand as={Link} to="/driver/dashboard" className="fw-bold text-primary d-flex align-items-center gap-2">
          <span>TrackFleet</span>
          <span className="badge bg-success-subtle text-success border border-success-subtle" style={{ fontSize: 10, fontWeight: 700 }}>
            DRIVER PORTAL
          </span>
        </BootstrapNavbar.Brand>

        <div className="d-flex align-items-center gap-3">
          {/* Assigned Vehicle Badge */}
          {user?.vehicleCode && (
            <div className="d-none d-sm-flex align-items-center gap-1 bg-light px-2.5 py-1 rounded-3 border" style={{ fontSize: 12 }}>
              <Truck size={14} className="text-primary" />
              <span className="text-muted">Vehicle:</span>
              <strong className="text-dark">{user.vehicleCode}</strong>
              {user.vehicleRegistrationNumber && (
                <span className="text-muted ms-1">({user.vehicleRegistrationNumber})</span>
              )}
            </div>
          )}

          {/* Driver Profile */}
          <div className="d-flex align-items-center border-start ps-3">
            <div className="bg-success text-white rounded-circle d-flex align-items-center justify-content-center" style={{ width: '36px', height: '36px' }}>
              <User size={18} />
            </div>
            <div className="ms-2 d-none d-md-block me-3">
              <div className="fw-semibold text-dark" style={{ fontSize: '13px' }}>{user?.driverName || 'Driver'}</div>
              <div className="text-muted" style={{ fontSize: '11px' }}>ID: {user?.driverCode || `DRV#${user?.driverId}`}</div>
            </div>
            <Button
              id="btn-driver-logout"
              variant="outline-danger"
              size="sm"
              onClick={logout}
              className="d-flex align-items-center gap-1"
              style={{ fontSize: 12, borderRadius: 6 }}
              title="Sign out"
            >
              <LogOut size={13} />
              <span className="d-none d-sm-inline">Logout</span>
            </Button>
          </div>
        </div>
      </Container>
    </BootstrapNavbar>
  );
};

export default DriverNavbar;
