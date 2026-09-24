import os
import sys
import csv
import time
from datetime import datetime
from django.core.management.base import BaseCommand
from django.db import models
from django.utils.dateparse import parse_datetime
from django.utils.timezone import make_aware, is_naive
from api.models import Driver, Vehicle, Trip, Alert, NotificationLog, SystemConfig
from api.notifications import NotificationService

if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

class Command(BaseCommand):
    help = 'Polls the CSV dataset, updates persistent DB state, and triggers notifications only for genuinely NEW alerts.'

    def add_arguments(self, parser):
        parser.add_argument('--once', action='store_true', help='Process the CSV once and exit instead of looping.')
        parser.add_argument('--interval', type=int, default=30, help='Polling interval in seconds (default: 30).')
        parser.add_argument('--reset-initial', action='store_true', help='Reset the initial import status flag.')

    def handle(self, *args, **options):
        # Path to vehicle_fleet_alerts_fake_dataset.csv
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__)))))
        csv_file_path = os.path.join(base_dir, 'dataset', 'vehicle_fleet_alerts_fake_dataset.csv')

        if options.get('reset_initial'):
            SystemConfig.objects.filter(key='initial_import_completed').delete()
            self.stdout.write(self.style.WARNING("Reset initial_import_completed status in database."))

        self.stdout.write(self.style.SUCCESS(f"Starting CSV polling from {csv_file_path}..."))

        if options.get('once'):
            self.process_csv(csv_file_path)
            self.stdout.write(self.style.SUCCESS("Single pass completed."))
            return

        interval = options.get('interval', 30)
        while True:
            try:
                self.process_csv(csv_file_path)
                self.stdout.write(self.style.SUCCESS(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Polled CSV successfully. Next check in {interval}s..."))
            except Exception as e:
                self.stdout.write(self.style.ERROR(f"Error processing CSV: {e}"))

            time.sleep(interval)

    def process_csv(self, file_path):
        if not os.path.exists(file_path):
            self.stdout.write(self.style.WARNING(f"CSV file not found at {file_path}"))
            return

        # Check persistent database configuration to see if historical data was already imported
        initial_import_record = SystemConfig.objects.filter(key='initial_import_completed', value='true').first()
        is_initial_load = initial_import_record is None and not Alert.objects.exists()

        if is_initial_load:
            self.stdout.write(self.style.WARNING("=" * 60))
            self.stdout.write(self.style.WARNING("[INITIAL LOAD] Importing historical dataset without sending notifications..."))
            self.stdout.write(self.style.WARNING("=" * 60))

        with open(file_path, mode='r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                # 1. Update Driver with Name, Contact, and Email from CSV
                driver_id = row.get('Driver ID', '').strip()
                driver_name = row.get('Driver Name', '').strip()
                driver_contact = row.get('Driver Contact', '').strip()
                driver_email = row.get('Driver Email', '').strip()

                driver, _ = Driver.objects.update_or_create(
                    driver_id=driver_id,
                    defaults={
                        'name': driver_name,
                        'contact': driver_contact,
                        'email': driver_email,
                    }
                )

                # 2. Update Vehicle
                vehicle_id = row.get('Vehicle ID', '').strip()
                vehicle_number = row.get('Vehicle Number', '').strip()
                speed_max = float(row.get('Speed Max (km/h)', 0) or 0)
                speed_min = float(row.get('Speed Min (km/h)', 0) or 0)
                location = row.get('Location', '').strip()
                ignition_raw = row.get('Ignition On/Off Alert', 'Off').strip()
                ignition_state = ignition_raw in ['On', 'Yes']

                vehicle, _ = Vehicle.objects.update_or_create(
                    vehicle_id=vehicle_id,
                    defaults={
                        'number': vehicle_number,
                        'driver': driver,
                        'location': location,
                        'speed': speed_max,
                        'ignition': ignition_state
                    }
                )

                # 3. Update Trip
                start_time = self.parse_time(row.get('Trip Start Time'))
                end_time = self.parse_time(row.get('Trip End Time'))
                trip_distance = float(row.get('Trip Distance (km)', 0) or 0)
                trip_duration = int(row.get('Trip Duration (min)', 0) or 0)

                if start_time and end_time:
                    try:
                        Trip.objects.update_or_create(
                            vehicle=vehicle,
                            start_time=start_time,
                            defaults={
                                'driver': driver,
                                'end_time': end_time,
                                'distance_km': trip_distance,
                                'duration_min': trip_duration,
                                'min_speed': speed_min,
                                'max_speed': speed_max
                            }
                        )
                    except Exception as e:
                        self.stdout.write(self.style.ERROR(f"Error saving trip: {e}"))

                # 4. Check all active alert types
                alert_types = []
                if row.get('Overspeed Alert', '').strip().lower() == 'yes':
                    alert_types.append('Overspeed Alert')
                if row.get('Harsh Braking Alert', '').strip().lower() == 'yes':
                    alert_types.append('Harsh Braking Alert')
                if row.get('GPS Disconnect Alert', '').strip().lower() == 'yes':
                    alert_types.append('GPS Disconnect Alert')
                if row.get('Night Driving Alert', '').strip().lower() == 'yes':
                    alert_types.append('Night Driving Alert')
                if row.get('Ignition On/Off Alert', '').strip().lower() == 'yes':
                    alert_types.append('Ignition Alert')

                if alert_types:
                    timestamp = self.parse_time(row.get('Time'))
                    if timestamp:
                        combined_type = ", ".join(alert_types)
                        timestamp_str = timestamp.strftime('%Y%m%d%H%M%S')
                        trip_start_str = start_time.strftime('%Y%m%d%H%M%S') if start_time else '00000000000000'
                        driver_id_clean = driver.driver_id.replace(' ', '')
                        type_slug = combined_type.replace(' ', '').replace(',', '_')

                        # Deterministic unique event ID based on:
                        # Vehicle ID, Vehicle Number, Driver ID, Time, Trip Start Time, Alert Type
                        event_id = f"{vehicle.vehicle_id}_{vehicle.number}_{driver_id_clean}_{timestamp_str}_{trip_start_str}_{type_slug}"
                        # Also check legacy format for backwards compatibility with existing rows
                        legacy_event_id = f"{vehicle.vehicle_id}_{vehicle.number}_{timestamp_str}_{type_slug}"

                        existing_alert = Alert.objects.filter(
                            models.Q(event_id=event_id) | models.Q(event_id=legacy_event_id) | models.Q(vehicle=vehicle, timestamp=timestamp)
                        ).first()

                        if existing_alert:
                            # Alert already processed and recorded in database
                            alert = existing_alert
                            created = False
                            # Migrate event_id to comprehensive format and update fields if needed
                            fields_to_update = []
                            if alert.event_id != event_id:
                                alert.event_id = event_id
                                fields_to_update.append('event_id')
                            if not alert.alert_type:
                                alert.alert_type = combined_type
                                fields_to_update.append('alert_type')
                            if alert.min_speed is None:
                                alert.min_speed = speed_min
                                fields_to_update.append('min_speed')
                            if alert.max_speed is None:
                                alert.max_speed = speed_max
                                fields_to_update.append('max_speed')
                            if alert.trip_start_time is None and start_time:
                                alert.trip_start_time = start_time
                                fields_to_update.append('trip_start_time')
                            if alert.trip_end_time is None and end_time:
                                alert.trip_end_time = end_time
                                fields_to_update.append('trip_end_time')
                            if alert.trip_distance is None:
                                alert.trip_distance = trip_distance
                                fields_to_update.append('trip_distance')
                            if alert.trip_duration is None:
                                alert.trip_duration = trip_duration
                                fields_to_update.append('trip_duration')
                            if fields_to_update:
                                alert.save(update_fields=fields_to_update)
                        else:
                            alert = Alert.objects.create(
                                event_id=event_id,
                                vehicle=vehicle,
                                driver=driver,
                                timestamp=timestamp,
                                alert_type=combined_type,
                                location=location,
                                speed=speed_max,
                                min_speed=speed_min,
                                max_speed=speed_max,
                                trip_start_time=start_time,
                                trip_end_time=end_time,
                                trip_distance=trip_distance,
                                trip_duration=trip_duration,
                            )
                            created = True

                        if created:
                            if is_initial_load:
                                # Historical import: record in database without sending external notifications
                                NotificationService.process_alert(alert, alert_types, silence_notifications=True)
                            else:
                                # Genuinely NEW alert detected! Trigger EMAIL and SMS to all 3 recipients!
                                self.stdout.write(self.style.SUCCESS("-" * 60))
                                self.stdout.write(self.style.SUCCESS(
                                    f"[NEW ALERT DETECTED]\n"
                                    f"  Alert Type : {combined_type}\n"
                                    f"  Vehicle    : {vehicle.number} ({vehicle.vehicle_id})\n"
                                    f"  Driver     : {driver.name} ({driver.driver_id})\n"
                                    f"  Contact    : {driver.contact}\n"
                                    f"  Email      : {driver.email}\n"
                                    f"  Event ID   : {event_id}"
                                ))
                                self.stdout.write(self.style.SUCCESS("Dispatching notifications to Fleet, Manager, and Driver..."))
                                NotificationService.process_alert(alert, alert_types, silence_notifications=False)
                                self.stdout.write(self.style.SUCCESS("-" * 60))

        # Mark initial import completed in persistent database state
        if is_initial_load or not SystemConfig.objects.filter(key='initial_import_completed', value='true').exists():
            SystemConfig.objects.update_or_create(
                key='initial_import_completed',
                defaults={'value': 'true'}
            )
            self.stdout.write(self.style.SUCCESS("[INITIAL LOAD] Completed. System is now armed and watching for genuinely NEW alerts."))

    def parse_time(self, time_str):
        if not time_str:
            return None
        dt = parse_datetime(time_str.replace(' ', 'T'))
        if dt and is_naive(dt):
            dt = make_aware(dt)
        return dt
