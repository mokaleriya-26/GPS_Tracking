import os
import smtplib
from email.mime.text import MIMEText
import requests
from django.utils import timezone
from .models import NotificationLog

class NotificationService:
    @staticmethod
    def send_email(alert, alert_types):
        fleet_email = os.getenv('FLEET_EMAIL')
        manager_email = os.getenv('MANAGER_EMAIL')
        if not fleet_email or not manager_email:
            print("Skipping email: FLEET_EMAIL or MANAGER_EMAIL not configured")
            return False, "FLEET_EMAIL or MANAGER_EMAIL not configured"

        subject = f"New Fleet Alert - {', '.join(alert_types)} - {alert.vehicle.number}"
        body = f"""A new fleet alert has been detected.

Alert Type: {', '.join(alert_types)}
Vehicle ID: {alert.vehicle.vehicle_id}
Vehicle Number: {alert.vehicle.number}
Driver: {alert.driver.name if alert.driver else 'Unknown'}
Location: {alert.location}
Speed: {alert.speed} km/h
Event Time: {alert.timestamp}
"""
        try:
            msg = MIMEText(body)
            msg['Subject'] = subject
            msg['From'] = os.getenv('EMAIL_USER', 'no-reply@trackfleet.com')
            msg['To'] = f"{fleet_email}, {manager_email}"

            with smtplib.SMTP(os.getenv('EMAIL_HOST', 'smtp.gmail.com'), int(os.getenv('EMAIL_PORT', 587))) as server:
                server.starttls()
                server.login(os.getenv('EMAIL_USER'), os.getenv('EMAIL_PASSWORD'))
                server.send_message(msg)
            print(f"Email sent for {alert.vehicle.number}")
            return True, None
        except Exception as e:
            error_msg = f"Failed to send email: {e}"
            print(error_msg)
            return False, error_msg

    @staticmethod
    def send_sms(alert, alert_types):
        auth_key = os.getenv('MSG91_AUTH_KEY')
        template_id = os.getenv('MSG91_SMS_TEMPLATE_ID')
        fleet_phone = os.getenv('FLEET_PHONE')
        manager_phone = os.getenv('MANAGER_PHONE')

        if not auth_key or not template_id:
            print("Skipping SMS: MSG91 configuration missing")
            return False, "MSG91 configuration missing"

        mobiles = [phone for phone in [fleet_phone, manager_phone] if phone]
        if not mobiles:
            print("Skipping SMS: No phone numbers configured")
            return False, "No phone numbers configured"

        try:
            payload = {
                "template_id": template_id,
                "short_url": "0",
                "recipients": [{"mobiles": m, "vehicle": alert.vehicle.number, "alert_type": ", ".join(alert_types), "time": str(alert.timestamp), "driver": alert.driver.name if alert.driver else 'Unknown', "location": alert.location, "speed": str(alert.speed)} for m in mobiles]
            }
            response = requests.post(
                "https://control.msg91.com/api/v5/flow/",
                headers={"authkey": auth_key, "Content-Type": "application/json"},
                json=payload
            )
            
            # Mask phone numbers for logging
            masked_recipients = [f"{m[:2]}******{m[-2:]}" if len(m) >= 6 else "***" for m in mobiles]
            
            if response.status_code == 200:
                print(f"MSG91 SMS request Alert ID: {alert.id} Recipient: {', '.join(masked_recipients)} Template ID: {template_id} HTTP Status: 200 MSG91 Response: {response.text}")
                return True, None
            else:
                error_msg = f"MSG91 error {response.status_code} - {response.text}"
                if response.status_code == 418:
                    error_msg = f"MSG91 error 418 - IP not whitelisted"
                print(f"MSG91 SMS request Alert ID: {alert.id} Recipient: {', '.join(masked_recipients)} Template ID: {template_id} HTTP Status: {response.status_code} MSG91 Response: {response.text}")
                return False, error_msg
        except Exception as e:
            error_msg = f"Failed to send SMS: {e}"
            print(error_msg)
            return False, error_msg

    @staticmethod
    def send_whatsapp(alert, alert_types):
        return False, "WhatsApp disabled"

    @classmethod
    def process_alert(cls, alert, alert_types, silence_notifications=False):
        notification_log, created = NotificationLog.objects.get_or_create(alert=alert)
        
        if silence_notifications:
            notification_log.email_sent = True
            notification_log.sms_sent = True
            notification_log.whatsapp_sent = True
            notification_log.email_sent_at = timezone.now()
            notification_log.sms_sent_at = timezone.now()
            notification_log.whatsapp_sent_at = timezone.now()
            notification_log.save()
            return

        if not notification_log.email_sent:
            success, err = cls.send_email(alert, alert_types)
            if success:
                notification_log.email_sent = True
                notification_log.email_sent_at = timezone.now()
                notification_log.email_error = None
            else:
                notification_log.email_error = err
            notification_log.save()

        if not notification_log.sms_sent:
            success, err = cls.send_sms(alert, alert_types)
            if success:
                notification_log.sms_sent = True
                notification_log.sms_sent_at = timezone.now()
                notification_log.sms_error = None
            else:
                notification_log.sms_error = err
            notification_log.save()
        
        if not notification_log.whatsapp_sent:
            success, err = cls.send_whatsapp(alert, alert_types)
            if success:
                notification_log.whatsapp_sent = True
                notification_log.whatsapp_sent_at = timezone.now()
                notification_log.whatsapp_error = None
            else:
                notification_log.whatsapp_error = err
            notification_log.save()
