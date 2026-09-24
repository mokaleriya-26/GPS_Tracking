import React from 'react';
import { Card, Table, Badge } from 'react-bootstrap';

const Vehicles = ({ vehicles = [] }) => {
  return (
    <div>
      <div className="mb-4">
        <h1 className="h3 mb-2 text-dark fw-bold">Vehicles Directory</h1>
        <p className="text-muted">View all active and inactive fleet vehicles.</p>
      </div>

      <Card className="shadow-sm border-0">
        <Card.Body className="p-0 overflow-auto">
          <Table hover responsive className="mb-0 align-middle">
            <thead className="bg-light text-muted" style={{ fontSize: '14px' }}>
              <tr>
                <th className="border-0 px-4 py-3">Vehicle ID</th>
                <th className="border-0 py-3">Vehicle Number</th>
                <th className="border-0 py-3">Driver</th>
                <th className="border-0 py-3">Status</th>
                <th className="border-0 py-3">Speed Max</th>
                <th className="border-0 py-3">Last Location</th>
              </tr>
            </thead>
            <tbody style={{ fontSize: '14px' }}>
              {vehicles.map((v, idx) => (
                <tr key={v.vehicle_id || idx}>
                  <td className="px-4 fw-semibold text-dark">{v.vehicle_id}</td>
                  <td>{v.number}</td>
                  <td>{v.driver_name || 'Unassigned'}</td>
                  <td>
                    <Badge bg={v.ignition ? "success" : "secondary"}>
                      {v.ignition ? "Tracking" : "Offline"}
                    </Badge>
                  </td>
                  <td>{v.speed} km/h</td>
                  <td>{v.location || 'N/A'}</td>
                </tr>
              ))}
              {vehicles.length === 0 && (
                <tr>
                  <td colSpan={6} className="text-center text-muted py-4">No vehicles found</td>
                </tr>
              )}
            </tbody>
          </Table>
        </Card.Body>
      </Card>
    </div>
  );
};

export default Vehicles;
