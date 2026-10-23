import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, User, Car, Bell, Scale, Route,
  FileText, LogOut, ChevronLeft, ChevronRight, Gauge
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const DRIVER_NAV_ITEMS = [
  { to: '/driver/dashboard', icon: <LayoutDashboard size={18} />, label: 'My Dashboard' },
  { to: '/driver/profile',   icon: <User size={18} />,            label: 'My Profile' },
  { to: '/driver/vehicles',  icon: <Car size={18} />,             label: 'My Vehicles' },
  { to: '/driver/alerts',    icon: <Bell size={18} />,            label: 'My Alerts' },
  { to: '/driver/penalties', icon: <Scale size={18} />,           label: 'My Penalties' },
  { to: '/driver/trips',     icon: <Route size={18} />,           label: 'My Trips' },
  { to: '/driver/reports',   icon: <FileText size={18} />,        label: 'My Reports' },
];

const DriverSidebar = () => {
  const [collapsed, setCollapsed] = useState(false);
  const { logout } = useAuth();

  const navLinkStyle = ({ isActive }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: collapsed ? 0 : 10,
    padding: collapsed ? '10px 0' : '10px 14px',
    borderRadius: 10,
    textDecoration: 'none',
    fontWeight: 500,
    fontSize: 13,
    color: isActive ? '#059669' : '#475569',
    background: isActive ? 'linear-gradient(135deg, #ecfdf5, #d1fae5)' : 'transparent',
    justifyContent: collapsed ? 'center' : 'flex-start',
    transition: 'all 0.15s ease',
    overflow: 'hidden',
    whiteSpace: 'nowrap',
    marginBottom: 4,
  });

  return (
    <aside style={{
      width: collapsed ? 60 : 220,
      minWidth: collapsed ? 60 : 220,
      background: '#fff',
      borderRight: '1px solid #f1f5f9',
      display: 'flex',
      flexDirection: 'column',
      transition: 'width 0.2s ease, min-width 0.2s ease',
      overflowX: 'hidden',
      overflowY: 'auto',
    }}>
      {/* Brand Header */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: collapsed ? 'center' : 'space-between',
        padding: collapsed ? '16px 0' : '16px 16px 16px 18px',
        borderBottom: '1px solid #f1f5f9',
      }}>
        {!collapsed && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 32, height: 32, borderRadius: 8,
              background: 'linear-gradient(135deg, #059669, #10b981)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Gauge size={16} color="#fff" />
            </div>
            <span style={{ fontSize: 15, fontWeight: 800, color: '#0f172a' }}>TrackFleet</span>
          </div>
        )}
        {collapsed && (
          <div style={{
            width: 32, height: 32, borderRadius: 8,
            background: 'linear-gradient(135deg, #059669, #10b981)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <Gauge size={16} color="#fff" />
          </div>
        )}
        <button
          onClick={() => setCollapsed(c => !c)}
          style={{
            border: 'none', background: '#f8fafc', borderRadius: 6,
            width: 24, height: 24, cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#94a3b8', flexShrink: 0,
            ...(collapsed ? { position: 'absolute', left: 44, top: 20 } : {})
          }}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}
        </button>
      </div>

      {/* Nav items */}
      <nav style={{ flex: 1, padding: '16px 10px', overflowY: 'auto' }}>
        {!collapsed && (
          <div style={{
            fontSize: 10, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase',
            letterSpacing: '0.08em', padding: '0 8px 8px', marginBottom: 4,
          }}>
            Driver Portal
          </div>
        )}

        {DRIVER_NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            style={navLinkStyle}
            title={collapsed ? item.label : undefined}
          >
            <span style={{ flexShrink: 0 }}>{item.icon}</span>
            {!collapsed && <span>{item.label}</span>}
          </NavLink>
        ))}

        <div style={{ margin: '14px 4px 8px', borderTop: '1px solid #f1f5f9' }} />

        {/* Logout item in sidebar */}
        <button
          onClick={logout}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: collapsed ? 0 : 10,
            padding: collapsed ? '10px 0' : '10px 14px',
            borderRadius: 10,
            border: 'none',
            background: 'transparent',
            fontWeight: 500,
            fontSize: 13,
            color: '#dc2626',
            width: '100%',
            cursor: 'pointer',
            justifyContent: collapsed ? 'center' : 'flex-start',
            transition: 'all 0.15s ease',
          }}
          title={collapsed ? 'Logout' : undefined}
        >
          <LogOut size={18} />
          {!collapsed && <span>Logout</span>}
        </button>
      </nav>

      {/* Footer */}
      {!collapsed && (
        <div style={{
          padding: '12px 16px', borderTop: '1px solid #f1f5f9',
          fontSize: 10, color: '#cbd5e1', textAlign: 'center',
        }}>
          TrackFleet Driver v3.0
        </div>
      )}
    </aside>
  );
};

export default DriverSidebar;
