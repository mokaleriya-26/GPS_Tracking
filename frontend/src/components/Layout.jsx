import React from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

const Layout = ({ alerts }) => {
  return (
    <div className="d-flex flex-column vh-100 bg-light">
      <Navbar alerts={alerts} />
      <div className="d-flex flex-grow-1 overflow-hidden">
        <Sidebar />
        <main className="flex-grow-1 overflow-auto p-4 app-main">
          <div className="container-fluid max-w-1600">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default Layout;
