import os
import subprocess

tables = [
    ('vehicles', 'id IN (7, 11, 13)'),
    ('trips', 'vehicle_id IN (7, 11, 13)'),
    ('alerts', 'vehicle_id IN (7, 11, 13)'),
    ('maintenance_records', 'vehicle_id IN (7, 11, 13)'),
    ('cost_records', 'vehicle_id IN (7, 11, 13)')
]

for tbl, where in tables:
    csv_path = f"data/backup_{tbl}_cars.csv"
    cmd = f"psql -U riyamokale -d gps_tracking -c \"COPY (SELECT * FROM {tbl} WHERE {where}) TO STDOUT WITH CSV HEADER;\" > \"{csv_path}\""
    subprocess.run(cmd, shell=True, check=True)
    with open(csv_path) as f:
        lines = f.readlines()
        print(f"Backed up {tbl}: {len(lines) - 1} rows to {csv_path}")

print("All affected records backed up successfully.")
