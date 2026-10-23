import React from 'react';
import { Outlet } from 'react-router-dom';
import DriverNavbar from './DriverNavbar';
import DriverSidebar from './DriverSidebar';

const DriverLayout = () => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      <DriverNavbar />
      <div style={{ display: 'flex', flex: '1 1 0', overflow: 'hidden', minHeight: 0 }}>
        <DriverSidebar />
        <main className="app-main" style={{ padding: '24px' }}>
          <div className="container-fluid" style={{ width: '100%', maxWidth: '100%' }}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default DriverLayout;
