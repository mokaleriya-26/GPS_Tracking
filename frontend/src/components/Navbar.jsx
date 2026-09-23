import React from 'react';
import { Navbar as BootstrapNavbar, Container, Badge } from 'react-bootstrap';
import { Bell } from 'lucide-react';
import { Link } from 'react-router-dom';

const Navbar = ({ alerts = [] }) => {
  return (
    <BootstrapNavbar bg="white" className="border-bottom shadow-sm sticky-top">
      <Container fluid className="px-4">
        <BootstrapNavbar.Brand as={Link} to="/" className="fw-bold text-primary">
          TrackFleet
        </BootstrapNavbar.Brand>
        <div className="d-flex align-items-center">
          <Link to="/alerts" className="position-relative text-dark me-4">
            <Bell size={24} />
            {alerts.length > 0 && (
              <Badge 
                bg="danger" 
                pill 
                className="position-absolute top-0 start-100 translate-middle"
              >
                {alerts.length}
              </Badge>
            )}
          </Link>
          <div className="d-flex align-items-center">
            <div className="bg-primary text-white rounded-circle d-flex align-items-center justify-content-center" style={{ width: '40px', height: '40px' }}>
              AD
            </div>
            <div className="ms-2 d-none d-md-block">
              <div className="fw-semibold text-dark" style={{ fontSize: '14px' }}>Admin User</div>
              <div className="text-muted" style={{ fontSize: '12px' }}>Manager</div>
            </div>
          </div>
        </div>
      </Container>
    </BootstrapNavbar>
  );
};

export default Navbar;
