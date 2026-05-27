#!/usr/bin/env python3
"""
update-libs.py — Update built-in libraries in app/src/main/assets/libs/libs.zip
and dexs.zip with newer versions downloaded from Google Maven.

Usage:
    python3 tools/update-libs.py

Requirements:
    Network access to dl.google.com (Google Maven) and repo.maven.apache.org
    Python 3.8+, d8 (from Android SDK build-tools) on PATH for DEX compilation

Currently updates:
    - material-1.13.0  →  material-1.14.0-alpha09  (adds Material3 Expressive)
"""

import os
import sys
import zipfile
import shutil
import urllib.request
import subprocess
import tempfile

REPO = "https://dl.google.com/dl/android/maven2"
LIBS_ZIP = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "libs", "libs.zip")
DEXS_ZIP = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "libs", "dexs.zip")

UPDATES = [
    {
        "old_name":  "material-1.13.0",
        "new_name":  "material-1.14.0-alpha09",
        "group":     "com/google/android/material",
        "artifact":  "material",
        "version":   "1.14.0-alpha09",
    },
]


def download(url, dest):
    print(f"  Downloading {url}")
    try:
        urllib.request.urlretrieve(url, dest)
    except Exception as e:
        print(f"  ERROR: {e}")
        return False
    return True


def aar_to_lib_dir(aar_path, lib_name, out_dir):
    """Extract an AAR into the out_dir/{lib_name}/ directory structure used by libs.zip."""
    lib_dir = os.path.join(out_dir, lib_name)
    os.makedirs(lib_dir, exist_ok=True)
    with zipfile.ZipFile(aar_path, "r") as aar:
        for entry in aar.namelist():
            target = os.path.join(lib_dir, entry)
            if entry.endswith("/"):
                os.makedirs(target, exist_ok=True)
            else:
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with aar.open(entry) as src, open(target, "wb") as dst:
                    shutil.copyfileobj(src, dst)
    return lib_dir


def dex_from_jar(jar_path, dex_out, d8_cmd="d8"):
    """Compile a classes.jar to a DEX file using d8."""
    try:
        result = subprocess.run(
            [d8_cmd, "--output", os.path.dirname(dex_out), jar_path],
            capture_output=True, text=True, timeout=120
        )
        classes_dex = os.path.join(os.path.dirname(dex_out), "classes.dex")
        if os.path.exists(classes_dex):
            os.rename(classes_dex, dex_out)
            return True
        print(f"  d8 stderr: {result.stderr[:500]}")
        return False
    except FileNotFoundError:
        print("  WARNING: d8 not found on PATH — DEX will not be updated.")
        return False
    except Exception as e:
        print(f"  WARNING: DEX compilation failed: {e}")
        return False


def update_zip_entries(zip_path, remove_prefix, add_dir, add_prefix):
    """Rebuild zip_path: remove entries with remove_prefix, add entries from add_dir as add_prefix."""
    tmp = zip_path + ".tmp"
    with zipfile.ZipFile(zip_path, "r") as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        # Copy all existing entries except the ones being replaced
        for entry in zin.namelist():
            if not entry.startswith(remove_prefix):
                zout.writestr(entry, zin.read(entry))
        # Add new entries
        for root, dirs, files in os.walk(add_dir):
            for fname in files:
                fpath = os.path.join(root, fname)
                arcname = add_prefix + os.path.relpath(fpath, add_dir).replace(os.sep, "/")
                zout.write(fpath, arcname)
    os.replace(tmp, zip_path)


def main():
    libs_zip = os.path.abspath(LIBS_ZIP)
    dexs_zip = os.path.abspath(DEXS_ZIP)

    if not os.path.exists(libs_zip):
        print(f"ERROR: {libs_zip} not found — run from the repo root")
        sys.exit(1)

    with tempfile.TemporaryDirectory() as tmpdir:
        for update in UPDATES:
            old, new = update["old_name"], update["new_name"]
            print(f"\n=== Updating {old} → {new} ===")

            # Download AAR
            aar_url = f"{REPO}/{update['group']}/{update['artifact']}/{update['version']}/{update['artifact']}-{update['version']}.aar"
            aar_path = os.path.join(tmpdir, f"{new}.aar")
            if not download(aar_url, aar_path):
                print(f"  SKIP: could not download AAR")
                continue

            # Extract AAR to lib directory
            lib_dir = aar_to_lib_dir(aar_path, new, tmpdir)
            print(f"  Extracted to {lib_dir}")

            # Update libs.zip
            print(f"  Updating libs.zip: removing {old}/, adding {new}/")
            update_zip_entries(libs_zip, old + "/", lib_dir, new + "/")
            print(f"  libs.zip updated")

            # Try to update dexs.zip
            jar_path = os.path.join(lib_dir, "classes.jar")
            if os.path.exists(jar_path):
                dex_out = os.path.join(tmpdir, f"{new}.dex")
                print(f"  Compiling DEX for {new}...")
                if dex_from_jar(jar_path, dex_out):
                    print(f"  Updating dexs.zip: removing {old}.dex, adding {new}.dex")
                    update_zip_entries(dexs_zip, old + ".dex", tmpdir, "")
                    print(f"  dexs.zip updated")

    print("\nDone! Remember to update BuiltInLibraries.java if you changed a library version.")
    print("  MATERIAL = \"" + UPDATES[0]["new_name"] + "\"")


if __name__ == "__main__":
    main()
