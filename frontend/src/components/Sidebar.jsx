import React from 'react';
import { Nav } from 'react-bootstrap';
import { Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, Car, Bell } from 'lucide-react';

const Sidebar = () => {
  const location = useLocation();

  const links = [
    { to: '/', icon: <LayoutDashboard size={20} className="me-3" />, label: 'Dashboard' },
    { to: '/vehicles', icon: <Car size={20} className="me-3" />, label: 'Vehicles' },
    { to: '/alerts', icon: <Bell size={20} className="me-3" />, label: 'Alerts' },
  ];

  return (
    <div className="bg-white border-end h-100 position-fixed d-none d-md-flex flex-column shadow-sm" style={{ width: '250px', top: 0, left: 0, zIndex: 1000, paddingTop: '70px' }}>
      <Nav className="flex-column px-3 pt-4 w-100">
        <div className="text-uppercase text-muted fw-bold mb-3 px-3" style={{ fontSize: '12px', letterSpacing: '1px' }}>Menu</div>
        {links.map((link, idx) => {
          const isActive = location.pathname === link.to;
          return (
            <Nav.Item key={idx} className="mb-2">
              <Nav.Link 
                as={Link} 
                to={link.to} 
                className={`d-flex align-items-center px-3 py-2 rounded ${isActive ? 'bg-primary text-white' : 'text-dark hover-bg-light'}`}
                style={{ fontWeight: isActive ? '600' : '400' }}
              >
                {link.icon}
                {link.label}
              </Nav.Link>
            </Nav.Item>
          )
        })}
      </Nav>
    </div>
  );
};

export default Sidebar;
