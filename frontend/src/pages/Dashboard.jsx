import React from 'react';
import { Card, Row, Col, Badge, Table, Button } from 'react-bootstrap';
import { Link } from 'react-router-dom';

const Dashboard = ({ vehicles = [], alerts = [] }) => {
  // Vehicles are unique in DRF now (since it's a model), so we just use the length
  const activeVehicles = vehicles.filter(v => v.ignition).length;
  
  const criticalAlerts = alerts.filter(a => a.alert_type?.includes('Overspeed') || a.alert_type?.includes('Disconnect')).length;

  return (
    <>
      <div className="mb-4">
        <h1 className="h3 mb-2 text-dark fw-bold">Fleet Overview</h1>
        <p className="text-muted">Monitor your vehicles, trips and alerts in real time.</p>
      </div>

      <Row className="g-4 mb-4">
        <Col md={6} lg={3}>
          <Card className="shadow-sm border-0 h-100">
            <Card.Body className="d-flex flex-column justify-content-between">
              <div>
                <h6 className="text-muted text-uppercase mb-2" style={{ fontSize: '12px' }}>Vehicles Tracking</h6>
                <h3 className="display-6 fw-bold text-dark">{activeVehicles}</h3>
              </div>
              <div className="mt-3">
                <Badge bg="success" className="me-1">Live</Badge>
                <small className="text-muted">Currently active</small>
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col md={6} lg={3}>
          <Card className="shadow-sm border-0 h-100">
            <Card.Body className="d-flex flex-column justify-content-between">
              <div>
                <h6 className="text-muted text-uppercase mb-2" style={{ fontSize: '12px' }}>Total Vehicles</h6>
                <h3 className="display-6 fw-bold text-dark">{vehicles.length}</h3>
              </div>
              <div className="mt-3">
                <small className="text-muted">Registered vehicles</small>
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col md={6} lg={3}>
          <Card className="shadow-sm border-0 h-100">
            <Card.Body className="d-flex flex-column justify-content-between">
              <div>
                <h6 className="text-muted text-uppercase mb-2" style={{ fontSize: '12px' }}>Active Alerts</h6>
                <h3 className="display-6 fw-bold text-dark">{alerts.length}</h3>
              </div>
              <div className="mt-3">
                <Badge bg="warning" text="dark" className="me-1">Warning</Badge>
                <small className="text-muted">Requires attention</small>
              </div>
            </Card.Body>
          </Card>
        </Col>

        <Col md={6} lg={3}>
          <Card className="shadow-sm border-0 h-100">
            <Card.Body className="d-flex flex-column justify-content-between">
              <div>
                <h6 className="text-muted text-uppercase mb-2" style={{ fontSize: '12px' }}>Critical Alerts</h6>
                <h3 className="display-6 fw-bold text-dark">{criticalAlerts}</h3>
              </div>
              <div className="mt-3">
                <Badge bg="danger" className="me-1">Critical</Badge>
                <small className="text-muted">High priority</small>
              </div>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      <Row className="g-4">
        <Col lg={8}>
          <Card className="shadow-sm border-0 mb-4 h-100">
            <Card.Header className="bg-white border-bottom py-3 d-flex justify-content-between align-items-center">
              <h5 className="mb-0 fw-bold">Vehicle Overview</h5>
              <Button as={Link} to="/vehicles" variant="outline-primary" size="sm">View All</Button>
            </Card.Header>
            <Card.Body className="p-0 overflow-auto">
              <Table hover responsive className="mb-0 align-middle">
                <thead className="bg-light text-muted" style={{ fontSize: '14px' }}>
                  <tr>
                    <th className="border-0 px-4 py-3">Vehicle Number</th>
                    <th className="border-0 py-3">Driver</th>
                    <th className="border-0 py-3">Status</th>
                    <th className="border-0 py-3">Speed</th>
                    <th className="border-0 py-3">Location</th>
                  </tr>
                </thead>
                <tbody style={{ fontSize: '14px' }}>
                  {vehicles.slice(0, 5).map((v, idx) => (
                    <tr key={v.vehicle_id || idx}>
                      <td className="px-4 fw-semibold text-dark">{v.number}</td>
                      <td>{v.driver_name || 'Unassigned'}</td>
                      <td>
                        <Badge bg={v.ignition ? "success" : "secondary"}>
                          {v.ignition ? "Tracking" : "Offline"}
                        </Badge>
                      </td>
                      <td>{v.speed} km/h</td>
                      <td><div className="text-truncate" style={{ maxWidth: '200px' }}>{v.location || 'N/A'}</div></td>
                    </tr>
                  ))}
                  {vehicles.length === 0 && (
                    <tr>
                      <td colSpan={5} className="text-center text-muted py-4">No active vehicles found</td>
                    </tr>
                  )}
                </tbody>
              </Table>
            </Card.Body>
          </Card>
        </Col>

        <Col lg={4}>
          <Card className="shadow-sm border-0 h-100">
            <Card.Header className="bg-white border-bottom py-3">
              <h5 className="mb-0 fw-bold">Recent Alerts</h5>
            </Card.Header>
            <Card.Body className="p-3 overflow-auto" style={{ maxHeight: '400px' }}>
              {alerts.slice(0, 5).map((alert, idx) => {
                const isCritical = alert.alert_type?.includes('Overspeed') || alert.alert_type?.includes('Disconnect');
                return (
                  <div key={alert.event_id || alert.id || idx} className={`p-3 mb-3 border rounded ${isCritical ? 'border-danger bg-opacity-10 bg-danger' : 'border-warning bg-opacity-10 bg-warning'}`}>
                    <div className="d-flex justify-content-between mb-1">
                      <strong className={isCritical ? 'text-danger' : 'text-warning-emphasis'}>{alert.alert_type}</strong>
                      <small className="text-muted">{alert.timestamp ? new Date(alert.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : 'N/A'}</small>
                    </div>
                    <div className="text-muted small mb-2">{alert.driver_name || 'Unknown'} • {alert.vehicle_number || 'Unknown'}</div>
                    <Badge bg={isCritical ? 'danger' : 'warning'} text={isCritical ? 'white' : 'dark'}>{isCritical ? 'Critical' : 'Warning'}</Badge>
                  </div>
                )
              })}
              {alerts.length === 0 && <p className="text-muted text-center my-4">No recent alerts</p>}
            </Card.Body>
            <Card.Footer className="bg-light border-top p-0 text-center">
              <Button as={Link} to="/alerts" variant="link" className="w-100 text-decoration-none py-3">View All Notifications</Button>
            </Card.Footer>
          </Card>
        </Col>
      </Row>
    </>
  );
};

export default Dashboard;
