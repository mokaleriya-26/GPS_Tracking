"""
GPS Fleet AI/ML Service — Python microservice for driver behaviour analysis.
Port: 8000 (configurable via PORT env var)

Endpoints:
  POST /predict             - Safety score prediction for a driver
  POST /analyze-driver      - Full driver behaviour analysis
  POST /anomaly-detection   - Telemetry anomaly detection

Safety Score Formula (transparent, as documented in MIGRATION_PLAN.md Section 10.2):
  safety_score = 100
    - (overspeed_events * 5)
    - (harsh_braking_events * 4)
    - (harsh_acceleration_events * 4)
    - (night_driving_seconds / 3600 * 3)
    - (fatigue_events * 8)
    + (trips_without_incident * 1)
  Clamped between 0 and 100.
"""
import os
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)


def calculate_safety_score(data: dict) -> float:
    overspeed = data.get("overspeed_events", 0) or 0
    harsh_braking = data.get("harsh_braking_events", 0) or 0
    harsh_accel = data.get("harsh_acceleration_events", 0) or 0
    night_driving_sec = data.get("night_driving_seconds", 0) or 0
    fatigue = data.get("fatigue_events", 0) or 0
    clean_trips = data.get("trips_without_incident", 0) or 0

    score = (100
             - overspeed * 5
             - harsh_braking * 4
             - harsh_accel * 4
             - (night_driving_sec / 3600) * 3
             - fatigue * 8
             + clean_trips * 1)
    return max(0.0, min(100.0, round(score, 2)))


def get_behaviour_label(score: float) -> str:
    if score >= 90: return "Excellent"
    if score >= 75: return "Good"
    if score >= 60: return "Average"
    if score >= 40: return "Below Average"
    return "Poor"


@app.get("/health")
def health():
    return jsonify({"status": "ok", "service": "GPS Fleet AI/ML Service", "version": "1.0.0"})


@app.post("/predict")
def predict():
    """Predict safety score from driver metrics."""
    data = request.get_json(force=True) or {}
    score = calculate_safety_score(data)
    label = get_behaviour_label(score)

    return jsonify({
        "safety_score": score,
        "behaviour_label": label,
        "method": "RULE_BASED",
        "score_breakdown": {
            "base": 100,
            "overspeed_deduction": data.get("overspeed_events", 0) * 5,
            "harsh_braking_deduction": data.get("harsh_braking_events", 0) * 4,
            "harsh_acceleration_deduction": data.get("harsh_acceleration_events", 0) * 4,
            "night_driving_deduction": round((data.get("night_driving_seconds", 0) / 3600) * 3, 2),
            "fatigue_deduction": data.get("fatigue_events", 0) * 8,
            "clean_trip_bonus": data.get("trips_without_incident", 0),
        }
    })


@app.post("/analyze-driver")
def analyze_driver():
    """Full driver behaviour analysis with recommendations."""
    data = request.get_json(force=True) or {}
    score = calculate_safety_score(data)
    label = get_behaviour_label(score)

    recommendations = []
    if data.get("overspeed_events", 0) > 3:
        recommendations.append("⚠️ Frequent overspeeding detected. Recommend speed awareness training.")
    if data.get("harsh_braking_events", 0) > 5:
        recommendations.append("⚠️ Multiple harsh braking events. Recommend defensive driving training.")
    if data.get("harsh_acceleration_events", 0) > 5:
        recommendations.append("⚠️ Harsh acceleration observed. Recommend smooth driving coaching.")
    if data.get("night_driving_seconds", 0) > 7200:
        recommendations.append("⚠️ Significant night driving (>2 hours). Ensure adequate rest before shifts.")
    if data.get("fatigue_events", 0) > 0:
        recommendations.append("🔴 Fatigue events detected. Mandatory rest review required.")
    if not recommendations:
        recommendations.append("✅ No significant issues detected. Keep up the good work!")

    trip_count = data.get("trip_count", 0) or 0
    total_distance = data.get("distance_km", 0) or 0

    return jsonify({
        "safety_score": score,
        "behaviour_label": label,
        "recommendations": recommendations,
        "trip_count": trip_count,
        "total_distance_km": total_distance,
        "avg_trips_per_day": round(trip_count / 30, 1) if trip_count else 0,
        "risk_level": "HIGH" if score < 50 else "MEDIUM" if score < 75 else "LOW",
        "method": "RULE_BASED"
    })


@app.post("/anomaly-detection")
def anomaly_detection():
    """Detect anomalies in telemetry data."""
    data = request.get_json(force=True) or {}
    points = data.get("telemetry_points", [])
    anomalies = []

    prev_speed = None
    for i, point in enumerate(points):
        speed = point.get("speed_kmph", 0) or 0
        speed_limit = point.get("speed_limit_kmph", 80) or 80
        accel = point.get("acceleration_mps2")

        # Overspeed detection
        if speed > speed_limit * 1.1:
            anomalies.append({
                "index": i,
                "type": "OVERSPEED",
                "severity": "CRITICAL" if speed > speed_limit * 1.3 else "WARNING",
                "value": speed,
                "threshold": speed_limit,
                "lat": point.get("latitude"),
                "lon": point.get("longitude")
            })

        # Harsh braking detection
        if accel is not None and accel < -3.5:
            anomalies.append({
                "index": i,
                "type": "HARSH_BRAKING",
                "severity": "WARNING",
                "value": accel,
                "threshold": -3.5,
                "lat": point.get("latitude"),
                "lon": point.get("longitude")
            })

        # Harsh acceleration detection
        if accel is not None and accel > 3.0:
            anomalies.append({
                "index": i,
                "type": "HARSH_ACCELERATION",
                "severity": "WARNING",
                "value": accel,
                "threshold": 3.0
            })

        prev_speed = speed

    return jsonify({
        "anomaly_count": len(anomalies),
        "anomalies": anomalies,
        "points_analyzed": len(points),
        "method": "RULE_BASED"
    })


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    debug = os.environ.get("FLASK_DEBUG", "0") == "1"
    print(f"GPS Fleet AI/ML Service starting on port {port}")
    app.run(host="0.0.0.0", port=port, debug=debug)
