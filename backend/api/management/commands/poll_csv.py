import os
import csv
import time
from datetime import datetime
from django.core.management.base import BaseCommand
from django.utils.dateparse import parse_datetime
from django.utils.timezone import make_aware, is_naive
from api.models import Driver, Vehicle, Trip, Alert, NotificationLog
from api.notifications import NotificationService

class Command(BaseCommand):
    help = 'Polls the CSV dataset and updates the database, triggering notifications for new alerts.'

    def handle(self, *args, **options):
        # Go up from backend/api/management/commands/poll_csv.py to root/dataset/
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__)))))
        csv_file_path = os.path.join(base_dir, 'dataset', 'vehicle_fleet_alerts_fake_dataset.csv')
        
        self.stdout.write(self.style.SUCCESS(f"Starting CSV polling from {csv_file_path}..."))

        while True:
            try:
                self.process_csv(csv_file_path)
                self.stdout.write(self.style.SUCCESS(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Processed CSV successfully. Waiting 30s..."))
            except Exception as e:
                self.stdout.write(self.style.ERROR(f"Error processing CSV: {e}"))
            
            time.sleep(30) # Poll every 30 seconds

    def process_csv(self, file_path):
        if not os.path.exists(file_path):
            self.stdout.write(self.style.WARNING(f"CSV file not found at {file_path}"))
            return

        is_initial_load = not Alert.objects.exists()
        
        if is_initial_load:
            self.stdout.write(self.style.WARNING("First run detected. Importing historical data without sending notifications..."))

        with open(file_path, mode='r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                # Update Driver
                driver, _ = Driver.objects.update_or_create(
                    driver_id=row.get('Driver ID', ''),
                    defaults={
                        'name': row.get('Driver Name', ''),
                        'contact': row.get('Driver Contact', '')
                    }
                )

                # Update Vehicle
                vehicle, _ = Vehicle.objects.update_or_create(
                    vehicle_id=row.get('Vehicle ID', ''),
                    defaults={
                        'number': row.get('Vehicle Number', ''),
                        'driver': driver,
                        'location': row.get('Location', ''),
                        'speed': float(row.get('Speed Max (km/h)', 0) or 0),
                        'ignition': row.get('Ignition On/Off Alert', 'Off') == 'On'
                    }
                )

                # Handle Trip
                try:
                    start_time = self.parse_time(row.get('Trip Start Time'))
                    end_time = self.parse_time(row.get('Trip End Time'))
                    
                    if start_time and end_time:
                        Trip.objects.update_or_create(
                            vehicle=vehicle,
                            start_time=start_time,
                            defaults={
                                'driver': driver,
                                'end_time': end_time,
                                'distance_km': float(row.get('Trip Distance (km)', 0) or 0),
                                'duration_min': int(row.get('Trip Duration (min)', 0) or 0),
                                'min_speed': float(row.get('Speed Min (km/h)', 0) or 0),
                                'max_speed': float(row.get('Speed Max (km/h)', 0) or 0)
                            }
                        )
                except Exception as e:
                    self.stdout.write(self.style.ERROR(f"Error saving trip: {e}"))

                # Handle Alerts
                alert_types = []
                if row.get('Overspeed Alert') == 'Yes': alert_types.append('Overspeed Alert')
                if row.get('Harsh Braking Alert') == 'Yes': alert_types.append('Harsh Braking Alert')
                if row.get('GPS Disconnect Alert') == 'Yes': alert_types.append('GPS Disconnect Alert')
                if row.get('Night Driving Alert') == 'Yes': alert_types.append('Night Driving Alert')

                if alert_types:
                    timestamp = self.parse_time(row.get('Time'))
                    if timestamp:
                        combined_type = ", ".join(alert_types)
                        timestamp_str = timestamp.strftime('%Y%m%d%H%M%S')
                        
                        # Generate deterministic ID
                        event_id = f"{vehicle.vehicle_id}_{vehicle.number}_{timestamp_str}_{combined_type.replace(' ', '').replace(',', '_')}"
                        
                        alert, created = Alert.objects.get_or_create(
                            event_id=event_id,
                            defaults={
                                'vehicle': vehicle,
                                'timestamp': timestamp,
                                'driver': driver,
                                'alert_type': combined_type,
                                'location': row.get('Location', ''),
                                'speed': float(row.get('Speed Max (km/h)', 0) or 0)
                            }
                        )

                        if created:
                            NotificationService.process_alert(alert, alert_types, silence_notifications=is_initial_load)
                            if not is_initial_load:
                                self.stdout.write(self.style.SUCCESS(f"New alert detected and processed: {event_id}"))

    def parse_time(self, time_str):
        if not time_str: return None
        dt = parse_datetime(time_str.replace(' ', 'T'))
        if dt and is_naive(dt):
            dt = make_aware(dt)
        return dt
