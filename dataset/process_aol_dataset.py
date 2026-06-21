import os
import tarfile
import gzip
import csv
import re
from collections import Counter

TAR_PATH = "aol-data.tar"
EXTRACT_DIR = "aol_extracted"
OUTPUT_CSV = "seed_queries.csv"
TOP_N = 100000

def extract_available_files(tar_path, dest_dir):
    """Extract as many files as possible from a potentially truncated tar."""
    os.makedirs(dest_dir, exist_ok=True)
    
    # Check if we already extracted
    existing_gz = [f for f in os.listdir(dest_dir) if f.endswith('.gz') or f.endswith('.txt')]
    if existing_gz:
        print(f"Already extracted files found: {existing_gz}. Skipping extraction.")
        return
    
    print(f"Extracting available files from {tar_path}...")
    with tarfile.open(tar_path, 'r') as tar:
        members = []
        try:
            for m in tar:
                members.append(m)
        except tarfile.ReadError:
            print(f"Tar truncated after {len(members)} members (expected — download was partial).")
        
        for member in members:
            if member.isfile():
                try:
                    tar.extract(member, path=dest_dir)
                    print(f"  Extracted: {member.name} ({member.size:,} bytes)")
                except Exception as e:
                    print(f"  Skipped {member.name}: {e}")
    
    print("Extraction done.")

def clean_query(q):
    if not q:
        return ""
    q = q.lower().strip()
    if q.startswith('"') and q.endswith('"'):
        q = q[1:-1].strip()
    if len(q) < 2:
        return ""
    if not re.search(r"[a-z0-9]", q):
        return ""
    if len(q) > 100:
        return ""
    return q

def process_gz_file(gz_path, counter):
    """Process a single gzipped query log file."""
    print(f"Processing {gz_path}...")
    lines_read = 0
    with gzip.open(gz_path, 'rt', encoding='utf-8', errors='ignore') as f:
        # Skip header line
        f.readline()
        for line in f:
            lines_read += 1
            if lines_read % 1_000_000 == 0:
                print(f"  ...{lines_read:,} lines read")
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                cleaned = clean_query(parts[1])
                if cleaned:
                    counter[cleaned] += 1
    print(f"  Done. {lines_read:,} lines from {os.path.basename(gz_path)}")
    return lines_read

def process_txt_file(txt_path, counter):
    """Process a plain text query log file."""
    print(f"Processing {txt_path}...")
    lines_read = 0
    with open(txt_path, 'r', encoding='utf-8', errors='ignore') as f:
        f.readline()  # skip header
        for line in f:
            lines_read += 1
            if lines_read % 1_000_000 == 0:
                print(f"  ...{lines_read:,} lines read")
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                cleaned = clean_query(parts[1])
                if cleaned:
                    counter[cleaned] += 1
    print(f"  Done. {lines_read:,} lines from {os.path.basename(txt_path)}")
    return lines_read

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    
    if not os.path.exists(TAR_PATH):
        print(f"ERROR: {TAR_PATH} not found. Please run the download first.")
        return
    
    extract_available_files(TAR_PATH, EXTRACT_DIR)
    
    # Find all .gz and .txt log files
    counter = Counter()
    total_lines = 0
    
    for root, _, files in os.walk(EXTRACT_DIR):
        for fname in sorted(files):
            fpath = os.path.join(root, fname)
            if fname.endswith('.gz'):
                try:
                    total_lines += process_gz_file(fpath, counter)
                except Exception as e:
                    print(f"  Error processing {fname}: {e}")
            elif fname.endswith('.txt') and 'README' not in fname:
                try:
                    total_lines += process_txt_file(fpath, counter)
                except Exception as e:
                    print(f"  Error processing {fname}: {e}")
    
    print(f"\nTotal lines processed: {total_lines:,}")
    print(f"Unique queries found: {len(counter):,}")
    
    # Get top N
    print(f"\nSelecting top {TOP_N:,} queries by frequency...")
    most_common = counter.most_common(TOP_N)
    
    # Print top 20 for sanity check
    print("\nTop 20 queries:")
    for i, (query, count) in enumerate(most_common[:20], 1):
        print(f"  {i:2d}. '{query}' — {count:,} occurrences")
    
    # Write CSV
    print(f"\nWriting {OUTPUT_CSV}...")
    with open(OUTPUT_CSV, 'w', encoding='utf-8', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['query', 'count'])
        for query, count in most_common:
            writer.writerow([query, count])
    
    print(f"Done! Wrote {len(most_common):,} queries to {OUTPUT_CSV}")

if __name__ == '__main__':
    main()
