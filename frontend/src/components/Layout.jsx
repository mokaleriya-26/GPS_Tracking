import React from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

const Layout = ({ alerts }) => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      <Navbar alerts={alerts} />
      <div style={{ display: 'flex', flex: '1 1 0', overflow: 'hidden', minHeight: 0 }}>
        <Sidebar />
        {/* min-width:0 is CRITICAL — allows this flex child to shrink below its content size */}
        <main className="app-main" style={{ padding: '24px' }}>
          <div className="container-fluid" style={{ width: '100%', maxWidth: '100%' }}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default Layout;
