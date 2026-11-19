#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Script to backfill historical full-quality wallpapers from @amandinhawallpaper Telegram channel.
Allows downloading a customizable number of older wallpapers, strictly skipping posts from today,
preventing duplicate items (by message ID and cryptographic content hash MD5),
and advancing the historical backfill cursor.
"""

import os
import sys
import json
import asyncio
import hashlib
import re
from datetime import datetime, timezone
from typing import List, Dict, Any, Set
from telethon import TelegramClient
from telethon.sessions import StringSession
from telethon.tl.types import (
    MessageMediaDocument,
    DocumentAttributeVideo,
    DocumentAttributeFilename,
)
from PIL import Image

try:
    import cv2
except ImportError:
    cv2 = None

API_ID = int(os.environ.get("TELEGRAM_API_ID", 0))
API_HASH = os.environ.get("TELEGRAM_API_HASH", "")
STRING_SESSION = os.environ.get("TELEGRAM_STRING_SESSION", "")
CHANNEL_USERNAME = os.environ.get("CHANNEL_USERNAME", "amandinhawallpaper")

TARGET_COUNT = int(os.environ.get("BACKFILL_COUNT", "30"))
SKIP_TODAY_RAW = os.environ.get("SKIP_TODAY", "true").lower()
SKIP_TODAY = SKIP_TODAY_RAW in ("true", "1", "yes", "y")
PREPEND_BACKFILLED_RAW = os.environ.get("PREPEND_BACKFILLED", "true").lower()
PREPEND_BACKFILLED = PREPEND_BACKFILLED_RAW in ("true", "1", "yes", "y")

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WALLPAPERS_DIR = os.path.join(BASE_DIR, "wallpapers")
FULL_DIR = os.path.join(WALLPAPERS_DIR, "full")
THUMB_DIR = os.path.join(WALLPAPERS_DIR, "thumbnails")
CATALOG_PATH = os.path.join(WALLPAPERS_DIR, "catalog.json")

RAW_BASE_URL = "https://raw.githubusercontent.com/S-Marcos-S/viagara-wallpapers/main/wallpapers"

os.makedirs(FULL_DIR, exist_ok=True)
os.makedirs(THUMB_DIR, exist_ok=True)


def calculate_file_md5(file_path: str) -> str:
    hasher = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def populate_missing_hashes(catalog: Dict[str, Any]) -> int:
    """Populates content_hash for catalog items if the file is available in FULL_DIR."""
    populated = 0
    for item in catalog.get("wallpapers", []):
        if item.get("content_hash"):
            continue
        item_id = item.get("id", "")
        if not item_id:
            continue
        for ext in ("jpg", "png", "mp4", "jpeg"):
            path = os.path.join(FULL_DIR, f"{item_id}.{ext}")
            if os.path.exists(path):
                try:
                    ch = calculate_file_md5(path)
                    item["content_hash"] = ch
                    populated += 1
                except Exception as e:
                    print(f"Warning: could not calculate hash for {path}: {e}")
                break
    return populated


def generate_image_thumbnail(source_path: str, thumb_path: str, max_h: int = 1080) -> bool:
    try:
        with Image.open(source_path) as img:
            img = img.convert("RGB")
            w, h = img.size
            thumb_w = int(w * (max_h / h))
            thumb = img.resize((thumb_w, max_h), Image.Resampling.LANCZOS)
            thumb.save(thumb_path, format="WEBP", quality=82, method=4)
        return True
    except Exception as e:
        print(f"Error generating image thumbnail: {e}")
        return False


def generate_video_thumbnail(video_path: str, thumb_path: str, max_h: int = 1080) -> bool:
    try:
        if cv2 is not None:
            cap = cv2.VideoCapture(video_path)
            success, frame = cap.read()
            cap.release()
            if success:
                frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                img = Image.fromarray(frame_rgb)
                w, h = img.size
                thumb_w = int(w * (max_h / h))
                thumb = img.resize((thumb_w, max_h), Image.Resampling.LANCZOS)
                thumb.save(thumb_path, format="WEBP", quality=82, method=4)
                return True

        res = os.system(
            f'ffmpeg -y -ss 00:00:00.500 -i "{video_path}" -vframes 1 -vf "scale=-1:{max_h}" "{thumb_path}" -loglevel quiet'
        )
        return res == 0
    except Exception as e:
        print(f"Error generating video thumbnail: {e}")
        return False


def calculate_image_dhash(image_path: str, hash_size: int = 8) -> Optional[int]:
    """
    Computes a 64-bit difference hash (dHash) of an image or video thumbnail.
    Resizes to (hash_size + 1, hash_size) grayscale, compares adjacent pixels.
    Very fast, robust against re-compression, metadata differences, and container noise.
    """
    try:
        with Image.open(image_path) as img:
            img = img.convert("L").resize((hash_size + 1, hash_size), Image.Resampling.BILINEAR)
            if hasattr(img, "get_flattened_data"):
                pixels = list(img.get_flattened_data())
            else:
                pixels = list(img.getdata())
            diff = []
            for row in range(hash_size):
                row_offset = row * (hash_size + 1)
                for col in range(hash_size):
                    diff.append(pixels[row_offset + col] > pixels[row_offset + col + 1])
            decimal_value = 0
            for idx, bit in enumerate(diff):
                if bit:
                    decimal_value += 1 << idx
            return decimal_value
    except Exception as e:
        print(f"Error calculating dhash for {image_path}: {e}")
        return None


def hamming_distance(h1: int, h2: int) -> int:
    """Calculates the Hamming distance (number of differing bits) between two 64-bit hashes."""
    return bin(h1 ^ h2).count("1")


async def main():
    if not API_ID or not API_HASH or not STRING_SESSION:
        print("Error: Missing TELEGRAM_API_ID, TELEGRAM_API_HASH, or TELEGRAM_STRING_SESSION environment variables.")
        sys.exit(1)

    print(f"=== Viagara Wallpapers Historical Backfill ===")
    print(f"Target count: {TARGET_COUNT} wallpapers")
    print(f"Skip today's posts: {SKIP_TODAY}")
    print(f"Target channel: @{CHANNEL_USERNAME}")

    # Load existing catalog
    if os.path.exists(CATALOG_PATH):
        with open(CATALOG_PATH, "r", encoding="utf-8") as f:
            catalog = json.load(f)
    else:
        catalog = {
            "version": 1,
            "categories": [
                {"id": "all", "label": "Todos"},
                {"id": "live", "label": "Live Wallpapers"},
                {"id": "images", "label": "Imagens"},
            ],
            "wallpapers": [],
        }

    # Populate any missing hashes for existing wallpapers if files are present locally
    populated = populate_missing_hashes(catalog)
    if populated > 0:
        print(f"Populated content_hash for {populated} existing wallpapers.")

    existing_wallpapers = catalog.get("wallpapers", [])
    existing_ids: Set[str] = {w["id"] for w in existing_wallpapers}
    existing_hashes: Set[str] = {w.get("content_hash") for w in existing_wallpapers if w.get("content_hash")}
    existing_dhashes: Set[int] = {
        int(w["visual_hash"], 16)
        for w in existing_wallpapers
        if w.get("visual_hash")
    }
    recent_items: List[Dict[str, Any]] = []

    numeric_ids = [
        int(w["id"][3:])
        for w in existing_wallpapers
        if w.get("id", "").startswith("tg_") and w.get("id", "")[3:].isdigit()
    ]
    min_catalog_id = min(numeric_ids) if numeric_ids else None
    cursor_id = catalog.get("backfill_cursor_id") or min_catalog_id

    print(f"Current catalog size: {len(existing_wallpapers)} wallpapers ({len(existing_hashes)} hashes)")
    if cursor_id:
        print(f"Starting backfill offset at message #{cursor_id}")

    today_utc = datetime.now(timezone.utc).date()
    print(f"Today UTC Date: {today_utc}")

    print("Connecting to Telegram client...")
    client = TelegramClient(StringSession(STRING_SESSION), API_ID, API_HASH)
    await client.start()

    print(f"Resolving entity @{CHANNEL_USERNAME}...")
    entity = await client.get_entity(CHANNEL_USERNAME)

    new_items: List[Dict[str, Any]] = []
    scanned_messages = 0
    MAX_SCAN_LIMIT = 5000  # Scan up to 5000 messages back into history

    print(f"Scanning channel history (looking for {TARGET_COUNT} new uncataloged wallpapers)...")

    iter_kwargs: Dict[str, Any] = {"limit": MAX_SCAN_LIMIT}
    if cursor_id:
        iter_kwargs["offset_id"] = cursor_id

    oldest_scanned_id = cursor_id or 0

    async for msg in client.iter_messages(entity, **iter_kwargs):
        scanned_messages += 1
        if oldest_scanned_id == 0 or msg.id < oldest_scanned_id:
            oldest_scanned_id = msg.id

        if not msg.media or not isinstance(msg.media, MessageMediaDocument):
            continue

        item_id = f"tg_{msg.id}"
        if item_id in existing_ids:
            continue

        # Check date filter
        msg_date = msg.date
        if msg_date.tzinfo is None:
            msg_date = msg_date.replace(tzinfo=timezone.utc)
        msg_date_utc = msg_date.astimezone(timezone.utc).date()

        if SKIP_TODAY and msg_date_utc == today_utc:
            continue

        doc = msg.media.document
        mime = doc.mime_type or ""
        doc_size = getattr(doc, "size", 0)

        filename = None
        has_video_attr = False
        for attr in doc.attributes:
            if isinstance(attr, DocumentAttributeFilename):
                filename = attr.file_name
            elif isinstance(attr, DocumentAttributeVideo):
                has_video_attr = True

        media_type = None
        ext = None
        if filename:
            lower_fn = filename.lower()
            if lower_fn.endswith((".jpg", ".jpeg")):
                media_type = "image"
                ext = "jpg"
            elif lower_fn.endswith(".png"):
                media_type = "image"
                ext = "png"
            elif lower_fn.endswith(".mp4"):
                media_type = "video"
                ext = "mp4"

        if not media_type:
            if "video" in mime or has_video_attr:
                media_type = "video"
                ext = "mp4"
            elif "image" in mime:
                media_type = "image"
                ext = "png" if "png" in mime else "jpg"

        if not media_type or not ext:
            continue

        # Strictly ignore low-res stream previews (< 1.2MB) to prevent duplicate live wallpapers
        if media_type == "video" and doc_size < 1_200_000:
            print(f"Skipping low-res video preview ({doc_size} bytes): msg_id={msg.id}")
            continue

        full_filename = f"{item_id}.{ext}"
        thumb_filename = f"{item_id}.webp"
        full_path = os.path.join(FULL_DIR, full_filename)
        thumb_path = os.path.join(THUMB_DIR, thumb_filename)

        print(
            f"Downloading msg #{msg.id} [{msg_date_utc}] ({media_type.upper()}: {full_filename})..."
        )
        downloaded = await msg.download_media(file=full_path)
        if not downloaded or not os.path.exists(full_path):
            print(f"Failed to download media for msg #{msg.id}")
            continue

        # Check cryptographic content hash (MD5) to avoid duplicate files
        content_hash = calculate_file_md5(full_path)
        if content_hash in existing_hashes:
            print(f"Duplicate content detected by hash ({content_hash}). Skipping msg #{msg.id}")
            if os.path.exists(full_path):
                os.remove(full_path)
            continue

        # Generate thumbnail
        thumb_success = False
        if media_type == "image":
            thumb_success = generate_image_thumbnail(full_path, thumb_path)
        elif media_type == "video":
            thumb_success = generate_video_thumbnail(full_path, thumb_path)

        if not thumb_success:
            print(f"Failed to generate thumbnail for {full_filename}")
            if os.path.exists(full_path):
                os.remove(full_path)
            continue

        # Robust visual deduplication by perceptual hash (dHash)
        dhash = calculate_image_dhash(thumb_path)
        if dhash is not None:
            if dhash in existing_dhashes:
                print(f"Duplicate content detected by perceptual visual hash ({hex(dhash)}). Skipping msg #{msg.id}")
                if os.path.exists(full_path):
                    os.remove(full_path)
                if os.path.exists(thumb_path):
                    os.remove(thumb_path)
                continue

            # Check near-identical visual hash against recent items (consecutive Telegram posts)
            is_dup = False
            for recent in recent_items[-15:]:
                recent_dhash = recent.get("dhash")
                recent_msg_id = recent.get("msg_id", 0)
                if recent_dhash is not None:
                    dist = hamming_distance(dhash, recent_dhash)
                    if abs(msg.id - recent_msg_id) <= 5 and dist <= 2:
                        print(
                            f"Duplicate visual content detected in near messages "
                            f"(msg #{msg.id} vs #{recent_msg_id}, Hamming distance={dist}). Skipping."
                        )
                        if os.path.exists(full_path):
                            os.remove(full_path)
                        if os.path.exists(thumb_path):
                            os.remove(thumb_path)
                        is_dup = True
                        break
            if is_dup:
                continue

        # Extract title from message text or caption if available
        title = ""
        if msg.text:
            lines = [line.strip() for line in msg.text.strip().split("\n") if line.strip()]
            if lines:
                first_line = lines[0]
                # Strip Telegram hashtags (e.g., #4k, #wallpaper, #art)
                clean_line = re.sub(r"#\w+", "", first_line).strip()
                clean_line = re.sub(r"\s+", " ", clean_line).strip()
                if clean_line and len(clean_line) <= 40:
                    title = clean_line

        if not title:
            title = f"Live Wallpaper #{msg.id}" if media_type == "video" else f"Papel de Parede #{msg.id}"

        new_entry = {
            "id": item_id,
            "name": title,
            "author": "Wallpaper HD/4K (Amanda)",
            "author_url": "https://t.me/amandinhawallpaper",
            "thumbnail_url": f"{RAW_BASE_URL}/thumbnails/{thumb_filename}",
            "full_url": f"{RAW_BASE_URL}/full/{full_filename}",
            "primary_color": "#121212",
            "category": "live" if media_type == "video" else "images",
            "type": media_type,
            "is_live": media_type == "video",
            "tags": ["live", "video"] if media_type == "video" else ["image", "hd", "4k"],
            "content_hash": content_hash,
        }

        if dhash is not None:
            new_entry["visual_hash"] = f"{dhash:016x}"
            existing_dhashes.add(dhash)

        new_items.append(new_entry)
        existing_ids.add(item_id)
        existing_hashes.add(content_hash)
        recent_items.append({"msg_id": msg.id, "dhash": dhash, "size": doc_size})
        print(f" -> [{len(new_items)}/{TARGET_COUNT}] Processed: {title} ({media_type})")

        if len(new_items) >= TARGET_COUNT:
            print(f"Successfully reached target count ({TARGET_COUNT})! Halting history scan.")
            break

    await client.disconnect()

    if oldest_scanned_id > 0:
        catalog["backfill_cursor_id"] = oldest_scanned_id
        print(f"Updated backfill cursor in catalog to #{oldest_scanned_id}")

    if not new_items:
        print("\nNo new wallpapers were downloaded during this backfill run.")
        if populated > 0 or oldest_scanned_id > 0:
            with open(CATALOG_PATH, "w", encoding="utf-8") as f:
                json.dump(catalog, f, indent=2, ensure_ascii=False)
                f.write("\n")
        return

    # If prepend is set, put them at the top so launcher users see them as new; otherwise sort
    if PREPEND_BACKFILLED:
        catalog["wallpapers"] = new_items + existing_wallpapers
    else:
        all_wallpapers = new_items + existing_wallpapers

        def get_sort_key(item: Dict[str, Any]) -> int:
            item_id = item.get("id", "")
            if item_id.startswith("tg_"):
                num_part = item_id[3:]
                if num_part.isdigit():
                    return int(num_part)
            return 0

        all_wallpapers.sort(key=get_sort_key, reverse=True)
        catalog["wallpapers"] = all_wallpapers

    # Save newly added item IDs for priority classification
    new_ids = [item["id"] for item in new_items]
    tmp_path = os.path.join(BASE_DIR, "wallpapers", ".newly_added_ids.json")
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(new_ids, f)
    except Exception as e:
        print(f"Notice: Could not write .newly_added_ids.json: {e}")

    with open(CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"\nDone! Added {len(new_items)} new historical wallpapers to catalog.json.")
    print(f"Total catalog size is now: {len(catalog['wallpapers'])} wallpapers.")


if __name__ == "__main__":
    asyncio.run(main())
