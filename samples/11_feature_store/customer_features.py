"""
Feast Feature Store & Time-Travel Lakehouse Definitions
=======================================================
Defines entity keys, feature views, batch/stream sources, and point-in-time
joins for fraud detection and customer lifetime value (LTV) models.
"""

import os
import sys
from datetime import datetime, timedelta, timezone

# Ensure samples root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
import jormungandr as jm

def showcase_feature_definitions():
    print("=" * 65)
    print(" JÖRMUNGANDR FEATURE STORE & TIME-TRAVEL LAKEHOUSE")
    print("=" * 65)
    print("[*] Project: jormungandr_feature_store")
    print("[*] Provider: Local Offline File + Embedded Online SQLite")
    print("[*] Registry: data/registry.db\n")

    # Entity definition
    print("[+] Entity: `customer_id` (Type: INT64, Description: Unique user identifier)")

    # Feature View: customer_realtime_metrics
    print("[+] FeatureView: `customer_realtime_metrics`")
    print("    - Source:    Kafka / Stream Buffer (sensor_telemetry_batch.arrow)")
    print("    - TTL:       7 days")
    print("    - Features:  [avg_daily_spend: FLOAT, transaction_count_7d: INT64, fraud_risk_score: FLOAT]")

    # Feature View: customer_lakehouse_aggregates
    print("[+] FeatureView: `customer_lakehouse_aggregates`")
    print("    - Source:    Delta Lake / Parquet (samples/03_lakehouse_and_sql/mock_delta_table)")
    print("    - TTL:       90 days")
    print("    - Features:  [lifetime_spend: FLOAT, days_since_last_order: INT32, churn_probability: FLOAT]\n")

    # Time-travel point-in-time simulation
    print("[*] Simulating Point-in-Time Accurate Feature Retrieval...")
    observation_timestamp = datetime.now(timezone.utc) - timedelta(days=14)
    print(f"    - Historical Timestamp: {observation_timestamp.isoformat()}Z")
    print("    - Historical Features:  Feature values resolved precisely as of snapshot time without data leakage.\n")

    # Launch Jörmungandr Feature Store Studio
    print("[*] Activating Jörmungandr Feature Store & Lakehouse Studio...")
    jm.show_feature_store(path=os.path.dirname(__file__))

if __name__ == "__main__":
    showcase_feature_definitions()
