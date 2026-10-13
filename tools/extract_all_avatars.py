import subprocess
import os
import shutil

pdf_path = "avatars.pdf"
dest_dir = "app/src/main/res/drawable-nodpi"
os.makedirs(dest_dir, exist_ok=True)

# Also ensure design/ folder has avatars.pdf per specification
os.makedirs("design", exist_ok=True)
if not os.path.exists("design/avatars.pdf"):
    shutil.copyfile("avatars.pdf", "design/avatars.pdf")

# Generate circular mask
mask_path = "/tmp/circle_mask_384.png"
subprocess.check_call([
    "convert", "-size", "384x384", "xc:none",
    "-fill", "white", "-draw", "circle 192,192 192,0",
    mask_path
])

print("Extracting 17 avatars from", pdf_path)
for i in range(17):
    avatar_num = i + 1
    out_filename = f"avatar_{avatar_num:02d}.webp"
    out_path = os.path.join(dest_dir, out_filename)
    
    raw_tmp = f"/tmp/raw_page_{i}.png"
    sq_tmp = f"/tmp/sq_page_{i}.png"
    
    # 1. Rasterize at 200 DPI and trim transparent edges
    subprocess.check_call([
        "convert", "-density", "200", f"{pdf_path}[{i}]",
        "-fuzz", "5%", "-trim", "+repage", raw_tmp
    ])
    
    # 2. Get trimmed dimensions
    dims = subprocess.check_output([
        "convert", raw_tmp, "-format", "%w %h", "info:"
    ]).decode().strip().split()
    w, h = int(dims[0]), int(dims[1])
    max_dim = max(w, h)
    
    # 3. Fit to square canvas with transparent background and resize to 384x384
    subprocess.check_call([
        "convert", raw_tmp,
        "-gravity", "center", "-background", "transparent",
        "-extent", f"{max_dim}x{max_dim}",
        "-filter", "Lanczos", "-resize", "384x384",
        sq_tmp
    ])
    
    # 4. Composite with circular mask and save as WebP quality 92
    subprocess.check_call([
        "convert", sq_tmp, mask_path,
        "-alpha", "Off", "-compose", "CopyOpacity", "-composite",
        "-quality", "92", "-define", "webp:lossless=false",
        out_path
    ])
    
    # Clean up temp files
    if os.path.exists(raw_tmp):
        os.remove(raw_tmp)
    if os.path.exists(sq_tmp):
        os.remove(sq_tmp)
        
    size_bytes = os.path.getsize(out_path)
    print(f"[{avatar_num}/17] Successfully extracted {out_filename} ({size_bytes} bytes)")

print("All 17 avatars extracted successfully.")
