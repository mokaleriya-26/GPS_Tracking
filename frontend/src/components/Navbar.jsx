import React from 'react';
import { Navbar as BootstrapNavbar, Container, Badge, Button } from 'react-bootstrap';
import { Bell, LogOut, Shield } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const Navbar = ({ alerts = [] }) => {
  const { user, logout } = useAuth();

  return (
    <BootstrapNavbar bg="white" className="border-bottom shadow-sm sticky-top">
      <Container fluid className="px-4">
        <BootstrapNavbar.Brand as={Link} to="/" className="fw-bold text-primary d-flex align-items-center gap-2">
          <span>TrackFleet</span>
          <span className="badge bg-primary-subtle text-primary border border-primary-subtle" style={{ fontSize: 10, fontWeight: 700 }}>
            ADMIN PORTAL
          </span>
        </BootstrapNavbar.Brand>

        <div className="d-flex align-items-center gap-3">
          <Link to="/alerts" className="position-relative text-dark me-2">
            <Bell size={20} />
            {alerts.length > 0 && (
              <Badge 
                bg="danger" 
                pill 
                className="position-absolute top-0 start-100 translate-middle"
                style={{ fontSize: 10 }}
              >
                {alerts.length}
              </Badge>
            )}
          </Link>

          <div className="d-flex align-items-center border-start ps-3">
            <div className="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center" style={{ width: '36px', height: '36px' }}>
              <Shield size={18} />
            </div>
            <div className="ms-2 d-none d-md-block me-3">
              <div className="fw-semibold text-dark" style={{ fontSize: '13px' }}>{user?.driverName || 'Admin User'}</div>
              <div className="text-muted" style={{ fontSize: '11px' }}>Fleet Administrator</div>
            </div>
            <Button
              id="btn-logout"
              variant="outline-danger"
              size="sm"
              onClick={logout}
              className="d-flex align-items-center gap-1"
              style={{ fontSize: 12, borderRadius: 6 }}
              title="Sign out of TrackFleet"
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

export default Navbar;
