from rest_framework import serializers
from .models import Driver, Vehicle, Alert, NotificationLog, Trip

class DriverSerializer(serializers.ModelSerializer):
    class Meta:
        model = Driver
        fields = '__all__'

class VehicleSerializer(serializers.ModelSerializer):
    driver_name = serializers.CharField(source='driver.name', read_only=True)

    class Meta:
        model = Vehicle
        fields = '__all__'

class NotificationLogSerializer(serializers.ModelSerializer):
    class Meta:
        model = NotificationLog
        fields = '__all__'

class AlertSerializer(serializers.ModelSerializer):
    vehicle_number = serializers.CharField(source='vehicle.number', read_only=True)
    driver_name = serializers.CharField(source='driver.name', read_only=True)
    notification = NotificationLogSerializer(read_only=True)

    class Meta:
        model = Alert
        fields = '__all__'

class TripSerializer(serializers.ModelSerializer):
    vehicle_number = serializers.CharField(source='vehicle.number', read_only=True)
    driver_name = serializers.CharField(source='driver.name', read_only=True)

    class Meta:
        model = Trip
        fields = '__all__'
