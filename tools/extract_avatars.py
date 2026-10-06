import os
import sys
import fitz  # PyMuPDF
import numpy as np
from PIL import Image, ImageFilter

def process_pdf(pdf_path, output_dir):
    os.makedirs(output_dir, exist_ok=True)
    doc = fitz.open(pdf_path)
    page_count = len(doc)
    print(f"Opened {pdf_path}: total pages = {page_count}")

    for page_num in range(page_count):
        page = doc[page_num]
        image_list = page.get_images(full=True)
        img_pil = None

        if image_list:
            # Extract embedded image at native resolution
            xref = image_list[0][0]
            base_image = doc.extract_image(xref)
            image_bytes = base_image["image"]
            import io
            img_pil = Image.open(io.BytesIO(image_bytes)).convert("RGB")
            print(f"Page {page_num+1}: Extracted embedded image {img_pil.size}")
        else:
            # Render page at 300 DPI
            pix = page.get_pixmap(dpi=300)
            img_pil = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
            print(f"Page {page_num+1}: Rendered page at 300 DPI {img_pil.size}")

        # Circle Detection & Masking
        img_np = np.array(img_pil, dtype=np.float32)

        # Determine background color from corners
        h, w, _ = img_np.shape
        corner_pixels = np.concatenate([
            img_np[:20, :20].reshape(-1, 3),
            img_np[:20, -20:].reshape(-1, 3),
            img_np[-20:, :20].reshape(-1, 3),
            img_np[-20:, -20:].reshape(-1, 3)
        ], axis=0)
        bg_color = np.median(corner_pixels, axis=0)

        # Compute difference from background
        diff = np.linalg.norm(img_np - bg_color, axis=2)
        # Threshold for foreground (non-background artwork)
        fg_mask = diff > 25.0

        y_indices, x_indices = np.where(fg_mask)
        if len(x_indices) == 0:
            print(f"Error: Page {page_num+1} has no foreground artwork!")
            continue

        minX = float(np.min(x_indices))
        maxX = float(np.max(x_indices))
        minY = float(np.min(y_indices))

        # Circle derived from LEFT, RIGHT, TOP extremes ONLY:
        radius = (maxX - minX) / 2.0
        centerX = (minX + maxX) / 2.0
        centerY = minY + radius

        # Crop square around [centerX - radius, centerY - radius, centerX + radius, centerY + radius]
        # Inset crop box slightly or pad to ensure full circle fits
        crop_min_x = max(0, int(round(centerX - radius)))
        crop_max_x = min(w, int(round(centerX + radius)))
        crop_min_y = max(0, int(round(centerY - radius)))
        crop_max_y = min(h, int(round(centerY + radius)))

        cropped_np = img_np[crop_min_y:crop_max_y, crop_min_x:crop_max_x]
        crop_h, crop_w, _ = cropped_np.shape

        # Re-sample/resize cropped image to 384x384
        crop_pil = Image.fromarray(cropped_np.astype(np.uint8))
        target_size = 384
        crop_resized = crop_pil.resize((target_size, target_size), Image.Resampling.LANCZOS)

        # Create circular alpha mask with 1.5% inset and anti-aliasing feather
        mask = Image.new("L", (target_size, target_size), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)

        # Circle radius inset by 1.5%
        inset = target_size * 0.015
        circle_bbox = [
            inset,
            inset,
            target_size - inset,
            target_size - inset
        ]
        draw.ellipse(circle_bbox, fill=255)

        # Apply 1.5px feathering blur for smooth anti-aliased edge
        mask = mask.filter(ImageFilter.GaussianBlur(radius=1.2))

        # Combine RGB resized image with alpha mask
        crop_resized.putalpha(mask)

        # Output zero-padded filename: avatar_01.webp ... avatar_NN.webp
        filename = f"avatar_{page_num+1:02d}.webp"
        out_path = os.path.join(output_dir, filename)
        crop_resized.save(out_path, "WEBP", quality=92)
        print(f"Page {page_num+1}: Saved {filename} ({os.path.getsize(out_path)} bytes)")

if __name__ == "__main__":
    pdf_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "../app/design/avatars.pdf"))
    output_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../app/src/main/res/drawable-nodpi"))
    process_pdf(pdf_path, output_dir)
