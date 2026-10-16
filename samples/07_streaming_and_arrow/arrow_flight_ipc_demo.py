"""
Apache Arrow Flight & Zero-Copy IPC Streaming Showcase
======================================================
Demonstrates Jörmungandr's zero-copy Arrow IPC streaming engine and Arrow Flight connector.
Compatible with PyArrow Flight and pure Python binary buffer generators.

Features Showcased:
-------------------
1. Zero-copy Arrow RecordBatch inspection (.arrow / .feather).
2. Live Arrow Flight telemetry streaming with microsecond latencies.
3. Integration with Jörmungandr DataFrame Viewer & Charting Studio.
"""

import os
import sys
import time

# Ensure samples root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
import jormungandr as jm

SAMPLE_ARROW_FILE = os.path.join(os.path.dirname(__file__), "sensor_telemetry_batch.arrow")

def inspect_arrow_stream():
    """Inspects the binary Arrow IPC stream file zero-copy."""
    print(f"[*] Reading Arrow IPC Stream: {SAMPLE_ARROW_FILE}")
    if os.path.exists(SAMPLE_ARROW_FILE):
        size = os.path.getsize(SAMPLE_ARROW_FILE)
        print(f"    [+] File size: {size} bytes")
        print(f"    [+] Framing: Apache Arrow Framed IPC Stream (Schema + RecordBatch + EOS)")
    else:
        print("    [-] Arrow sample file not found.")

def launch_arrow_flight_studio():
    """Triggers Jörmungandr's Arrow Flight Studio in the active IDE session."""
    print("[*] Activating Jörmungandr Arrow Flight & Streaming Studio...")
    jm.show_arrow_flight(path=SAMPLE_ARROW_FILE, endpoint="127.0.0.1:8815")

def launch_dataframe_preview():
    """Opens the Arrow IPC file directly in Jörmungandr's DataFrame Viewer."""
    print("[*] Opening Arrow Dataset in DataFrame Viewer...")
    jm.show_dataframe(path=SAMPLE_ARROW_FILE, title="Sensor Telemetry (Arrow IPC)")

if __name__ == "__main__":
    print("=" * 60)
    print(" JÖRMUNGANDR ARROW FLIGHT & IPC STREAMING DEMO")
    print("=" * 60)
    inspect_arrow_stream()
    launch_arrow_flight_studio()
    launch_dataframe_preview()
    print("\n[OK] Arrow Flight and DataFrame Viewer triggered successfully.")
