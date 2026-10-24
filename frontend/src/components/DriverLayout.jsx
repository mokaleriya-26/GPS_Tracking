import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import DriverNavbar from './DriverNavbar';
import DriverSidebar from './DriverSidebar';

const DriverLayout = () => {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      <DriverNavbar onToggleMobileSidebar={() => setMobileOpen(prev => !prev)} />
      <div style={{ display: 'flex', flex: '1 1 0', overflow: 'hidden', minHeight: 0, position: 'relative' }}>
        <DriverSidebar mobileOpen={mobileOpen} onCloseMobile={() => setMobileOpen(false)} />
        <main className="app-main" style={{ padding: '24px 20px', background: '#f8fafc' }}>
          <div className="container-fluid" style={{ width: '100%', maxWidth: '100%' }}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default DriverLayout;
