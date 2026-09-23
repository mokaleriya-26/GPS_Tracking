import React from 'react';
import { Card, Badge, Row, Col } from 'react-bootstrap';
import { Mail, Smartphone, MessageSquare, AlertTriangle, AlertCircle } from 'lucide-react';

const Alerts = ({ alerts }) => {
  return (
    <div>
      <div className="mb-4">
        <h1 className="h3 mb-2 text-dark fw-bold">Fleet Notifications</h1>
        <p className="text-muted">Review all recent alerts across your fleet.</p>
      </div>

      <Card className="shadow-sm border-0 h-100">
        <Card.Header className="bg-white border-bottom py-3">
          <h5 className="mb-0 fw-bold">All Alerts</h5>
        </Card.Header>
        <Card.Body className="p-4">
          {alerts.map((alert, idx) => {
            const isCritical = alert.alert_type.includes('Overspeed') || alert.alert_type.includes('Disconnect');
            
            return (
              <div key={idx} className={`p-4 mb-3 border rounded-3 ${isCritical ? 'border-danger bg-opacity-10 bg-danger' : 'border-warning bg-opacity-10 bg-warning'}`}>
                <Row>
                  <Col md={1} className="d-flex align-items-center justify-content-center mb-3 mb-md-0">
                    <div className={`p-3 rounded-circle ${isCritical ? 'bg-danger text-white' : 'bg-warning text-dark'}`}>
                      {isCritical ? <AlertCircle size={24} /> : <AlertTriangle size={24} />}
                    </div>
                  </Col>
                  <Col md={7}>
                    <div className="d-flex justify-content-between align-items-start mb-2">
                      <h5 className="fw-bold mb-0 text-dark">{alert.alert_type}</h5>
                      <small className="text-muted fw-semibold">
                        {new Date(alert.timestamp).toLocaleDateString([], {day: 'numeric', month: 'short', year: 'numeric'})} - {new Date(alert.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
                      </small>
                    </div>
                    <Row className="mt-3">
                      <Col sm={6}>
                        <p className="mb-1 text-muted small">Driver: <strong className="text-dark">{alert.driver_name}</strong></p>
                        <p className="mb-1 text-muted small">Vehicle: <strong className="text-dark">{alert.vehicle_number}</strong></p>
                      </Col>
                      <Col sm={6}>
                        <p className="mb-1 text-muted small">Speed: <strong className="text-dark">{alert.speed} km/h</strong></p>
                        <p className="mb-1 text-muted small">Location: <strong className="text-dark">{alert.location}</strong></p>
                      </Col>
                    </Row>
                  </Col>
                  <Col md={4} className="border-start ps-4 d-flex flex-column justify-content-center">
                    <h6 className="text-muted small text-uppercase mb-3 fw-bold">Notification Status</h6>
                    
                    <div className="d-flex align-items-center mb-2">
                      <Mail size={16} className="me-2 text-secondary" />
                      <span className="small text-muted me-2">Email:</span>
                      {alert.notification?.email_error ? (
                        <Badge bg="danger" className="ms-auto text-truncate" style={{maxWidth: '150px'}} title={alert.notification.email_error}>⚠ Failed</Badge>
                      ) : alert.notification?.email_sent ? (
                        <Badge bg="success" className="ms-auto">✓ Sent</Badge>
                      ) : (
                        <Badge bg="secondary" className="ms-auto">Pending</Badge>
                      )}
                    </div>

                    <div className="d-flex align-items-center mb-2">
                      <Smartphone size={16} className="me-2 text-secondary" />
                      <span className="small text-muted me-2">SMS:</span>
                      {alert.notification?.sms_error ? (
                        <Badge bg="danger" className="ms-auto text-truncate" style={{maxWidth: '150px'}} title={alert.notification.sms_error}>
                          ⚠ Failed {alert.notification.sms_error.includes('418') ? '- 418' : ''}
                        </Badge>
                      ) : alert.notification?.sms_sent ? (
                        <Badge bg="success" className="ms-auto">✓ Sent</Badge>
                      ) : (
                        <Badge bg="secondary" className="ms-auto">Pending</Badge>
                      )}
                    </div>

                    <div className="d-flex align-items-center">
                      <MessageSquare size={16} className="me-2 text-secondary" />
                      <span className="small text-muted me-2">WhatsApp:</span>
                      <Badge bg="light" text="muted" className="ms-auto border">— Disabled</Badge>
                    </div>
                  </Col>
                </Row>
              </div>
            );
          })}
          {alerts.length === 0 && (
             <p className="text-center text-muted fs-5 py-5">No alerts found</p>
          )}
        </Card.Body>
      </Card>
    </div>
  );
};

export default Alerts;
