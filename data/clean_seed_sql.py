import re

with open("data/seed.sql", "r") as f:
    lines = f.readlines()

new_lines = []
removed = 0
for line in lines:
    # Check if this line is an insert for vehicles 7, 11, 13
    if line.startswith("INSERT INTO vehicles ") and re.search(r"VALUES \((7|11|13),", line):
        removed += 1
        continue
    # Check trips: INSERT INTO trips (..., vehicle_id, ...) VALUES (..., vid, ...)
    # Let's check if line mentions vehicle_id 7, 11, 13
    if line.startswith("INSERT INTO trips ") and (", 7, " in line or ", 11, " in line or ", 13, " in line):
        removed += 1
        continue
    # Check alerts: INSERT INTO alerts ...
    if line.startswith("INSERT INTO alerts ") and (", 7, " in line or ", 11, " in line or ", 13, " in line):
        removed += 1
        continue
    # Check maintenance_records
    if line.startswith("INSERT INTO maintenance_records ") and (", 7, " in line or ", 11, " in line or ", 13, " in line):
        removed += 1
        continue
    # Check cost_records
    if line.startswith("INSERT INTO cost_records ") and (", 7, " in line or ", 11, " in line or ", 13, " in line):
        removed += 1
        continue
    new_lines.append(line)

with open("data/seed.sql", "w") as f:
    f.writelines(new_lines)

print(f"Cleaned data/seed.sql: removed {removed} lines referencing car vehicles 7, 11, 13.")
