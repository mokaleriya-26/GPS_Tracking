from django.db import models

class Driver(models.Model):
    driver_id = models.CharField(max_length=50, primary_key=True)
    name = models.CharField(max_length=100)
    contact = models.CharField(max_length=20)
    email = models.CharField(max_length=100, null=True, blank=True)

    def __str__(self):
        return f"{self.name} ({self.driver_id})"

class Vehicle(models.Model):
    vehicle_id = models.CharField(max_length=50, primary_key=True)
    number = models.CharField(max_length=50)
    driver = models.ForeignKey(Driver, on_delete=models.SET_NULL, null=True, blank=True)
    location = models.CharField(max_length=255, null=True, blank=True)
    speed = models.FloatField(default=0.0)
    ignition = models.BooleanField(default=True)
    last_updated = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return self.number

class Alert(models.Model):
    event_id = models.CharField(max_length=255, unique=True, null=True, db_index=True)
    vehicle = models.ForeignKey(Vehicle, on_delete=models.CASCADE)
    driver = models.ForeignKey(Driver, on_delete=models.SET_NULL, null=True, blank=True)
    alert_type = models.CharField(max_length=100)
    location = models.CharField(max_length=255)
    speed = models.FloatField()
    timestamp = models.DateTimeField()
    min_speed = models.FloatField(null=True, blank=True)
    max_speed = models.FloatField(null=True, blank=True)
    trip_start_time = models.DateTimeField(null=True, blank=True)
    trip_end_time = models.DateTimeField(null=True, blank=True)
    trip_distance = models.FloatField(null=True, blank=True)
    trip_duration = models.IntegerField(null=True, blank=True)

    def __str__(self):
        return f"{self.alert_type} for {self.vehicle.number}"

class NotificationLog(models.Model):
    alert = models.OneToOneField(Alert, on_delete=models.CASCADE, related_name='notification')
    
    # Overall flags
    email_sent = models.BooleanField(default=False)
    sms_sent = models.BooleanField(default=False)
    whatsapp_sent = models.BooleanField(default=False)
    email_sent_at = models.DateTimeField(null=True, blank=True)
    sms_sent_at = models.DateTimeField(null=True, blank=True)
    whatsapp_sent_at = models.DateTimeField(null=True, blank=True)
    email_error = models.TextField(null=True, blank=True)
    sms_error = models.TextField(null=True, blank=True)
    whatsapp_error = models.TextField(null=True, blank=True)
    
    # Recipient-level Email status
    fleet_email_sent = models.BooleanField(default=False)
    fleet_email_error = models.TextField(null=True, blank=True)
    manager_email_sent = models.BooleanField(default=False)
    manager_email_error = models.TextField(null=True, blank=True)
    driver_email_sent = models.BooleanField(default=False)
    driver_email_error = models.TextField(null=True, blank=True)

    # Recipient-level SMS status
    fleet_sms_sent = models.BooleanField(default=False)
    fleet_sms_error = models.TextField(null=True, blank=True)
    fleet_sms_request_id = models.CharField(max_length=100, null=True, blank=True)

    manager_sms_sent = models.BooleanField(default=False)
    manager_sms_error = models.TextField(null=True, blank=True)
    manager_sms_request_id = models.CharField(max_length=100, null=True, blank=True)

    driver_sms_sent = models.BooleanField(default=False)
    driver_sms_error = models.TextField(null=True, blank=True)
    driver_sms_request_id = models.CharField(max_length=100, null=True, blank=True)

    # Flag for historical data silencing
    is_silenced = models.BooleanField(default=False)

    def __str__(self):
        return f"Notifications for {self.alert.id}"

class Trip(models.Model):
    vehicle = models.ForeignKey(Vehicle, on_delete=models.CASCADE)
    driver = models.ForeignKey(Driver, on_delete=models.SET_NULL, null=True, blank=True)
    start_time = models.DateTimeField()
    end_time = models.DateTimeField()
    distance_km = models.FloatField()
    duration_min = models.IntegerField()
    min_speed = models.FloatField()
    max_speed = models.FloatField()

    def __str__(self):
        return f"Trip {self.vehicle.number} on {self.start_time}"

class SystemConfig(models.Model):
    key = models.CharField(max_length=100, primary_key=True)
    value = models.TextField()
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.key}: {self.value}"

