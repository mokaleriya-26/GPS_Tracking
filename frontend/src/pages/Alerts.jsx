import React from 'react';
import { Card, Badge, Row, Col } from 'react-bootstrap';
import { Mail, Smartphone, MessageSquare, AlertTriangle, AlertCircle, Info } from 'lucide-react';

const renderRecipientBadge = (label, isSent, error, isSilenced, requestId) => {
  if (isSilenced) {
    return (
      <span 
        key={label}
        className="badge bg-light text-muted border px-2 py-1" 
        title="Historical record (Notifications silenced)"
      >
        — {label}
      </span>
    );
  }
  if (isSent) {
    return (
      <span 
        key={label}
        className="badge bg-success bg-opacity-10 text-success border border-success px-2 py-1" 
        title={requestId ? `Sent successfully (MSG91 Request ID: ${requestId})` : 'Sent successfully'}
      >
        ✓ {label}
      </span>
    );
  }
  if (error) {
    return (
      <span 
        key={label}
        className="badge bg-danger bg-opacity-10 text-danger border border-danger px-2 py-1" 
        title={`Failed: ${error}`}
      >
        ✗ {label}
      </span>
    );
  }
  return (
    <span 
      key={label}
      className="badge bg-light text-secondary border px-2 py-1" 
      title="Pending evaluation"
    >
      ○ {label}
    </span>
  );
};

const Alerts = ({ alerts = [] }) => {
  return (
    <div>
      <div className="mb-4">
        <h1 className="h3 mb-2 text-dark fw-bold">Fleet Notifications</h1>
        <p className="text-muted">Review all recent alerts and multi-recipient notification delivery status.</p>
      </div>

      <Card className="shadow-sm border-0 h-100">
        <Card.Header className="bg-white border-bottom py-3 d-flex justify-content-between align-items-center">
          <h5 className="mb-0 fw-bold">All Alerts ({alerts.length})</h5>
          <span className="small text-muted">Recipients: Fleet, Manager, Driver</span>
        </Card.Header>
        <Card.Body className="p-4">
          {alerts.map((alert, idx) => {
            const isCritical = alert.alert_type?.includes('Overspeed') || alert.alert_type?.includes('Disconnect');
            const notif = alert.notification || {};
            const isSilenced = notif.is_silenced;

            return (
              <div 
                key={alert.event_id || alert.id || idx} 
                className={`p-4 mb-3 border rounded-3 ${isCritical ? 'border-danger bg-opacity-10 bg-danger' : 'border-warning bg-opacity-10 bg-warning'}`}
              >
                <Row>
                  <Col md={1} className="d-flex align-items-center justify-content-center mb-3 mb-md-0">
                    <div className={`p-3 rounded-circle ${isCritical ? 'bg-danger text-white' : 'bg-warning text-dark'}`}>
                      {isCritical ? <AlertCircle size={24} /> : <AlertTriangle size={24} />}
                    </div>
                  </Col>
                  
                  <Col md={6}>
                    <div className="d-flex justify-content-between align-items-start mb-2">
                      <h5 className="fw-bold mb-0 text-dark">{alert.alert_type}</h5>
                      <small className="text-muted fw-semibold">
                        {alert.timestamp ? (
                          `${new Date(alert.timestamp).toLocaleDateString([], {day: 'numeric', month: 'short', year: 'numeric'})} - ${new Date(alert.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}`
                        ) : 'N/A'}
                      </small>
                    </div>

                    <Row className="mt-3">
                      <Col sm={6}>
                        <p className="mb-1 text-muted small">
                          Vehicle: <strong className="text-dark">{alert.vehicle_number}</strong>
                          {alert.vehicle && alert.vehicle !== alert.vehicle_number && (
                            <span className="text-muted ms-1">({alert.vehicle})</span>
                          )}
                        </p>
                        <p className="mb-1 text-muted small">
                          Driver: <strong className="text-dark">{alert.driver_name || 'N/A'}</strong>
                        </p>
                        {alert.driver_contact && (
                          <p className="mb-1 text-muted small" style={{fontSize: '0.78rem'}}>
                            Contact: <span className="text-dark">{alert.driver_contact}</span>
                          </p>
                        )}
                        {alert.driver_email && (
                          <p className="mb-1 text-muted small" style={{fontSize: '0.78rem'}}>
                            Email: <span className="text-dark">{alert.driver_email}</span>
                          </p>
                        )}
                      </Col>

                      <Col sm={6}>
                        <p className="mb-1 text-muted small">
                          Speed: <strong className="text-dark">{alert.speed} km/h</strong>
                        </p>
                        <p className="mb-1 text-muted small">
                          Location: <strong className="text-dark">{alert.location}</strong>
                        </p>
                        {alert.trip_distance != null && (
                          <p className="mb-1 text-muted small" style={{fontSize: '0.78rem'}}>
                            Distance: <span className="text-dark">{alert.trip_distance} km</span>
                            {alert.trip_duration != null && ` | ${alert.trip_duration} min`}
                          </p>
                        )}
                      </Col>
                    </Row>
                  </Col>

                  <Col md={5} className="border-start ps-4 d-flex flex-column justify-content-center">
                    <h6 className="text-muted small text-uppercase mb-3 fw-bold d-flex justify-content-between align-items-center">
                      <span>Delivery Status</span>
                      {isSilenced && (
                        <span className="badge bg-secondary" style={{fontSize: '0.65rem'}}>Historical (Silenced)</span>
                      )}
                    </h6>
                    
                    {/* EMAIL RECIPIENTS */}
                    <div className="mb-3 p-2 rounded bg-white bg-opacity-75 border">
                      <div className="d-flex align-items-center justify-content-between mb-1">
                        <div className="d-flex align-items-center">
                          <Mail size={15} className="me-2 text-primary" />
                          <span className="small fw-bold text-dark">Email</span>
                        </div>
                        {isSilenced ? (
                          <span className="badge bg-light text-muted border" style={{fontSize: '0.68rem'}}>Silenced</span>
                        ) : notif.email_sent ? (
                          <Badge bg="success" style={{fontSize: '0.68rem'}}>✓ All Sent</Badge>
                        ) : notif.email_error ? (
                          <Badge bg="danger" style={{fontSize: '0.68rem'}} title={notif.email_error}>⚠ Error</Badge>
                        ) : (
                          <Badge bg="secondary" style={{fontSize: '0.68rem'}}>Pending</Badge>
                        )}
                      </div>
                      <div className="d-flex flex-wrap gap-2 mt-1">
                        {renderRecipientBadge('Fleet', notif.fleet_email_sent, notif.fleet_email_error, isSilenced)}
                        {renderRecipientBadge('Manager', notif.manager_email_sent, notif.manager_email_error, isSilenced)}
                        {renderRecipientBadge('Driver', notif.driver_email_sent, notif.driver_email_error, isSilenced)}
                      </div>
                    </div>

                    {/* SMS RECIPIENTS (MSG91) */}
                    <div className="mb-2 p-2 rounded bg-white bg-opacity-75 border">
                      <div className="d-flex align-items-center justify-content-between mb-1">
                        <div className="d-flex align-items-center">
                          <Smartphone size={15} className="me-2 text-success" />
                          <span className="small fw-bold text-dark">SMS (MSG91)</span>
                        </div>
                        {isSilenced ? (
                          <span className="badge bg-light text-muted border" style={{fontSize: '0.68rem'}}>Silenced</span>
                        ) : notif.sms_sent ? (
                          <Badge bg="success" style={{fontSize: '0.68rem'}}>✓ All Sent</Badge>
                        ) : notif.sms_error ? (
                          <Badge bg="danger" style={{fontSize: '0.68rem'}} title={notif.sms_error}>
                            ⚠ {notif.sms_error.includes('418') ? '418 Whitelist' : 'Failed'}
                          </Badge>
                        ) : (
                          <Badge bg="secondary" style={{fontSize: '0.68rem'}}>Pending</Badge>
                        )}
                      </div>
                      <div className="d-flex flex-wrap gap-2 mt-1">
                        {renderRecipientBadge('Fleet', notif.fleet_sms_sent, notif.fleet_sms_error, isSilenced, notif.fleet_sms_request_id)}
                        {renderRecipientBadge('Manager', notif.manager_sms_sent, notif.manager_sms_error, isSilenced, notif.manager_sms_request_id)}
                        {renderRecipientBadge('Driver', notif.driver_sms_sent, notif.driver_sms_error, isSilenced, notif.driver_sms_request_id)}
                      </div>
                      
                      {/* Detailed error callout if MSG91 rejected */}
                      {notif.sms_error && !isSilenced && (
                        <div className="mt-2 p-2 rounded bg-danger bg-opacity-10 text-danger border border-danger small" style={{fontSize: '0.72rem'}}>
                          {notif.sms_error.includes('418') ? (
                            <>
                              <strong>MSG91 Error 418:</strong> IP not whitelisted. Backend public IP must be whitelisted in MSG91 API Security.
                            </>
                          ) : (
                            <>
                              <strong>SMS Error:</strong> {notif.sms_error}
                            </>
                          )}
                        </div>
                      )}
                    </div>

                    {/* WhatsApp */}
                    <div className="d-flex align-items-center justify-content-between mt-1 px-1">
                      <div className="d-flex align-items-center">
                        <MessageSquare size={13} className="me-2 text-secondary" />
                        <span className="small text-muted" style={{fontSize: '0.75rem'}}>WhatsApp:</span>
                      </div>
                      <span className="badge bg-light text-muted border" style={{fontSize: '0.65rem'}}>Disabled</span>
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
