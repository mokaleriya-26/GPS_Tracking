import os
import sys
import re
import smtplib
from email.mime.text import MIMEText
import requests
from django.utils import timezone
from .models import NotificationLog

if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

def format_indian_phone(phone_raw):
    """
    Format a phone number for MSG91 Indian SMS delivery (e.g. 919876543210).
    """
    if not phone_raw:
        return None
    clean = re.sub(r'[\s\-\(\)\+\.]', '', str(phone_raw).strip())
    if not clean or not clean.isdigit():
        return None
    # 10-digit Indian number: prepend 91
    if len(clean) == 10:
        return f"91{clean}"
    # 11-digit starting with 0: replace 0 with 91
    if len(clean) == 11 and clean.startswith('0'):
        return f"91{clean[1:]}"
    # 12-digit already starting with 91: keep as is
    if len(clean) == 12 and clean.startswith('91'):
        return clean
    # Other international numbers: return cleaned digits
    return clean

def mask_phone(phone_raw):
    """Safely mask phone number for logs."""
    if not phone_raw:
        return "N/A"
    p = str(phone_raw).strip()
    if len(p) >= 8:
        return f"{p[:4]}****{p[-2:]}"
    return "****"

def mask_email(email_raw):
    """Safely mask email for logs."""
    if not email_raw or '@' not in email_raw:
        return "N/A"
    parts = email_raw.split('@')
    name = parts[0]
    domain = parts[1]
    masked_name = f"{name[0]}***{name[-1]}" if len(name) > 2 else "***"
    return f"{masked_name}@{domain}"


class NotificationService:

    @staticmethod
    def generate_email_content(alert, alert_types):
        driver_name = alert.driver.name if alert.driver else 'Unknown'
        driver_id = alert.driver.driver_id if alert.driver else 'N/A'
        driver_contact = alert.driver.contact if alert.driver else 'N/A'
        driver_email = alert.driver.email if alert.driver and alert.driver.email else 'N/A'

        event_time_str = alert.timestamp.strftime('%Y-%m-%d %H:%M:%S') if alert.timestamp else 'N/A'
        trip_start_str = alert.trip_start_time.strftime('%Y-%m-%d %H:%M:%S') if alert.trip_start_time else 'N/A'
        trip_end_str = alert.trip_end_time.strftime('%Y-%m-%d %H:%M:%S') if alert.trip_end_time else 'N/A'

        min_speed_val = f"{alert.min_speed} km/h" if alert.min_speed is not None else "N/A"
        max_speed_val = f"{alert.max_speed} km/h" if alert.max_speed is not None else f"{alert.speed} km/h"
        distance_val = f"{alert.trip_distance} km" if alert.trip_distance is not None else "N/A"
        duration_val = f"{alert.trip_duration} min" if alert.trip_duration is not None else "N/A"

        subject = f"Fleet Alert - {', '.join(alert_types)} - {alert.vehicle.number}"
        body = f"""Fleet Alert Notification

Alert Type: {', '.join(alert_types)}
Vehicle ID: {alert.vehicle.vehicle_id}
Vehicle Number: {alert.vehicle.number}

Driver:
Name: {driver_name}
Driver ID: {driver_id}
Contact: {driver_contact}
Email: {driver_email}

Location: {alert.location}
Event Time: {event_time_str}

Speed:
Minimum: {min_speed_val}
Maximum: {max_speed_val}

Trip Start: {trip_start_str}
Trip End: {trip_end_str}
Distance: {distance_val}
Duration: {duration_val}

Status: Active Alert

Please take appropriate action.
"""
        return subject, body

    @classmethod
    def send_email_notifications(cls, alert, alert_types):
        fleet_email = os.getenv('FLEET_EMAIL')
        manager_email = os.getenv('MANAGER_EMAIL')
        driver_email = alert.driver.email if alert.driver and alert.driver.email else None

        results = {
            'Fleet': {'sent': False, 'error': None, 'recipient': fleet_email},
            'Manager': {'sent': False, 'error': None, 'recipient': manager_email},
            'Driver': {'sent': False, 'error': None, 'recipient': driver_email},
        }

        subject, body = cls.generate_email_content(alert, alert_types)

        smtp_user = os.getenv('EMAIL_USER')
        smtp_password = os.getenv('EMAIL_PASSWORD')
        smtp_host = os.getenv('EMAIL_HOST', 'smtp.gmail.com')
        smtp_port = int(os.getenv('EMAIL_PORT', 587))
        from_email = os.getenv('EMAIL_FROM') or smtp_user or 'no-reply@trackfleet.com'

        if not smtp_user or not smtp_password:
            err_msg = "SMTP credentials not configured (EMAIL_USER or EMAIL_PASSWORD missing)"
            for role in results:
                results[role]['error'] = err_msg
                print(f"Email → {role}: FAILED | {err_msg}")
            return results

        # Attempt to open SMTP connection and send individually
        try:
            with smtplib.SMTP(smtp_host, smtp_port, timeout=15) as server:
                server.starttls()
                server.login(smtp_user, smtp_password)

                for role, email_addr in [('Fleet', fleet_email), ('Manager', manager_email), ('Driver', driver_email)]:
                    if not email_addr or not email_addr.strip():
                        err = f"{role} email address not configured"
                        results[role]['sent'] = False
                        results[role]['error'] = err
                        print(f"Email → {role}: FAILED | {err}")
                        continue

                    try:
                        msg = MIMEText(body)
                        msg['Subject'] = subject
                        msg['From'] = from_email
                        msg['To'] = email_addr.strip()

                        server.send_message(msg)
                        results[role]['sent'] = True
                        results[role]['error'] = None
                        print(f"Email → {role}: SUCCESS | Sent to {mask_email(email_addr)}")
                    except Exception as e:
                        err = f"Failed to send to {role}: {e}"
                        results[role]['sent'] = False
                        results[role]['error'] = str(e)
                        print(f"Email → {role}: FAILED | {e}")
        except Exception as conn_err:
            err_msg = f"SMTP Connection/Authentication failed: {conn_err}"
            for role in results:
                results[role]['sent'] = False
                results[role]['error'] = err_msg
                print(f"Email → {role}: FAILED | {conn_err}")

        return results

    @classmethod
    def send_sms_notifications(cls, alert, alert_types):
        auth_key = os.getenv('MSG91_AUTH_KEY')
        template_id = os.getenv('MSG91_SMS_TEMPLATE_ID')
        sender_id = os.getenv('MSG91_SENDER_ID')
        fleet_phone = os.getenv('FLEET_PHONE')
        manager_phone = os.getenv('MANAGER_PHONE')
        driver_phone = alert.driver.contact if alert.driver and alert.driver.contact else None

        results = {
            'Fleet': {'sent': False, 'error': None, 'request_id': None, 'recipient': fleet_phone},
            'Manager': {'sent': False, 'error': None, 'request_id': None, 'recipient': manager_phone},
            'Driver': {'sent': False, 'error': None, 'request_id': None, 'recipient': driver_phone},
        }

        if not auth_key or not template_id:
            err = "MSG91 configuration missing (MSG91_AUTH_KEY or MSG91_SMS_TEMPLATE_ID not set in environment)"
            for role in results:
                results[role]['error'] = err
                print(f"SMS → {role}: FAILED | {err}")
            return results

        # Format alert timestamp and values matching MSG91 approved template
        alert_time_str = alert.timestamp.strftime('%d-%b-%Y %H:%M') if alert.timestamp else 'N/A'
        max_speed_str = f"{int(alert.max_speed or alert.speed)} km/h"
        driver_name = alert.driver.name if alert.driver else 'Unknown'

        # Send to each recipient individually to capture exact MSG91 request ID and response
        recipients_list = [
            ('Fleet', fleet_phone),
            ('Manager', manager_phone),
            ('Driver', driver_phone)
        ]

        for role, raw_phone in recipients_list:
            formatted_mobile = format_indian_phone(raw_phone)
            if not formatted_mobile:
                err = f"{role} phone number missing or invalid format (got: '{raw_phone}')"
                results[role]['sent'] = False
                results[role]['error'] = err
                print(f"SMS → {role}: FAILED | {err}")
                continue

            payload = {
                "template_id": template_id,
                "short_url": "0",
                "recipients": [
                    {
                        "mobiles": formatted_mobile,
                        "vehicle": alert.vehicle.number,
                        "alert_type": ", ".join(alert_types),
                        "driver": driver_name,
                        "location": alert.location,
                        "time": alert_time_str,
                        "speed": max_speed_str
                    }
                ]
            }

            if sender_id:
                payload["sender"] = sender_id

            try:
                response = requests.post(
                    "https://control.msg91.com/api/v5/flow/",
                    headers={
                        "authkey": auth_key,
                        "Content-Type": "application/json"
                    },
                    json=payload,
                    timeout=15
                )

                http_code = response.status_code
                res_text = response.text.strip()

                if http_code == 418:
                    err = "MSG91 Error 418: IP not whitelisted. Backend public IP must be whitelisted in MSG91 API Security."
                    results[role]['sent'] = False
                    results[role]['error'] = err
                    print(f"SMS → {role}: FAILED | MSG91 Error 418: IP not whitelisted")
                elif http_code == 203:
                    err = f"MSG91 Error 203: Sender ID/DLT issue ({res_text})"
                    results[role]['sent'] = False
                    results[role]['error'] = err
                    print(f"SMS → {role}: FAILED | MSG91 Error 203: Sender ID/DLT issue")
                elif http_code == 211:
                    err = f"MSG91 Error 211: DLT Template ID missing ({res_text})"
                    results[role]['sent'] = False
                    results[role]['error'] = err
                    print(f"SMS → {role}: FAILED | MSG91 Error 211: DLT Template ID missing")
                elif http_code == 400:
                    err = f"MSG91 Error 400: Invalid/missing template ({res_text})"
                    results[role]['sent'] = False
                    results[role]['error'] = err
                    print(f"SMS → {role}: FAILED | MSG91 Error 400: Invalid/missing template")
                elif http_code == 200:
                    try:
                        res_json = response.json()
                    except Exception:
                        res_json = {}

                    res_type = str(res_json.get('type', '')).lower()
                    if res_type == 'success' or (res_type != 'error' and ('message' in res_json or 'request_id' in res_json)):
                        req_id = res_json.get('request_id') or res_json.get('message')
                        results[role]['sent'] = True
                        results[role]['request_id'] = str(req_id)
                        results[role]['error'] = None
                        print(f"SMS → {role}: SUCCESS | MSG91 Request ID: {req_id}")
                    else:
                        err_msg = res_json.get('message') or res_text
                        err = f"MSG91 rejected: {err_msg}"
                        results[role]['sent'] = False
                        results[role]['error'] = err
                        print(f"SMS → {role}: FAILED | MSG91 Error: {err_msg}")
                else:
                    err = f"MSG91 Error {http_code}: {res_text}"
                    results[role]['sent'] = False
                    results[role]['error'] = err
                    print(f"SMS → {role}: FAILED | {err}")

            except Exception as e:
                err = f"SMS submission error: {e}"
                results[role]['sent'] = False
                results[role]['error'] = err
                print(f"SMS → {role}: FAILED | {err}")

        return results

    @classmethod
    def process_alert(cls, alert, alert_types, silence_notifications=False):
        notification_log, _ = NotificationLog.objects.get_or_create(alert=alert)

        if silence_notifications:
            notification_log.is_silenced = True
            notification_log.email_sent = False
            notification_log.sms_sent = False
            notification_log.fleet_email_sent = False
            notification_log.manager_email_sent = False
            notification_log.driver_email_sent = False
            notification_log.fleet_sms_sent = False
            notification_log.manager_sms_sent = False
            notification_log.driver_sms_sent = False
            notification_log.save()
            return notification_log

        # If already completely sent or silenced, skip to prevent any duplicate notifications
        if notification_log.is_silenced:
            return notification_log

        # Process Email Notifications if not already sent
        if not notification_log.email_sent:
            email_res = cls.send_email_notifications(alert, alert_types)
            notification_log.fleet_email_sent = email_res['Fleet']['sent']
            notification_log.fleet_email_error = email_res['Fleet']['error']
            notification_log.manager_email_sent = email_res['Manager']['sent']
            notification_log.manager_email_error = email_res['Manager']['error']
            notification_log.driver_email_sent = email_res['Driver']['sent']
            notification_log.driver_email_error = email_res['Driver']['error']

            notification_log.email_sent = (
                notification_log.fleet_email_sent and
                notification_log.manager_email_sent and
                notification_log.driver_email_sent
            )
            if notification_log.email_sent:
                notification_log.email_sent_at = timezone.now()
                notification_log.email_error = None
            else:
                email_errs = [f"{k}: {v['error']}" for k, v in email_res.items() if v['error']]
                notification_log.email_error = "; ".join(email_errs) if email_errs else None

        # Process SMS Notifications if not already sent
        if not notification_log.sms_sent:
            sms_res = cls.send_sms_notifications(alert, alert_types)
            notification_log.fleet_sms_sent = sms_res['Fleet']['sent']
            notification_log.fleet_sms_error = sms_res['Fleet']['error']
            notification_log.fleet_sms_request_id = sms_res['Fleet']['request_id']

            notification_log.manager_sms_sent = sms_res['Manager']['sent']
            notification_log.manager_sms_error = sms_res['Manager']['error']
            notification_log.manager_sms_request_id = sms_res['Manager']['request_id']

            notification_log.driver_sms_sent = sms_res['Driver']['sent']
            notification_log.driver_sms_error = sms_res['Driver']['error']
            notification_log.driver_sms_request_id = sms_res['Driver']['request_id']

            notification_log.sms_sent = (
                notification_log.fleet_sms_sent and
                notification_log.manager_sms_sent and
                notification_log.driver_sms_sent
            )
            if notification_log.sms_sent:
                notification_log.sms_sent_at = timezone.now()
                notification_log.sms_error = None
            else:
                sms_errs = [f"{k}: {v['error']}" for k, v in sms_res.items() if v['error']]
                notification_log.sms_error = "; ".join(sms_errs) if sms_errs else None

        notification_log.save()
        return notification_log
