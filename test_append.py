import csv

csv_path = 'dataset/vehicle_fleet_alerts_fake_dataset.csv'

# Vehicle ID,Vehicle Number,Driver ID,Driver Name,Driver Contact,Speed Min (km/h),Speed Max (km/h),Location,Time,Trip Start Time,Trip End Time,Trip Distance (km),Trip Duration (min),No. of Trips Covered by Vehicle,Overspeed Alert,Harsh Braking Alert,GPS Disconnect Alert,Ignition On/Off Alert,Night Driving Alert

# Row 1: No alerts
row1 = ["VH001","MH04AB1234","DRV001","Aarav Sharma","9876543210","40","55","Navi Mumbai","2026-09-23 20:55:00","2026-09-23 20:50:00","2026-09-23 21:00:00","15","10","13","No","No","No","On","No"]

# Row 2: Overspeed alert
row2 = ["VH002","MH46CD5678","DRV002","Rohan Patil","9876543211","60","120","Pune","2026-09-23 21:05:00","2026-09-23 20:50:00","2026-09-23 21:10:00","30","20","13","Yes","No","No","On","No"]

with open(csv_path, 'a', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(row1)
    writer.writerow(row2)

print("Rows appended successfully.")
