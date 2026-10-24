import React from 'react';
import { Navbar as BootstrapNavbar, Container, Button, Badge } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LogOut, User, Truck, Bell, Menu, Gauge } from 'lucide-react';

const DriverNavbar = ({ onToggleMobileSidebar }) => {
  const { user, logout } = useAuth();

  return (
    <BootstrapNavbar
      bg="white"
      className="border-bottom sticky-top py-2 px-0"
      style={{
        zIndex: 1020,
        boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
      }}
    >
      <Container fluid className="px-3 px-md-4">
        {/* Left: Mobile hamburger + Brand */}
        <div className="d-flex align-items-center gap-2">
          {onToggleMobileSidebar && (
            <Button
              variant="light"
              size="sm"
              onClick={onToggleMobileSidebar}
              className="d-md-none p-1.5 border-0 text-secondary"
              aria-label="Toggle navigation drawer"
            >
              <Menu size={20} />
            </Button>
          )}

          <BootstrapNavbar.Brand
            as={Link}
            to="/driver/dashboard"
            className="d-flex align-items-center gap-2 p-0 m-0 text-decoration-none"
          >
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: 'linear-gradient(135deg, #059669, #10b981)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <Gauge size={18} color="#ffffff" />
            </div>
            <span style={{ fontSize: 16, fontWeight: 800, color: '#0f172a' }}>
              TrackFleet
            </span>
            <span
              className="badge"
              style={{
                backgroundColor: '#ecfdf5',
                color: '#059669',
                border: '1px solid #d1fae5',
                fontSize: 10,
                fontWeight: 700,
                letterSpacing: '0.04em',
                padding: '3px 7px',
                borderRadius: 6,
              }}
            >
              DRIVER PORTAL
            </span>
          </BootstrapNavbar.Brand>
        </div>

        {/* Right: Assigned Vehicle, Notification indicator, Driver Profile, Logout */}
        <div className="d-flex align-items-center gap-2 gap-sm-3">
          {/* Assigned Vehicle Badge */}
          {user?.vehicleCode && (
            <div
              className="d-none d-sm-flex align-items-center gap-1.5 bg-light px-2.5 py-1 rounded-3 border"
              style={{ fontSize: 12, borderColor: '#e2e8f0' }}
            >
              <Truck size={14} color="#059669" />
              <span className="text-muted" style={{ fontSize: 11 }}>Vehicle:</span>
              <strong className="text-dark" style={{ fontFamily: 'monospace', fontSize: 12 }}>
                {user.vehicleCode}
              </strong>
              {user.vehicleRegistrationNumber && (
                <span className="text-muted" style={{ fontSize: 11 }}>
                  ({user.vehicleRegistrationNumber})
                </span>
              )}
            </div>
          )}

          {/* Notification Indicator */}
          <Link
            to="/driver/alerts"
            className="btn btn-light btn-sm rounded-circle d-flex align-items-center justify-content-center position-relative text-secondary"
            style={{ width: 34, height: 34, padding: 0 }}
            title="View recent alerts"
          >
            <Bell size={16} />
            <span
              className="position-absolute bg-warning rounded-circle"
              style={{
                width: 7,
                height: 7,
                top: 7,
                right: 7,
                border: '1.5px solid #ffffff',
              }}
            />
          </Link>

          {/* Driver Profile Summary */}
          <div className="d-flex align-items-center border-start ps-2 ps-sm-3">
            <Link
              to="/driver/profile"
              className="d-flex align-items-center text-decoration-none"
              title="Go to My Profile"
            >
              <div
                style={{
                  width: 34,
                  height: 34,
                  borderRadius: '50%',
                  background: 'linear-gradient(135deg, #059669, #047857)',
                  color: '#ffffff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 700,
                  fontSize: 13,
                  flexShrink: 0,
                }}
              >
                {user?.driverName ? user.driverName.charAt(0).toUpperCase() : <User size={16} />}
              </div>
              <div className="ms-2 d-none d-md-block me-3 text-start">
                <div
                  className="fw-semibold text-dark text-truncate"
                  style={{ fontSize: 13, maxWidth: 140, lineHeight: 1.2 }}
                >
                  {user?.driverName || 'Driver'}
                </div>
                <div className="text-muted" style={{ fontSize: 11 }}>
                  {user?.driverCode || (user?.driverId ? `DRV#${user.driverId}` : 'Authorized')}
                </div>
              </div>
            </Link>

            {/* Logout Button */}
            <Button
              id="btn-driver-logout"
              variant="outline-danger"
              size="sm"
              onClick={logout}
              className="d-flex align-items-center gap-1.5 ms-1 ms-sm-2"
              style={{
                fontSize: 12,
                borderRadius: 8,
                padding: '5px 10px',
                fontWeight: 600,
              }}
              title="Sign out of Driver Portal"
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
