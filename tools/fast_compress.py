#!/usr/bin/env python3
"""
tools/fast_compress.py
Ultra-fast multithreaded / optimized zip archiving for Jörmungandr releases.
Uses 7-Zip with multithreading if available, otherwise uses high-speed buffered Python zipfile.
Also produces an automated SHA-256 checksum manifest.
"""

import os
import sys
import shutil
import hashlib
import subprocess
import zipfile

def compute_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def fast_compress(source_root, target_dir_name, output_zip_path, threads=8):
    source_root = os.path.abspath(source_root)
    output_zip_path = os.path.abspath(output_zip_path)
    full_source_path = os.path.join(source_root, target_dir_name)

    if not os.path.exists(full_source_path):
        raise FileNotFoundError(f"Source folder to compress does not exist: {full_source_path}")

    os.makedirs(os.path.dirname(output_zip_path), exist_ok=True)
    if os.path.exists(output_zip_path):
        os.remove(output_zip_path)

    # 1. Attempt 7-Zip if available
    seven_zip_candidates = [
        "7z",
        "7za",
        r"C:\Program Files\7-Zip\7z.exe",
        r"C:\Program Files (x86)\7-Zip\7z.exe"
    ]
    seven_zip_bin = None
    for cand in seven_zip_candidates:
        if shutil.which(cand) or os.path.exists(cand):
            seven_zip_bin = cand
            break

    compressed_via_7z = False
    if seven_zip_bin:
        print(f"[*] Fast compression via 7-Zip ({seven_zip_bin}) with {threads} threads...")
        cmd = [
            seven_zip_bin, "a", "-tzip", "-mx=3",
            f"-mmt={threads}",
            output_zip_path,
            target_dir_name
        ]
        try:
            res = subprocess.run(cmd, cwd=source_root, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if res.returncode == 0 and os.path.exists(output_zip_path):
                compressed_via_7z = True
                print(f"[+] 7-Zip compression succeeded.")
            else:
                print(f"[-] 7-Zip returned {res.returncode}, falling back to optimized Python zipfile...")
        except Exception as e:
            print(f"[-] 7-Zip execution failed: {e}, falling back to Python zipfile...")

    # 2. High-speed buffered Python zipfile fallback
    if not compressed_via_7z:
        print(f"[*] Compressing via optimized Python zipfile (compresslevel=3)...")
        with zipfile.ZipFile(output_zip_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=3) as zf:
            for root, _, files in os.walk(full_source_path):
                for f in files:
                    abs_f = os.path.join(root, f)
                    rel_f = os.path.relpath(abs_f, source_root).replace('\\', '/')
                    zf.write(abs_f, rel_f)
        print(f"[+] Python zipfile compression succeeded.")

    # 3. Generate SHA-256 Checksum Manifest
    sha256_hash = compute_sha256(output_zip_path)
    sha256_file = output_zip_path + ".sha256"
    filename = os.path.basename(output_zip_path)
    with open(sha256_file, 'w', encoding='utf-8') as f:
        f.write(f"{sha256_hash} *{filename}\n")
    print(f"[+] Generated SHA-256 checksum: {sha256_hash}")

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: fast_compress.py <source_root> <target_dir_name> <output_zip_path> [threads]")
        sys.exit(1)
    s_root = sys.argv[1]
    t_name = sys.argv[2]
    out_zip = sys.argv[3]
    th = int(sys.argv[4]) if len(sys.argv) > 4 else 8
    fast_compress(s_root, t_name, out_zip, th)
