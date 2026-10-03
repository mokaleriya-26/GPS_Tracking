import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard, Car, Bell, FileText, Users, Route,
  Settings, ChevronLeft, ChevronRight, Gauge
} from 'lucide-react';

const NAV_GROUPS = [
  {
    label: 'Overview',
    items: [
      { to: '/',        icon: <LayoutDashboard size={18} />, label: 'Dashboard' },
    ]
  },
  {
    label: 'Fleet',
    items: [
      { to: '/vehicles', icon: <Car size={18} />,   label: 'Vehicles' },
      { to: '/drivers',  icon: <Users size={18} />, label: 'Drivers' },
      { to: '/trips',    icon: <Route size={18} />, label: 'Trips' },
    ]
  },
  {
    label: 'Monitoring',
    items: [
      { to: '/alerts',  icon: <Bell size={18} />,   label: 'Alerts' },
    ]
  },
  {
    label: 'Reporting',
    items: [
      { to: '/reports',  icon: <FileText size={18} />, label: 'Reports' },
    ]
  },
  {
    label: 'System',
    items: [
      { to: '/settings', icon: <Settings size={18} />, label: 'Settings' },
    ]
  },
];

const Sidebar = () => {
  const [collapsed, setCollapsed] = useState(false);

  const navLinkStyle = ({ isActive }) => ({
    display: 'flex',
    alignItems: 'center',
    gap: collapsed ? 0 : 10,
    padding: collapsed ? '10px 0' : '9px 14px',
    borderRadius: 10,
    textDecoration: 'none',
    fontWeight: 500,
    fontSize: 13,
    color: isActive ? '#1d4ed8' : '#475569',
    background: isActive ? 'linear-gradient(135deg,#eff6ff,#dbeafe)' : 'transparent',
    justifyContent: collapsed ? 'center' : 'flex-start',
    transition: 'all 0.15s ease',
    overflow: 'hidden',
    whiteSpace: 'nowrap',
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
      {/* Logo area */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: collapsed ? 'center' : 'space-between',
        padding: collapsed ? '16px 0' : '16px 16px 16px 18px',
        borderBottom: '1px solid #f1f5f9',
      }}>
        {!collapsed && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 32, height: 32, borderRadius: 8,
              background: 'linear-gradient(135deg,#2563eb,#7c3aed)',
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
            background: 'linear-gradient(135deg,#2563eb,#7c3aed)',
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

      {/* Nav groups */}
      <nav style={{ flex: 1, padding: '12px 8px', overflowY: 'auto' }}>
        {NAV_GROUPS.map((group) => (
          <div key={group.label} style={{ marginBottom: 8 }}>
            {!collapsed && (
              <div style={{
                fontSize: 10, fontWeight: 700, color: '#94a3b8', textTransform: 'uppercase',
                letterSpacing: '0.08em', padding: '6px 8px 4px', marginBottom: 2,
              }}>
                {group.label}
              </div>
            )}
            {collapsed && <div style={{ height: 4 }} />}
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                style={navLinkStyle}
                title={collapsed ? item.label : undefined}
              >
                <span style={{ flexShrink: 0 }}>{item.icon}</span>
                {!collapsed && <span>{item.label}</span>}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      {/* Version */}
      {!collapsed && (
        <div style={{
          padding: '12px 16px', borderTop: '1px solid #f1f5f9',
          fontSize: 10, color: '#cbd5e1', textAlign: 'center',
        }}>
          TrackFleet v3.0 · Spring Boot
        </div>
      )}
    </aside>
  );
};

export default Sidebar;
